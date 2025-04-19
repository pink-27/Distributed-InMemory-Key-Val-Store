package org.example.server.state;

import org.example.message.ReplyMessage;
import org.example.message.RequestMessage;
import org.json.JSONObject;

import java.util.concurrent.BlockingQueue;

public interface CurrState {

    RequestMessage readFromClient() throws InterruptedException;




    public void waitForAction() throws InterruptedException;
}
