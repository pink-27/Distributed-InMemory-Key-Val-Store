


# Java Key-Value Store – V3

It’s still a key-value store. Still Java. Still pretending to be distributed. But now it has **Raft**, so everything feels a bit more academic.

The leader has commitment issues (commitIndex), followers just do what they’re told (unless their logs say otherwise), and consensus is more of a "eventuality" than a guarantee. But logs replicate, things eventually commit, and we all pretend this isn't just threads in hoodies.

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
- Everything's in-memory —> fast & fragile
- Upon restart, a node reconstructs its term, vote, and log history before resuming its role. Amnesia isn’t tolerated.
- Client timeout after 1000s of inactivity, because we respect our time
- Followers start a stopwatch every time they hear from the leader. If it runs out, they panic and hold elections.
- JSON-based protocol: easy to break, annoying to write

## Architecture
This project is a distributed key-value store pretending to be a cloud, but running entirely inside a single JVM. It models real-world distributed systems behavior using threads, queues, and logs, without the latency or cost of actual networks. Here's how it's wired:
#### Nodes 
- Java threads with their own Raft state machine, persistent log, key-value store, and message queue. Zero shared memory - just message passing.
#### Leader Election 
- Raft-standard: Followers timeout, self-nominate, increment term, and vote. Majority wins. Randomized timeouts prevent split votes.
#### Two-Phase Commit
- Prepare: Leader logs, broadcasts to followers
- Commit: After quorum acknowledgment, leader commits and applies
#### Proxy
- Routes writes to leader, distributes reads across followers. Uses registry to track cluster status.
#### Heartbeats
- Leaders send regular pulses. Followers reset timers or call elections when needed.
#### Persistence
- Operations logged to disk. On restart: replay log, restore term/votes, rebuild state.
#### Cluster Coordination
- Registry tracks node roles, message queues, and quorum info - the cluster's control plane.

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
- Everything is in one process (multithreaded). But it dreams of scale.

## Coming Soon (Maybe)

- Real networking
- Crash recovery that recovers something
- Proper elections, not just a designated leader at launch

## License

MIT — fork it, run it, break it, build something cooler with it.
