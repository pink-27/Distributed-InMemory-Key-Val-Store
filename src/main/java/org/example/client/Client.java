package org.example.client;

import org.example.server.Server;
import org.json.JSONObject;

import java.io.*;
import java.net.Socket;
public class Client {

    private Socket clientSocket;
    private BufferedWriter writer;
    private  BufferedReader reader;

    public Client() throws IOException {
        clientSocket=new Socket("localhost" , Server.PORT);
        writer=new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
        reader =new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
    }

    public JSONObject  sendCommand(String key) throws IOException{
        JSONObject json = new JSONObject();
        json.put("key",key);
        writer.write(json.toString());
        writer.newLine();
        writer.flush();
        return readMessage();

    }

    public void sendCommand(String key, String value) throws IOException{
        JSONObject json = new JSONObject();
        json.put("key",key);
        json.put("value",value);
        writer.write(json.toString());
        writer.newLine();
        writer.flush();
    }

    private JSONObject readMessage() throws IOException {
        String line =  reader.readLine();
        JSONObject json = new JSONObject(line);
        return json;

    }

    public void close() throws IOException {
        clientSocket.close();
    }


}
