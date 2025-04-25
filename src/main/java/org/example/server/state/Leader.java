package org.example.server.state;

import org.example.logger.FileLogger;
import org.example.logger.LogEntry;
import org.example.message.AppendEntries;
import org.example.message.MessageType;
import org.example.raft.Ack;
import org.example.raft.Beats;
import org.example.raft.ClusterRegistry;
import org.example.store.inMemoryStore;
import org.example.message.RequestMessage;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.*;

/**
 * Leader implementation with correct Raft append-entries, log replication, commit, and durability.
 */
public class Leader implements CurrState {
    private final BlockingDeque<RequestMessage> writeQueue;
    private ArrayList<BlockingDeque<RequestMessage>> followers;
    private inMemoryStore store;
    private final ArrayList<BlockingDeque<RequestMessage>> beatsQueues;
    private final BlockingDeque<Ack> ackQueue;
    private final Integer nodeId;
    private final ClusterRegistry registry = ClusterRegistry.getInstance();
    private FileLogger logger;

    private int currentTerm;
    private int votedFor;
    private int leaderCommitIndex = -1;
    private int lastAppliedIndex = -1;
    private ArrayList<LogEntry> log = new ArrayList<>();

    // nextIndex and matchIndex per follower
    private HashMap<Integer, Integer> nextIndex = new HashMap<>();
    private HashMap<Integer, Integer> matchIndex = new HashMap<>();

    public Leader(int nodeId) {
        this.nodeId = nodeId;
        this.writeQueue = registry.getLeaderQueue();
        this.followers = registry.getAllFollowerQueues();
        this.beatsQueues = registry.getAllFollowerBeatsQueues();
        this.ackQueue = new LinkedBlockingDeque<Ack>(100);

        System.out.println("[Leader-" + nodeId + "] Created with " + followers.size() + " followers");

        // initialize indices for each follower
        for (int i = 0; i < followers.size(); i++) {
            nextIndex.put(i, 0);
            matchIndex.put(i, -1);
        }
    }

    public void setStore(inMemoryStore store) {
        this.store = store;
        System.out.println("[Leader-" + nodeId + "] Store set");
    }

    private RequestMessage readFromClient() throws InterruptedException {
        RequestMessage msg = writeQueue.take();
        if (msg != null) {
            System.out.println("[Leader-" + nodeId + "] Received client request: " +
                    msg.getMsgType());
        }
        return msg;
    }

    /**
     * Send AppendEntries RPC to a follower.
     */
    private void sendAppendEntriesToFollower(int followerId) throws InterruptedException {
        int prevIndex = nextIndex.get(followerId) - 1;
        int prevTerm = (prevIndex >= 0 ? log.get(prevIndex).getTerm() : 0);

        // entries to send
        ArrayList<LogEntry> entriesToSend = new ArrayList<>();
        for (int i = nextIndex.get(followerId); i < log.size(); i++) {
            entriesToSend.add(log.get(i));
        }

        System.out.println("[Leader-" + nodeId + "] Sending AppendEntries to follower " + followerId +
                ", prevIndex=" + prevIndex + ", prevTerm=" + prevTerm +
                ", entries=" + entriesToSend.size() + ", nextIndex=" + nextIndex.get(followerId));

        AppendEntries rpc = new AppendEntries(
                currentTerm,
                nodeId,
                prevIndex,
                prevTerm,
                entriesToSend,
                leaderCommitIndex,
                MessageType.appendEntries,
                ackQueue
        );
        RequestMessage msg = new RequestMessage(MessageType.appendEntries, rpc);
        followers.get(followerId).putFirst(msg);
    }

    private void broadcastAppendEntries() throws InterruptedException {
        System.out.println("[Leader-" + nodeId + "] Broadcasting AppendEntries to " + followers.size() + " followers");
        this.ackQueue.clear();
        for (int i = 0; i < followers.size(); i++) {
            sendAppendEntriesToFollower(i);
        }
    }

    /**
     * Count acknowledgments and update nextIndex/matchIndex.
     */
    private boolean hasQuorum() {
        int required = (followers.size() + 1) / 2 + 1;
        int success = 1;  // leader self vote
        int failure = 0;
        long deadline = System.currentTimeMillis() + 500;

        System.out.println("[Leader-" + nodeId + "] Waiting for quorum, required=" + required);
//        success + failure < required &&
        while (System.currentTimeMillis() < deadline && success + failure<5) {
            try {
                Ack ack = ackQueue.poll(deadline - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
                if (ack == null) {
                    System.out.println("Oops time out!!!!");
                    break;
                }

                if (ack.getSuccess() == 1) {
                    success++;
                    int idx = ack.getMatchIndex();
                    int oldNextIndex = nextIndex.get(ack.getNodeId());
                    nextIndex.put(ack.getNodeId(), idx + 1);
                    matchIndex.put(ack.getNodeId(), idx);

                    System.out.println("[Leader-" + nodeId + "] Received success ACK from node " + ack.getNodeId() +
                            ", matchIndex=" + idx + ", updated nextIndex " + oldNextIndex + "->" + (idx + 1) +
                            ", success count=" + success);
                } else {
                    failure++;
                    int oldNextIndex = nextIndex.get(ack.getNodeId());
                    nextIndex.put(ack.getNodeId(), Math.max(0, oldNextIndex - 1));

                    System.out.println("[Leader-" + nodeId + "] Received failure ACK from node " + ack.getNodeId() +
                            ", decremented nextIndex " + oldNextIndex + "->" + nextIndex.get(ack.getNodeId()) +
                            ", failure count=" + failure);
                    try {
                        sendAppendEntriesToFollower(ack.getNodeId());
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            } catch (InterruptedException e) {
                // Thread.currentThread().interrupt();
                System.out.println("[Leader-" + nodeId + "] Interrupted while waiting for ACKs");
                break;
            }
        }

        boolean hasQuorum = success >= required;
        System.out.println("[Leader-" + nodeId + "] Quorum check: success=" + success +
                ", failures=" + failure + ", required=" + required +
                ", result=" + (hasQuorum ? "SUCCESS" : "FAILURE"));

        return hasQuorum;
    }

    @Override
    public void waitForAction() throws InterruptedException, IOException {
        System.out.println("[Leader-" + nodeId + "] Starting in term " + currentTerm +
                ", log size=" + log.size());

        // start heartbeat thread
        Thread beat = new Thread(new Beats(NodeRole.leader, beatsQueues, nodeId));
        beat.start();

        while (true) {
            RequestMessage req = readFromClient();
            JSONObject j = req.getJson();
            String key = j.getString("key");
            String value = j.getString("value");

            System.out.println("[Leader-" + nodeId + "] Processing PUT request: key=" + key +
                    ", value=" + value);

            // 1) Append to local log
            this.followers=registry.getAllFollowerQueues();
            int entryIndex = log.size();
            LogEntry entry = new LogEntry(entryIndex, currentTerm, key, value);
            log.add(entry);
            System.out.println("[Leader-" + nodeId + "] Appended entry to local log at index " + entryIndex);

            // 2) Replicate to followers
            broadcastAppendEntries();

            // 3) Wait for majority
            if (hasQuorum()) {
                // commit
                ArrayList<Integer> matchList = new ArrayList<>(matchIndex.values());
                matchList.add(log.size() - 1); // include leader's own index
                matchList.sort((a, b) -> b - a); // descending

                int majorityMatchIndex = matchList.get((followers.size()) / 2);
                System.out.println("[Leader-" + nodeId + "] Majority match index: " + majorityMatchIndex +
                        ", current leaderCommitIndex=" + leaderCommitIndex);

                if (majorityMatchIndex > leaderCommitIndex && log.get(majorityMatchIndex).getTerm() == currentTerm) {
                    int oldCommitIndex = leaderCommitIndex;
                    leaderCommitIndex = majorityMatchIndex;
                    System.out.println("[Leader-" + nodeId + "] Advanced commit index: " + oldCommitIndex +
                            " -> " + leaderCommitIndex);

                    // apply to state machine
                    for (int i = lastAppliedIndex + 1; i <= leaderCommitIndex; i++) {
                        LogEntry e = log.get(i);
                        String k = e.getOperation().getString("key");
                        String v = e.getOperation().getString("value");
                        store.updateKeyVal(k, v);
                        logger.writeToLog(log.get(i));
                        System.out.println("[Leader-" + nodeId + "] Applied entry " + i +
                                ": key=" + k + ", value=" + v);
                    }

                    lastAppliedIndex = leaderCommitIndex;
                    System.out.println("[Leader-" + nodeId + "] Advanced lastApplied index to " + lastAppliedIndex);

                    writeToDb(leaderCommitIndex);
                }
            } else {
                System.out.println("[Leader-" + nodeId + "] Failed to achieve quorum, retrying");
                // Could add a retry mechanism here
            }
        }
    }

    private void writeToDb(int leaderCommitIndex) throws InterruptedException {
        JSONObject json = new JSONObject();
        json.put("commitIndex", leaderCommitIndex);
        RequestMessage msg = new RequestMessage(json, MessageType.putMessage);

        System.out.println("[Leader-" + nodeId + "] Sending commit notification to followers, commitIndex=" + leaderCommitIndex);

        for (int i = 0; i < followers.size(); i++) {
            followers.get(i).putFirst(msg);
        }
    }

    @Override
    public void setLogger(FileLogger logger) {
        this.logger = logger;
        System.out.println("[Leader-" + nodeId + "] Logger set");
    }

    @Override
    public void setCommitIndex(int commitIndex) {
        this.leaderCommitIndex = commitIndex;
        System.out.println("[Leader-" + nodeId + "] CommitIndex set to " + commitIndex);
    }

    @Override
    public void setLastApplied(int lastApplied) {
        this.lastAppliedIndex = lastApplied;
        System.out.println("[Leader-" + nodeId + "] LastApplied set to " + lastApplied);
    }

    @Override
    public void setMetaData(int votedFor, int currentTerm, ArrayList<LogEntry> log, HashMap<Integer, Integer> matchIndex, HashMap<Integer, Integer> nextIndex) {
        this.votedFor = votedFor;
        this.currentTerm = currentTerm;
        this.log = log;
        this.matchIndex = matchIndex;
        this.nextIndex = nextIndex;

        System.out.println("[Leader-" + nodeId + "] Metadata set: term=" + currentTerm +
                ", votedFor=" + votedFor + ", logSize=" + log.size());

        // Initialize nextIndex and matchIndex for all followers
        for (Integer i : nextIndex.keySet()) {
            int oldVal = nextIndex.get(i);
            nextIndex.put(i, log.size());
            System.out.println("[Leader-" + nodeId + "] Initialized nextIndex for follower " + i +
                    ": " + oldVal + " -> " + log.size());
        }
        for (Integer i : matchIndex.keySet()) {
            int oldVal = matchIndex.get(i);
            matchIndex.put(i, -1);
            System.out.println("[Leader-" + nodeId + "] Initialized matchIndex for follower " + i +
                    ": " + oldVal + " -> -1");
        }
    }
}