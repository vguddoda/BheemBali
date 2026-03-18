# Day 1: Kafka Fundamentals - Interview Questions

## Basic Concepts

### 1. What is Apache Kafka and why is it used?
**Answer:**
Apache Kafka is a distributed event streaming platform designed for high-throughput, fault-tolerant, real-time data processing. It's used for:
- Building real-time streaming data pipelines
- Building real-time streaming applications
- Decoupling data producers from consumers
- Event sourcing and log aggregation
- Message buffering for microservices

**Key characteristics:**
- Distributed and scalable
- High throughput (millions of messages/sec)
- Low latency (sub-millisecond)
- Fault-tolerant and durable
- Persistent storage

### 2. Explain Kafka's core components
**Answer:**
- **Producer**: Publishes messages to Kafka topics
- **Consumer**: Subscribes to topics and processes messages
- **Broker**: Kafka server that stores and serves data
- **Topic**: Category or feed name to which records are published
- **Partition**: Ordered, immutable sequence of records within a topic
- **Cluster**: Group of Kafka brokers working together
- **ZooKeeper/KRaft**: Cluster coordination (KRaft is the modern replacement)

### 3. What is a Kafka Topic?
**Answer:**
A topic is a logical channel or category to which producers send messages and from which consumers read messages. Topics are:
- Multi-subscriber (multiple consumers can read from same topic)
- Partitioned for parallelism and scalability
- Replicated for fault tolerance
- Named uniquely within a cluster

Example: `user-registrations`, `payment-transactions`, `app-logs`

### 4. What is a Partition and why is it important?
**Answer:**
A partition is an ordered, immutable sequence of records that is continually appended to—a structured commit log.

**Importance:**
- **Parallelism**: Multiple consumers can read from different partitions simultaneously
- **Scalability**: Partitions can be distributed across brokers
- **Ordering**: Messages within a partition are strictly ordered
- **Performance**: Load is distributed across partitions

**Key points:**
- Each message within a partition has a unique offset
- Number of partitions is defined at topic creation
- More partitions = more parallelism but more overhead

### 5. What is an offset in Kafka?
**Answer:**
An offset is a unique sequential ID (long integer) assigned to each message within a partition. It represents the position of a message in the partition.

**Characteristics:**
- Unique within a partition (not across partitions)
- Sequential and immutable
- Used by consumers to track reading position
- Starts from 0 and increments
- Managed by Kafka or consumer

### 6. Explain Kafka's architecture
**Answer:**
```
Producers → Kafka Cluster (Multiple Brokers) → Consumers
                    ↓
            KRaft/ZooKeeper (Coordination)
```

**Components:**
- **Producers**: Write data to topics
- **Brokers**: Store data in partitions
- **Consumers**: Read data from topics
- **Controller**: One broker acts as controller (manages state)
- **KRaft**: Consensus protocol for metadata management (replaces ZooKeeper)

### 7. What is a Kafka Broker?
**Answer:**
A Kafka broker is a server that runs the Kafka process. 

**Responsibilities:**
- Receives messages from producers
- Stores messages on disk
- Serves messages to consumers
- Maintains partition replicas
- Handles replication

**Key points:**
- A cluster typically has multiple brokers (3-100+)
- Each broker is identified by a unique ID
- Brokers are stateless (metadata in KRaft/ZooKeeper)

### 8. What is the difference between Kafka and traditional message queues (RabbitMQ, ActiveMQ)?
**Answer:**

| Feature | Kafka | Traditional MQ |
|---------|-------|----------------|
| Message retention | Persistent (configurable) | Deleted after consumption |
| Message replay | Supported | Not supported |
| Throughput | Very high (millions/sec) | Lower |
| Consumer model | Pull-based | Push-based |
| Ordering | Per partition | Per queue |
| Use case | Streaming, log aggregation | Task queues, RPC |
| Scalability | Horizontal | Limited |

### 9. Explain the Producer-Broker-Consumer flow
**Answer:**
1. **Producer** creates a message with key and value
2. Producer serializes the message
3. Producer determines target partition (using key or round-robin)
4. Producer sends message to leader broker for that partition
5. **Broker** receives and appends message to partition log
6. Broker replicates to follower brokers
7. Broker sends acknowledgment to producer
8. **Consumer** polls broker for new messages
9. Consumer deserializes and processes messages
10. Consumer commits offset

### 10. What is a Consumer Group?
**Answer:**
A consumer group is a group of consumers that cooperate to consume messages from topics. Each partition is consumed by exactly one consumer within the group.

**Benefits:**
- Load balancing across consumers
- Fault tolerance (if one consumer fails, others take over)
- Scalability (add more consumers)

**Key points:**
- Group ID identifies the consumer group
- Each consumer in group gets exclusive partitions
- Multiple groups can read same topic independently

## Intermediate Questions

### 11. How does Kafka ensure message ordering?
**Answer:**
Kafka guarantees ordering **within a partition**, not across partitions.

**To maintain order:**
- Use the same key for related messages (same key → same partition)
- Use single partition (limits scalability)
- Process messages in order within consumer

**Example:** For user_id=123, all events go to same partition, maintaining order.

### 12. What is replication in Kafka and why is it important?
**Answer:**
Replication creates copies of partition data across multiple brokers.

**Configuration:**
- `replication.factor` (typically 3)
- One partition has 1 leader + N followers

**Benefits:**
- **Fault tolerance**: Data survives broker failure
- **High availability**: System continues if broker fails
- **Durability**: Data not lost

**Example:** RF=3 means data is on 3 brokers; can lose 2 brokers and still have data.

### 13. What is the difference between a leader and a follower?
**Answer:**

**Leader:**
- Handles all reads and writes for a partition
- Maintains the authoritative copy
- Only one leader per partition

**Follower:**
- Replicates data from leader
- Passive replica (no client interaction)
- Can become leader if current leader fails

**ISR (In-Sync Replica):** Followers that are caught up with leader.

### 14. Explain Kafka's storage mechanism
**Answer:**
Kafka stores messages in **log segments** on disk.

**Structure:**
```
/var/lib/kafka/data/
  topic-partition/
    00000000000000000000.log  (segment file)
    00000000000000000000.index (offset index)
    00000000000000000000.timeindex (time index)
```

**Characteristics:**
- Sequential writes (very fast)
- Immutable (append-only)
- Indexed for fast lookup
- Segmented for management
- Compressed (optional)

### 15. What are the different retention policies in Kafka?
**Answer:**

**Time-based retention:**
- `log.retention.hours=168` (7 days default)
- `log.retention.ms` (more precise)

**Size-based retention:**
- `log.retention.bytes` (per partition)

**Compaction:**
- `log.cleanup.policy=compact`
- Keeps only latest value per key
- Used for changelog topics

### 16. What is log compaction?
**Answer:**
Log compaction ensures Kafka keeps at least the last known value for each message key within a partition.

**Use cases:**
- Database change capture
- Maintaining current state
- Event sourcing snapshots

**How it works:**
- Background thread merges segments
- Keeps latest value per key
- Deletes older duplicates

### 17. How does Kafka achieve high throughput?
**Answer:**
1. **Sequential I/O**: Append-only writes to disk
2. **Zero-copy**: sendfile() system call
3. **Batching**: Producers/consumers batch messages
4. **Compression**: Reduce network/disk I/O
5. **Partitioning**: Parallel processing
6. **Page cache**: OS caches hot data
7. **Efficient protocols**: Binary protocol

### 18. What is KRaft and how is it different from ZooKeeper?
**Answer:**
KRaft (Kafka Raft) is the new consensus protocol that replaces ZooKeeper.

**Advantages over ZooKeeper:**
- Simpler architecture (one less system to manage)
- Faster metadata propagation
- Supports more partitions (millions)
- Easier to deploy and operate
- Better scalability
- Integrated with Kafka

**Status:** Production-ready as of Kafka 3.3+, ZooKeeper deprecated.

### 19. What happens when a broker fails?
**Answer:**
1. Controller detects broker failure
2. For partitions where failed broker was leader:
   - Controller elects new leader from ISR
   - Updates metadata
3. Producer/consumer clients get metadata update
4. Clients connect to new leader
5. Failed broker's partitions are under-replicated
6. When broker returns, it catches up as follower

**Downtime:** Typically seconds for leader election.

### 20. What is the CAP theorem and how does Kafka fit?
**Answer:**
CAP theorem: Can't have all three of Consistency, Availability, Partition tolerance.

**Kafka's approach:**
- **CP system** (Consistency + Partition tolerance)
- Prioritizes consistency and partition tolerance
- Sacrifices availability when necessary

**Configuration:**
- `acks=all` + `min.insync.replicas=2` ensures consistency
- Trade-off: Lower availability if replicas fail

## Practical Questions

### 21. How would you create a topic with 3 partitions and replication factor 3?
**Answer:**
```bash
kafka-topics --create \
  --bootstrap-server localhost:19092 \
  --topic my-topic \
  --partitions 3 \
  --replication-factor 3
```

### 22. How do you list all topics in a cluster?
**Answer:**
```bash
kafka-topics --list \
  --bootstrap-server localhost:19092
```

### 23. How do you describe a topic?
**Answer:**
```bash
kafka-topics --describe \
  --bootstrap-server localhost:19092 \
  --topic my-topic
```

### 24. How do you send messages using console producer?
**Answer:**
```bash
kafka-console-producer \
  --bootstrap-server localhost:19092 \
  --topic my-topic
```

### 25. How do you consume messages from the beginning?
**Answer:**
```bash
kafka-console-consumer \
  --bootstrap-server localhost:19092 \
  --topic my-topic \
  --from-beginning
```

## Scenario-Based Questions

### 26. How would you handle million messages per second?
**Answer:**
1. Increase partitions (parallel processing)
2. Batch messages in producer
3. Tune compression (lz4, snappy)
4. Increase broker resources (CPU, memory, disk)
5. Use multiple producer instances
6. Optimize consumer group (more consumers)
7. Tune batch.size and linger.ms

### 27. How do you ensure no message loss?
**Answer:**
**Producer side:**
- `acks=all` (wait for all replicas)
- `retries=Integer.MAX_VALUE`
- `enable.idempotence=true`

**Broker side:**
- `min.insync.replicas=2`
- `replication.factor=3`

**Consumer side:**
- Commit offsets only after processing
- Use manual offset management

### 28. A consumer is lagging behind. How do you diagnose and fix?
**Answer:**

**Diagnosis:**
- Check consumer lag: `kafka-consumer-groups --describe`
- Monitor consumer metrics
- Check processing time per message

**Solutions:**
1. Add more consumer instances (up to partition count)
2. Optimize processing logic
3. Increase consumer resources
4. Batch processing
5. Increase `max.poll.records`
6. Check for consumer rebalancing

### 29. How would you migrate from ZooKeeper to KRaft?
**Answer:**
1. Ensure Kafka version 3.3+
2. Create KRaft metadata snapshot
3. Stop ZooKeeper-based cluster
4. Start KRaft-based cluster with metadata
5. Verify metadata migrated correctly
6. Decommission ZooKeeper

**Note:** For new clusters, start with KRaft directly.

### 30. Design a system to process user click events in real-time
**Answer:**
```
Web App → Kafka Topic (click-events)
              ↓
         [Partitioned by user_id]
              ↓
       Kafka Streams/Flink
              ↓
    Aggregate & Process
              ↓
   → Analytics DB (ClickHouse)
   → Real-time Dashboard
   → Recommendations Service
```

**Considerations:**
- Partition by user_id for ordering
- Replication factor = 3
- Compression for large payloads
- Schema registry for data validation
- Monitoring and alerting

---

## Key Takeaways for Day 1

1. Kafka is a distributed streaming platform, not just a message queue
2. Topics are partitioned for scalability and ordering
3. Replication provides fault tolerance
4. KRaft is replacing ZooKeeper
5. Kafka excels at high-throughput, low-latency data streaming
6. Understanding partitions and consumer groups is crucial
7. Offset management is key to reliable consumption

## Next Steps
- Complete hands-on lab exercises
- Practice CLI commands
- Set up local cluster with Docker
- Move to Day 2: Producers Deep Dive

