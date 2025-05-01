package org.example.message;

import org.example.logger.LogEntry;
import org.example.raft.Ack;

import java.util.ArrayList;
import java.util.concurrent.BlockingDeque;

public class AppendEntries {
    private int currentTerm;
    private int candidateLogIndex;
    private int nodeID;
    private int prevLogIndex;
    private ArrayList<LogEntry> entries;       // Log entries to store (empty = heartbeat)
    private int leaderCommit;
    private MessageType msgType;
    private final BlockingDeque<Ack> ackQueue;
    private int prevLogTerm;
    int candiDateLogTerm;
    public AppendEntries(int currentTerm, int nodeID, int prevLogIndex,int prevLogTerm, ArrayList<LogEntry> entries, int leaderCommit, MessageType msgType, BlockingDeque<Ack> ackQueue) {
        this.currentTerm = currentTerm;
        this.nodeID = nodeID;
        this.prevLogIndex = prevLogIndex;
        this.entries = entries;
        this.leaderCommit = leaderCommit;
        this.msgType = msgType;
        this.prevLogTerm=prevLogTerm;
        this.ackQueue = ackQueue;
    }

    public AppendEntries(int currentTerm, int candlogterm, Integer nodeId, int candidateLogIndex, MessageType messageType, BlockingDeque<Ack> ackQueue) {
        this.currentTerm = currentTerm;
        this.candidateLogIndex = candidateLogIndex;
        this.ackQueue = ackQueue;
        this.nodeID = nodeId;
        this.msgType=messageType;
        this.candiDateLogTerm=candlogterm;
    }

    public MessageType getMsgType() {
        return msgType;
    }

    public int getLeaderCommit() {
        return leaderCommit;
    }

    public ArrayList<LogEntry> getEntries() {
        return entries;
    }

    public int getPrevLogIndex() {
        return prevLogIndex;
    }

    public int getLeaderID() {
        return nodeID;
    }

    public int getCurrentTerm() {
        return currentTerm;
    }

    public BlockingDeque<Ack> getAckQueue() {
        return ackQueue;
    }

    public int getPrevLogTerm() {
        return prevLogTerm;
    }

    public int getCandidateLogIndex() {
        return candidateLogIndex;
    }

    public int getCandidateLogTerm() {
        return candiDateLogTerm;
    }
}
