# Kafka Troubleshooting Guide

## Common Issues and Solutions

---

## 1. Docker & Setup Issues

### Containers Won't Start

**Symptoms:**
- `docker-compose up -d` fails
- Containers start but exit immediately
- Port conflicts

**Solutions:**

```bash
# Check if ports are already in use
lsof -i :9092
lsof -i :8080
lsof -i :8081

# Kill processes using those ports
kill -9 <PID>

# Or change ports in docker-compose.yml

# Check Docker resources
docker system df
docker system prune  # Clean up unused resources

# Remove old volumes and start fresh
docker-compose down -v
docker-compose up -d
```

### Containers Running but Can't Connect

**Check container logs:**
```bash
docker-compose logs kafka-broker-1
docker-compose logs kafka-broker-2
docker-compose logs kafka-broker-3
```

**Verify network:**
```bash
docker network ls
docker network inspect kafka_kafka-network
```

**Test connectivity:**
```bash
docker exec -it kafka-broker-1 bash
kafka-broker-api-versions --bootstrap-server localhost:19092
```

### Out of Memory

**Symptoms:**
- Docker crashes
- Containers restart repeatedly
- "Cannot allocate memory" errors

**Solutions:**

Edit `docker-compose.yml` and add memory limits:
```yaml
kafka-broker-1:
  ...
  environment:
    ...
    KAFKA_HEAP_OPTS: "-Xmx512M -Xms512M"
```

Or increase Docker Desktop memory:
- Mac: Docker → Preferences → Resources → Memory
- Recommended: 6GB+ for Kafka cluster

---

## 2. Kafka Cluster Issues

### Topic Creation Fails

**Error:** `Topic already exists`
```bash
# List existing topics
kafka-topics --list --bootstrap-server localhost:19092

# Delete topic
kafka-topics --delete --bootstrap-server localhost:19092 --topic my-topic
```

**Error:** `Replication factor larger than available brokers`
```bash
# Check number of running brokers
docker ps | grep kafka-broker

# Create with appropriate RF
kafka-topics --create --bootstrap-server localhost:19092 \
  --topic my-topic --partitions 3 --replication-factor 2
```

### Under-Replicated Partitions

**Check status:**
```bash
kafka-topics --describe --bootstrap-server localhost:19092 \
  --under-replicated-partitions
```

**Causes:**
- Broker down
- Network issues
- Disk full

**Solutions:**
```bash
# Check broker health
docker ps
docker logs kafka-broker-1

# Check disk space
docker exec -it kafka-broker-1 df -h

# Restart broker if needed
docker-compose restart kafka-broker-1
```

### No Leader for Partition

**Error:** `NOT_LEADER_FOR_PARTITION`

**Check topic:**
```bash
kafka-topics --describe --bootstrap-server localhost:19092 --topic my-topic
```

**Solutions:**
```bash
# Wait for leader election (usually automatic)
sleep 10

# If persists, trigger rebalance
docker-compose restart kafka-broker-1

# Check controller
docker exec kafka-broker-1 kafka-metadata --bootstrap-server localhost:19092 --describe
```

---

## 3. Producer Issues

### Messages Not Sending

**Check producer logs for errors:**

**Common issues:**

1. **Wrong bootstrap servers:**
```python
# Wrong
bootstrap_servers=['kafka-broker-1:9092']  # Won't work from host

# Correct
bootstrap_servers=['localhost:19092']  # From host machine
```

2. **Serialization error:**
```python
# Make sure serializer matches data type
producer = KafkaProducer(
    value_serializer=lambda v: json.dumps(v).encode('utf-8')
)
producer.send('topic', {'key': 'value'})  # Correct
```

3. **Topic doesn't exist:**
```bash
# Enable auto-create or create manually
kafka-topics --create --bootstrap-server localhost:19092 \
  --topic my-topic --partitions 3 --replication-factor 3
```

### Buffer Full / Timeout

**Error:** `BufferExhaustedException` or `TimeoutException`

**Causes:**
- Producing faster than brokers can handle
- Network issues
- Small buffer

**Solutions:**
```python
producer = KafkaProducer(
    buffer_memory=67108864,  # Increase buffer
    max_block_ms=60000,      # Wait longer
    linger_ms=10,            # Better batching
    compression_type='lz4'   # Reduce size
)
```

### Duplicate Messages

**Causes:**
- Retries without idempotence
- Network timeouts
- Producer restart

**Solution:**
```python
producer = KafkaProducer(
    enable_idempotence=True,  # Prevent duplicates
    acks='all',
    retries=2147483647
)
```

### Slow Producer Performance

**Diagnosis:**
```python
# Check metrics
metrics = producer.metrics()
print(metrics['record-send-rate'])
print(metrics['request-latency-avg'])
```

**Tuning:**
```python
producer = KafkaProducer(
    compression_type='lz4',
    batch_size=32768,
    linger_ms=10,
    buffer_memory=67108864
)
```

---

## 4. Consumer Issues

### Consumer Not Receiving Messages

**Check if messages exist:**
```bash
kafka-console-consumer --bootstrap-server localhost:19092 \
  --topic my-topic --from-beginning
```

**Common issues:**

1. **Wrong offset:**
```python
# Reset to beginning
consumer = KafkaConsumer(
    auto_offset_reset='earliest'  # or 'latest'
)
```

2. **Group already consumed:**
```bash
# Check group offsets
kafka-consumer-groups --describe --bootstrap-server localhost:19092 \
  --group my-group

# Reset offsets
kafka-consumer-groups --reset-offsets --bootstrap-server localhost:19092 \
  --group my-group --topic my-topic --to-earliest --execute
```

3. **No partition assignment:**
```python
# Check assignment
consumer.poll(timeout_ms=1000)
print(consumer.assignment())  # Should show partitions
```

### Consumer Lag

**Check lag:**
```bash
kafka-consumer-groups --describe --bootstrap-server localhost:19092 \
  --group my-group
```

**Solutions:**

1. **Add more consumers:**
```python
# Up to number of partitions
# Run multiple consumer instances with same group_id
```

2. **Optimize processing:**
```python
# Batch processing
consumer = KafkaConsumer(
    max_poll_records=500,  # Process more at once
)
```

3. **Tune fetch settings:**
```python
consumer = KafkaConsumer(
    fetch_min_bytes=50000,
    fetch_max_wait_ms=500
)
```

### Rebalancing Issues

**Symptoms:**
- Frequent "rebalancing" logs
- Processing pauses
- Duplicate processing

**Causes:**
- Consumer taking too long to process
- Session timeout too short
- Network issues

**Solutions:**
```python
consumer = KafkaConsumer(
    session_timeout_ms=45000,       # Increase if needed
    heartbeat_interval_ms=15000,    # Must be < session_timeout / 3
    max_poll_interval_ms=300000,    # Time allowed for processing
    max_poll_records=100            # Process fewer at once
)
```

### Offset Commit Failures

**Error:** `CommitFailedException`

**Causes:**
- Consumer took too long
- Group rebalanced
- Consumer not in group

**Solutions:**
```python
consumer = KafkaConsumer(
    enable_auto_commit=False,  # Manual control
    max_poll_interval_ms=600000  # Allow more time
)

# Commit more frequently
for message in consumer:
    process(message)
    consumer.commit()  # Commit after each message
```

---

## 5. Performance Issues

### Low Throughput

**Diagnosis:**
```bash
# Run performance test
kafka-producer-perf-test --topic perf-test \
  --num-records 100000 --record-size 1024 \
  --throughput -1 --producer-props bootstrap.servers=localhost:19092

kafka-consumer-perf-test --broker-list localhost:19092 \
  --topic perf-test --messages 100000
```

**Producer tuning:**
```properties
compression.type=lz4
batch.size=32768
linger.ms=10
buffer.memory=67108864
```

**Consumer tuning:**
```properties
fetch.min.bytes=50000
max.poll.records=500
```

**Broker tuning:**
- Add more partitions
- More brokers
- Better hardware (SSD)

### High Latency

**Measure latency:**
```python
import time
start = time.time()
future = producer.send('topic', 'message')
record_metadata = future.get()
latency = time.time() - start
print(f'Latency: {latency*1000}ms')
```

**Reduce latency:**
```properties
# Producer
linger.ms=0
compression.type=none
acks=1

# Consumer
fetch.min.bytes=1
```

### Disk Space Issues

**Check disk:**
```bash
docker exec kafka-broker-1 df -h
```

**Solutions:**
```bash
# Reduce retention
kafka-configs --alter --bootstrap-server localhost:19092 \
  --entity-type topics --entity-name my-topic \
  --add-config retention.ms=86400000  # 1 day

# Enable compression
kafka-configs --alter --bootstrap-server localhost:19092 \
  --entity-type topics --entity-name my-topic \
  --add-config compression.type=lz4

# Delete old topics
kafka-topics --delete --bootstrap-server localhost:19092 --topic old-topic
```

---

## 6. Connection Issues

### Connection Refused

**Check if Kafka is running:**
```bash
docker ps | grep kafka
```

**Check ports:**
```bash
netstat -an | grep 19092
```

**Test connection:**
```bash
telnet localhost 19092
```

**Common fixes:**
```bash
# Restart cluster
docker-compose restart

# Check firewall
sudo ufw status

# Verify bootstrap servers
# From host: localhost:19092
# From container: kafka-broker-1:9092
```

### Connection Timeout

**Increase timeouts:**
```python
producer = KafkaProducer(
    request_timeout_ms=60000,
    metadata_max_age_ms=60000
)

consumer = KafkaConsumer(
    request_timeout_ms=60000
)
```

---

## 7. Data Issues

### Message Corruption

**Symptoms:**
- Deserialization errors
- Unexpected data

**Solutions:**
```python
# Add error handling
try:
    value = json.loads(message.value)
except json.JSONDecodeError:
    logger.error(f"Invalid JSON at offset {message.offset}")
    # Skip or send to DLQ
```

### Missing Messages

**Check retention:**
```bash
kafka-configs --describe --bootstrap-server localhost:19092 \
  --entity-type topics --entity-name my-topic
```

**Verify replication:**
```bash
kafka-topics --describe --bootstrap-server localhost:19092 \
  --topic my-topic
# Check if ISR = Replicas
```

### Message Ordering Issues

**Remember:** Ordering only guaranteed within partition

**Ensure ordering:**
```python
# Use same key for related messages
producer.send('topic', key='user123', value='event1')
producer.send('topic', key='user123', value='event2')

# Or use single partition (not scalable)
```

---

## 8. Monitoring & Debugging

### Enable Debug Logging

**Producer:**
```python
import logging
logging.basicConfig(level=logging.DEBUG)
```

**Broker:**
```bash
# View logs
docker logs kafka-broker-1 -f
```

### Check Metrics

**JMX metrics:**
```bash
docker exec kafka-broker-1 \
  kafka-run-class kafka.tools.JmxTool \
  --object-name kafka.server:type=BrokerTopicMetrics,name=MessagesInPerSec
```

**Consumer group:**
```bash
kafka-consumer-groups --describe --bootstrap-server localhost:19092 \
  --group my-group --members --verbose
```

---

## 9. Quick Diagnostics Checklist

When something goes wrong:

```bash
# 1. Check containers
docker ps

# 2. Check broker health
docker exec kafka-broker-1 \
  kafka-broker-api-versions --bootstrap-server localhost:19092

# 3. List topics
kafka-topics --list --bootstrap-server localhost:19092

# 4. Describe topic
kafka-topics --describe --bootstrap-server localhost:19092 --topic my-topic

# 5. Check consumer groups
kafka-consumer-groups --list --bootstrap-server localhost:19092

# 6. Check specific group
kafka-consumer-groups --describe --bootstrap-server localhost:19092 \
  --group my-group

# 7. Test produce/consume
echo "test" | kafka-console-producer --bootstrap-server localhost:19092 \
  --topic test-topic

kafka-console-consumer --bootstrap-server localhost:19092 \
  --topic test-topic --from-beginning --max-messages 1

# 8. Check logs
docker logs kafka-broker-1 --tail 100
```

---

## 10. Emergency Procedures

### Complete Reset

```bash
# Stop everything
docker-compose down

# Remove all data
docker-compose down -v

# Remove all images (optional)
docker rmi $(docker images -q confluentinc/*)

# Start fresh
docker-compose up -d
```

### Backup Important Data

```bash
# Backup topic data
kafka-console-consumer --bootstrap-server localhost:19092 \
  --topic important-topic --from-beginning > backup.txt

# Restore later
cat backup.txt | kafka-console-producer \
  --bootstrap-server localhost:19092 --topic important-topic
```

---

## Getting Help

1. **Check logs first:** `docker-compose logs`
2. **Search error messages:** Google + Stack Overflow
3. **Kafka documentation:** https://kafka.apache.org/documentation/
4. **Community:** Confluent Community Slack

---

## Prevention Tips

1. **Always use version control** for configs
2. **Monitor metrics** proactively
3. **Test in staging** before production
4. **Keep Kafka updated**
5. **Document your setup**
6. **Regular backups** of critical data
7. **Set up alerts** for critical metrics

---

## Common Error Messages

| Error | Meaning | Solution |
|-------|---------|----------|
| `NOT_LEADER_FOR_PARTITION` | Trying to write to wrong broker | Retry, metadata will update |
| `LEADER_NOT_AVAILABLE` | No leader elected yet | Wait for election |
| `REQUEST_TIMED_OUT` | Broker didn't respond in time | Check network/broker health |
| `OFFSET_OUT_OF_RANGE` | Requested offset doesn't exist | Reset offset or use auto.offset.reset |
| `GROUP_COORDINATOR_NOT_AVAILABLE` | Group coordinator not ready | Retry, usually temporary |
| `INVALID_REPLICATION_FACTOR` | RF > number of brokers | Reduce RF or add brokers |
| `MESSAGE_TOO_LARGE` | Message exceeds max.message.bytes | Increase limit or split message |

---

Remember: Most issues are configuration-related. Check configs first! 🔍

