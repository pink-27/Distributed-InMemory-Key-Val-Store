package org.example.message;

public enum MessageType {

    heartBeat(1), getMessage(2),putMessage(3), replyMessage(4), requestVote(5), appendEntries(6);
    private final int msgNumber;

    MessageType(int msgNumber){
        this.msgNumber=msgNumber;
    }
}
