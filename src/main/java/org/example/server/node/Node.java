package org.example.server.node;

import org.example.logger.FileLogger;
import org.example.logger.LogEntry;
import org.example.raft.ClusterRegistry;
import org.example.server.state.*;
import org.example.store.inMemoryStore;
import org.example.message.ReplyMessage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import static org.example.server.state.NodeRole.*;

public class Node implements Nodes, Runnable {

    private CurrState state;
    private final inMemoryStore store;
    private final int nodeId;
    private final ClusterRegistry registry = ClusterRegistry.getInstance();
    int first=0;
    // Raft metadata
    private Integer currentTerm;
    private Integer votedFor;
    private ArrayList<LogEntry> log;
    private ConcurrentHashMap<Integer, Integer> nextIndex;
    private ConcurrentHashMap<Integer, Integer> matchIndex;

    private final FileLogger logger;

    public Node(CurrState state, int nodeId) throws IOException {
        this.state = state;
        this.store = new inMemoryStore();
        this.state.setStore(store);
        this.nodeId = nodeId;
        this.log = new ArrayList<>();
        this.logger = new FileLogger(nodeId, store);
    }

    @Override
    public void handleClient() throws InterruptedException, IOException {
        startNode();
    }

    @Override
    public void startNode() throws InterruptedException, IOException {
        // enter the state's main loop
        first=1;

        state.waitForAction();

        // once that returns, we switch to the next role
        NodeRole nextRole = registry.getRole(nodeId);
        if (nextRole == follower) {
            changeState(new Follower(nodeId));
            this.state.setStore(store);
            registry.updateRole(nodeId, follower);
        } else {
            changeState(new Leader(nodeId));
            this.state.setStore(store);
            registry.updateRole(nodeId, leader);
        }

        run();
    }

    @Override
    public void changeState(CurrState state) {
        this.state = state;
    }

    /**
     Recover everything from disk into memory, then push into the state before doing any work
     */
    private void recoverAllMetadata() throws IOException {
        // 1) tell the state about the logger
        state.setLogger(logger);

        // 2) rebuild the log[] from the log file
        if (state instanceof Follower) logger.recoverFromLog(log);

        if(state instanceof Leader && first==0)logger.recoverFromLog(log);

        // 3) recover term + votedFor
        currentTerm = logger.getCurrentTerm();
        votedFor = logger.getVotedFor();

        // 4) init nextIndex & matchIndex = “just past end” of log
        ArrayList<Integer> followers= registry.getAllPeersIds(nodeId);
        nextIndex = new ConcurrentHashMap<>();
        matchIndex = new ConcurrentHashMap<>();
        for (int i = 0; i < followers.size(); i++) {
            nextIndex.put(followers.get(i), 0);
            matchIndex.put(followers.get(i), -1);
        }

        // 5) inject into state
        state.setMetaData(votedFor, currentTerm, log, matchIndex, nextIndex);

        // 6) if the state object supports commitIndex/lastApplied, set them too
        int commitIndex = log.size() - 1;
        if (state instanceof Leader) {
            state.setCommitIndex(commitIndex);
            state.setLastApplied(commitIndex);
        }
        if (state instanceof Follower) {
            state.setCommitIndex(commitIndex);
            state.setLastApplied(commitIndex);
        }
    }

    @Override
    public void run() {
        try {
            recoverAllMetadata();
            handleClient();
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }
}
