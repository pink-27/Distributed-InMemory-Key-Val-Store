package org.example.server.state;

import org.example.logger.FileLogger;
import org.example.logger.LogEntry;
import org.example.message.AppendEntries;
import org.example.message.MessageType;
import org.example.raft.Ack;
import org.example.raft.Beats;
import org.example.raft.ClusterRegistry;
import org.example.store.inMemoryStore;
import org.example.message.ReplyMessage;
import org.example.message.RequestMessage;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.*;

import static java.lang.Thread.sleep;
import static org.example.message.MessageType.*;

public class Follower implements CurrState {
    private final BlockingDeque<RequestMessage> readQueue;
    private final BlockingDeque<RequestMessage> beatsQueue;
    private inMemoryStore store;
    private final HashMap<Integer, BlockingDeque<ReplyMessage>> clientReplyQueue = new HashMap<>();
    private final Integer nodeId;
    private final ClusterRegistry registry = ClusterRegistry.getInstance();
    private FileLogger logger;
    private int currentTerm;
    private int votedFor;
    private ArrayList<LogEntry> log;
    private long leaderTimeout;
    private int commitIndex = -1;
    private int lastApplied = -1;      // ← track what we've already applied
    ScheduledExecutorService hbListenerScheduler = Executors.newSingleThreadScheduledExecutor();

    // matchIndex / nextIndex shared maps
    private HashMap<Integer, Integer> nextIndex;
    private HashMap<Integer, Integer> matchIndex;
    long deadline;
    public Follower(int nodeId) {
        this.nodeId = nodeId;
        this.readQueue = registry.getFollowerQueue(nodeId);
        this.beatsQueue = registry.getFollowerBeatsQueue(nodeId);
        this.leaderTimeout = 1000 + (long) (Math.random() * 150); // 1000–1150 ms
        System.out.println("[Follower-" + nodeId + "] Created");
    }

    private RequestMessage readFromClient() throws InterruptedException {
        return readQueue.poll(5, TimeUnit.MILLISECONDS);
    }


    public void setStore(inMemoryStore store) {
        this.store = store;
        System.out.println("[Follower-" + nodeId + "] Store set");
    }

    private boolean sendVoteRequest() throws InterruptedException {
//        ArrayList<BlockingDeque<RequestMessage>> followers = registry.getAllFollowerQueues();
        ArrayList<Integer> followers = registry.getFollowerId();
        for (int i = 0; i < followers.size(); i++) {
            if (registry.getFollowerQueue(followers.get(i)).hashCode() == readQueue.hashCode()) {
                followers.remove(i);
                break;
            }
        }
        // entries to send


        BlockingDeque<Ack> ackQueue = new LinkedBlockingDeque<>(100);
        AppendEntries rpc = new AppendEntries(
                currentTerm,
                nodeId,
                log.size() - 1,
                requestVote,
                ackQueue
        );
        RequestMessage msg = new RequestMessage(requestVote, rpc);
        for (int i = 0; i < followers.size(); i++) {
            registry.getFollowerQueue(followers.get(i)).putFirst(msg);
        }
        int votes = 1;
        while (!ackQueue.isEmpty()) {
            Ack ack = ackQueue.poll(1500, TimeUnit.MILLISECONDS);
            if (ack != null) {
                votes += ack.getSuccess();
            }
        }
        return votes >= 3;
    }

    public void handleAppendEntries(AppendEntries ae) throws InterruptedException {
        BlockingDeque<Ack> ackQ = ae.getAckQueue();

//                System.out.println("[Follower-" + nodeId + "] Received AppendEntries from leader " + registry.getLeaderId() +
//                        ", term=" + ae.getCurrentTerm() + ", prevLogIndex=" + ae.getPrevLogIndex() +
//                        ", entries=" + ae.getEntries().size());

        // 1) reject stale term
        if (ae.getCurrentTerm() < currentTerm) {
//                    System.out.println("[Follower-" + nodeId + "] Rejecting stale term " + ae.getCurrentTerm() +
//                            " (current=" + currentTerm + ")");
            ackQ.put(new Ack(nodeId, 0, -1, currentTerm));
            return;
        }
        currentTerm = ae.getCurrentTerm();

        // 2) log consistency
        int prevIdx = ae.getPrevLogIndex();
        int prevTerm = ae.getPrevLogTerm();
        if (prevIdx >= log.size() ||
                (prevIdx >= 0 && log.get(prevIdx).getTerm() != prevTerm)) {
//                    System.out.println("[Follower-" + nodeId + "] Log inconsistency at index " + prevIdx +
//                            ", my log size=" + log.size());
            ackQ.put(new Ack(nodeId, 0, -1, currentTerm));
            return;
        }

        // 3) append new entries
        int match = prevIdx;
        int turncateIndex = prevIdx + 1;
        while (log.size() > turncateIndex) {
            log.remove(log.size() - 1);
        }
        if (!ae.getEntries().isEmpty()) {
//                    System.out.println("[Follower-" + nodeId + "] Appending " + ae.getEntries().size() + " entries");
        }

        for (LogEntry entry : ae.getEntries()) {
            int idx = entry.getIndex();
            log.add(entry);
            match = idx;
//                    System.out.println("[Follower-" + nodeId + "] Added entry at index " + idx +
//                            ", term=" + entry.getTerm());

        }

        // 4) ACK success
//                System.out.println("[Follower-" + nodeId + "] Sending success ACK, matchIndex=" + match);
        ackQ.put(new Ack(nodeId, 1, match, currentTerm));
    }

    @Override
    public void waitForAction() throws InterruptedException, IOException {
        System.out.println("[Follower-" + nodeId + "] Starting in term " + currentTerm);
//        Thread beat = new Thread(new Beats(NodeRole.follower, beatsQueue, nodeId));
//        beat.start();
//        int cnt = 0;
        deadline = System.currentTimeMillis() + leaderTimeout;
        while (true) {
            RequestMessage rpc = readFromClient();
            RequestMessage beat = beatsQueue.poll();

            if (System.currentTimeMillis() > deadline) {
                registry.updateRole(nodeId, NodeRole.candidate);
            }

            if (registry.getRole(nodeId) != NodeRole.follower) {
                System.out.println("[Follower-" + nodeId + "] Role changed to Candidate, exiting follower loop");
                currentTerm+=1;
                boolean isLeader = sendVoteRequest();
                if (!isLeader) {
                    registry.updateRole(nodeId, NodeRole.follower);
                    currentTerm--;
                } else {
                    registry.updateRole(nodeId, NodeRole.leader);
                    break;
                }

            } else if (rpc != null && rpc.getMsgType() == requestVote) {
                int vote = 0;
                if (currentTerm == rpc.getAppendEntries().getCurrentTerm() && votedFor == -1) {
                    if (log.size() - 1 < rpc.getAppendEntries().getCandidateLogIndex()) {
                        vote = 1;
                    }
                }
                if (currentTerm < rpc.getAppendEntries().getCurrentTerm() && votedFor == -1) {
                    vote = 1;
                }
                Ack ack = new Ack(nodeId, vote, -1, currentTerm);
                votedFor=rpc.getAppendEntries().getLeaderID();
                rpc.getAppendEntries().getAckQueue().put(ack);
            }
            else if (rpc != null && rpc.getMsgType() == putMessage) {
                JSONObject jsonReq = rpc.getJson();

                int leaderCommit = jsonReq.getInt("commitIndex");
                System.out.println("[Follower-" + nodeId + "] Received commit notification, leaderCommit=" + leaderCommit);
                commit(leaderCommit);
            } else if (rpc!=null && rpc.getMsgType() == getMessage) {
                JSONObject jsonReq = rpc.getJson();
                int clientID = jsonReq.getInt("client");
                String key = jsonReq.getString("key");
                System.out.println("[Follower-" + nodeId + "] GET request from client " + clientID +
                        " for key=" + key);

                BlockingDeque<ReplyMessage> replyQ = rpc.getClientReplyQueue();
                String value = store.getValue(key);
                JSONObject rep = new JSONObject();
                rep.put("key", key);
                rep.put("value", value);
                replyQ.put(new ReplyMessage(rep));
//                System.out.println("[Follower-" + nodeId + "] Sent GET response for key=" + key +
//                        ", value=" + value);
            }
            if (rpc != null && rpc.getMsgType()==appendEntries) {
                handleAppendEntries(rpc.getAppendEntries());
                deadline = System.currentTimeMillis() + leaderTimeout;

            }
            else if (beat != null && beat.getMsgType()==appendEntries) {
                handleAppendEntries(beat.getAppendEntries());
                deadline = System.currentTimeMillis() + leaderTimeout;

            }

        }
    }

    /**
     * Applies all newly committed entries to state machine, advances lastApplied.
     */
    public void commit(int leaderCommit) throws IOException {
        int newCommit = Math.min(leaderCommit, log.size() - 1);
        if (newCommit > commitIndex) {
            System.out.println("[Follower-" + nodeId + "] Committing entries from " +
                    (commitIndex + 1) + " to " + newCommit);

            for (int i = commitIndex + 1; i <= newCommit; i++) {
                LogEntry e = log.get(i);
                String key = e.getOperation().getString("key");
                String value = e.getOperation().getString("value");
                store.updateKeyVal(key, value);
                System.out.println("[Follower-" + nodeId + "] Applied entry " + i +
                        ": key=" + key + ", value=" + value);
            }
            // advance both commitIndex and lastApplied
            commitIndex = newCommit;
            lastApplied = newCommit;
        }
    }

    @Override
    public void setLogger(FileLogger logger) {
        this.logger = logger;
        System.out.println("[Follower-" + nodeId + "] Logger set");
    }

    @Override
    public void setCommitIndex(int commitIndex) {
        this.commitIndex = commitIndex;
        System.out.println("[Follower-" + nodeId + "] CommitIndex set to " + commitIndex);
    }

    @Override
    public void setLastApplied(int lastApplied) {
        this.lastApplied = lastApplied;
        System.out.println("[Follower-" + nodeId + "] LastApplied set to " + lastApplied);
    }

    @Override
    public void setMetaData(int votedFor, int currentTerm, ArrayList<LogEntry> log, HashMap<Integer, Integer> matchIndex, HashMap<Integer, Integer> nextIndex) {
        this.votedFor = votedFor;
        this.currentTerm = currentTerm;
        this.log = log;
        this.matchIndex = matchIndex;
        this.nextIndex = nextIndex;
        System.out.println("[Follower-" + nodeId + "] Metadata set: term=" + currentTerm +
                ", votedFor=" + votedFor + ", logSize=" + log.size());
    }
}