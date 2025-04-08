

# Simple In-Memory Key-Value Store (V1)

This is a basic single-node, in-memory key-value store built in Java.  
It supports `GET` and `PUT` operations over TCP using a custom protocol based on JSON.

## Features

- In-memory data storage using `ConcurrentHashMap`
- TCP server-client communication over sockets
- Supports multiple clients manually (one at a time)
- Thread-safe storage operations
- JSON-based message format

## Usage

### Server
Start the server:
```bash
java -cp target/your-jar-name.jar org.example.ServerMain
```

### Client
Start the client in a separate terminal:
```bash
java -cp target/your-jar-name.jar org.example.ClientMain
```

You can run multiple clients from different terminals.  
Each client supports:
- `1` → GET a key
- `2` → PUT a key-value pair
- `##` → Close connection

## Message Format

All communication between client and server is in JSON.

### PUT Example
```json
{
  "key": "foo",
  "value": "bar"
}
```

### GET Request
```json
{
  "key": "foo"
}
```

### GET Response
```json
{
  "key": "foo",
  "value": "bar",
  "status": "ok"
}
```

If the key is not found:
```json
{
  "key": "foo",
  "value": null,
  "status": "error"
}
```

## Limitations (V1)

- Single-threaded: server can only handle one client at a time
- All data is stored in-memory; no persistence
- No TTLs or eviction
- No distributed capability

## Next Steps (V2 and beyond)

- Add multi-threaded client handling
- Support persistence to disk
- Introduce command parsing logic
- Explore compression and protocol optimizations
- Eventually scale to a distributed architecture

---

This was built to get hands-on with TCP sockets, threads, and Java I/O — not trying to reinvent Redis... yet.
```

---

Let me know if you want badges, project structure, or installation instructions.
