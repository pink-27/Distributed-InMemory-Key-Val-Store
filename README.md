# Java Key‑Value Store V3 (Raft‑backed)

An in‑JVM key‑value store with enough Raft consensus to feel distributed, without the cloud bill.

---

## What’s New in V3

- **Raft Consensus**: real leader elections (no emperor’s new clothes), AppendEntries, terms, and quorum-based commits.
- **Heartbeat Scheduler**: leader’s metronome—beats every `beatTime` ms to keep followers from staging a coup.
- **Persistent Metadata**: `currentTerm` & `votedFor` on disk (`metaX.txt`), so crashes don’t invent ghost votes.
- **Durable Command Log**: every `PUT` is immortalized in `log0X.txt`—replayed on restart, because amnesia is cheating.

---

## Core Features

| Operation | Routing                    | Consistency                             |
|-----------|----------------------------|-----------------------------------------|
| **PUT**   | Proxy → Leader             | Linearizable (strict order, no surprises) |
| **GET**   | Proxy → Random Follower    | Eventual (fast reads, occasional staleness) |

- **TCP+JSON** interface—because HTTP is so 2005.
- **Leader election**: followers timeout, become candidates, shake virtual ballots—one winner per term.
- **Log replication**: leader maintains `nextIndex`/`matchIndex`, retries on failure, holds grudges.
- **Commit**: majority ACK → leader advances `commitIndex` → state machine applies.
- **Election safety**: one vote per term, enforced by persisted `votedFor`.

---

## Architecture

```
Client ↔ Proxy ↔ Raft Nodes (threads in one JVM, dramatic flair)
```

1. **Proxy** (`MultiThreadProxy`)
  - Accepts client TCP connections.
  - Routes `PUT` → leader queue; `GET` → random follower queue (spin the wheel).
2. **ClusterRegistry**
  - The cluster’s phone book: roles, request queues, heartbeat queues.
  - APIs: `getLeaderQueue()`, `getRandomFollowerQueue()`, `getAllPeersQueues()`, etc.
3. **Follower / Leader** (`CurrState` implementations)
  - **Follower**: resets election timer on heartbeat, replies to GETs, votes once per term, applies commits.
  - **Leader**: schedules heartbeats, handles PUTs, replicates logs, tracks quorum, commits & applies.
4. **Heartbeat Scheduler**
  - Leader’s `ScheduledExecutorService` fires AppendEntries (no entries) every `beatTime` ms.
  - Followers reset `deadline` on valid heartbeat—no heartbeat = panic election.
5. **Persistence** (`FileLogger`)
  - Command log: JSON lines in `log0X.txt`.
  - Metadata: JSON in `metaX.txt`, so `currentTerm` & `votedFor` survive reboots.

---

## Protocol Examples

**SET**
```json
{ "key": "snack", "value": "chips" }
```
**GET**
```json
{ "key": "snack" }
```
**OK**
```json
{ "key":"snack","value":"chips","status":"ok" }
```
**Missing**
```json
{ "key":"snack","value":null,"status":"error" }
```
**Idle timeout**
```json
{ "close":"ok" }
```

---

## Build & Run

```bash
# Compile
mvn clean compile

# Start server (Proxy spawns Raft nodes)
mvn exec:java -Dexec.mainClass=org.example.ServerMain

# Start a client
mvn exec:java -Dexec.mainClass=org.example.ClientMain
```

Then throw JSON at it and watch the consensus circus.

---

## Limitations & Next Steps

- **No snapshots**: log grows forever—bring a bigger disk.
- **Single‑JVM**: no real network faults, but you get threads flaming out.
- **ReadIndex/leases**: not yet—followers might serve stale reads.
- **Dynamic membership**: someday you’ll add nodes without restarting everything.

---

MIT License — hack it, break it, impress your peers with your consensus cred.

