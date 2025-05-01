package org.example.server.state;

import org.example.logger.FileLogger;
import org.example.logger.LogEntry;
import org.example.message.RequestMessage;
import org.example.store.inMemoryStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;


public interface CurrState {

    public void setStore(inMemoryStore store);
    public void waitForAction() throws InterruptedException, IOException;
    public void setLogger(FileLogger logger);
    public void setCommitIndex(int commitIndex);
    public void setLastApplied(int lastApplied);

    public void setMetaData(Integer votedFor, Integer currentTerm, ArrayList<LogEntry> log, ConcurrentHashMap<Integer,Integer> matchIndex, ConcurrentHashMap<Integer,Integer> nextIndex);

}
