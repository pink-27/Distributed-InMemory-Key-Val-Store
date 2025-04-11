package org.example.server;

import org.example.Store.inMemoryStore;
import org.example.logger.FileLogger;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

public class Server {

    private ServerSocket serverSocket;
    private Socket clientSocket;
    private inMemoryStore store;
    private BufferedReader reader;
    private BufferedWriter writer;
    public static int PORT = 3001;
    FileLogger logger;
    ReentrantLock reenLock;

    public Server() {
        try {
            serverSocket = new ServerSocket(PORT);
            store = new inMemoryStore();
            reenLock = new ReentrantLock();
            logger = new FileLogger("logs",store,reenLock);
            logger.recoverFromLog();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void handleClient() {
        try {
            int clientID=0;
            while (true) {
                clientSocket = serverSocket.accept();
                MultiThreadServer threadServer = new MultiThreadServer(serverSocket, clientSocket, store,logger,reenLock, ++clientID);
                threadServer.start();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


}
