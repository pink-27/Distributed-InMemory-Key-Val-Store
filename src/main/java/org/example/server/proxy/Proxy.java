package org.example.server.proxy;

import org.example.raft.ClusterRegistry;
import org.example.server.state.NodeRole;
import org.example.store.inMemoryStore;
import org.example.logger.FileLogger;
import org.example.message.RequestMessage;
import org.example.server.node.Node;
import org.example.server.state.Follower;
import org.example.server.state.Leader;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

public class Proxy {

    private ServerSocket serverSocket;
    private Socket clientSocket;
    private inMemoryStore store;
    public static int PORT = 3001;
    ReentrantLock reenLock;
    ExecutorService nodePool;
    Node leader;
    ArrayList<Node>nodes;
    ClusterRegistry registry = ClusterRegistry.getInstance();

    public Proxy() {
        try {
            serverSocket = new ServerSocket(PORT);
            store = new inMemoryStore();
            reenLock = new ReentrantLock();
            this.nodePool= Executors.newFixedThreadPool(5);
            this.nodes = new ArrayList<>();

            for(int i=0;i<4;i++){
                registry.updateRole(i, NodeRole.follower);

                Follower follower = new Follower(i);
                nodes.add(new Node(follower,i));
                Thread f = new Thread(nodes.get(i));
                f.start();
            }
            registry.updateLeader(4);
            Leader leader1 = new Leader(4);
            leader = new Node(leader1,4);
            nodes.add(leader);
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
                MultiThreadProxy threadProxy = new MultiThreadProxy(clientSocket,reenLock,nodes, ++clientID);
                threadProxy.start();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


}
