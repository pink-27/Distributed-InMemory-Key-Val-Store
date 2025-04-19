package org.example.server.state;

import org.example.Store.inMemoryStore;
import org.example.message.ReplyMessage;
import org.example.message.RequestMessage;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.concurrent.BlockingQueue;

public class Follower implements CurrState {
    private BlockingQueue<RequestMessage> readQueue;
    private inMemoryStore store;
    private HashMap<Integer,BlockingQueue<ReplyMessage>> clientReplyQueue=new HashMap<>();

    public RequestMessage readFromClient() throws InterruptedException {
        return readQueue.take();
    }


    private boolean checkClientConnExists(int clientID){
        return clientReplyQueue.containsKey(clientID);
    }

    public void establishConn(int clientID, BlockingQueue<ReplyMessage> replyQueue){
        if(!checkClientConnExists(clientID)) {
            clientReplyQueue.put(clientID,replyQueue);
        }

    }
    public void setQueue( BlockingQueue<RequestMessage> readQueue) {
        this.readQueue=readQueue;
    }


    public void replyToClient(int clientID,ReplyMessage msg) throws InterruptedException {
        clientReplyQueue.get(clientID).put(msg);
    }

    public void waitForAction() throws InterruptedException {
        store = new inMemoryStore();
        while(true){
            RequestMessage query = readFromClient();
            JSONObject jsonReq = query.getJson();

            String key=(jsonReq.get("key")).toString();
            if(query.isWrite()==1){
                String value = (jsonReq.get("value")).toString();
                store.updateKeyVal(key,value);
            }
            else {
                int clientID= (int) (jsonReq.get("client"));
                establishConn(clientID,query.getClientReplyQueue());
                String value = store.getValue(key);
                JSONObject rep = new JSONObject();
                rep.put("key", key);
                rep.put("value", value);
                ReplyMessage reply = new ReplyMessage(rep);
                replyToClient(clientID, reply);
            }

        }
    }
}
