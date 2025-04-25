package org.example.message;

import org.json.JSONObject;

import java.util.concurrent.*;

public class RequestMessage {

    private JSONObject json;
    private BlockingDeque<ReplyMessage> clientReplyQueue;
    private final MessageType msgType;
    private AppendEntries entries;
    public RequestMessage(JSONObject json,BlockingDeque<ReplyMessage>clientReplyQueue, MessageType msgType){
        this.json=json;
        this.clientReplyQueue=clientReplyQueue;
        this.msgType=msgType;
    }


    public RequestMessage(JSONObject json, MessageType msgType){
        this.json=json;
        this.msgType=msgType;
    }

    public RequestMessage(MessageType msgType){
        this.msgType=msgType;
    }

    public RequestMessage(MessageType msgType,AppendEntries entries){
        this.msgType=msgType;
        this.entries=entries;
    }



    public BlockingDeque<ReplyMessage> getClientReplyQueue() {
        return clientReplyQueue;
    }


    public JSONObject getJson() {
        return json;
    }

    public MessageType getMsgType(){
        return msgType;
    }

    public AppendEntries getAppendEntries() {
        return entries;
    }
}
