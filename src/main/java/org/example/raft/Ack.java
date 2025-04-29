package org.example.raft;

public class Ack {
    public Ack(int nodeId, int success, int match, int currentTerm) {
        this.nodeId = nodeId;
        this.success = success;
        this.match=match;
    }

    public int getNodeId() {
        return nodeId;
    }

    public int getSuccess() {
        return success;
    }

    int nodeId;
    int success;
    int match;
    int currentTerm;


    public int getMatchIndex() {
        return this.match;
    }

    public int getCurrentTerm(){return this.currentTerm;}
}
