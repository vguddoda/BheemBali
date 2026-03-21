# Day 4 Lab: Kafka Architecture & Internals

## Lab Objectives

By the end of this lab, you will:
- ✅ Inspect log segments and indexes on disk
- ✅ Test log compaction with real data
- ✅ Observe controller failover behavior
- ✅ Compare KRaft vs ZooKeeper architecture
- ✅ Design and test retention policies
- ✅ Monitor cluster health metrics

---

## Lab 1: Exploring Storage Internals (30 min)

### Step 1: Create a Test Topic
```bash
docker exec kafka-broker-1 kafka-topics --create \
  --bootstrap-server localhost:19092 \
  --topic storage-test \
  --partitions 3 \
  --replication-factor 3
```

### Step 2: Send Some Messages
```bash
# Send 1000 messages
for i in {1..1000}; do
  echo "key-$i:message-$i-$(date +%s)"
done | docker exec -i kafka-broker-1 kafka-console-producer \
  --bootstrap-server localhost:19092 \
  --topic storage-test \
  --property "parse.key=true" \
  --property "key.separator=:"
```

### Step 3: Inspect Directory Structure
```bash
# View partition directories
docker exec kafka-broker-1 ls -lh /var/lib/kafka/data/ | grep storage-test

# Pick a partition and view files
docker exec kafka-broker-1 ls -lh /var/lib/kafka/data/storage-test-0/
```

**Expected Output:**
```
00000000000000000000.log        # Message data
00000000000000000000.index      # Offset index
00000000000000000000.timeindex  # Time index
leader-epoch-checkpoint         # Leader epoch tracking
partition.metadata              # Partition metadata
```

### Step 4: Examine Log Segment Contents
```bash
# Dump log file (first 10 messages)
docker exec kafka-broker-1 kafka-dump-log \
  --files /var/lib/kafka/data/storage-test-0/00000000000000000000.log \
  --print-data-log | head -50
```

**Observe:**
- Offset numbers
- Message keys and values
- Timestamps
- Batch metadata

### Step 5: Check Index Files
```bash
# View offset index
docker exec kafka-broker-1 kafka-dump-log \
  --files /var/lib/kafka/data/storage-test-0/00000000000000000000.index \
  --print-data-log
```

**What to Notice:**
- Sparse entries (not every offset)
- Offset → File position mapping
- Binary searchable structure

### Step 6: Monitor Page Cache Usage
```bash
# Check memory usage (page cache)
docker exec kafka-broker-1 free -h
```

**Understanding:**
- "cached" memory = page cache
- Kafka leverages this for fast reads
- More available memory = better performance

---

## Lab 2: Log Compaction in Action (30 min)

### Step 1: Create Compacted Topic
```bash
docker exec kafka-broker-1 kafka-topics --create \
  --bootstrap-server localhost:19092 \
  --topic user-profiles \
  --partitions 1 \
  --replication-factor 3 \
  --config cleanup.policy=compact \
  --config segment.ms=10000 \
  --config min.cleanable.dirty.ratio=0.01
```

### Step 2: Send Initial User Profiles
```bash
cat << 'EOF' | docker exec -i kafka-broker-1 kafka-console-producer \
  --bootstrap-server localhost:19092 \
  --topic user-profiles \
  --property "parse.key=true" \
  --property "key.separator=:"
user1:{"name":"Alice","age":25}
user2:{"name":"Bob","age":30}
user3:{"name":"Charlie","age":35}
EOF
```

### Step 3: Update User Profiles
```bash
cat << 'EOF' | docker exec -i kafka-broker-1 kafka-console-producer \
  --bootstrap-server localhost:19092 \
  --topic user-profiles \
  --property "parse.key=true" \
  --property "key.separator=:"
user1:{"name":"Alice Smith","age":26}
user2:{"name":"Robert","age":31}
user1:{"name":"Alice S. Smith","age":26}
EOF
```

### Step 4: Read All Messages (Before Compaction)
```bash
docker exec kafka-broker-1 kafka-console-consumer \
  --bootstrap-server localhost:19092 \
  --topic user-profiles \
  --from-beginning \
  --property print.key=true \
  --timeout-ms 5000
```

**Count:** Should see 6 messages (3 original + 3 updates)

### Step 5: Trigger Compaction
```bash
# Wait for segment to roll (10 seconds based on segment.ms)
sleep 15

# Check if compaction ran
docker exec kafka-broker-1 kafka-log-dirs \
  --bootstrap-server localhost:19092 \
  --topic-list user-profiles \
  --describe
```

### Step 6: Read After Compaction
```bash
# Consume again
docker exec kafka-broker-1 kafka-console-consumer \
  --bootstrap-server localhost:19092 \
  --topic user-profiles \
  --from-beginning \
  --property print.key=true \
  --timeout-ms 5000
```

**Expected:** Only latest values per key:
- user1: Alice S. Smith (latest)
- user2: Robert (latest)
- user3: Charlie (only one)

### Step 7: Test Tombstone Deletion
```bash
# Send null value to delete user2
echo "user2:" | docker exec -i kafka-broker-1 kafka-console-producer \
  --bootstrap-server localhost:19092 \
  --topic user-profiles \
  --property "parse.key=true" \
  --property "key.separator=:" \
  --property "null.marker="

# Wait and check
sleep 15

docker exec kafka-broker-1 kafka-console-consumer \
  --bootstrap-server localhost:19092 \
  --topic user-profiles \
  --from-beginning \
  --property print.key=true \
  --timeout-ms 5000
```

**Result:** user2 should be gone (or show null value temporarily)

---

## Lab 3: Controller & ISR Behavior (20 min)

### Step 1: Identify Current Controller
```bash
# Check which broker is controller
docker exec kafka-broker-1 kafka-metadata-shell \
  --snapshot /var/lib/kafka/kraft-combined-logs/__cluster_metadata-0/ \
  --print-controllers

# Or check logs
docker logs kafka-broker-1 2>&1 | grep -i "controller"
```

### Step 2: Check Partition Leaders and ISR
```bash
docker exec kafka-broker-1 kafka-topics \
  --bootstrap-server localhost:19092 \
  --describe --topic storage-test
```

**Look for:**
```
Topic: storage-test  Partition: 0  Leader: 1  Replicas: 1,2,3  ISR: 1,2,3
```

### Step 3: Simulate Broker Failure
```bash
# Stop broker-2
docker stop kafka-broker-2

# Wait a few seconds, then check again
sleep 10

docker exec kafka-broker-1 kafka-topics \
  --bootstrap-server localhost:19092 \
  --describe --topic storage-test
```

**Observe:**
- Leader changes for partitions where broker-2 was leader
- ISR no longer includes broker-2
- Messages still being produced/consumed

### Step 4: Check Under-Replicated Partitions
```bash
docker exec kafka-broker-1 kafka-topics \
  --bootstrap-server localhost:19092 \
  --describe --under-replicated-partitions
```

**Expected:** All partitions missing broker-2 will be under-replicated

### Step 5: Bring Broker Back
```bash
# Start broker-2
docker start kafka-broker-2

# Wait for catch-up
sleep 30

# Check ISR again
docker exec kafka-broker-1 kafka-topics \
  --bootstrap-server localhost:19092 \
  --describe --topic storage-test
```

**Observe:**
- Broker-2 rejoins ISR
- Under-replicated count back to 0
- Automatic recovery!

---

## Lab 4: Testing Retention Policies (20 min)

### Step 1: Create Topic with Short Retention
```bash
docker exec kafka-broker-1 kafka-topics --create \
  --bootstrap-server localhost:19092 \
  --topic short-retention \
  --partitions 1 \
  --replication-factor 2 \
  --config retention.ms=60000 \
  --config segment.ms=30000
```

### Step 2: Send Messages
```bash
# Send batch 1
for i in {1..100}; do
  echo "batch1-message-$i"
done | docker exec -i kafka-broker-1 kafka-console-producer \
  --bootstrap-server localhost:19092 \
  --topic short-retention
```

### Step 3: Wait and Check Segments
```bash
# Check current segments
docker exec kafka-broker-1 ls -lh /var/lib/kafka/data/short-retention-0/

# Note the timestamp
```

### Step 4: Wait for Retention to Kick In
```bash
# Wait 2 minutes
sleep 120

# Check segments again
docker exec kafka-broker-1 ls -lh /var/lib/kafka/data/short-retention-0/
```

**Observe:**
- Old segments renamed to `.log.deleted`
- After 60 seconds more, completely deleted

### Step 5: Test Size-Based Retention
```bash
docker exec kafka-broker-1 kafka-topics --create \
  --bootstrap-server localhost:19092 \
  --topic size-retention \
  --partitions 1 \
  --replication-factor 2 \
  --config retention.bytes=1048576 \
  --config segment.bytes=524288
```

```bash
# Send large messages until exceeds 1 MB
for i in {1..2000}; do
  echo "large-message-$i-$(head -c 512 /dev/urandom | base64)"
done | docker exec -i kafka-broker-1 kafka-console-producer \
  --bootstrap-server localhost:19092 \
  --topic size-retention
```

```bash
# Check partition size
docker exec kafka-broker-1 du -sh /var/lib/kafka/data/size-retention-0/
```

**Result:** Should stay around 1 MB, old segments deleted

---

## Lab 5: KRaft Internals (15 min)

### Step 1: View KRaft Metadata
```bash
# Check cluster metadata topic
docker exec kafka-broker-1 ls -lh /var/lib/kafka/kraft-combined-logs/__cluster_metadata-0/
```

**Files:**
- Metadata stored as log (just like regular topics!)
- `__cluster_metadata` is special internal topic

### Step 2: Read Cluster Configuration
```bash
# View controller configuration
docker exec kafka-broker-1 cat /etc/kafka/server.properties | grep -E "(process.roles|controller)"
```

**Key Settings:**
```
process.roles=broker,controller
controller.quorum.voters=1@kafka-broker-1:9093,2@kafka-broker-2:9093,3@kafka-broker-3:9093
```

### Step 3: Check Controller Quorum
```bash
# View controller status
docker exec kafka-broker-1 kafka-metadata-quorum --bootstrap-server localhost:19092 describe --status
```

**Observe:**
- Leader ID
- Follower IDs
- Log positions

### Step 4: Test Controller Failover
```bash
# Find current controller leader
LEADER=$(docker exec kafka-broker-1 kafka-metadata-quorum --bootstrap-server localhost:19092 describe --status | grep Leader | awk '{print $2}')

echo "Current leader: kafka-broker-$LEADER"

# Stop leader broker
docker stop kafka-broker-$LEADER

# Wait and check new leader (should be < 500ms)
sleep 2

docker exec kafka-broker-1 kafka-metadata-quorum --bootstrap-server localhost:19092 describe --status
```

**Result:** New leader elected in milliseconds!

```bash
# Restart stopped broker
docker start kafka-broker-$LEADER
```

---

## Lab 6: Performance Analysis (15 min)

### Step 1: Measure Sequential Write Performance
```bash
# Producer performance test
docker exec kafka-broker-1 kafka-producer-perf-test \
  --topic perf-test \
  --num-records 100000 \
  --record-size 1024 \
  --throughput -1 \
  --producer-props bootstrap.servers=localhost:19092
```

**Metrics to note:**
- Records/sec
- MB/sec
- Average latency

### Step 2: Measure Consumer Performance
```bash
# Consumer performance test
docker exec kafka-broker-1 kafka-consumer-perf-test \
  --bootstrap-server localhost:19092 \
  --topic perf-test \
  --messages 100000 \
  --threads 1
```

**Compare:**
- Consumer throughput vs producer
- Should be similar or faster (page cache!)

### Step 3: Test with Compression
```bash
# With compression
docker exec kafka-broker-1 kafka-producer-perf-test \
  --topic perf-test-compressed \
  --num-records 100000 \
  --record-size 1024 \
  --throughput -1 \
  --producer-props \
    bootstrap.servers=localhost:19092 \
    compression.type=lz4
```

**Expected:**
- Higher throughput (less network data)
- Slightly higher latency (compression time)

---

## Lab 7: Monitoring & Health Checks (10 min)

### Step 1: Check Cluster Health
```bash
# Broker list
docker exec kafka-broker-1 kafka-broker-api-versions \
  --bootstrap-server localhost:19092 | head -10

# Topic list with details
docker exec kafka-broker-1 kafka-topics --list \
  --bootstrap-server localhost:19092
```

### Step 2: Check Under-Replicated Partitions
```bash
docker exec kafka-broker-1 kafka-topics \
  --bootstrap-server localhost:19092 \
  --describe --under-replicated-partitions
```

**Healthy cluster:** Should return nothing (or "No under-replicated partitions found")

### Step 3: Check Consumer Group Lag
```bash
# Create a consumer group
docker exec kafka-broker-1 kafka-console-consumer \
  --bootstrap-server localhost:19092 \
  --topic storage-test \
  --group lab-consumer-group \
  --from-beginning \
  --timeout-ms 5000

# Check lag
docker exec kafka-broker-1 kafka-consumer-groups \
  --bootstrap-server localhost:19092 \
  --group lab-consumer-group \
  --describe
```

**Look for:**
- CURRENT-OFFSET
- LOG-END-OFFSET
- LAG (should be 0 if caught up)

### Step 4: Check Disk Usage
```bash
# Per topic disk usage
docker exec kafka-broker-1 du -sh /var/lib/kafka/data/*/ | sort -h
```

---

## Challenge Exercises

### Challenge 1: Design Retention Policy
**Scenario:** E-commerce order tracking system
- 10,000 orders/day
- Average order size: 5 KB
- Need: Keep 1 year for compliance
- Budget: 100 GB storage

**Your Task:**
1. Calculate required retention settings
2. Decide: time-based or size-based or both?
3. Configure the topic
4. Estimate cost savings with compression

**Solution Hints:**
```
Daily volume: 10,000 × 5 KB = 50 MB/day
Yearly: 50 MB × 365 = 18.25 GB (uncompressed)
With compression (70%): ~5.5 GB
Replication factor 3: 16.5 GB total
→ Well within 100 GB budget!
```

### Challenge 2: Troubleshoot Under-Replication
**Scenario:** 
- Topic has 100 partitions
- 20 partitions under-replicated
- All brokers healthy

**Your Task:**
1. Identify possible causes
2. Use commands to diagnose
3. Propose solutions

### Challenge 3: Optimize for Throughput
**Scenario:**
- Current: 10K msgs/sec
- Need: 100K msgs/sec
- Budget: Can't add more brokers

**Your Task:**
1. Tune producer configs
2. Tune broker configs
3. Test and measure improvement

---

## Verification Checklist

After completing these labs, you should be able to:

- [ ] Navigate Kafka data directories
- [ ] Interpret log segment files
- [ ] Understand index file structure
- [ ] Configure and test log compaction
- [ ] Send tombstone records
- [ ] Identify the controller broker
- [ ] Observe ISR changes during failures
- [ ] Test controller failover in KRaft
- [ ] Configure retention policies (time, size, compaction)
- [ ] Monitor cluster health metrics
- [ ] Check consumer lag
- [ ] Analyze disk usage
- [ ] Measure performance with kafka-*-perf-test tools
- [ ] Troubleshoot under-replicated partitions

---

## Key Commands Reference

```bash
# Storage
ls /var/lib/kafka/data/
kafka-dump-log --files <path> --print-data-log

# Topics
kafka-topics --describe --topic <name>
kafka-topics --describe --under-replicated-partitions

# Consumer Groups
kafka-consumer-groups --describe --group <name>

# Performance
kafka-producer-perf-test
kafka-consumer-perf-test

# Metadata (KRaft)
kafka-metadata-quorum describe --status
kafka-metadata-shell --snapshot <path>

# Health
kafka-log-dirs --describe
```

---

## Interview Prep from Labs

**Q: "How does Kafka store data?"**
→ Show them you explored /var/lib/kafka/data/ and saw .log, .index, .timeindex files

**Q: "What happens during compaction?"**
→ Demonstrate your lab where user1 had 3 updates, only latest kept

**Q: "How fast is controller failover in KRaft?"**
→ "I tested it - under 500ms! I stopped the controller and new one elected immediately"

**Q: "How to check cluster health?"**
→ List the commands you learned: under-replicated partitions, consumer lag, disk usage

---

## Next Steps

1. Run all labs (takes ~2-3 hours)
2. Review day4-architecture.md questions
3. Practice explaining what you observed
4. Try the challenge exercises
5. Create your own scenarios

**You're mastering Kafka internals!** 🚀

