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
    private long beatTime;
    private int currentTerm;
    private int votedFor;
    private int leaderCommitIndex = -1;
    private int lastAppliedIndex = -1;
    private int isLeader;
    ScheduledExecutorService hbScheduler = Executors.newSingleThreadScheduledExecutor();

    private ArrayList<LogEntry> log = new ArrayList<>();

    // nextIndex and matchIndex per follower
    private HashMap<Integer, Integer> nextIndex = new HashMap<>();
    private HashMap<Integer, Integer> matchIndex = new HashMap<>();

    public Leader(int nodeId) {
        this.nodeId = nodeId;
        this.writeQueue = registry.getLeaderQueue();
        this.isLeader=1;
        this.followers = registry.getAllFollowerQueues();
        this.beatsQueues = registry.getAllFollowerBeatsQueues();
        this.ackQueue = new LinkedBlockingDeque<>(100);
        this.beatTime= (15 + (long)(Math.random() * 10));

        System.out.println("[Leader-" + nodeId + "] Initializing heartbeat scheduler with beat time: " + beatTime + "ms");
        // initialize indices for each follower
        ArrayList<Integer>fids=registry.getFollowerId();
        for (int i = 0; i < fids.size(); i++) {
            nextIndex.put(fids.get(i), 0);
            matchIndex.put(fids.get(i), -1);
            System.out.println("[Leader-" + nodeId + "] Initialized indices for follower " + i +
                    ": nextIndex=0, matchIndex=-1");
        }
        System.out.println("[Leader-" + nodeId + "] Created with " + followers.size() + " followers");
    }

    public void setStore(inMemoryStore store) {
        this.store = store;
        System.out.println("[Leader-" + nodeId + "] Store set");
    }

    private RequestMessage readFromClient() throws InterruptedException {
//        System.out.println("[Leader-" + nodeId + "] Waiting for client requests...");
        RequestMessage msg = writeQueue.poll(5, TimeUnit.MILLISECONDS);
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
//        System.out.println("[Leader-" + nodeId + "] Preparing AppendEntries for follower " + followerId);

        int prevIndex = nextIndex.get(followerId) - 1;
        int prevTerm = (prevIndex >= 0 ? log.get(prevIndex).getTerm() : 0);

        // entries to send
        ArrayList<LogEntry> entriesToSend = new ArrayList<>();
        for (int i = nextIndex.get(followerId); i < log.size(); i++) {
            entriesToSend.add(log.get(i));
        }

//        System.out.println("[Leader-" + nodeId + "] Sending AppendEntries to follower " + followerId +
//                ", prevIndex=" + prevIndex + ", prevTerm=" + prevTerm +
//                ", entries=" + entriesToSend.size() + ", nextIndex=" + nextIndex.get(followerId));

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
//        System.out.println("[Leaderader-" + nodeId + "] Putting AppendEntries in follower " + followerId + "'s queue");
        registry.getFollowerQueue(followerId).putFirst(msg);
    }

    private void broadcastAppendEntries() throws InterruptedException {
        System.out.println("[Leader-" + nodeId + "] Broadcasting AppendEntries to " + followers.size() + " followers");
        System.out.println("[Leader-" + nodeId + "] Clearing previous ACK queue");
        this.ackQueue.clear();
        ArrayList<Integer>followerID = registry.getFollowerId();
        System.out.println("[Leader-" + nodeId + "] Found " + followerID.size() + " follower IDs");
        for (int i = 0; i < followerID.size(); i++) {
            System.out.println("[Leader-" + nodeId + "] Starting AppendEntries broadcast to follower " + followerID.get(i));
            sendAppendEntriesToFollower(followerID.get(i));
        }
    }
    /**
     * Count acknowledgments and update nextIndex/matchIndex.
     */
    private boolean hasQuorum() {
        int required = (followers.size() + 1) / 2 + 1;
        int success = 1;  // leader self vote
        int failure = 0;
        long deadline = System.currentTimeMillis() + 5000;

//        System.out.println("[Leader-" + nodeId + "] Waiting for quorum, required=" + required);
//        System.out.println("[Leader-" + nodeId + "] Quorum deadline set to " + deadline + " (5000ms timeout)");
//        success + failure < required &&
        while (System.currentTimeMillis() < deadline && success + failure <= followers.size()) {
            try {
//                System.out.println("[Leader-" + nodeId + "] Polling for ACKs, current status: success=" +
//                        success + ", failure=" + failure);
                Ack ack = ackQueue.poll(deadline - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
                if (ack == null) {
//                    System.out.println("[Leader-" + nodeId + "] Oops time out!!!!");
                    break;
                }

                if (ack.getSuccess() == 1) {
                    success++;
                    int idx = ack.getMatchIndex();
                    nextIndex.put(ack.getNodeId(), idx + 1);
                    matchIndex.put(ack.getNodeId(), idx);

//                    System.out.println("[Leader-" + nodeId + "] Received success ACK from node " + ack.getNodeId() +
//                            ", matchIndex=" + idx + ", updated nextIndex " + oldNextIndex + "->" + (idx + 1) +
//                            ", success count=" + success);
                } else {
                    failure++;
                    if(ack.getCurrentTerm()>currentTerm){
//                        System.out.println("[Leader-" + nodeId + "] Received ACK with higher term: current=" +
//                                currentTerm + ", received=" + ack.getCurrentTerm() + ", stepping down");
                        currentTerm=ack.getCurrentTerm();
                        isLeader=0;
                        return false;
                    }
                    nextIndex.compute(ack.getNodeId(), (k, oldNextIndex) -> Math.max(0, oldNextIndex - 1));

//                    System.out.println("[Leader-" + nodeId + "] Received failure ACK from node " + ack.getNodeId() +
//                            ", decremented nextIndex " + oldNextIndex + "->" + nextIndex.get(ack.getNodeId()) +
//                            ", failure count=" + failure);
                    try {
//                        System.out.println("[Leader-" + nodeId + "] Retrying AppendEntries to follower " + ack.getNodeId());
                        sendAppendEntriesToFollower(ack.getNodeId());
                    } catch (InterruptedException e) {
//                        System.out.println("[Leader-" + nodeId + "] Interrupted while retrying AppendEntries: " + e.getMessage());
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
//        System.out.println("[Leader-" + nodeId + "] Quorum check: success=" + success +
//                ", failures=" + failure + ", required=" + required +
//                ", result=" + (hasQuorum ? "SUCCESS" : "FAILURE"));

        return hasQuorum;
    }
    public void sendHeartBeat() throws InterruptedException {
//        System.out.println("[Leader-" + nodeId + "] Starting heartbeat cycle");
        this.followers=registry.getAllFollowerBeatsQueues();
        ArrayList<Integer>followerID = registry.getFollowerId();
//        System.out.println("[Leader-" + nodeId + "] Sending heartbeats to " + followerID.size() + " followers");

        for (int i = 0; i < followerID.size(); i++) {
            int prevIndex = nextIndex.get(followerID.get(i)) - 1;
            RequestMessage msg = getRequestMessage(prevIndex);
//            System.out.println("[Leader-" + nodeId + "] Sending heartbeat to follower " + followerID.get(i));
            registry.getFollowerBeatsQueue(followerID.get(i)).offer(msg);
        }
//        System.out.println("[Leader-" + nodeId + "] Checking for quorum after heartbeats");
        hasQuorum();

        // Reschedule next heartbeat
//        System.out.println("[Leader-" + nodeId + "] Scheduling next heartbeat in " + beatTime + "ms");

    }

    private RequestMessage getRequestMessage(int prevIndex) {
        int prevTerm = (prevIndex >= 0 ? log.get(prevIndex).getTerm() : 0);
        ArrayList<LogEntry> entries = new ArrayList<>();
//            System.out.println("[Leader-" + nodeId + "] Creating heartbeat for follower " + followerID.get(i) +
//                    ", prevIndex=" + prevIndex + ", prevTerm=" + prevTerm);

        AppendEntries rpc = new AppendEntries(currentTerm,nodeId, prevIndex,prevTerm,entries,leaderCommitIndex,MessageType.appendEntries,ackQueue);
        RequestMessage msg = new RequestMessage(MessageType.appendEntries, rpc);
        return msg;
    }

    @Override
    public void waitForAction() throws InterruptedException, IOException {
        System.out.println("[Leader-" + nodeId + "] Starting in term " + currentTerm +
                ", log size=" + log.size());

        hbScheduler.scheduleWithFixedDelay(()->{
            try {
                sendHeartBeat();
            } catch (InterruptedException e) {
                System.out.println("[Leader-" + nodeId + "] Error during heartbeat: " + e.getMessage());
                throw new RuntimeException(e);
            }
        },0,beatTime,TimeUnit.MILLISECONDS);
        while (isLeader==1) {
            RequestMessage req = readFromClient();
            if (registry.getRole(nodeId) != NodeRole.leader) {
                System.out.println("[Leader-" + nodeId + "] No longer the leader, shutting down heartbeat scheduler");
                hbScheduler.shutdownNow();
                break;
            }
            if (req == null) continue;
            JSONObject j = req.getJson();
            String key = j.getString("key");
            String value = j.getString("value");

            System.out.println("[Leader-" + nodeId + "] Processing PUT request: key=" + key +
                    ", value=" + value);

            // 1) Append to local log
            this.followers = registry.getAllFollowerQueues();
            int entryIndex = log.size();
            LogEntry entry = new LogEntry(entryIndex, currentTerm, key, value);
            log.add(entry);
            System.out.println("[Leader-" + nodeId + "] Appended entry to local log at index " + entryIndex);

            // 2) Replicate to followers
            System.out.println("[Leader-" + nodeId + "] Starting log replication to followers");
            broadcastAppendEntries();

            // 3) Wait for majority
            System.out.println("[Leader-" + nodeId + "] Waiting for quorum on log entry " + entryIndex);
            if (hasQuorum()) {
                // commit
                System.out.println("[Leader-" + nodeId + "] Quorum achieved, calculating majority match index");
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
                    System.out.println("[Leader-" + nodeId + "] Applying entries from index " +
                            (lastAppliedIndex + 1) + " to " + leaderCommitIndex);
                    for (int i = lastAppliedIndex + 1; i <= leaderCommitIndex; i++) {
                        LogEntry e = log.get(i);
                        String k = e.getOperation().getString("key");
                        String v = e.getOperation().getString("value");
                        System.out.println("[Leader-" + nodeId + "] Applying to store: key=" + k + ", value=" + v);
                        store.updateKeyVal(k, v);
                        System.out.println("[Leader-" + nodeId + "] Writing to log: entry " + i);
                        logger.writeToLog(log.get(i));
                        System.out.println("[Leader-" + nodeId + "] Applied entry " + i +
                                ": key=" + k + ", value=" + v);
                    }

                    lastAppliedIndex = leaderCommitIndex;
                    System.out.println("[Leader-" + nodeId + "] Advanced lastApplied index to " + lastAppliedIndex);

                    System.out.println("[Leader-" + nodeId + "] Notifying followers of new commit index: " + leaderCommitIndex);
                    writeToDb(leaderCommitIndex);
                }
            } else {
                System.out.println("[Leader-" + nodeId + "] Failed to achieve quorum, retrying");
                // Could add a retry mechanism here
            }
        }
        System.out.println("[Leader-" + nodeId + "] Exiting leader loop, isLeader=" + isLeader);
    }

    private void writeToDb(int leaderCommitIndex) throws InterruptedException {
        System.out.println("[Leader-" + nodeId + "] Creating commit notification with commitIndex=" + leaderCommitIndex);
        JSONObject json = new JSONObject();
        json.put("commitIndex", leaderCommitIndex);
        RequestMessage msg = new RequestMessage(json, MessageType.putMessage);

        System.out.println("[Leader-" + nodeId + "] Sending commit notification to followers, commitIndex=" + leaderCommitIndex);

        for (int i = 0; i < followers.size(); i++) {
            System.out.println("[Leader-" + nodeId + "] Sending commit notification to follower #" + i);
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
            matchIndex.put(i, log.size()-1);
            System.out.println("[Leader-" + nodeId + "] Initialized matchIndex for follower " + i +
                    ": " + oldVal + " -> " + (log.size()-1));
        }
    }
}