package org.example.raft;

public class Ack {
    public Ack(int nodeId, int success, int match) {
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


    public int getMatchIndex() {
        return this.match;
    }
}
