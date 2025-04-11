

# Java Key-Value Store – V2

A lightweight in-memory key-value store in Java. It handles multiple clients, writes to disk so it doesn’t forget things, and now even kicks out idle clients after 30 seconds of silence. Thread-safe. Mostly polite.

## Features

- Basic `SET` and `GET` over TCP
- Thread pool for handling clients (no more freeloaders)
- Clients get disconnected after 10s of inactivity (tough love)
- JSON-based protocol — because YAML felt like overkill
- Write-ahead logging to disk
- Crash recovery on startup

## Project Structure

```
src/
├── org.example.Store/       // In-memory storage logic
├── org.example.logger/      // File-based logger
├── org.example.server/      // Server + client thread pool
└── org.example.client/      // Command-line client
```

## Protocol

All messages are JSON, sent over a plain TCP connection.

### `SET` request
```json
{ "key": "exampleKey", "value": "exampleValue" }
```

### `GET` request
```json
{ "key": "exampleKey" }
```

### Server response
```json
{ "key": "exampleKey", "value": "exampleValue", "status": "ok" }
```

If the key doesn’t exist:
```json
{ "key": "exampleKey", "value": null, "status": "error" }
```

If you go quiet too long:
```json
{ "close": "ok" }
```

## How It Works

- Starts up, reads log file, and rebuilds in-memory state like nothing happened.
- Listens on TCP, uses a thread pool to manage client connections.
- Clients have 10 seconds of grace period between commands — stay chatty.
- `SET` updates memory and logs to disk — in that order.
- `GET` reads straight from memory.
- All threads share one log file, safely locked behind a `ReentrantLock`.

## Run It

1. Compile:
```bash
javac -d out src/main/java/org/example/**/*.java
```

2. Start the server:
```bash
java -cp out org.example.server.Server
```

3. Run the client:
```bash
java -cp out org.example.ClientMain
```

Send it something like:
```
{"key":"username", "value":"alice"}
{"key":"username"}
```

It talks back. But if you ghost it, it’ll ghost you back.

## Notes

- Log file is at `src/main/logs/logs.txt`
- Logging is synchronized. Reads are fast and unsynchronized.
- No third-party libraries beyond `org.json` (writing JSON by hand is not a personality trait)

## Roadmap

- TTL for keys (per key expiry)
- log compaction
- Binary protocol (because plain text is for poets)
- Distributed mode (eventually)

