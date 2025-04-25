package org.example.logger;

import org.json.JSONObject;

public class LogEntry {
    private int index;
    private int term;
    private JSONObject operation;


    public JSONObject getOperation() {
        return operation;
    }

    public int getTerm() {
        return term;
    }

    public int getIndex() {
        return index;
    }

    public LogEntry(int index, int term, String key, String value) {
        this.index = index;
        this.term = term;
        this.operation = new JSONObject();
        this.operation.put("op", "PUT");
        this.operation.put("key", key);
        this.operation.put("value", value);
        this.operation.put("index", index);
        this.operation.put("term", term);
    }

    public LogEntry(int index, int term, String key) {
        this.index = index;
        this.term = term;
        this.operation = new JSONObject();
        this.operation.put("op", "PUT");
        this.operation.put("key", key);
        this.operation.put("index", index);
        this.operation.put("term", term);
    }

}
