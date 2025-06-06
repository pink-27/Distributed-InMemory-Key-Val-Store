package org.example.server.state;

import org.example.logger.FileLogger;
import org.example.logger.LogEntry;
import org.example.message.AppendEntries;
import org.example.message.MessageType;
import org.example.raft.Ack;
import org.example.raft.ClusterRegistry;
import org.example.store.inMemoryStore;
import org.example.message.RequestMessage;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.*;

import static java.lang.Thread.sleep;
import static org.example.server.state.NodeRole.follower;

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
    private final BlockingDeque<RequestMessage> beatsQueue;
    private Integer currentTerm;
    private Integer votedFor;
    private int leaderCommitIndex = -1;
    private int lastAppliedIndex = -1;
    private volatile int isLeader;
    ScheduledExecutorService hbScheduler = Executors.newSingleThreadScheduledExecutor();

    private ArrayList<LogEntry> log = new ArrayList<>();

    // nextIndex and matchIndex per follower
    private ConcurrentHashMap<Integer, Integer> nextIndex = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, Integer> matchIndex = new ConcurrentHashMap<>();

    public Leader(int nodeId) {
        this.nodeId = nodeId;
        this.writeQueue = registry.getLeaderQueue();
        this.isLeader = 1;
        this.beatsQueue = registry.getBeatsQueue(nodeId);
        this.followers = registry.getAllPeersQueues(nodeId);
        this.beatsQueues = registry.getAllPeersBeatsQueues(nodeId);
        this.ackQueue = new LinkedBlockingDeque<>(100);
        this.beatTime = (15 + (long) (Math.random() * 10));

        System.out.println("[Leader-" + nodeId + "] Initializing heartbeat scheduler with beat time: " + beatTime + "ms");
        // initialize indices for each follower
        ArrayList<Integer> fids = registry.getFollowerId();
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
        registry.getFollowerQueue(followerId).putFirst(msg);
    }

    private void broadcastAppendEntries() throws InterruptedException {
        System.out.println("[Leader-" + nodeId + "] Broadcasting AppendEntries to " + followers.size() + " followers");
        System.out.println("[Leader-" + nodeId + "] Clearing previous ACK queue");
        this.ackQueue.clear();
        ArrayList<Integer> followerID = registry.getFollowerId();
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
        while (System.currentTimeMillis() < deadline && success + failure <= followers.size()) {
            try {

                Ack ack = ackQueue.poll(deadline - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
                if (ack == null) {
                    break;
                }

                if (ack.getSuccess() == 1) {
                    success++;
                    int idx = ack.getMatchIndex();
                    nextIndex.put(ack.getNodeId(), idx + 1);
                    matchIndex.put(ack.getNodeId(), idx);

                } else {
                    failure++;
                    if (ack.getCurrentTerm() > currentTerm) {

                        currentTerm = ack.getCurrentTerm();
                        isLeader = 0;
                        return false;
                    }
                    nextIndex.compute(ack.getNodeId(), (k, oldNextIndex) -> Math.max(0, oldNextIndex - 1));
                    try {
                        sendAppendEntriesToFollower(ack.getNodeId());
                        deadline += 5000;
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

        boolean hasQuorum = success >= 3;

        return hasQuorum;
    }

    public void sendHeartBeat() throws InterruptedException {
        if (isLeader == 0) return;
        ArrayList<Integer> peersID = registry.getAllPeersIds(nodeId);
        for (int i = 0; i < peersID.size(); i++) {
            int prevIndex = nextIndex.get(peersID.get(i)) - 1;
            RequestMessage msg = getRequestMessage(prevIndex);
            registry.getBeatsQueue(peersID.get(i)).offer(msg);
        }
        hasQuorum();

    }

    private RequestMessage getRequestMessage(int prevIndex) {
        int prevTerm = (prevIndex >= 0 ? log.get(prevIndex).getTerm() : 0);
        ArrayList<LogEntry> entries = new ArrayList<>();

        AppendEntries rpc = new AppendEntries(currentTerm, nodeId, prevIndex, prevTerm, entries, leaderCommitIndex, MessageType.appendEntries, ackQueue);
        return new RequestMessage(MessageType.appendEntries, rpc);
    }

    @Override
    public void waitForAction() throws InterruptedException, IOException {
        System.out.println("[Leader-" + nodeId + "] Starting in term " + currentTerm +
                ", log size=" + log.size());
        ScheduledFuture<?> heartbeatTask = hbScheduler.scheduleWithFixedDelay(() -> {
            if (Thread.currentThread().isInterrupted()) return;
            try {
                sendHeartBeat();
            } catch (InterruptedException e) {
                System.out.println("[Leader-" + nodeId + "] Error during heartbeat: " + e.getMessage());
                Thread.currentThread().interrupt(); // preserve interruption status

            }
        }, 0, beatTime, TimeUnit.MILLISECONDS);

//        int cnt = 0;
        while (isLeader == 1) {
//            cnt++;
//            if (cnt == 1000) {
//                isLeader = 0;
//                heartbeatTask.cancel(true);
//                hbScheduler.shutdownNow();
////                sleep(10000, TimeUnit.MILLISECONDS.ordinal());
//                break;
//            }
            RequestMessage req = readFromClient();
            RequestMessage beat = beatsQueue.poll();
            if (beat != null && beat.getAppendEntries().getCurrentTerm() >= currentTerm) {
                isLeader = 0;
                heartbeatTask.cancel(true);
                hbScheduler.shutdownNow();
                break;
            }

            if (registry.getRole(nodeId) != NodeRole.leader) {
                isLeader = 0;
                heartbeatTask.cancel(true);
                System.out.println("[Leader-" + nodeId + "] No longer the leader, shutting down heartbeat scheduler");
                hbScheduler.shutdownNow();
                break;
            }
            if (req == null) continue;

            JSONObject j = req.getJson();
            if(j==null)continue;
            if (!j.has("key")) continue;
            String key = j.getString("key");
            String value = j.getString("value");

            System.out.println("[Leader-" + nodeId + "] Processing PUT request: key=" + key +
                    ", value=" + value);

            // 1) Append to local log
            this.followers = registry.getAllPeersQueues(nodeId);
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
        isLeader = 0;
        heartbeatTask.cancel(true);
        registry.updateRole(nodeId, follower);
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
    public void setMetaData(Integer votedFor, Integer currentTerm, ArrayList<LogEntry> log, ConcurrentHashMap<Integer, Integer> matchIndex, ConcurrentHashMap<Integer, Integer> nextIndex) {
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
            matchIndex.put(i, log.size() - 1);
            System.out.println("[Leader-" + nodeId + "] Initialized matchIndex for follower " + i +
                    ": " + oldVal + " -> " + (log.size() - 1));
        }
    }
}