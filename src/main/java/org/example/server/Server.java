package org.example.server;

import org.example.Store.inMemoryStore;
import org.json.JSONObject;

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
    private String STOP="##";

    public Server(){
        try {
            serverSocket = new ServerSocket(PORT);
            store = new inMemoryStore();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void handleClient(){
        try {
            while(true) {
                clientSocket = serverSocket.accept();
                reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
                readMessages();
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void readMessages() throws IOException {
        String line;
        while((line = reader.readLine()) != null && !line.equals(STOP)){
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
    public void updateKeyValue(String Key, String Value){
        store.updateKeyVal(Key,Value);
    }
}
