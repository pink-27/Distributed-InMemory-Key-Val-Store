package org.example.server.node;

import org.example.server.state.CurrState;

import java.io.IOException;

public interface Nodes {
    public void handleClient() throws InterruptedException, IOException;
    public void startNode() throws InterruptedException, IOException;

    public void changeState(CurrState state);

}
