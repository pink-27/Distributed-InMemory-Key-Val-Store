package org.example.logger;

import org.example.Store.inMemoryStore;
import org.json.JSONObject;

import java.io.*;
import java.util.Scanner;
import java.util.concurrent.locks.ReentrantLock;

public class FileLogger implements PersistentLogger{

    String path="src/main/logs";
    File file;
    Scanner dataReader;
    inMemoryStore store;
    BufferedWriter writer;
    ReentrantLock reenLock;

    public FileLogger(String name, inMemoryStore store, ReentrantLock reenLock) throws IOException {
        path=path+"/"+name+".txt";
        this.store=store;
        file = new File(path);
        this.reenLock=reenLock;
        openLogFile();
        dataReader = new Scanner(file);
        writer = new BufferedWriter(new FileWriter(file,true));
    }

    @Override
    public void openLogFile() throws IOException {
        if(file.createNewFile()){
            System.out.println(path+" Created!");
        }
        else{
            System.out.println(path+" Already Exists!");
        }
    }

    @Override
    public void writeToLog(String key, String value) throws IOException {
        reenLock.lock();
        try {
            JSONObject json = new JSONObject();
            json.put("key", key);
            json.put("value", value);
            String content = json.toString();
            writer.write(content);
            writer.newLine();
            writer.flush();
        } finally {
            reenLock.unlock();
        }
    }


    @Override
    public void recoverFromLog() throws IOException {
        while(dataReader.hasNextLine()){
            String fileData = dataReader.nextLine();
            JSONObject json = new JSONObject(fileData);
            String key = (String) json.get("key");
            String value= (String) json.get("value");
            store.updateKeyVal(key,value);
        }
    }

    @Override
    public void closeLogFile() throws IOException {
        if (writer != null) writer.close();
        if (dataReader != null) dataReader.close();
    }

}
