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
    private Integer currentTerm;
    private Integer votedFor;
    private ArrayList<LogEntry> log;
    private long leaderTimeout;
    private int commitIndex = -1;
    private int lastApplied = -1;      // ← track what we've already applied

    // matchIndex / nextIndex shared maps
    private ConcurrentHashMap<Integer, Integer> nextIndex;
    private ConcurrentHashMap<Integer, Integer> matchIndex;
    long deadline;

    public Follower(int nodeId) {
        this.nodeId = nodeId;
        this.readQueue = registry.getFollowerQueue(nodeId);
        this.beatsQueue = registry.getBeatsQueue(nodeId);
        this.leaderTimeout = 5000 + (long) (Math.random() * 1500); // 5000–6150 ms
        this.votedFor = -1;
        System.out.println("[Follower-" + nodeId + "] Created");
    }

    private RequestMessage readFromClient() throws InterruptedException {
        return readQueue.poll(5, TimeUnit.MILLISECONDS);
    }


    public void setStore(inMemoryStore store) {
        this.store = store;
        System.out.println("[Follower-" + nodeId + "] Store set");
    }

    private boolean sendVoteRequest() {
        ArrayList<BlockingDeque<RequestMessage>> peers = registry.getAllPeersQueues(nodeId);
//        int majority = (followers.size() + 1) / 2 + 1;   // e.g. 5 nodes → 3 votes
        int majority = 3;   // e.g. 5 nodes → 3 votes
        // this queue is shared in the RPC so followers put their Acks here
        BlockingDeque<Ack> ackQueue = new LinkedBlockingDeque<>(100);

        // build the RequestVote RPC (you’re reusing AppendEntries for votes)
        AppendEntries voteReq = new AppendEntries(
                currentTerm,
                log.isEmpty() ? -1 : log.get(log.size() - 1).getTerm(),
                nodeId,
                log.size() - 1,
                requestVote,
                ackQueue
        );
        RequestMessage msg = new RequestMessage(requestVote, voteReq);

        // send to all followers
        for (BlockingDeque<RequestMessage> fid : peers) {
            fid.offer(msg);
        }

        int votes = 1;                // you always vote for yourself
        int replies = 0;
        long timeoutMs = leaderTimeout;
        long deadline = System.currentTimeMillis() + timeoutMs;

        try {
            while (System.currentTimeMillis() < deadline && replies < peers.size()) {
                long wait = deadline - System.currentTimeMillis();
                Ack ack = ackQueue.poll(wait, TimeUnit.MILLISECONDS);
                if (ack == null) break;      // timed out waiting for more replies
                replies++;
                if (ack.getSuccess() == 1) votes++;
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            System.out.println("[Candidate-" + nodeId + "] Interrupted while waiting for ACKs");
            return false;
        }

        System.out.println("[Candidate-" + nodeId + "] Received " + votes +
                " votes (replies=" + replies + ") in term " + currentTerm + " and majority needed- " + majority);
        return votes >= majority;
    }


    public void handleAppendEntries(AppendEntries ae) throws InterruptedException {
        BlockingDeque<Ack> ackQ = ae.getAckQueue();

        // 1) reject stale term
        if (ae.getCurrentTerm() < currentTerm) {

            ackQ.put(new Ack(nodeId, 0, -1, currentTerm));
            return;
        }
        currentTerm = ae.getCurrentTerm();
        votedFor = -1;
        // 2) log consistency
        int prevIdx = ae.getPrevLogIndex();
        int prevTerm = ae.getPrevLogTerm();
        if (prevIdx >= log.size() ||
                (prevIdx >= 0 && log.get(prevIdx).getTerm() != prevTerm)) {
            ackQ.put(new Ack(nodeId, 0, -1, currentTerm));
            return;
        }

        // 3) append new entries
        int match = prevIdx;
        int turncateIndex = prevIdx + 1;
        while (log.size() > turncateIndex) {
            log.remove(log.size() - 1);
        }

        for (LogEntry entry : ae.getEntries()) {
            int idx = entry.getIndex();
            log.add(entry);
            match = idx;

        }
        ackQ.put(new Ack(nodeId, 1, match, currentTerm));
    }

    @Override
    public void waitForAction() throws InterruptedException, IOException {
        System.out.println("[Follower-" + nodeId + "] Starting in term " + currentTerm);
        deadline = System.currentTimeMillis() + leaderTimeout;
        while (true) {
            leaderTimeout = 5000 + (long) (Math.random() * 1500); // 5000–6150 ms

            RequestMessage rpc = readFromClient();
            RequestMessage beat = beatsQueue.poll();

            if (System.currentTimeMillis() > deadline) {
                registry.updateRole(nodeId, NodeRole.candidate);
            }
            if (beat != null) {
                if (beat.getAppendEntries().getCurrentTerm() >= currentTerm)
                    deadline = System.currentTimeMillis() + leaderTimeout;
                else {
                    beat = null;
                }
            }
            int lastLogIndex = log.size() - 1;
            int lastLogTerm = lastLogIndex >= 0
                    ? log.get(lastLogIndex).getTerm()
                    : -1;
            if (registry.getRole(nodeId) != NodeRole.follower) {
                System.out.println("[Follower-" + nodeId + "] Role changed to Candidate, exiting follower loop");
                currentTerm += 1;
                votedFor = -1;
                deadline = System.currentTimeMillis() + leaderTimeout;
                boolean isLeader = sendVoteRequest();

                if (!isLeader) {
                    registry.updateRole(nodeId, NodeRole.follower);
                } else {
                    votedFor=nodeId;
                    logger.persistMetadata(currentTerm, nodeId);
                    registry.updateRole(nodeId, NodeRole.leader);
                    break;
                }

            } else if (rpc != null && rpc.getMsgType() == requestVote) {
                deadline = System.currentTimeMillis() + leaderTimeout;
                AppendEntries voteReq = rpc.getAppendEntries();
                int candidateTerm = voteReq.getCurrentTerm();
                int candidateId = voteReq.getLeaderID();
                int candidateLogIndex = voteReq.getCandidateLogIndex();
                int candidateLogTerm = voteReq.getCandidateLogTerm();
                int vote = 0;

                // 1) if candidate’s term is newer, update term and reset vote
                if (candidateTerm >= currentTerm) {
                    currentTerm = candidateTerm;
                    votedFor = -1;
                    // reset election timeout so we don’t immediately re-candidate
                }
                else {
                    voteReq.getAckQueue().put(new Ack(nodeId, 0, -1, currentTerm));
                    logger.persistMetadata(currentTerm, nodeId);
                }

                // 2) ensure candidate’s log is at least as up-to-date as ours
                boolean upToDate =
                        (lastLogTerm < candidateLogTerm)
                                || (lastLogTerm == candidateLogTerm && lastLogIndex <= candidateLogIndex);

                // 3) grant vote if same term, haven’t voted (or voted for this candidate), and logs up-to-date
                if (candidateTerm == currentTerm
                        && (votedFor == -1 || votedFor == candidateId)
                        && upToDate) {
                    vote = 1;
                    votedFor = candidateId;
                    System.out.println("[Follower-" + nodeId + "] Voted for "
                            + candidateId + " in term " + currentTerm);
                }

                // send back the Ack(vote)
                voteReq.getAckQueue().put(new Ack(nodeId, vote, -1, currentTerm));

            } else if (rpc != null && rpc.getMsgType() == putMessage) {
                JSONObject jsonReq = rpc.getJson();

                int leaderCommit = jsonReq.getInt("commitIndex");
                System.out.println("[Follower-" + nodeId + "] Received commit notification, leaderCommit=" + leaderCommit);
                commit(leaderCommit);
            } else if (rpc != null && rpc.getMsgType() == getMessage) {
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
                System.out.println("[Follower-" + nodeId + "] Sent GET response for key=" + key +
                        ", value=" + value);
            }
            if (rpc != null && rpc.getMsgType() == appendEntries) {
                handleAppendEntries(rpc.getAppendEntries());
            } else if (beat != null && beat.getMsgType() == appendEntries) {
//                System.out.println("Got Beat Follower- " + nodeId);
                handleAppendEntries(beat.getAppendEntries());
            }
            logger.persistMetadata(currentTerm, nodeId);
        }
        logger.persistMetadata(currentTerm, nodeId);

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
    public void setMetaData(Integer votedFor, Integer currentTerm, ArrayList<LogEntry> log, ConcurrentHashMap<Integer, Integer> matchIndex, ConcurrentHashMap<Integer, Integer> nextIndex) {
        this.votedFor = votedFor;
        this.currentTerm = currentTerm;
        this.log = log;
        this.matchIndex = matchIndex;
        this.nextIndex = nextIndex;
        System.out.println("[Follower-" + nodeId + "] Metadata set: term=" + currentTerm +
                ", votedFor=" + votedFor + ", logSize=" + log.size());
    }
}