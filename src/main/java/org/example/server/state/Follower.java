package org.example.server.state;

import org.example.logger.FileLogger;
import org.example.logger.LogEntry;
import org.example.message.AppendEntries;
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
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.TimeUnit;

import static java.lang.Thread.sleep;
import static org.example.message.MessageType.*;

public class Follower implements CurrState {
    private BlockingDeque<RequestMessage> readQueue;
    private BlockingDeque<RequestMessage> beatsQueue;
    private inMemoryStore store;
    private HashMap<Integer, BlockingDeque<ReplyMessage>> clientReplyQueue = new HashMap<>();
    private Integer nodeId;
    private ClusterRegistry registry = ClusterRegistry.getInstance();
    private FileLogger logger;
    private int currentTerm;
    private int votedFor;
    private ArrayList<LogEntry> log;

    private int commitIndex = -1;
    private int lastApplied = -1;      // ← track what we've already applied

    // matchIndex / nextIndex shared maps
    private HashMap<Integer, Integer> nextIndex;
    private HashMap<Integer, Integer> matchIndex;

    public Follower(int nodeId) {
        this.nodeId = nodeId;
        this.readQueue = registry.getFollowerQueue(nodeId);
        this.beatsQueue = registry.getFollowerBeatsQueue(nodeId);
        System.out.println("[Follower-" + nodeId + "] Created");
    }

    private RequestMessage readFromClient() throws InterruptedException {
        return readQueue.poll(5, TimeUnit.MILLISECONDS);
    }

    private boolean checkClientConnExists(int clientID) {
        return clientReplyQueue.containsKey(clientID);
    }

    public void establishConn(int clientID, BlockingDeque<ReplyMessage> replyQueue) {
        if (!checkClientConnExists(clientID)) {
            clientReplyQueue.put(clientID, replyQueue);
            System.out.println("[Follower-" + nodeId + "] Established connection with client " + clientID);
        }
    }

    public void replyToClient(int clientID, ReplyMessage msg) throws InterruptedException {
        clientReplyQueue.get(clientID).put(msg);
    }

    public void setStore(inMemoryStore store) {
        this.store = store;
        System.out.println("[Follower-" + nodeId + "] Store set");
    }

    @Override
    public void waitForAction() throws InterruptedException, IOException {
        System.out.println("[Follower-" + nodeId + "] Starting in term " + currentTerm);
        Thread beat = new Thread(new Beats(NodeRole.follower, beatsQueue, nodeId));
        beat.start();
        int cnt = 0;
        while (true) {
            if (nodeId == 1 && cnt == 0) {
                registry.updateRole(nodeId, NodeRole.candidate);
                sleep(30000, TimeUnit.MILLISECONDS.ordinal());
                registry.updateRole(nodeId, NodeRole.follower);
                this.readQueue = registry.getFollowerQueue(nodeId);

            }
            cnt++;
            RequestMessage rpc = readFromClient();
            if (rpc == null) continue;

            if (registry.getRole(nodeId) != NodeRole.follower) {
                System.out.println("[Follower-" + nodeId + "] Role changed, exiting follower loop");
                break;
            }

            if (rpc.getMsgType() == appendEntries) {
                AppendEntries ae = rpc.getAppendEntries();
                BlockingDeque<Ack> ackQ = ae.getAckQueue();

                System.out.println("[Follower-" + nodeId + "] Received AppendEntries from leader " + registry.getLeaderId() +
                        ", term=" + ae.getCurrentTerm() + ", prevLogIndex=" + ae.getPrevLogIndex() +
                        ", entries=" + ae.getEntries().size());

                // 1) reject stale term
                if (ae.getCurrentTerm() < currentTerm) {
                    System.out.println("[Follower-" + nodeId + "] Rejecting stale term " + ae.getCurrentTerm() +
                            " (current=" + currentTerm + ")");
                    ackQ.put(new Ack(nodeId, 0, -1));
                    continue;
                }
                currentTerm = ae.getCurrentTerm();

                // 2) log consistency
                int prevIdx = ae.getPrevLogIndex();
                int prevTerm = ae.getPrevLogTerm();
                if (prevIdx >= log.size() ||
                        (prevIdx >= 0 && log.get(prevIdx).getTerm() != prevTerm)) {
                    System.out.println("[Follower-" + nodeId + "] Log inconsistency at index " + prevIdx +
                            ", my log size=" + log.size());
                    ackQ.put(new Ack(nodeId, 0, -1));
                    continue;
                }

                // 3) append new entries
                int match = prevIdx;
                int turncateIndex = prevIdx + 1;
                while (log.size() > turncateIndex) {
                    log.remove(log.size() - 1);
                }
                if (!ae.getEntries().isEmpty()) {
                    System.out.println("[Follower-" + nodeId + "] Appending " + ae.getEntries().size() + " entries");
                }

                for (LogEntry entry : ae.getEntries()) {
                    int idx = entry.getIndex();
                    log.add(entry);
                    match = idx;
                    System.out.println("[Follower-" + nodeId + "] Added entry at index " + idx +
                            ", term=" + entry.getTerm());

                }

                // 4) ACK success
                System.out.println("[Follower-" + nodeId + "] Sending success ACK, matchIndex=" + match);
                ackQ.put(new Ack(nodeId, 1, match));
                continue;
            }

            // client-driven commit notification
            JSONObject jsonReq = rpc.getJson();
            if (rpc.getMsgType() == putMessage) {
                int leaderCommit = jsonReq.getInt("commitIndex");
                System.out.println("[Follower-" + nodeId + "] Received commit notification, leaderCommit=" + leaderCommit);
                commit(leaderCommit);
            } else if (rpc.getMsgType() == getMessage) {
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
                System.out.println("[Follower-" + nodeId + "] Sent GET response for key=" + key +
                        ", value=" + value);
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