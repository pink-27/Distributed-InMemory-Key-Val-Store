package org.example.message;

import org.example.logger.LogEntry;
import org.example.raft.Ack;

import java.util.ArrayList;
import java.util.concurrent.BlockingDeque;

public class AppendEntries {
    private int currentTerm;
    private int leaderID;
    private int prevLogIndex;
    private ArrayList<LogEntry> entries;       // Log entries to store (empty = heartbeat)
    private int leaderCommit;
    private final MessageType msgType;
    private final BlockingDeque<Ack>ackQueue;
    private int prevLogTerm;
    public AppendEntries(int currentTerm, int leaderID, int prevLogIndex,int prevLogTerm, ArrayList<LogEntry> entries, int leaderCommit, MessageType msgType, BlockingDeque<Ack> ackQueue) {
        this.currentTerm = currentTerm;
        this.leaderID = leaderID;
        this.prevLogIndex = prevLogIndex;
        this.entries = entries;
        this.leaderCommit = leaderCommit;
        this.msgType = msgType;
        this.prevLogTerm=prevLogTerm;
        this.ackQueue = ackQueue;
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
        return leaderID;
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
}
