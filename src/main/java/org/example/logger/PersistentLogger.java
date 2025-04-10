package org.example.logger;

import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

public interface PersistentLogger {

    void openLogFile() throws IOException;

    void writeToLog(String key, String value) throws IOException;

    void recoverFromLog() throws IOException;

    void closeLogFile() throws IOException;
}
