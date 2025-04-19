package org.example.message;

import org.json.JSONObject;

import java.util.concurrent.BlockingQueue;

public class RequestMessage {

    private JSONObject json;
    private BlockingQueue<ReplyMessage> clientReplyQueue;
    private int write=0;
    public RequestMessage(JSONObject json,BlockingQueue<ReplyMessage>clientReplyQueue){
        this.json=json;
        this.clientReplyQueue=clientReplyQueue;
    }

    public RequestMessage(JSONObject json){
        this.json=json;
    }

    public BlockingQueue<ReplyMessage> getClientReplyQueue() {
        return clientReplyQueue;
    }

    public void Writeop(){
        this.write=1;
    }

    public int isWrite(){
        return this.write;
    }

    public JSONObject getJson() {
        return json;
    }
}
