package org.example.logger;

import org.example.store.inMemoryStore;
import org.json.JSONObject;

import java.io.*;
import java.util.ArrayList;
import java.util.Scanner;

public class FileLogger {

    String path = "src/main/logs";
    File file;
    File metaFile;
    Scanner dataReader;
    inMemoryStore store;
    BufferedWriter writer;
    int nodeId;

    public FileLogger(int name, inMemoryStore store) throws IOException {
        this.nodeId = name;
        this.path = path + "/" + "log" + name + ".txt";
        this.metaFile = new File("src/main/logs/meta" + name + ".txt");
        this.store = store;
        this.file = new File(path);
        openLogFile();
        dataReader = new Scanner(file);
        writer = new BufferedWriter(new FileWriter(file, true));
    }

    public void openLogFile() throws IOException {
        if (file.createNewFile()) {
            System.out.println(path + " Created!");
        } else {
            System.out.println(path + " Already Exists!");
        }

        if (metaFile.createNewFile()) {
            System.out.println(metaFile.getPath() + " Metadata Created!");
        } else {
            System.out.println(metaFile.getPath() + " Metadata Already Exists!");
        }
    }

    public void writeToLog(LogEntry log) throws IOException {
        JSONObject json = log.getOperation();
        String content = json.toString();
        writer.write(content);
        writer.newLine();
        writer.flush();
    }

    public void recoverFromLog(ArrayList<LogEntry> log) throws IOException {
        while (dataReader.hasNextLine()) {
            String fileData = dataReader.nextLine();
            JSONObject json = new JSONObject(fileData);
            String key = json.getString("key");
            String value = json.getString("value");
            store.updateKeyVal(key, value);
            int index = json.getInt("index");
            int term = json.getInt("term");
            LogEntry entry = new LogEntry(index, term, key, value);
            log.add(entry);
        }
    }

    public void persistMetadata(int currentTerm, int votedFor) throws IOException {
        JSONObject meta = new JSONObject();
        meta.put("currentTerm", currentTerm);
        meta.put("votedFor", votedFor);

        try (BufferedWriter metaWriter = new BufferedWriter(new FileWriter(metaFile, false))) {
            metaWriter.write(meta.toString());
            metaWriter.flush();
        }
    }

    public int getCurrentTerm() {
        try (BufferedReader reader = new BufferedReader(new FileReader(metaFile))) {
            String line = reader.readLine();
            if (line != null) {
                JSONObject json = new JSONObject(line);
                return json.getInt("currentTerm");
            }
        } catch (Exception ignored) {}
        return 0; // default
    }

    public int getVotedFor() {
        try (BufferedReader reader = new BufferedReader(new FileReader(metaFile))) {
            String line = reader.readLine();
            if (line != null) {
                JSONObject json = new JSONObject(line);
                return json.getInt("votedFor");
            }
        } catch (Exception ignored) {}
        return -1; // default
    }

    public void closeLogFile() throws IOException {
        if (writer != null) writer.close();
        if (dataReader != null) dataReader.close();
    }
}
