
---

# Java Key-Value Store – V2.5

A distributed, multithreaded, in-memory key-value store in Java. Now featuring a proxy that delegates like middle management, a leader who handles the hard stuff, and followers who respond to reads and don’t ask too many questions. Still polite. Still threads. Slightly more distributed. Slightly more chaotic.

## What’s New in 2.5

- **Distributed(ish) architecture** – everything runs in threads, looks like a cluster, smells like a cluster
- **Proxy** that routes `SET` to the leader and `GET` to random followers
- **Leader + follower** node threads — they know their place
- **Thread pool** to juggle client sessions (no freeloading threads anymore)
- Clients get kicked after 10s of silence — because discipline
- **Write-ahead logging was working in V2**… and then V2.5 happened. It's currently MIA.
- **Crash recovery used to happen** — it might again one day

## Features

- `SET` and `GET` over TCP
- JSON-based protocol (easy to debug, hard to type)
- Proxy routes requests to the right node (or tries)
- Leader handles all writes, followers handle reads
- Clients have an idle timeout — ghost us, get ghosted
- Logging exists in the code, just not in spirit

## Project Structure

```
src/
├── org.example.Store/          // In-memory storage
├── org.example.logger/         // Logging code that used to work
├── org.example.message/        // JSON protocol messages
├── org.example.server/
│   ├── proxy/                  // Proxy server + thread pool
│   ├── node/                   // Node logic for leader + followers
│   └── state/                  // State interfaces + roles
├── org.example.client/         // CLI client
└── org.example/                // Entrypoints
```

## Protocol

TCP + JSON. Nothing fancy. Easy to grok.

### `SET`
```json
{ "key": "username", "value": "alice" }
```

### `GET`
```json
{ "key": "username" }
```

### Response
```json
{ "key": "username", "value": "alice", "status": "ok" }
```

If the key is missing:
```json
{ "key": "username", "value": null, "status": "error" }
```

If you sit idle for 10 seconds:
```json
{ "close": "ok" }
```

## How It Works

- You start the proxy — it spawns the leader and followers
- Clients connect and start sending JSON requests
- Proxy routes:
    - `SET` ➜ leader (who was supposed to log it… R.I.P. logging)
    - `GET` ➜ random follower (who returns whatever’s in memory)
- All nodes live in the same process for now — but they act like they’re distributed
- Thread-safe where it counts, chill where it doesn’t

## Run It

Start the proxy + nodes:

```bash
mvn compile exec:java -Dexec.mainClass=org.example.ServerMain
```

Then spin up one or more clients:

```bash
mvn exec:java -Dexec.mainClass=org.example.ClientMain
```

Try it out:
```
{"key":"name", "value":"vihan"}
{"key":"name"}
```

If nothing breaks, you’re doing great. If something breaks, you’re probably in the right directory.

## Notes

- Logs are at `src/main/logs/logs.txt`… or they would be, if logging wasn’t currently broken
- `org.json` is finally handled properly via Maven (about time)
- No frameworks. No Spring. No nonsense. Just Java and vibes

---

Basically, V2.5 is like V2’s ambitious cousin who moved out, got a job, and forgot how to do laundry. It’s distributed now, but logging? We’ll get back to that.