package org.example.server.state;

import org.example.Store.inMemoryStore;
import org.example.message.ReplyMessage;
import org.example.message.RequestMessage;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;

public class Leader implements CurrState {

    private BlockingQueue<RequestMessage> writeQueue;
    ArrayList< BlockingQueue<RequestMessage>> followers;
    private inMemoryStore store;


    public void setQueue(BlockingQueue<RequestMessage> writeQueue) {
        this.writeQueue=writeQueue;
    }

    public void setFollowers(ArrayList< BlockingQueue<RequestMessage>> followers) {
        this.followers=followers;
    }

    @Override
    public RequestMessage readFromClient() throws InterruptedException {
        return  writeQueue.take();
    }

    public void sendUpdateds(String key, String value) throws InterruptedException {

        for(int i=0;i<4;i++){
            JSONObject msg = new JSONObject();
            msg.put("key",key);
            msg.put("value",value);
            RequestMessage requestMessage = new RequestMessage(msg);
            requestMessage.Writeop();
            followers.get(i).put(requestMessage);
        }
    }
    public void writeToDB(String key, String value) throws InterruptedException {
        store.updateKeyVal(key,value);
        sendUpdateds(key,value);
    }

    @Override
    public void waitForAction() throws InterruptedException {
        store = new inMemoryStore();

        while(true){
            RequestMessage query = readFromClient();
            JSONObject jsonReq = query.getJson();
            String key=(jsonReq.get("key")).toString();
            String value=(jsonReq.get("value")).toString();
            writeToDB(key,value);

        }
    }
}
