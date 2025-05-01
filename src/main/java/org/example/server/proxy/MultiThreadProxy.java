package org.example.server.proxy;
import org.example.logger.FileLogger;
import org.example.message.MessageType;
import org.example.message.ReplyMessage;
import org.example.message.RequestMessage;
import org.example.server.node.Node;
import org.example.raft.ClusterRegistry;
import org.example.server.state.NodeRole;
import org.json.JSONObject;
import java.io.*;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

public class MultiThreadProxy extends Thread {
    private final Socket clientSocket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private final Duration timeout;
    ReentrantLock reenLock;
    ArrayList<BlockingDeque<RequestMessage>>followerQueues;
    BlockingDeque<RequestMessage>writerQueue;
    BlockingDeque<ReplyMessage>replyQueue;
    ClusterRegistry registry = ClusterRegistry.getInstance();
    ArrayList<Node>nodes;
    ArrayList<Integer>followerId;
    int clientID;

    public MultiThreadProxy(Socket clientSocket, ReentrantLock reenLock,ArrayList<Node>nodes,int id){
        this.clientSocket=clientSocket;
        this.clientID=id;
        this.reenLock=reenLock;
        timeout=Duration.ofSeconds(1000);
        this.nodes=nodes;
        this.replyQueue=new LinkedBlockingDeque<>(100);
        System.out.println("ClientHandler-" + clientID);
    }

    @Override
    public void run(){

        handleClient();
    }

    public void handleClient(){
        try {
            reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
            readMessages();
        } catch (IOException | ExecutionException | InterruptedException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    private void readMessages() throws IOException, ExecutionException, InterruptedException, TimeoutException {
        String line;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            while (true) {
                Future<String> future = executor.submit(() -> {
                    try {
                        return reader.readLine();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                try {
                    line = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    System.err.println("Timeout occurred: Task exceeded for Client " + clientID + ", " + timeout.toSeconds() + " seconds.");
                    line = null;
                    future.cancel(true);
                } catch (InterruptedException | ExecutionException e) {
                    line = null;
                    System.err.println("Exception occurred: " + e.getMessage());
                }


                if (line == null) {
                    closeThread();
                    return;
                }

                JSONObject json = new JSONObject(line);
                json.put("client",clientID);
                if (json.has("value")) {
                    updateKeyValue(json);
                } else {
                    getValue(json);
                }
            }
        }
        finally {
            executor.shutdown();
        }
    }

    private void closeThread() {
        try {
            JSONObject json = new JSONObject();
            json.put("close","ok");
            json.put("status","ok");

            writer.write(json.toString());
            writer.newLine();
            writer.flush();

            if (reader != null) reader.close();
            writer.close();
            if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();

        } catch (IOException e) {
            System.out.println("Error while closing resources for ClientHandler-" + clientID);
        }
        System.out.println("ClientHandler-" + clientID + " closed");
    }

    public void queryFollower(RequestMessage requestMessage) throws InterruptedException {
        registry.getRandomFollowerQueue().put(requestMessage);
    }


    public void getValue(JSONObject msg) throws IOException, InterruptedException {
        RequestMessage requestMessage= new RequestMessage(msg,replyQueue, MessageType.getMessage);
        queryFollower(requestMessage);
        ReplyMessage res = replyQueue.take();
        JSONObject response = res.getJson();

        if(!response.has("value")){
            response.put("status","error");
        }
        else{
            response.put("status","ok");
        }
        response.put("close","notok");
        writer.write(response.toString());
        writer.newLine();
        writer.flush();

    }

    public void sendToLeader(RequestMessage msg) throws InterruptedException {
        while(true) {
            this.writerQueue = registry.getLeaderQueue();
            if(writerQueue!=null)break;
        }
        writerQueue.put(msg);
    }
    public void updateKeyValue(JSONObject msg) throws IOException, InterruptedException {
        RequestMessage requestMessage= new RequestMessage(msg,MessageType.putMessage);
        sendToLeader(requestMessage);
        JSONObject response = new JSONObject();
        response.put("close","notok");
        writer.write(response.toString());
        writer.newLine();
        writer.flush();
    }

}