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

**Across partitions (not guaranteed):**
- Use single partition (not scalable)
- Add sequence numbers
- Use timestamps

**Best practice:**
```properties
enable.idempotence=true
max.in.flight.requests.per.connection=5
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

