package org.example.server.state;

public enum NodeRole {

    leader(1),follower(2),candidate(3);
    private final int i;
    NodeRole(int i) {

        this.i = i;
    }
}
