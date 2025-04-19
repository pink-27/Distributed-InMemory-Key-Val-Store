package org.example.server.node;

import org.example.server.state.CurrState;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;

public class Node implements Nodes, Runnable {

    private CurrState state;
    public Node(CurrState state){
        this.state=state;
    }

    @Override
    public void handleClient() throws InterruptedException {
        startNode();
    }

    @Override
    public void startNode() throws InterruptedException {
        state.waitForAction();

    }

    @Override
    public void run() {
        try {
            handleClient();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


}
