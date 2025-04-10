package org.example.server;

import org.example.Store.inMemoryStore;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {

    private ServerSocket serverSocket;
    private Socket clientSocket;
    private inMemoryStore store;
    private BufferedReader reader;
    private BufferedWriter writer;
    public static int PORT = 3001;
    private String STOP = "##";

    public Server() {
        try {
            serverSocket = new ServerSocket(PORT);
            store = new inMemoryStore();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void handleClient() {
        try {
            int clientID=0;
            while (true) {
                clientSocket = serverSocket.accept();
                MultiThreadServer threadServer = new MultiThreadServer(serverSocket, clientSocket, store, ++clientID);
                threadServer.start();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


}
