package org.example.server;

import org.example.Store.inMemoryStore;
import org.example.logger.FileLogger;
import org.json.JSONObject;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.locks.ReentrantLock;

public class MultiThreadServer extends Thread {
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private inMemoryStore store;
    private BufferedReader reader;
    private BufferedWriter writer;
    private String STOP="##";
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
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void readMessages() throws IOException {
        String line;
        while(true){
            line =reader.readLine();
            if(line==null){
                closeThread();
                return;
            }
            JSONObject json = new JSONObject(line);
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

    private void closeThread() {
        try {
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
