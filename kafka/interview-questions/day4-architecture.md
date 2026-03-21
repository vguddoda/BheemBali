# Day 4: Kafka Architecture & Internals - Interview Questions

## 🎯 START HERE - Study Plan (3-4 hours)

### What You'll Learn
- ✅ Storage Internals - How Kafka stores data on disk
- ✅ Log Compaction - Keep latest value per key
- ✅ Controller & ISR - Failure recovery & leader election
- ✅ KRaft vs ZooKeeper - Modern architecture (10x faster)
- ✅ Retention Policies - Time, size, compaction

### How to Study
**Morning (2 hours):** Read questions 1-25 below
**Afternoon (2 hours):** Run hands-on labs (`labs/day4-architecture/README.md`)

### Quick Self-Test (Answer these first, then study)
1. Where does Kafka store messages?
2. How to find message by offset fast?
3. What is log compaction?
4. What is ISR?
5. KRaft vs ZooKeeper - key difference?

**After studying, 8/10 correct = Mastered!** ✅

---

## 📋 Quick Reference (Print This!)

**Storage:**
- Messages in log segments: .log (data), .index (offset→position), .timeindex
- Fast lookup: Binary search sparse index, then sequential scan
- Page cache critical: OS caches hot data in RAM

**Compaction:**
- Keeps latest value per key, tombstone (null) deletes key
- Use for: state, changelog | Use retention for: events, logs

**Controller:**
- Manages leader election, ISR tracking, broker failures
- Failover: ZooKeeper (~5s), KRaft (<500ms)

**ISR (In-Sync Replicas):**
- Replicas caught up with leader (lag < replica.lag.time.max.ms)
- acks=all waits for ISR only (not all replicas)

**KRaft vs ZooKeeper:**
- KRaft: No external dependency, 10x faster, millions of partitions
- Metadata in __cluster_metadata topic instead of ZooKeeper

**Retention:**
- Time: log.retention.hours | Size: log.retention.bytes | Compact: log.cleanup.policy=compact

**Why Kafka Fast:**
- Sequential I/O (6000x faster than random)
- Zero-copy (disk→network, skip app buffer)
- Batching, compression, page cache, parallelism

**Key Commands:**
```bash
# View segments
ls /var/lib/kafka/data/topic-0/

# Check ISR
kafka-topics --describe --topic <name>

# Under-replicated
kafka-topics --describe --under-replicated-partitions

# Consumer lag
kafka-consumer-groups --describe --group <name>
```

---

## Storage Internals

### 1. How does Kafka store messages on disk?
**Answer:**

Kafka stores messages in **log segments** on disk, organized by topic and partition.

**Directory Structure:**
```
/var/lib/kafka/data/
├── topic-name-0/           # Partition 0
│   ├── 00000000000000000000.log     # Segment file
│   ├── 00000000000000000000.index   # Offset index
│   ├── 00000000000000000000.timeindex  # Timestamp index
│   ├── 00000000000001000000.log     # Next segment
│   ├── 00000000000001000000.index
│   └── leader-epoch-checkpoint
├── topic-name-1/           # Partition 1
└── topic-name-2/           # Partition 2
```

**Key Points:**
- Each partition = separate directory
- Messages appended to active segment file (.log)
- Segment rolls over when size limit reached (default: 1GB)
- Immutable once written (append-only)

### 2. What is a log segment?
**Answer:**

A **log segment** is a single file containing a portion of partition data.

**Structure:**
```
Segment = .log + .index + .timeindex

00000000000000000000.log        # Message data (1GB max)
00000000000000000000.index      # Offset → Position mapping
00000000000000000000.timeindex  # Timestamp → Offset mapping
```

**Segment Naming:**
- Filename = base offset (first message offset in segment)
- `00000000000000000000.log` = messages starting from offset 0
- `00000000000001000000.log` = messages starting from offset 1,000,000

**Why Segments?**
1. **Deletion efficiency** - Delete old segments, not individual messages
2. **Compaction** - Compact segments independently
3. **Performance** - Active segment in memory, old segments on disk
4. **Recovery** - Recover from last good segment

### 3. How does Kafka find a message by offset so fast?
**Answer:**

Using **sparse index files** (.index).

**Index File Structure:**
```
Offset Index (.index):
Offset → File Position

0      → 0
1000   → 102400
2000   → 204800
3000   → 307200
```

**Lookup Process:**
To find offset 2500:
1. Binary search in index: Find largest offset ≤ 2500 (finds 2000 → position 204800)
2. Seek to position 204800 in .log file
3. Scan sequentially from 2000 to 2500
4. Return message at offset 2500

**Why Sparse?**
- Index entry every ~4KB of messages (configurable)
- Smaller index files (fit in memory)
- Fast binary search
- Small sequential scan cost

### 4. What's inside a .log file?
**Answer:**

**Message Format in .log file:**
```
┌─────────────────────────────────────┐
│ Offset: 8 bytes                     │
│ Message Size: 4 bytes               │
│ CRC: 4 bytes (checksum)            │
│ Magic Byte: 1 byte (version)       │
│ Attributes: 1 byte (compression)    │
│ Timestamp: 8 bytes                  │
│ Key Length: 4 bytes                 │
│ Key: variable                       │
│ Value Length: 4 bytes               │
│ Value: variable                     │
│ Headers: variable                   │
└─────────────────────────────────────┘
```

**Example:**
```
Offset: 12345
Size: 150 bytes
CRC: 0xABCD1234
Key: "user-123"
Value: "{"action":"login","time":"2024-01-01"}"
```

**Compression:**
If compression enabled (lz4, snappy, gzip):
- Multiple messages compressed together as batch
- Better compression ratio than single messages

### 4a. Deep Dive: Internal Structure of .log, .index, and .timeindex Files
**Answer:**

Let's examine the binary format and internals of all three segment files.

---

## **1. THE .LOG FILE (Message Data)**

**Binary Structure:**

The .log file is a **sequential binary file** containing message batches (records).

### **Record Batch Format (Kafka 2.0+):**

```
┌──────────────────────────────────────────────────────┐
│ BASE OFFSET (8 bytes)                                │  ← Starting offset of batch
├──────────────────────────────────────────────────────┤
│ BATCH LENGTH (4 bytes)                               │  ← Total batch size
├──────────────────────────────────────────────────────┤
│ PARTITION LEADER EPOCH (4 bytes)                     │  ← Leader generation
├──────────────────────────────────────────────────────┤
│ MAGIC (1 byte) = 2                                   │  ← Format version
├──────────────────────────────────────────────────────┤
│ CRC (4 bytes)                                        │  ← Checksum (CRC-32C)
├──────────────────────────────────────────────────────┤
│ ATTRIBUTES (2 bytes)                                 │  ← Compression, timestamp type
├──────────────────────────────────────────────────────┤
│ LAST OFFSET DELTA (4 bytes)                          │  ← Offset of last record in batch
├──────────────────────────────────────────────────────┤
│ FIRST TIMESTAMP (8 bytes)                            │  ← Timestamp of first message
├──────────────────────────────────────────────────────┤
│ MAX TIMESTAMP (8 bytes)                              │  ← Timestamp of last message
├──────────────────────────────────────────────────────┤
│ PRODUCER ID (8 bytes)                                │  ← For idempotence/transactions
├──────────────────────────────────────────────────────┤
│ PRODUCER EPOCH (2 bytes)                             │  ← Producer generation
├──────────────────────────────────────────────────────┤
│ BASE SEQUENCE (4 bytes)                              │  ← For deduplication
├──────────────────────────────────────────────────────┤
│ RECORDS COUNT (4 bytes)                              │  ← Number of messages in batch
├──────────────────────────────────────────────────────┤
│                                                      │
│ RECORD 1 (variable length)                          │  ← First message
│   ├─ Length (varint)                                │
│   ├─ Attributes (1 byte)                            │
│   ├─ Timestamp Delta (varint)                       │
│   ├─ Offset Delta (varint)                          │
│   ├─ Key Length (varint)                            │
│   ├─ Key (variable)                                 │
│   ├─ Value Length (varint)                          │
│   ├─ Value (variable)                               │
│   └─ Headers (variable)                             │
│                                                      │
│ RECORD 2 (variable length)                          │  ← Second message
│ ...                                                  │
│ RECORD N (variable length)                          │  ← Last message
└──────────────────────────────────────────────────────┘
```

### **Actual Example on Disk:**

```
File: 00000000000000000000.log

Byte Position | Hex Data           | Meaning
──────────────┼────────────────────┼──────────────────────────
0-7           | 00 00 00 00 00 00 00 00 | Base Offset = 0
8-11          | 00 00 00 B4        | Batch Length = 180 bytes
12-15         | 00 00 00 01        | Leader Epoch = 1
16            | 02                 | Magic = 2 (version)
17-20         | A3 2F 1C 84        | CRC checksum
21-22         | 00 00              | Attributes = 0 (no compression)
23-26         | 00 00 00 02        | Last offset delta = 2 (3 messages, offsets 0,1,2)
27-34         | 00 00 01 7F A1 B2 C3 D4 | First timestamp (milliseconds)
35-42         | 00 00 01 7F A1 B2 C3 D6 | Max timestamp
43-50         | FF FF FF FF FF FF FF FF | Producer ID = -1 (not idempotent)
51-52         | FF FF              | Producer epoch = -1
53-56         | FF FF FF FF        | Base sequence = -1
57-60         | 00 00 00 03        | Records count = 3
61-...        | [Record 1 data]    | Message 1
...           | [Record 2 data]    | Message 2
...           | [Record 3 data]    | Message 3
```

### **Individual Record Format Inside Batch:**

```
Length (varint):          0x2A (42 bytes)
Attributes (1 byte):      0x00
Timestamp Delta (varint): 0x00 (same as batch timestamp)
Offset Delta (varint):    0x00 (first message in batch)
Key Length (varint):      0x08 (8 bytes)
Key:                      "user-123" (UTF-8 encoded)
Value Length (varint):    0x1C (28 bytes)
Value:                    {"action":"login","ts":123}
Headers Count (varint):   0x00 (no headers)
```

### **Why Batching?**

Producer batches messages together → One disk write for multiple messages → Much faster!

---

## **2. THE .INDEX FILE (Offset → Position Mapping)**

**Binary Structure:**

The .index file is a **memory-mapped binary file** with fixed-size entries.

### **Index Entry Format:**

```
Each entry = 8 bytes:
┌──────────────────────────┐
│ Relative Offset (4 bytes)│  ← Offset relative to base offset
├──────────────────────────┤
│ Physical Position (4 bytes)│ ← Byte position in .log file
└──────────────────────────┘
```

### **Actual .index File Structure:**

```
File: 00000000000000000000.index
Base Offset (from filename) = 0

Byte Pos | Relative Offset | Physical Position | Actual Offset | Meaning
─────────┼─────────────────┼───────────────────┼───────────────┼─────────────────
0-3      | 00 00 00 00     | 00 00 00 00      | 0             | Offset 0 at byte 0
4-7      |                 |                   |               |
8-11     | 00 00 03 E8     | 00 00 19 00      | 1000          | Offset 1000 at byte 6400
12-15    |                 |                   |               |
16-19    | 00 00 07 D0     | 00 00 32 00      | 2000          | Offset 2000 at byte 12800
20-23    |                 |                   |               |
24-27    | 00 00 0B B8     | 00 00 4B 00      | 3000          | Offset 3000 at byte 19200
28-31    |                 |                   |               |
```

### **Key Properties:**

1. **Sparse Index** - Not every offset, only every ~4KB of data
2. **Fixed Size** - Each entry exactly 8 bytes
3. **Sorted** - Entries in offset order → Binary search O(log n)
4. **Memory Mapped** - OS loads into RAM for fast access
5. **Relative Offsets** - To keep numbers smaller (4 bytes enough)

### **Lookup Algorithm:**

To find offset 2500:

```
1. Binary search in .index for largest offset ≤ 2500
   → Finds entry: offset 2000 at position 12800
   
2. Seek to byte 12800 in .log file

3. Sequential scan from offset 2000 to 2500
   → Read ~500 messages sequentially
   
4. Return message at offset 2500
```

**Why Sparse?**

- Full index = 1 entry per message = Too large!
- Sparse index = 1 entry per ~4KB = Fits in RAM
- Binary search + small sequential scan = Fast enough!

---

## **3. THE .TIMEINDEX FILE (Timestamp → Offset Mapping)**

**Binary Structure:**

The .timeindex file maps timestamps to offsets for time-based searches.

### **Time Index Entry Format:**

```
Each entry = 12 bytes:
┌──────────────────────────────┐
│ Timestamp (8 bytes)          │  ← Message timestamp (milliseconds since epoch)
├──────────────────────────────┤
│ Relative Offset (4 bytes)    │  ← Offset with this timestamp
└──────────────────────────────┘
```

### **Actual .timeindex File Structure:**

```
File: 00000000000000000000.timeindex
Base Offset (from filename) = 0

Byte Pos | Timestamp (ms)      | Relative Offset | Actual Offset | Human Time
─────────┼─────────────────────┼─────────────────┼───────────────┼──────────────────
0-7      | 00 00 01 7F A1 B2 C3 D4 | 00 00 00 00  | 0            | 2024-01-15 10:00:00
8-11     |                     |                 |              |
12-19    | 00 00 01 7F A1 C4 E5 F6 | 00 00 03 E8  | 1000         | 2024-01-15 10:05:00
20-23    |                     |                 |              |
24-31    | 00 00 01 7F A1 D6 F7 08 | 00 00 07 D0  | 2000         | 2024-01-15 10:10:00
32-35    |                     |                 |              |
```

### **Use Case: Find Messages by Time**

To find messages from "2024-01-15 10:07:00":

```
Target timestamp: 1705315620000 ms

1. Binary search .timeindex for largest timestamp ≤ target
   → Finds entry: timestamp 1705315500000 (10:05:00) at offset 1000
   
2. Start reading from offset 1000 in .log file

3. Read messages, filter by timestamp
   → Return messages with timestamp ≥ 10:07:00
```

---

## **Visual Comparison: All Three Files Working Together**

```
SCENARIO: Find message at offset 2500

┌─────────────────────────────────────────────────────────────┐
│ 1. .INDEX FILE (Offset → Position mapping)                 │
├─────────────────────────────────────────────────────────────┤
│ Binary search for offset 2500:                             │
│   Entry: offset 2000 → position 12800                      │
│   Entry: offset 3000 → position 19200                      │
│   → Use offset 2000 (largest ≤ 2500)                       │
└─────────────────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. .LOG FILE (Message data)                                │
├─────────────────────────────────────────────────────────────┤
│ Seek to byte position 12800                                │
│ Sequential read from offset 2000:                          │
│   offset 2000 | key: user-200 | value: ...                │
│   offset 2001 | key: user-201 | value: ...                │
│   ...                                                       │
│   offset 2500 | key: user-500 | value: ... ← FOUND!       │
└─────────────────────────────────────────────────────────────┘

SCENARIO: Find messages from specific time

┌─────────────────────────────────────────────────────────────┐
│ 1. .TIMEINDEX FILE (Timestamp → Offset mapping)            │
├─────────────────────────────────────────────────────────────┤
│ Binary search for timestamp 10:07:00:                      │
│   Entry: timestamp 10:05:00 → offset 1000                  │
│   Entry: timestamp 10:10:00 → offset 2000                  │
│   → Use offset 1000 (largest ≤ target time)                │
└─────────────────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. .INDEX FILE                                              │
│   offset 1000 → position 6400                              │
└─────────────────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. .LOG FILE                                                │
│   Read from position 6400, filter by timestamp ≥ 10:07:00 │
└─────────────────────────────────────────────────────────────┘
```

---

## **File Size Examples:**

```
Topic with 1 million messages, avg size 1KB each:

.log file:        ~1 GB (actual message data)
.index file:      ~2 MB (1 entry per ~4KB = 250K entries × 8 bytes)
.timeindex file:  ~3 MB (1 entry per ~4KB = 250K entries × 12 bytes)

Total: ~1.005 GB
```

---

## **Key Insights:**

### **1. Why .log is Sequential:**
```
Append-only → Sequential writes → 600 MB/s (HDD)
No random updates → No fragmentation
Simple structure → Fast writes
```

### **2. Why .index is Binary Searchable:**
```
Fixed-size entries (8 bytes)
Sorted by offset
Entry N at position: N × 8 bytes
→ O(log n) binary search
```

### **3. Why Sparse Indexes Work:**
```
Trade-off:
- Smaller index (fits in RAM)
- Small sequential scan cost (reading 500 messages = ~1ms)
- Total lookup: Binary search (µs) + Sequential scan (ms) = Fast!
```

### **4. Memory Mapping Benefits:**
```
OS maps .index file to RAM
Access index entries like array
No file I/O overhead
Kernel handles caching automatically
```

---

## **Interview Deep Dive:**

**Q: "How does Kafka read a message by offset so fast?"**

**Complete Answer:**
```
1. .index file memory-mapped to RAM
2. Binary search (O(log n)) for offset in .index
3. Find nearest offset ≤ target (e.g., 2000 for target 2500)
4. Get physical position in .log file (e.g., byte 12800)
5. Seek to position 12800 (OS syscall)
6. Sequential read from offset 2000 to 2500 (~500 messages)
7. Return message at offset 2500

Total time: < 5ms
- Binary search: ~10 µs
- Seek: ~100 µs (if not in page cache)
- Sequential read: ~2ms
```

**Q: "Why not full index (every offset)?"**
```
Full index for 1M messages:
- 1M entries × 8 bytes = 8 MB per segment
- 100 segments = 800 MB just for indexes!
- Doesn't fit in RAM

Sparse index (1 per 4KB):
- 250K entries × 8 bytes = 2 MB per segment
- 100 segments = 200 MB
- Fits in RAM! ✓

Sequential scan cost:
- Read 500 messages = ~500 KB = ~2ms
- Acceptable trade-off!
```

**Q: "What if .index file corrupted?"**
```
Kafka can rebuild .index from .log:
1. Read entire .log file
2. Record offset → position for every ~4KB
3. Write new .index file
4. Resume operations

Recovery time: ~1 minute per GB
```

---

## **Practical Commands to Inspect:**

```bash
# View .log file contents (human-readable)
kafka-dump-log --files 00000000000000000000.log --print-data-log

# View .index file structure
kafka-dump-log --files 00000000000000000000.index --print-data-log

# View .timeindex file structure
kafka-dump-log --files 00000000000000000000.timeindex --print-data-log

# Check file sizes
ls -lh /var/lib/kafka/data/topic-0/

# Hex dump (see actual bytes)
hexdump -C 00000000000000000000.index | head -50
```

---

**Summary:**

- **.log** = Sequential binary file with batched messages
- **.index** = Binary file with fixed 8-byte entries (offset → position)
- **.timeindex** = Binary file with fixed 12-byte entries (timestamp → offset)
- **All three** work together for fast lookups (< 5ms)
- **Sparse indexes** = Small enough to fit in RAM
- **Memory mapping** = OS handles caching automatically

This design achieves both **fast writes** (sequential .log) and **fast reads** (binary search indexes)! 🚀

### 5. What is the page cache and why is it important?
**Answer:**

**Page Cache** = OS-level disk cache in RAM.

**How Kafka Uses It:**
```
Producer → Kafka → Page Cache → Disk
                      ↓
Consumer ← Page Cache (read from RAM, not disk!)
```

**Benefits:**
1. **Fast writes**: Write to page cache (RAM) immediately, OS flushes to disk later
2. **Fast reads**: Recent messages served from RAM
3. **Zero-copy**: Messages sent from page cache directly to network socket
4. **Free caching**: OS manages cache automatically

**Example:**
```
Producer sends 1000 msg/sec → Written to page cache (fast)
Consumer reads recent messages → Served from page cache (no disk I/O)
Old messages → Read from disk (slower but infrequent)
```

**Why it's critical:**
- Kafka doesn't implement its own cache
- Relies entirely on OS page cache
- More RAM = better performance
- Don't allocate huge JVM heap (leave RAM for page cache)

### 5a. What happens to messages when entire Kafka cluster crashes? Are they lost?
**Answer:**

**NO! Messages are NOT lost because they're written to persistent disk volumes.**

---

**Key Concept: Durability Through Disk Persistence**

Kafka writes all messages to `.log` files on **persistent disk volumes** (not just RAM). Even if the entire cluster crashes, messages are safe on disk and can be replayed when cluster recovers.

---

**How It Works:**

**Step 1: Messages Written to Disk**
```
Producer sends message:
1. Broker receives message
2. Written to page cache (RAM) - Fast!
3. OS flushes to disk (.log file) - Persistent!
4. ACK sent to producer

Disk location: /var/lib/kafka/data/topic-0/00000000000000000000.log
```

**Step 2: Entire Cluster Crashes**
```
T=0: All 3 brokers crash (power failure, network issue, etc.)
     - Broker-1: DOWN
     - Broker-2: DOWN
     - Broker-3: DOWN
     
Messages in RAM (page cache): Lost ✗
Messages on disk (.log files): Safe ✓
```

**Step 3: Cluster Recovers**
```
T=60s: All brokers restart
1. Brokers read .log files from disk
2. Rebuild in-memory indexes
3. Resume serving from last committed offset
4. Consumers replay from last committed position

→ No data loss! All messages recovered from disk ✓
```

---

**Visual Example:**

**Before Crash:**
```
Broker-1 Disk (/var/lib/kafka/data/):
  topic-orders-0/
    ├── 00000000000000000000.log    (1 GB, offsets 0-999,999)
    ├── 00000000000001000000.log    (1 GB, offsets 1M-1.9M)
    └── 00000000000002000000.log    (500 MB, offsets 2M-2.5M)
  
Total: 10,000 messages on disk
```

**During Crash:**
```
T=0: Power outage! All brokers down!
     Page cache cleared (RAM lost)
     Disk files intact ✓
```

**After Recovery:**
```
T=60s: Brokers restart, read from disk:
  ✓ Read 00000000000000000000.log
  ✓ Read 00000000000001000000.log
  ✓ Read 00000000000002000000.log
  
All 10,000 messages available!
Consumers resume from last committed offset
```

---

**Why Messages Survive:**

**1. Persistent Volumes (Docker/Kubernetes)**
```yaml
# Docker Compose example
volumes:
  - /var/lib/kafka/data:/var/lib/kafka/data

# Even if container dies, volume persists on host
```

**2. Replication Factor**
```
Topic with RF=3:
- Broker-1 has copy → Disk 1
- Broker-2 has copy → Disk 2
- Broker-3 has copy → Disk 3

Even if 2 brokers lose disks, 1 copy survives!
```

**3. OS Flush Guarantees**
```
acks=all ensures:
1. Message written to leader's disk
2. Message replicated to followers' disks
3. Only then ACK sent to producer

→ Guaranteed on disk before ACK
```

---

**Real-World Scenarios:**

**Scenario 1: Complete Data Center Outage**
```
Problem: Entire data center loses power
Impact: All brokers down, page cache cleared

Recovery:
1. Power restored
2. Brokers start
3. Read .log files from disk
4. Consumers resume from last offset
5. No messages lost ✓

Time to recover: 2-5 minutes
```

**Scenario 2: Kubernetes Pod Restart**
```
Problem: All Kafka pods killed (rolling update, crash)
Impact: Containers destroyed, RAM cleared

Recovery:
1. Pods recreate
2. Mount same persistent volumes
3. Read existing .log files
4. Resume operations
5. No messages lost ✓

Time to recover: 30 seconds - 2 minutes
```

**Scenario 3: Disk Failure (One Broker)**
```
Problem: Broker-1 disk fails
Impact: Lost local copy of partition data

Recovery:
1. Broker-1 removed from ISR
2. Broker-2 or Broker-3 becomes leader (have replica)
3. Replace disk on Broker-1
4. Broker-1 resyncs from leader
5. Rejoins ISR
6. No messages lost (replicas on other brokers) ✓
```

---

**Configuration for Maximum Durability:**

```properties
# Ensure messages written to disk before ACK
acks=all
min.insync.replicas=2

# Replication factor (3 copies on different disks)
replication.factor=3

# Flush settings (how often to force OS flush)
log.flush.interval.messages=10000  # Flush every 10K messages
log.flush.interval.ms=1000         # Or every 1 second

# Segment settings (smaller segments = faster recovery)
log.segment.bytes=1073741824  # 1 GB
```

---

**What CAN Be Lost:**

**1. Un-ACKed Messages (acks=0 or acks=1)**
```
Producer with acks=0 or acks=1:
Message written only to leader → Leader crashes before replication
→ Message lost if leader disk fails

Solution: Use acks=all + min.insync.replicas=2
```

**2. Messages in Page Cache (Not Yet Flushed)**
```
Message in page cache → Power failure → Not yet on disk
→ Lost

But: Very rare! OS flushes frequently (~30 seconds)
```

**3. Consumer Offsets (If Not Committed)**
```
Consumer processes message → Doesn't commit offset → Crashes
→ Message reprocessed (at-least-once)
→ Not lost, just reprocessed
```

---

**Interview Follow-Up:**

**Q: "What if all replicas fail simultaneously?"**
```
A: If all 3 brokers with replicas crash AND all 3 disks fail:
   → Data lost (catastrophic failure)
   
Mitigation:
- Multi-datacenter replication
- Backup to S3/HDFS
- RF=3 makes simultaneous disk failure extremely rare
```

**Q: "How fast is recovery from disk?"**
```
A: Depends on segment size:
   - Small segments (1 GB): 10-30 seconds
   - Large segments (10 GB): 2-5 minutes
   - Read from disk → Rebuild indexes → Resume
```

**Q: "Do consumers need to re-read from beginning?"**
```
A: No! Consumers resume from last committed offset.
   Offsets stored in __consumer_offsets topic (also on disk).
```

---

**Key Takeaways:**

1. ✅ **Messages written to .log files on persistent disk volumes**
2. ✅ **Survive cluster crashes, container restarts, power failures**
3. ✅ **Replication Factor ensures multiple disk copies**
4. ✅ **acks=all ensures on disk before ACK**
5. ✅ **Recovery = Read from disk + Rebuild indexes (minutes)**
6. ✅ **Consumers resume from last committed offset**

**Kafka is DURABLE by design!** Messages persist through failures because they live on disk, not just RAM. 🛡️

---

## Log Compaction

### 6. What is log compaction?
**Answer:**

**Log compaction** keeps only the **latest value for each key**, deleting old values.

**Before Compaction:**
```
Offset | Key    | Value
0      | user1  | name=Alice
1      | user2  | name=Bob
2      | user1  | name=Alice Smith  ← Latest
3      | user3  | name=Charlie
4      | user2  | name=Robert       ← Latest
```

**After Compaction:**
```
Offset | Key    | Value
2      | user1  | name=Alice Smith
4      | user2  | name=Robert
3      | user3  | name=Charlie
```

**Use Case:** Event sourcing, changelog topics, state synchronization

### 7. When to use log compaction vs retention?
**Answer:**

| Feature | Retention (Time/Size) | Compaction |
|---------|----------------------|------------|
| **Deletes** | All messages older than X | Old values of same key |
| **Use Case** | Time-series, logs, events | State, changelog, snapshots |
| **Guarantees** | Messages available for N days | Latest value always available |
| **Example** | Delete logs after 7 days | Keep latest user profile |

**Configuration:**
```properties
# Time-based retention (default)
log.retention.hours=168  # 7 days

# Size-based retention
log.retention.bytes=1073741824  # 1 GB

# Compaction
log.cleanup.policy=compact

# Both
log.cleanup.policy=compact,delete
```

**Real-World Example:**

**Compaction:**
```
Topic: user-profiles (compacted)
Key: user-id
Value: latest user profile
→ Always have latest state for each user
```

**Retention:**
```
Topic: user-clickstream (7-day retention)
No keys, just events
→ Delete after 7 days to save space
```

### 8. How does compaction work internally?
**Answer:**

**IMPORTANT: Compaction does NOT modify existing segments! It creates NEW segments.**

Kafka maintains append-only invariant. Compaction = Read old segments → Write new segments → Delete old.

---

**Step-by-Step Process:**

**Before Compaction:**
```
Segment 00000000000000000000.log (old, immutable):
Offset | Key    | Value
0      | user1  | name=Alice
1      | user2  | name=Bob
2      | user1  | name=Alice Smith  ← Latest for user1
3      | user3  | name=Charlie
4      | user2  | name=Robert       ← Latest for user2
```

**Phase 1: Build Hash Table (Read-only scan)**
```
Cleaner thread scans old segment:
1. Read all messages sequentially
2. Build in-memory hash map:
   user1 → offset 2 (latest)
   user2 → offset 4 (latest)
   user3 → offset 3 (only one)
3. No modification to old segment!
```

**Phase 2: Create NEW Segment**
```
Cleaner thread creates NEW segment 00000000000000000000.log.cleaned:
1. Read old segment sequentially
2. For each message:
   - Is this the latest offset for this key in hash map?
   - YES → Copy to new segment
   - NO → Skip (older version)
3. Write new segment with only latest values:

New Segment (00000000000000000000.log.cleaned):
Offset | Key    | Value
2      | user1  | name=Alice Smith
4      | user2  | name=Robert
3      | user3  | name=Charlie
```

**Phase 3: Atomic Swap**
```
1. Rename: .log.cleaned → .log (atomic operation)
2. Delete old segment
3. Rebuild indexes for new segment
```

---

**Visual Timeline:**

```
T=0: Old segment exists
     00000000000000000000.log (5 messages)

T=1: Cleaner scans, builds hash table
     (Old segment untouched - read-only)

T=2: Cleaner creates new segment
     00000000000000000000.log.cleaned (3 messages)
     (Old segment still exists)

T=3: Atomic rename
     .log.cleaned → .log (new replaces old)

T=4: Delete old segment
     Old .log marked .log.deleted
     
T=5: Old segment completely removed
```

---

**Why It's Not Binary Search & Update:**

❌ **Does NOT happen:**
```
1. Find offset 0 in old segment (binary search)
2. Update value in place
3. Write back to disk
→ This would violate append-only!
```

✅ **Actually happens:**
```
1. Read entire old segment sequentially
2. Write new segment with filtered messages
3. Delete old segment
→ Append-only maintained!
```

---

**Active vs Inactive Segments:**
```
Partition:
├── Active segment (00000000000005000000.log)
│   └── Currently being written → NEVER compacted
└── Inactive segments (closed, immutable)
    ├── 00000000000000000000.log → Eligible for compaction
    ├── 00000000000001000000.log → Eligible for compaction
    └── 00000000000003000000.log → Eligible for compaction
```

---

**Key Points:**

1. **Append-only preserved** - No in-place updates
2. **Sequential I/O** - Read old, write new (still fast!)
3. **Copy-on-compact** - Like copy-on-write
4. **Space needed** - Temporarily 2x space during compaction
5. **Atomic swap** - Rename is atomic, consumers see consistent view

---

**Compaction Settings:**
```properties
log.cleaner.enable=true
log.cleaner.threads=1  # Number of cleaner threads
log.cleaner.io.max.bytes.per.second=unlimited
min.compaction.lag.ms=0  # Min time before eligible
max.compaction.lag.ms=9223372036854775807  # Max time before forced
segment.ms=604800000  # 7 days before segment rolls (enables compaction)
```

### 9. What happens if you send null value for a key?
**Answer:**

**Tombstone Record** = Key with null value.

**Purpose:** Delete key from compacted topic.

**Example:**
```java
// Delete user-123 from compacted topic
producer.send(new ProducerRecord<>("users", "user-123", null));
```

**Compaction Behavior:**
```
Before:
Offset | Key      | Value
100    | user-123 | {"name":"Alice"}
200    | user-123 | {"name":"Alice Smith"}
300    | user-123 | null  ← Tombstone

After Compaction:
Offset | Key      | Value
300    | user-123 | null  ← Kept temporarily

After delete.retention.ms:
(Record completely removed)
```

**Configuration:**
```properties
delete.retention.ms=86400000  # Keep tombstone for 24 hours
```

**Why keep tombstone temporarily?**
- Consumers need time to process deletion
- After 24 hours, tombstone itself deleted

---

## Controller & Cluster Management

### 10. What is the Kafka Controller?
**Answer:**

The **Controller** is a special broker responsible for cluster-wide admin tasks.

**Responsibilities:**
1. **Leader election** - Choose leaders for partitions
2. **Replica management** - Track ISR (In-Sync Replicas)
3. **Partition assignment** - Assign partitions to brokers
4. **Topic operations** - Create/delete topics
5. **Broker failures** - Detect failures, trigger re-election

**How it works:**
```
Cluster with 3 brokers:
Broker-1 (Controller) ← Elected via ZooKeeper/KRaft
Broker-2
Broker-3

If Broker-1 fails:
→ New election
→ Broker-2 becomes Controller
```

**Controller Metadata:**
- Stored in ZooKeeper (old) or KRaft metadata log (new)
- All brokers watch controller
- Controller sends commands to brokers

### 11. What happens when a broker fails?
**Answer:**

**Failure Detection & Recovery:**

**Step 1: Detection**
```
Broker-3 fails → Heartbeat stops → ZooKeeper/Controller detects
```

**Step 2: Controller Action**
```
For each partition where Broker-3 was leader:
1. Select new leader from ISR (In-Sync Replicas)
2. Update metadata
3. Notify all brokers
```

**Step 3: Leader Election**
```
Partition topic-A-0:
Leader: Broker-3 (failed) ✗
ISR: [Broker-1, Broker-2, Broker-3]

→ New Leader: Broker-1 ✓
→ New ISR: [Broker-1, Broker-2]
```

**Step 4: Replication**
```
Broker-3 comes back:
1. Reads from new leader (Broker-1)
2. Catches up to latest offset
3. Rejoins ISR
```

**Timeline:**
```
T=0s:  Broker-3 fails
T=6s:  Controller detects (session.timeout.ms)
T=7s:  New leaders elected
T=8s:  Clients redirect to new leaders
→ ~8 seconds downtime for affected partitions
```

### 12. What is ISR (In-Sync Replicas)?
**Answer:**

**ISR** = Replicas that are "caught up" with leader.

**Criteria to be in ISR:**
```
1. Replica must be alive (sending heartbeats)
2. Replica must be caught up (lag < replica.lag.time.max.ms)
```

**Example:**
```
Partition with 3 replicas:
Leader: Broker-1 (offset: 10000)
Follower-1: Broker-2 (offset: 10000) ✓ In ISR
Follower-2: Broker-3 (offset: 9500)  ✗ Out of ISR (lagging)

ISR = [Broker-1, Broker-2]
```

**Why ISR Matters:**
```properties
# Only ack when all ISR members have written
acks=all → Wait for ISR replicas, not ALL replicas
```

**If replica falls behind:**
```
Broker-3 lag > 10 seconds → Removed from ISR
→ acks=all no longer waits for Broker-3
→ Faster acknowledgments
→ Broker-3 can catch up asynchronously
```

**Configuration:**
```properties
replica.lag.time.max.ms=10000  # 10 sec lag = out of ISR
min.insync.replicas=2  # Min ISR size for acks=all
```

### 13. What is unclean leader election?
**Answer:**

**Unclean Leader Election** = Elect leader from **outside ISR**.

**Scenario:**
```
Partition:
Leader: Broker-1 (offset: 10000) → Fails
ISR: [Broker-1] → Empty!
Follower: Broker-2 (offset: 9500) → Not in ISR (lagging)
```

**Options:**
```
1. Clean election: Wait for Broker-1 to recover
   → Data safe, but partition unavailable

2. Unclean election: Promote Broker-2
   → Partition available, but lose offsets 9500-10000 (data loss!)
```

**Configuration:**
```properties
unclean.leader.election.enable=false  # Default: false (prefer availability)
```

**Trade-off:**
```
unclean.leader.election.enable=false
→ Durability over availability
→ Wait for ISR replica
→ Partition unavailable until ISR member returns

unclean.leader.election.enable=true
→ Availability over durability
→ Accept data loss
→ Partition always available
```

**When to use true:**
- Metrics, logs (acceptable to lose some)
- High availability critical
- Can reprocess from source

---

## ZooKeeper vs KRaft

### 14. What is ZooKeeper's role in Kafka?
**Answer:**

**ZooKeeper** (legacy) stores Kafka metadata.

**What it stores:**
1. **Broker registration** - Which brokers are alive
2. **Topic configuration** - Topics, partitions, replication
3. **Controller election** - Which broker is controller
4. **ACLs** - Security permissions
5. **Quota configuration** - Rate limits

**Problems with ZooKeeper:**
1. **External dependency** - Extra system to manage
2. **Scaling limits** - ZooKeeper becomes bottleneck at scale
3. **Complexity** - Two distributed systems (Kafka + ZooKeeper)
4. **Recovery time** - Slow metadata operations
5. **Partition limits** - Struggles with 100K+ partitions

**Architecture:**
```
ZooKeeper Cluster (3-5 nodes)
    ↓ (Metadata)
Kafka Cluster (N brokers)
    ↓ (Data)
Producers/Consumers
```

### 15. What is KRaft and how does it work?
**Answer:**

**KRaft** = **K**afka **Raft** = Kafka without ZooKeeper!

**Key Innovation:**
- Kafka brokers manage their own metadata using Raft consensus
- No external ZooKeeper cluster needed

**Architecture:**
```
KRaft Mode:

Controller Quorum (3 brokers in controller role)
  ├── Broker-1 (Controller + Data)
  ├── Broker-2 (Controller + Data)
  └── Broker-3 (Controller + Data)
  
Metadata stored in special internal topic: __cluster_metadata
```

**How KRaft Works:**
1. **Controller Quorum** - 3-5 brokers elected as controllers
2. **Metadata Log** - Special partition `__cluster_metadata`
3. **Raft Consensus** - Leader election via Raft (not ZooKeeper)
4. **Event-driven** - Metadata changes = events in log

**Benefits:**
1. **Simpler** - One system instead of two
2. **Faster** - Metadata operations 10x faster
3. **Scalable** - Support millions of partitions
4. **Faster recovery** - Controller failover in milliseconds

### 16. ZooKeeper vs KRaft: Complete Comparison
**Answer:**

| Feature | ZooKeeper Mode | KRaft Mode |
|---------|---------------|------------|
| **Architecture** | Kafka + ZooKeeper (2 systems) | Kafka only (1 system) |
| **Metadata Storage** | ZooKeeper znodes | `__cluster_metadata` topic |
| **Controller Election** | Via ZooKeeper | Via Raft consensus |
| **Partition Limit** | ~200K partitions | Millions of partitions |
| **Failover Time** | 2-5 seconds | < 500ms |
| **Operations** | Manage 2 systems | Manage 1 system |
| **Resource Usage** | Extra nodes for ZK | No extra nodes needed |
| **Maturity** | Stable (10+ years) | Production-ready (Kafka 3.3+) |
| **Migration** | Default in old versions | Default in Kafka 3.3+ |

**Configuration Comparison:**

**ZooKeeper Mode:**
```properties
zookeeper.connect=zk1:2181,zk2:2181,zk3:2181
```

**KRaft Mode:**
```properties
process.roles=broker,controller
controller.quorum.voters=1@broker1:9093,2@broker2:9093,3@broker3:9093
```

**Migration Path:**
```
Kafka 2.x → ZooKeeper only
Kafka 3.0-3.2 → ZooKeeper (default), KRaft (preview)
Kafka 3.3+ → KRaft (production-ready)
Kafka 4.0+ → ZooKeeper deprecated
```

**Current Recommendation (2024):**
- New clusters: Use KRaft ✅
- Existing clusters: Plan migration to KRaft
- Your Docker setup: Already using KRaft! ✅

### 17. How does KRaft handle controller failover?
**Answer:**

**KRaft Controller Failover:**

**Setup:**
```
Controller Quorum:
  Leader: Controller-1
  Followers: Controller-2, Controller-3
```

**Failover Process:**

**Step 1: Leader fails**
```
Controller-1 fails → Heartbeats stop
```

**Step 2: New election (Raft)**
```
Controller-2 and Controller-3 detect timeout
→ Start new election round
→ Vote for new leader (majority needed)
→ Controller-2 becomes leader (< 500ms)
```

**Step 3: Resume operations**
```
Controller-2 now handles:
- Partition leader elections
- ISR management
- Topic operations
```

**Why so fast?**
1. **No external coordination** - Internal Raft algorithm
2. **Pre-warmed metadata** - All controllers have full metadata
3. **Event-driven** - Changes replicated via metadata log
4. **Optimized** - Designed for Kafka's needs

**vs ZooKeeper:**
```
ZooKeeper failover: 2-5 seconds
KRaft failover: < 500ms
→ 10x faster!
```

---

## Retention Policies

### 18. What are the different retention policies?
**Answer:**

**Three Types:**

**1. Time-Based Retention**
```properties
log.retention.hours=168  # Keep 7 days
log.retention.minutes=10080
log.retention.ms=604800000
```
Delete segments older than 7 days.

**2. Size-Based Retention**
```properties
log.retention.bytes=1073741824  # Keep 1 GB per partition
```
Delete oldest segments when partition exceeds 1 GB.

**3. Compaction**
```properties
log.cleanup.policy=compact
```
Keep only latest value for each key.

**Combined:**
```properties
log.cleanup.policy=compact,delete
log.retention.hours=168
```
Compact + delete after 7 days.

### 19. How does Kafka delete old segments?
**Answer:**

**Deletion Process:**

**Step 1: Mark for deletion**
```
Log Cleaner thread runs periodically:
1. Check segment last modified time
2. If older than retention period → Mark for deletion
3. Rename: .log → .log.deleted
```

**Step 2: Actual deletion**
```
After file.delete.delay.ms (default: 60 seconds):
1. Close all file handles
2. Delete .log.deleted, .index.deleted, .timeindex.deleted files
```

**Why delay?**
- Give consumers time to finish reading
- Prevent "file not found" errors

**Example:**
```
T=0:   Segment 00000000000000000000.log last modified 7 days ago
T=1:   Cleaner marks: .log → .log.deleted
T=61s: Files actually deleted from disk
```

**Configuration:**
```properties
log.retention.check.interval.ms=300000  # Check every 5 min
file.delete.delay.ms=60000  # Wait 60s before actual deletion
```

### 20. How to design retention policy for business needs?
**Answer:**

**Decision Framework:**

**Question 1: What's the data type?**

**Events/Logs (immutable):**
```properties
# Example: Click streams, audit logs
log.cleanup.policy=delete
log.retention.hours=168  # 7 days
```

**State/Changelog (latest value matters):**
```properties
# Example: User profiles, inventory
log.cleanup.policy=compact
min.compaction.lag.ms=0
```

**Question 2: What's the access pattern?**

**Hot data (recent, frequently accessed):**
```properties
log.retention.hours=24  # Keep 1 day
# Consumers read within hours
```

**Cold data (historical, rare access):**
```properties
log.retention.hours=720  # Keep 30 days
# Archive to S3/HDFS for long-term storage
```

**Question 3: What's the volume?**

**High volume (GB/hour):**
```properties
log.retention.hours=24  # Shorter retention
log.retention.bytes=1073741824  # 1 GB limit
log.segment.bytes=536870912  # Smaller segments (512 MB)
```

**Low volume (MB/hour):**
```properties
log.retention.hours=720  # Longer retention
# No size limit needed
```

**Real-World Examples:**

**Use Case 1: User Activity Tracking**
```properties
# Keep 7 days, high volume
log.retention.hours=168
log.retention.bytes=10737418240  # 10 GB per partition
log.segment.bytes=1073741824  # 1 GB segments
```

**Use Case 2: Financial Transactions**
```properties
# Keep 7 years (regulatory), compact for state
log.cleanup.policy=compact,delete
log.retention.hours=61320  # 7 years
min.compaction.lag.ms=86400000  # Compact after 1 day
```

**Use Case 3: Real-time Metrics**
```properties
# Keep 1 hour, very high volume
log.retention.hours=1
log.retention.bytes=5368709120  # 5 GB per partition
```

**Use Case 4: Event Sourcing (CDC)**
```properties
# Keep forever, compact
log.cleanup.policy=compact
# No retention limits - keep all state changes
```

**Capacity Planning:**
```
Daily volume per partition: 10 GB
Retention: 7 days
Replication factor: 3

Total storage needed:
10 GB/day × 7 days × 3 replicas × Number of partitions
= 210 GB per partition

With 100 partitions: 21 TB total
```

---

## Advanced Architecture

### 21. What is a partition replica and how does replication work?
**Answer:**

**Replica** = Copy of partition data on different broker.

**Setup:**
```
Topic: orders (replication factor = 3)
Partition 0:
  ├── Leader: Broker-1 (handles all reads/writes)
  ├── Follower: Broker-2 (replicates from leader)
  └── Follower: Broker-3 (replicates from leader)
```

**Replication Flow:**
```
1. Producer → Leader (Broker-1)
2. Leader writes to local log
3. Followers fetch from leader (pull model)
4. Followers write to local log
5. Followers send ACK to leader
6. Leader updates ISR
7. Leader sends ACK to producer (if acks=all)
```

**Why Pull (not Push)?**
- Followers control their rate
- Leader doesn't get overwhelmed
- Followers can catch up at their own pace

### 22. What happens during partition reassignment?
**Answer:**

**Partition Reassignment** = Moving partition replicas to different brokers.

**Use Cases:**
- Add new brokers → Rebalance load
- Decommission broker → Move partitions away
- Fix uneven distribution

**Process:**
```
Before:
Partition 0: [Broker-1, Broker-2, Broker-3]

Reassign to:
Partition 0: [Broker-2, Broker-3, Broker-4]

Steps:
1. Add Broker-4 as follower
2. Broker-4 catches up (copies all data)
3. Broker-4 joins ISR
4. Remove Broker-1 from replica set
5. Update metadata
```

**During reassignment:**
- Partition remains available
- Leader stays same until reassignment completes
- Heavy network traffic (copying data)

**Throttling:**
```properties
# Limit replication bandwidth
leader.replication.throttled.rate=10485760  # 10 MB/s
follower.replication.throttled.rate=10485760
```

### 23. How does Kafka achieve high throughput?
**Answer:**

**Key Design Decisions:**

**1. Sequential I/O**
```
Append-only log → Sequential writes to disk
Sequential writes: 600 MB/s (HDD)
Random writes: 100 KB/s (HDD)
→ 6000x faster!
```

**2. Zero-Copy**
```
Traditional:
Disk → Kernel buffer → Application buffer → Socket buffer → Network

Zero-Copy (sendfile):
Disk → Kernel buffer → Network
→ Skip 2 copies, 2x faster
```

**3. Batching**
```
Producer batches messages
Broker writes batches
Consumer fetches batches
→ Amortize overhead
```

**4. Compression**
```
Compress batch (not individual messages)
Better compression ratio
Less network bandwidth
```

**5. Page Cache**
```
OS caches hot data in RAM
Consumers read from cache (not disk)
No double caching (OS + application)
```

**6. Partition Parallelism**
```
100 partitions × 10 MB/s per partition
= 1 GB/s total throughput
```

**Result:**
```
Single Kafka cluster:
- 100K+ messages/sec
- 1 GB/s throughput
- Single-digit millisecond latency
```

### 24. What is log segment rolling?
**Answer:**

**Segment Rolling** = Close active segment, create new one.

**Triggers:**
```
1. Size limit reached:
   log.segment.bytes=1073741824  # 1 GB

2. Time limit reached:
   log.segment.ms=604800000  # 7 days

3. Index full:
   segment.index.bytes=10485760  # 10 MB
```

**Process:**
```
Active segment: 00000000000001000000.log (950 MB)
New message arrives → Segment size = 1.01 GB → Trigger roll

1. Flush active segment to disk
2. Close file handles
3. Create new segment: 00000000000002000000.log
4. Continue writing to new segment
```

**Why important?**
- Deletion granularity (delete whole segments)
- Compaction efficiency (compact old segments)
- Recovery speed (smaller segments = faster recovery)

**Tuning:**
```properties
# High-volume topic (fast rolling)
log.segment.bytes=536870912  # 512 MB
log.segment.ms=3600000  # 1 hour

# Low-volume topic (slow rolling)
log.segment.bytes=1073741824  # 1 GB
log.segment.ms=604800000  # 7 days
```

### 25. How to monitor Kafka cluster health?
**Answer:**

**Key Metrics to Monitor:**

**Broker Metrics:**
```
1. UnderReplicatedPartitions
   → Should be 0 (all partitions fully replicated)
   
2. OfflinePartitionsCount
   → Should be 0 (all partitions available)
   
3. ActiveControllerCount
   → Should be 1 (exactly one controller)
   
4. Request latency (produce/fetch)
   → Track p50, p95, p99
   
5. Bytes in/out rate
   → Monitor throughput
```

**Topic Metrics:**
```
6. Consumer lag
   → How far behind consumers are
   
7. Log size
   → Disk usage per partition
   
8. Messages per second
   → Throughput per topic
```

**JVM Metrics:**
```
9. GC pause time
   → Should be < 100ms
   
10. Heap usage
    → Should be < 80%
```

**Monitoring Commands:**
```bash
# Check under-replicated partitions
kafka-topics --describe --under-replicated-partitions \
  --bootstrap-server localhost:19092

# Check consumer lag
kafka-consumer-groups --describe --group my-group \
  --bootstrap-server localhost:19092

# JMX metrics
# Connect with JConsole or Prometheus JMX Exporter
```

**Alerting Thresholds:**
```
Critical:
- UnderReplicatedPartitions > 0 for 5 min
- OfflinePartitionsCount > 0
- ActiveControllerCount != 1

Warning:
- Consumer lag > 1000 messages
- GC pause > 100ms
- Disk usage > 80%
```

---

## Interview Practice

### Quick Recall Questions:

1. **Where does Kafka store messages?** → Log segments on disk
2. **How to find message by offset?** → Binary search in sparse index
3. **What is compaction?** → Keep latest value per key
4. **What is Controller?** → Special broker managing cluster metadata
5. **ISR meaning?** → In-Sync Replicas (caught up with leader)
6. **KRaft vs ZooKeeper?** → KRaft = no external dependency, faster
7. **Retention types?** → Time, size, compaction
8. **Why Kafka fast?** → Sequential I/O, zero-copy, batching, page cache

### Scenario Questions:

**Q: "Design retention for audit log system with 100 GB/day, need 90 days retention"**
```properties
log.retention.hours=2160  # 90 days
log.segment.bytes=1073741824  # 1 GB segments
log.retention.bytes=9663676416  # 9 TB per partition (90 × 100 GB)
```

**Q: "Broker fails, how long until recovery?"**
- Controller detects: 6 seconds (session.timeout.ms)
- New leader elected: 1 second
- Clients redirect: 1 second
- Total: ~8 seconds

**Q: "Topic has 1 million partitions, ZooKeeper or KRaft?"**
- KRaft! ZooKeeper struggles beyond 200K partitions

---

## Hands-On Practice

### Check Your Cluster Internals:

```bash
# 1. View log segments
docker exec kafka-broker-1 ls -lh /var/lib/kafka/data/test-topic-0/

# 2. Check segment contents
docker exec kafka-broker-1 kafka-dump-log \
  --files /var/lib/kafka/data/test-topic-0/00000000000000000000.log \
  --print-data-log

# 3. View controller
docker exec kafka-broker-1 kafka-metadata-shell \
  --snapshot /var/lib/kafka/kraft-combined-logs/__cluster_metadata-0/

# 4. Check under-replicated partitions
docker exec kafka-broker-1 kafka-topics \
  --describe --under-replicated-partitions \
  --bootstrap-server localhost:19092
```

---

## Summary

You now understand:

✅ **Storage Internals:**
- Log segments, indexes, page cache
- Message format, sequential I/O
- Zero-copy, batching optimizations

✅ **Log Compaction:**
- Keep latest value per key
- Tombstone records for deletion
- When to use vs retention

✅ **Controller:**
- Cluster metadata management
- Leader election, ISR tracking
- Failure detection and recovery

✅ **KRaft vs ZooKeeper:**
- KRaft = self-managed metadata
- Faster, simpler, more scalable
- Migration from ZooKeeper

✅ **Retention Policies:**
- Time, size, compaction
- Design for business needs
- Capacity planning

**You're now at 75% Kafka knowledge!** 🚀



