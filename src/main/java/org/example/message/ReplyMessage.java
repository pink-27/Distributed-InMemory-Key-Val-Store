package org.example.message;

import org.json.JSONObject;

public class ReplyMessage {
    private JSONObject json;

    public ReplyMessage(JSONObject json){
        this.json=json;
    }

    public JSONObject getJson() {
        return json;
    }

    private MessageType msgType= MessageType.replyMessage;
    public MessageType getMsgType(){
        return msgType;
    }
}
