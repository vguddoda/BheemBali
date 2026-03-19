# Day 2: Kafka Producers - Interview Questions

## Producer Basics

### 1. Explain the Kafka Producer workflow
**Answer:**
1. Producer creates ProducerRecord (topic, key, value)
2. Serializer converts key/value to byte arrays
3. Partitioner determines target partition
4. Record added to batch for that partition
5. Sender thread sends batch to broker
6. Broker writes to partition log
7. Broker sends acknowledgment
8. Producer receives callback (success/failure)

### 2. What is a ProducerRecord?
**Answer:**
A ProducerRecord is the object sent to Kafka containing:
- **Topic** (required): Destination topic
- **Key** (optional): For partitioning and compaction
- **Value** (required): Actual message data
- **Partition** (optional): Override partition selection
- **Timestamp** (optional): Message timestamp
- **Headers** (optional): Metadata key-value pairs

```java
ProducerRecord<String, String> record = 
    new ProducerRecord<>("topic", "key", "value");
```

### 3. What are the different ways to send messages?
**Answer:**

**Fire-and-forget:**
```java
producer.send(record);
```
- Fastest, no guarantee of delivery
- Use for non-critical data

**Synchronous:**
```java
RecordMetadata metadata = producer.send(record).get();
```
- Blocks until acknowledged
- Lowest throughput
- Use when need confirmation

**Asynchronous with callback:**
```java
producer.send(record, (metadata, exception) -> {
    if (exception != null) {
        // Handle error
    } else {
        // Success
    }
});
```
- Best throughput
- Non-blocking
- Recommended for production

### 4. Explain Producer partitioning strategies
**Answer:**

**Key-based (default):**
```
partition = hash(key) % num_partitions
```
- Same key → same partition
- Maintains ordering per key
- Can cause imbalance if keys are skewed

**Round-robin (no key):**
- Messages distributed evenly
- No ordering guarantee
- Good load distribution

**Custom partitioner:**
```java
class CustomPartitioner implements Partitioner {
    public int partition(String topic, Object key, byte[] keyBytes,
                        Object value, byte[] valueBytes, 
                        Cluster cluster) {
        // Custom logic
        return targetPartition;
    }
}
```

**Explicit partition:**
```java
new ProducerRecord<>("topic", partition, key, value);
```

### 5. What is the 'acks' configuration?
**Answer:**

**acks=0:**
- No acknowledgment from broker
- Highest throughput, can lose messages
- Use case: Metrics, non-critical logs

**acks=1:**
- Wait for leader acknowledgment only
- Balance between throughput and durability
- Can lose if leader fails before replication

**acks=all (or -1):**
- Wait for all in-sync replicas
- Strongest durability guarantee
- Lower throughput
- **Recommended for production with `min.insync.replicas=2`**

### 6. What is producer idempotence?
**Answer:**
Idempotence ensures that duplicate sends result in exactly one message being written.

**Configuration:**
```properties
enable.idempotence=true
```

**Automatically sets:**
- `acks=all`
- `retries=Integer.MAX_VALUE`
- `max.in.flight.requests.per.connection=5`

**Use case:** Prevent duplicates on retry

**Guarantee:** Exactly-once delivery per partition, per session

### 7. Explain producer batching and buffering
**Answer:**

**batch.size:**
- Accumulates messages per partition
- Default: 16KB
- Larger = better throughput, higher latency

**linger.ms:**
- Wait time before sending incomplete batch
- Default: 0 (send immediately)
- Higher = more batching, higher latency

**buffer.memory:**
- Total buffer for all unsent records
- Default: 32MB
- If full, `send()` blocks for `max.block.ms`

**Trade-off:** Throughput vs Latency

### 8. What compression types does Kafka support?
**Answer:**

| Type | Compression Ratio | CPU Usage | Speed |
|------|------------------|-----------|-------|
| none | N/A | Lowest | Fastest |
| gzip | Highest | Highest | Slowest |
| snappy | Medium | Low | Fast |
| lz4 | Medium | Lowest | Fastest |
| zstd | High | Medium | Medium |

**Recommended:**
- **lz4**: Best balance (default choice)
- **snappy**: Low CPU, good for real-time
- **zstd**: Best compression for high bandwidth cost

**Configuration:**
```properties
compression.type=lz4
```

### 9. How do you handle producer retries?
**Answer:**

**Configuration:**
```properties
retries=Integer.MAX_VALUE
retry.backoff.ms=100
delivery.timeout.ms=120000
```

**Retriable errors:**
- `NOT_LEADER_FOR_PARTITION`
- `NOT_ENOUGH_REPLICAS`
- `NETWORK_EXCEPTION`

**Non-retriable errors:**
- `INVALID_CONFIG`
- `AUTHORIZATION_FAILED`
- `MESSAGE_TOO_LARGE`

**Best practice:** Enable idempotence to prevent duplicates on retry

### 10. What is `max.in.flight.requests.per.connection`?
**Answer:**
Maximum number of unacknowledged requests before blocking.

**Values:**
- Default: 5
- 1: Strict ordering (slow)
- >1: Better throughput, potential reordering on retry

**With idempotence:** Can be up to 5 and still maintain ordering

**Without idempotence:** Set to 1 for strict ordering

## Advanced Topics

### 11. Explain transactional producers
**Answer:**
Enables exactly-once semantics across multiple partitions/topics.

**Configuration:**
```java
props.put("transactional.id", "unique-id");
producer.initTransactions();

try {
    producer.beginTransaction();
    producer.send(record1);
    producer.send(record2);
    producer.commitTransaction();
} catch (Exception e) {
    producer.abortTransaction();
}
```

**Use cases:**
- Exactly-once stream processing
- Atomic writes to multiple topics
- Read-process-write patterns

**Requirements:**
- Unique `transactional.id`
- `enable.idempotence=true`
- Consumer with `isolation.level=read_committed`

### 12. What are producer interceptors?
**Answer:**
Hooks to intercept and modify records before sending.

```java
class MetricsInterceptor implements ProducerInterceptor<K, V> {
    public ProducerRecord<K, V> onSend(ProducerRecord<K, V> record) {
        // Add headers, metrics, etc.
        return record;
    }
    
    public void onAcknowledgement(RecordMetadata metadata, Exception ex) {
        // Log, metrics
    }
}
```

**Use cases:**
- Add audit headers
- Collect metrics
- Modify records (e.g., encryption)

### 13. How do you ensure message ordering?
**Answer:**

**Within partition (guaranteed):**
- Use same key for related messages
- Enable idempotence
- Set `max.in.flight.requests=1` (if not idempotent)

**How idempotency ensures ordering:**

When `enable.idempotence=true`:
1. Producer gets unique Producer ID (PID) from broker
2. Each message gets sequence number per partition (0, 1, 2, 3...)
3. Broker tracks last sequence number for each PID+Partition
4. On retry, broker checks: "Already have PID=X, SeqNum=Y?"
   - **Yes**: Send ACK but DON'T append (duplicate detected)
   - **No**: Append to log and update sequence tracker

**Example scenario:**
```
Producer sends: [Msg1:seq=0, Msg2:seq=1, Msg3:seq=2]

Network timeout on Msg1's ACK (but Msg1 WAS written!)
Producer retries Msg1:seq=0

Broker sees: "I already wrote seq=0 for this producer"
→ Sends ACK, does NOT append again
→ No duplicate in log!
```

**Key point:** The message is **NOT** written twice at different positions. Broker deduplicates BEFORE appending, so the log stays clean and ordered.

**Ordering with retries:**
- Broker buffers out-of-order requests
- Uses sequence numbers to detect gaps
- Rejects messages with unexpected sequence (returns error)
- Producer retries from the gap

**Across partitions (not guaranteed):**
- Use single partition (not scalable)
- Add sequence numbers in message
- Use timestamps

**Best practice:**
```properties
enable.idempotence=true
max.in.flight.requests.per.connection=5  # Can be >1 with idempotence
acks=all
```

### 14. What is the producer metadata?
**Answer:**
Metadata about the Kafka cluster cached by producer:
- Available brokers
- Topic configurations
- Partition leaders
- Partition replicas

**Refresh triggers:**
- First connection
- Topic creation/deletion
- Leader change
- `metadata.max.age.ms` expiry (default: 5 minutes)

**Impact:** Producers automatically adapt to cluster changes

### 15. Explain producer error handling
**Answer:**

**Retriable errors:**
```java
producer.send(record, (metadata, exception) -> {
    if (exception instanceof RetriableException) {
        // Will be retried automatically
    }
});
```

**Non-retriable errors:**
```java
if (exception instanceof RecordTooLargeException) {
    // Split or discard
}
if (exception instanceof SerializationException) {
    // Fix data format
}
```

**Best practices:**
- Use callbacks for async sends
- Log all errors
- Implement dead letter queue for failed messages
- Set appropriate retry config

## Performance & Tuning

### 16. How do you optimize producer throughput?
**Answer:**

**Configuration:**
```properties
# Larger batches
batch.size=32768
linger.ms=10

# Compression
compression.type=lz4

# In-flight requests
max.in.flight.requests.per.connection=5

# Less durable (if acceptable)
acks=1

# Larger buffer
buffer.memory=67108864
```

**Application level:**
- Send asynchronously
- Use multiple producer instances
- Batch at application level
- Reuse producer instance

### 17. How do you optimize for low latency?
**Answer:**

**Configuration:**
```properties
# No batching
linger.ms=0
batch.size=1

# No compression
compression.type=none

# Fast acknowledgment
acks=1

# Immediate send
max.in.flight.requests.per.connection=1
```

**Trade-off:** Lower throughput for lower latency

### 18. What causes `BufferExhaustedException`?
**Answer:**
Producer buffer is full because messages are produced faster than sent.

**Causes:**
- Network issues
- Broker overload
- Slow brokers
- Small `buffer.memory`

**Solutions:**
```properties
# Increase buffer
buffer.memory=67108864

# Block longer
max.block.ms=60000

# Fail faster
max.block.ms=5000
```

**Application:**
- Slow down production
- Add backpressure
- Use multiple producers

### 19. How do you monitor producer performance?
**Answer:**

**Key metrics:**
- `record-send-rate`: Messages/sec
- `record-error-rate`: Errors/sec
- `request-latency-avg`: Average latency
- `request-latency-max`: Max latency
- `batch-size-avg`: Average batch size
- `buffer-available-bytes`: Free buffer

**Tools:**
- JMX metrics
- Kafka monitoring tools (Prometheus, Grafana)
- Application logs

**Code:**
```java
Map<MetricName, ? extends Metric> metrics = producer.metrics();
```

### 20. What is the producer request lifecycle?
**Answer:**
1. App creates ProducerRecord
2. Serialization (key, value)
3. Partition assignment
4. Add to RecordAccumulator batch
5. Sender thread picks up batch
6. Send to broker leader
7. Broker writes to log
8. Broker replicates (if `acks=all`)
9. Broker sends response
10. Producer invokes callback

**Timing:**
- Batching delay: `linger.ms`
- Network roundtrip: varies
- Broker write: milliseconds
- Replication: milliseconds

## Scenario-Based Questions

### 21. How would you implement exactly-once delivery?
**Answer:**

**Producer side:**
```java
props.put("enable.idempotence", "true");
props.put("transactional.id", "unique-id");

producer.initTransactions();
try {
    producer.beginTransaction();
    producer.send(records);
    producer.commitTransaction();
} catch (Exception e) {
    producer.abortTransaction();
}
```

**Consumer side:**
```properties
isolation.level=read_committed
```

**Requirements:**
- Idempotence enabled
- Transactional ID
- `acks=all`
- Consumer reads committed only

### 22. Producer is slow. How do you diagnose?
**Answer:**

**Check metrics:**
- High `request-latency-avg` → Network/broker issue
- Low `batch-size-avg` → Increase batching
- High `buffer-total-bytes` → Buffer exhaustion
- High `record-retry-rate` → Broker/network issues

**Solutions:**
1. Increase batching (`batch.size`, `linger.ms`)
2. Add compression
3. Check network latency
4. Check broker health
5. Scale out (more producers/partitions)

### 23. Messages are getting duplicated. Why?
**Answer:**

**Causes:**
- Producer retries after timeout
- Network issues causing ack loss
- Idempotence not enabled
- Producer restart without transaction

**Solutions:**
```properties
enable.idempotence=true
acks=all
max.in.flight.requests.per.connection=5
```

Or use transactions for exactly-once.

### 24. How do you handle large messages?
**Answer:**

**Broker config:**
```properties
message.max.bytes=10485760  # 10MB
```

**Producer config:**
```properties
max.request.size=10485760
```

**Better approach:**
- Store large payload in object store (S3)
- Send reference in Kafka message
- Implement claim-check pattern

**Why?** Large messages hurt performance

### 25. Design a high-throughput producer
**Answer:**

**Configuration:**
```properties
acks=1
compression.type=lz4
batch.size=65536
linger.ms=20
buffer.memory=134217728
max.in.flight.requests.per.connection=10
```

**How each parameter increases throughput:**

---

**1. `acks=1` (vs acks=all)**
```
acks=all: Wait for leader + all replicas → 15ms response time
acks=1:   Wait for leader only → 5ms response time
```
- **3x faster acknowledgments** = 3x more messages/sec
- Leader writes immediately, no waiting for replica sync
- Trade-off: Can lose data if leader fails before replication

---

**2. `compression.type=lz4`**
```
Without compression: 1MB message → 1MB network transfer
With lz4:           1MB message → 300KB network transfer (70% reduction)
```
- **Reduces network bandwidth by 50-70%**
- Less data to send = faster transfer = more messages fit in same time
- LZ4 is fast: Minimal CPU overhead (~5ms per MB)
- Trade-off: Small CPU cost for huge network savings

**Example:**
- Network: 100 MB/sec
- Without compression: 100 MB/sec = 100 messages/sec (1MB each)
- With lz4 (70% reduction): Same network sends 333 messages/sec!

---

**3. `batch.size=65536` (64KB, default=16KB)**
```
Default 16KB batch: 100 messages → 7 network requests
64KB batch:        100 messages → 2 network requests
```
- **Fewer network calls** = less overhead
- Each request has fixed cost (TCP headers, broker processing)
- 4x larger batches = ~4x fewer requests = higher throughput
- More messages per request = better efficiency

**Math:**
- 1000 messages of 1KB each
- batch.size=16KB → 63 requests (1000KB / 16KB)
- batch.size=64KB → 16 requests (1000KB / 64KB)
- **4x fewer requests = 4x less overhead**

---

**4. `linger.ms=20` (default=0)**
```
linger.ms=0:  Send immediately → many small batches
linger.ms=20: Wait 20ms → batches fill up more
```
- **Allows batches to fill up** instead of sending half-empty
- Wait 20ms → accumulate more messages → larger batches
- Larger batches = better compression + fewer requests

**Example:**
- Messages arrive at 100/sec
- linger.ms=0: Send each immediately → 100 requests/sec (batch size ~1KB)
- linger.ms=20: Wait 20ms → accumulate 2 messages → 50 requests/sec (batch size ~2KB)
- **Half the requests = double the throughput**

**Trade-off:** 20ms extra latency for much better throughput

---

**5. `buffer.memory=134217728` (128MB, default=32MB)**
```
32MB buffer:  Can hold ~32K messages before blocking
128MB buffer: Can hold ~128K messages before blocking
```
- **Prevents producer from blocking** when broker is slow
- More buffer = more messages queued = smoother flow
- Producer keeps accepting messages while sender thread catches up
- Prevents `BufferExhaustedException`

**When it helps:**
- Broker temporarily slow → buffer absorbs spike
- Network hiccup → messages queue instead of blocking app
- Burst traffic → smooth out over time

**Without enough buffer:**
```
App → producer.send() → BLOCKS (buffer full) → App waits → Lower throughput
```

**With large buffer:**
```
App → producer.send() → Queued → App continues → High throughput
```

---

**6. `max.in.flight.requests.per.connection=10` (default=5)**
```
max.in.flight=1:  Send batch → Wait for ACK → Send next → Wait...
max.in.flight=10: Send 10 batches → All in parallel → Wait once
```
- **Pipeline effect**: Network pipe stays full
- No idle waiting between requests
- Broker processes multiple batches concurrently

**Math:**
- Network roundtrip: 10ms
- Batch size: 64KB
- max.in.flight=1: 64KB per 10ms = 6.4 MB/sec
- max.in.flight=10: 640KB per 10ms = 64 MB/sec
- **10x throughput improvement!**

**Visual:**
```
Time:    0ms   10ms  20ms  30ms  40ms
in-flight=1:  [B1]→wait→[B2]→wait→[B3]→wait  (3 batches in 40ms)
in-flight=10: [B1,B2,...B10]→wait→[B11-B20]  (20 batches in 40ms)
```

---

**Combined Effect:**

| Parameter | Improvement | Reason |
|-----------|-------------|--------|
| acks=1 | 3x | Faster acknowledgments |
| compression.type=lz4 | 2-3x | Less data to send |
| batch.size=65536 | 4x | Fewer requests |
| linger.ms=20 | 2x | Fuller batches |
| buffer.memory=128MB | 1.5x | No blocking |
| max.in.flight=10 | 2x | Pipelining |

**Total theoretical improvement: ~144x over conservative defaults!**
(In practice: 10-20x improvement is realistic)

---

**Trade-offs:**
- **Latency**: +20ms (linger.ms)
- **Durability**: Lower (acks=1)
- **Memory**: 128MB per producer
- **Ordering**: Need `enable.idempotence=true`

**Architecture:**
- Multiple producer instances
- Async sends with callbacks
- Application-level batching
- Connection pooling
- Many partitions for parallelism

**Monitoring:**
- Track throughput metrics
- Monitor buffer utilization
- Alert on errors

---

## Key Takeaways

1. **Idempotence** prevents duplicates on retry
2. **Transactions** enable exactly-once across partitions
3. **Batching** improves throughput
4. **acks=all** ensures durability
5. **Compression** reduces network/storage
6. **Partitioning** strategy affects ordering and distribution
7. **Async sends** with callbacks for best performance
8. Reuse producer instances (thread-safe)

## Practice Tips
- Implement producers in multiple languages
- Test different configuration combinations
- Simulate failures and retries
- Measure throughput and latency
- Practice explaining trade-offs

