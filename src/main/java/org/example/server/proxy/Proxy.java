package org.example.server.proxy;

import netscape.javascript.JSObject;
import org.example.Store.inMemoryStore;
import org.example.logger.FileLogger;
import org.example.message.RequestMessage;
import org.example.server.node.Node;
import org.example.server.state.Follower;
import org.example.server.state.Leader;
import org.json.JSONObject;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

public class Proxy {

    private ServerSocket serverSocket;
    private Socket clientSocket;
    private inMemoryStore store;
    public static int PORT = 3001;
    FileLogger logger;
    ReentrantLock reenLock;
    ExecutorService nodePool;
    Node leader;
    ArrayList<Node> followers;
    ArrayList<BlockingQueue<RequestMessage>>followersQueue;
    BlockingQueue<RequestMessage> writersQueue =new ArrayBlockingQueue<>(100);



    public Proxy() {
        try {
            serverSocket = new ServerSocket(PORT);
            store = new inMemoryStore();
            reenLock = new ReentrantLock();
            logger = new FileLogger("logs",store,reenLock);
            logger.recoverFromLog();
            this.nodePool= Executors.newFixedThreadPool(5);

            Leader leader1 = new Leader();
            leader1.setQueue(writersQueue);
            leader = new Node(leader1);
            this.followersQueue=new ArrayList<>();
            this.followers = new ArrayList<>();
            for(int i=0;i<4;i++){
                Follower follower = new Follower();
                BlockingQueue<RequestMessage> readQueue=new ArrayBlockingQueue<>(100);
                follower.setQueue(readQueue);
                followers.add(new Node(follower));
                this.followersQueue.add(readQueue);
                Thread f = new Thread(followers.get(i));
                f.start();
            }
            leader1.setFollowers(followersQueue);
            Thread l = new Thread(leader);
            l.start();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void handleClient() {
        try {
            int clientID=0;
            while (true) {
                clientSocket = serverSocket.accept();
                MultiThreadProxy threadProxy = new MultiThreadProxy(clientSocket, logger,reenLock,followersQueue,writersQueue, ++clientID);
                threadProxy.start();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


}
