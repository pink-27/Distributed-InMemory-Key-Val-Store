

# Java Key-Value Store – V2

A lightweight in-memory key-value store in Java. It handles multiple clients, writes to disk so it doesn't forget things, and generally tries to keep its act together. Thread-safe. Mostly polite.

## Features

- Basic `SET` and `GET` over TCP
- One thread per client (we're friendly like that)
- JSON-based protocol — because YAML felt like overkill
- Write-ahead logging to disk
- Crash recovery on startup

## Project Structure

```
src/
├── org.example.Store/       // In-memory storage logic
├── org.example.logger/      // File-based logger
└── org.example.server/      // Server + per-client threading
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

## How It Works

- Starts up, reads log file, rebuilds memory like nothing happened.
- Listens on TCP, spawns a thread for each client (like a decent host).
- `SET` updates memory and logs to disk — in that order.
- `GET` just reads from memory — no fuss.
- All threads share one log file. There's a `ReentrantLock` making sure nobody talks over each other.

## Run It

1. Compile:
```bash
javac -d out src/main/java/org/example/**/*.java
```

2. Start the server:
```bash
java -cp out org.example.server.Server
```

3. Talk to it (Client):
```bash
java -cp out org.example.server.Client
```

Send it a message:
```
{"key":"username", "value":"alice"}
{"key":"username"}
```

It talks back. It's a good listener.

## Notes

- Log file is at `src/main/logs/logs.txt`
- Logging is synchronized. Reads are not. Don’t worry — memory's fast.
- No third-party libraries used beyond `org.json` (because writing JSON by hand is a trap).

## Roadmap

- TTL for keys (aka built-in forgetfulness)
- Snapshotting & log compaction
- Thread pool instead of spawning a thread for everyone who knocks
- Binary protocol
- Distributed mode (eventually)

