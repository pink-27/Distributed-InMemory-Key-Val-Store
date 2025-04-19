package org.example.server.proxy;
import org.example.logger.FileLogger;
import org.example.message.ReplyMessage;
import org.example.message.RequestMessage;
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
    FileLogger logger;
    ReentrantLock reenLock;
    ArrayList<BlockingQueue<RequestMessage>>followerQueues;
    BlockingQueue<RequestMessage>writerQueue;
    BlockingQueue<ReplyMessage>replyQueue;

    int clientID;

    public MultiThreadProxy(Socket clientSocket, FileLogger logger, ReentrantLock reenLock,ArrayList<BlockingQueue<RequestMessage>>followerQueues,BlockingQueue<RequestMessage> writerQueue, int id){
        this.clientSocket=clientSocket;
        this.clientID=id;
        this.logger=logger;
        this.reenLock=reenLock;
        timeout=Duration.ofSeconds(1000);
        this.followerQueues=followerQueues;
        this.writerQueue=writerQueue;
        this.replyQueue=new ArrayBlockingQueue<>(100);
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

    public void queryFollower(RequestMessage requestMessage, int follower) throws InterruptedException {
        followerQueues.get(follower).put(requestMessage);
    }

    public void getValue(JSONObject msg) throws IOException, InterruptedException {
        RequestMessage requestMessage= new RequestMessage(msg,replyQueue);
        int randomNum = (int) (Math.random()*4);
        queryFollower(requestMessage,randomNum);
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
        writerQueue.put(msg);
    }
    public void updateKeyValue(JSONObject msg) throws IOException, InterruptedException {
        RequestMessage requestMessage= new RequestMessage(msg);
//        logger.writeToLog(Key,Value);
        requestMessage.Writeop();
        sendToLeader(requestMessage);
        JSONObject response = new JSONObject();
        response.put("close","notok");
        writer.write(response.toString());
        writer.newLine();
        writer.flush();
    }

}