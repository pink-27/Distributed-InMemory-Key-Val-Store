package org.example.server;

import org.example.Store.inMemoryStore;
import org.example.logger.FileLogger;
import org.json.JSONObject;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public class MultiThreadServer extends Thread {
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private inMemoryStore store;
    private BufferedReader reader;
    private BufferedWriter writer;
    private final Duration timeout;
    FileLogger logger;
    ReentrantLock reenLock;
    int clientID;

    public MultiThreadServer(ServerSocket serverSocket, Socket clientSocket, inMemoryStore inMemoryStore, FileLogger logger, ReentrantLock reenLock, int id){
        this.serverSocket=serverSocket;
        this.store=inMemoryStore;
        this.clientSocket=clientSocket;
        this.clientID=id;
        this.logger=logger;
        this.reenLock=reenLock;
        timeout=Duration.ofSeconds(30);
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
                json.put("close", "notok");
                if (json.has("value")) {
                    String key = (String) json.get("key");
                    String value = (String) json.get("value");
                    updateKeyValue(key, value);
                } else {
                    String key = (String) json.get("key");
                    getValue(key);
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
            writer.write(json.toString());
            writer.newLine();
            writer.flush();

            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();

        } catch (IOException e) {
            System.out.println("Error while closing resources for ClientHandler-" + clientID);
        }
        System.out.println("ClientHandler-" + clientID + " closed");
    }


    public void getValue(String Key) throws IOException {
        String value = store.getValue(Key);
        JSONObject response = new JSONObject();
        response.put("key", Key);
        response.put("value", value != null ? value : JSONObject.NULL);
        if(value==null){
            response.put("status","error");
        }
        else{
            response.put("status","ok");
        }
        writer.write(response.toString());
        writer.newLine();
        writer.flush();

    }
    public void updateKeyValue(String Key, String Value) throws IOException {
        store.updateKeyVal(Key,Value);
        logger.writeToLog(Key,Value);
    }

}
