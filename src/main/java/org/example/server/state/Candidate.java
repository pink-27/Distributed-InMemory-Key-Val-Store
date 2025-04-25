package org.example.server.state;

import org.example.logger.FileLogger;
import org.example.logger.LogEntry;
import org.example.message.RequestMessage;
import org.example.store.inMemoryStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.BlockingDeque;

public class Candidate implements CurrState {


    @Override
    public void setStore(inMemoryStore store) {

    }

    @Override
    public void waitForAction() throws InterruptedException {

    }

    @Override
    public void setLogger(FileLogger logger) {
        return;
    }

    @Override
    public void setCommitIndex(int lastApplied) {

    }

    @Override
    public void setLastApplied(int lastApplied) {

    }

    @Override
    public void setMetaData(int votedFor, int currentTerm, ArrayList<LogEntry> log, HashMap<Integer,Integer> matchIndex, HashMap<Integer,Integer> nextIndex) {

    }

}
