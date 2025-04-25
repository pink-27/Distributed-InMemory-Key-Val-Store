


# Java Key-Value Store – V3

It’s still a key-value store. Still Java. Still pretending to be distributed. But now it has **Raft**, so everything feels a bit more academic.

The leader has commitment issues (commitIndex), followers just do what they’re told (unless their logs say otherwise), and consensus is more of a *vibe* than a guarantee. But hey — logs replicate, things eventually commit, and we all pretend this isn't just threads in trench coats.

## What’s New in V3

- **Raft-lite™** — elections, log replication, the works. Mostly.
- **AppendEntries** — the leader’s way of maintaining it's oppressive reign over its followers
- **Conflict resolution** — overwrite first, ask questions never
- **CommitIndex broadcast** — the leader decides what’s official now
- **Ack queues** — because everyone wants validation
- **PrevLogIndex check** — trust but verify. Then maybe delete half your log.

## Features
 
- `SET` and `GET` over TCP, through a proxy that pretends to understand consistency
- Writes go to the leader, reads go to whichever follower answers first
- Two-Phase Commit: Leaders don’t just write and hope. They negotiate. Followers ACK or NACK, and only then does the commit go through. Democracy, but with logs.
- Followers delete their dreams (aka conflicting entries) to stay in line
- Everything's in-memory — fast, fragile, and gone on restart
- Upon restart, a node reconstructs its term, vote, and log history before resuming its role. Amnesia isn’t tolerated.
- Client timeout after 10s — because we respect our time
- Followers start a stopwatch every time they hear from the leader. If it runs out, they panic and hold elections.
- JSON-based protocol: easy to break, annoying to write

## Architecture

```
src/
├── org.example.raft/           // Raft logic: logs, elections, disappointment
├── org.example.store/          // Memory storage — nothing lasts forever
├── org.example.server/
│   ├── proxy/                  // Threaded router that plays god
│   ├── node/                   // Leader/follower logic
│   └── nodeRole/               // They pretend to have roles
├── org.example.message/        // JSON protocol stuff
├── org.example.client/         // Where humans type into sockets
└── org.example/                // Main class, entrypoints, etc
```

## Protocol

TCP + JSON.

### `SET`
```json
{ "key": "snack", "value": "chips" }
```

### `GET`
```json
{ "key": "snack" }
```

### If all goes well
```json
{ "key": "snack", "value": "chips", "status": "ok" }
```

If you ask for something that doesn’t exist:
```json
{ "key": "snack", "value": null, "status": "error" }
```

If you ignore us for 10 seconds:
```json
{ "close": "ok" }
```

## How It Works (Allegedly)

- You start the proxy. It spawns the leader and some followers. Like a cult, but legal.
- Clients connect and throw JSON at the proxy.
- Proxy routes:
  - `SET` ➜ the leader
  - `GET` ➜ a follower (chosen with the rigor of a coin toss)
- Leader logs the write, replicates it to followers using `AppendEntries`
- Followers check if it fits with their current life choices (aka log consistency)
- If not? They purge the bad memories (delete conflicting entries)
- If yes? They send an ACK and pretend everything’s fine
- Once the leader gets enough approvals, it marks the entry as “committed” and tells everyone to act accordingly

## Running It

Start the system:

```bash
mvn compile exec:java -Dexec.mainClass=org.example.ServerMain
```

Spin up a client (or three):

```bash
mvn exec:java -Dexec.mainClass=org.example.ClientMain
```

Try typing:
```
{"key":"cat", "value":"meow"}
{"key":"cat"}
```

Results may vary. And that’s part of the fun.

## Known Issues

- Logs technically exist. Snapshots don't.
- If the leader crashes, leadership dies with it (no re-elections yet).
- Followers will delete logs on command, even if those were committed — whoops?
- Everything is in one process. But it dreams of scale.

## Coming Soon (Maybe)

- Real networking
- Crash recovery that recovers something
- Persistence, because memory is fleeting
- Proper elections, not just a designated leader at launch

## License

MIT — fork it, run it, break it, build something cooler with it.
```

Let me know if you want a changelog that roasts the project’s past versions.