# Kafka Cheat Sheet - Quick Reference

## 🚀 Quick Start Commands

### Start/Stop Cluster
```bash
# Start all services
docker-compose up -d

# Stop all services
docker-compose down

# Stop and remove all data
docker-compose down -v

# View logs
docker-compose logs -f kafka-broker-1

# Restart specific service
docker-compose restart kafka-broker-1
```

### Access Containers
```bash
# Access broker 1
docker exec -it kafka-broker-1 bash

# Execute single command
docker exec kafka-broker-1 kafka-topics --list --bootstrap-server localhost:19092
```

---

## 📝 Topic Management

### Create Topic
```bash
kafka-topics --create \
  --bootstrap-server localhost:19092 \
  --topic my-topic \
  --partitions 3 \
  --replication-factor 3
```

### List All Topics
```bash
kafka-topics --list --bootstrap-server localhost:19092
```

### Describe Topic
```bash
kafka-topics --describe \
  --bootstrap-server localhost:19092 \
  --topic my-topic
```

### Delete Topic
```bash
kafka-topics --delete \
  --bootstrap-server localhost:19092 \
  --topic my-topic
```

### Alter Topic (Add Partitions)
```bash
kafka-topics --alter \
  --bootstrap-server localhost:19092 \
  --topic my-topic \
  --partitions 5
```

### List Under-Replicated Partitions
```bash
kafka-topics --describe \
  --bootstrap-server localhost:19092 \
  --under-replicated-partitions
```

---

## 📤 Producer Commands

### Console Producer (Basic)
```bash
kafka-console-producer \
  --bootstrap-server localhost:19092 \
  --topic my-topic
```

### Producer with Key-Value
```bash
kafka-console-producer \
  --bootstrap-server localhost:19092 \
  --topic my-topic \
  --property "parse.key=true" \
  --property "key.separator=:"
```

### Producer from File
```bash
kafka-console-producer \
  --bootstrap-server localhost:19092 \
  --topic my-topic < messages.txt
```

### Performance Test
```bash
kafka-producer-perf-test \
  --topic perf-test \
  --num-records 100000 \
  --record-size 1024 \
  --throughput -1 \
  --producer-props bootstrap.servers=localhost:19092
```

---

## 📥 Consumer Commands

### Console Consumer (Latest)
```bash
kafka-console-consumer \
  --bootstrap-server localhost:19092 \
  --topic my-topic
```

### Consume from Beginning
```bash
kafka-console-consumer \
  --bootstrap-server localhost:19092 \
  --topic my-topic \
  --from-beginning
```

### Consumer with Keys
```bash
kafka-console-consumer \
  --bootstrap-server localhost:19092 \
  --topic my-topic \
  --property print.key=true \
  --property key.separator=":"
```

### Consumer with Group
```bash
kafka-console-consumer \
  --bootstrap-server localhost:19092 \
  --topic my-topic \
  --group my-consumer-group
```

### Consume Specific Partition
```bash
kafka-console-consumer \
  --bootstrap-server localhost:19092 \
  --topic my-topic \
  --partition 0 \
  --from-beginning
```

---

## 👥 Consumer Group Management

### List Consumer Groups
```bash
kafka-consumer-groups --list --bootstrap-server localhost:19092
```

### Describe Consumer Group
```bash
kafka-consumer-groups --describe \
  --bootstrap-server localhost:19092 \
  --group my-group
```

### Check Consumer Lag
```bash
kafka-consumer-groups --describe \
  --bootstrap-server localhost:19092 \
  --group my-group
```

### Reset Offsets to Earliest
```bash
kafka-consumer-groups \
  --bootstrap-server localhost:19092 \
  --group my-group \
  --topic my-topic \
  --reset-offsets --to-earliest \
  --execute
```

### Reset Offsets to Latest
```bash
kafka-consumer-groups \
  --bootstrap-server localhost:19092 \
  --group my-group \
  --topic my-topic \
  --reset-offsets --to-latest \
  --execute
```

### Reset to Specific Offset
```bash
kafka-consumer-groups \
  --bootstrap-server localhost:19092 \
  --group my-group \
  --topic my-topic:0 \
  --reset-offsets --to-offset 100 \
  --execute
```

### Delete Consumer Group
```bash
kafka-consumer-groups --delete \
  --bootstrap-server localhost:19092 \
  --group my-group
```

---

## ⚙️ Configuration Management

### View Topic Config
```bash
kafka-configs --describe \
  --bootstrap-server localhost:19092 \
  --entity-type topics \
  --entity-name my-topic
```

### Alter Topic Config
```bash
kafka-configs --alter \
  --bootstrap-server localhost:19092 \
  --entity-type topics \
  --entity-name my-topic \
  --add-config retention.ms=86400000
```

### Delete Topic Config
```bash
kafka-configs --alter \
  --bootstrap-server localhost:19092 \
  --entity-type topics \
  --entity-name my-topic \
  --delete-config retention.ms
```

---

## 🔧 Important Configurations

### Producer Configs
```properties
# Acknowledgments (0, 1, all)
acks=all

# Batch size
batch.size=16384

# Linger time
linger.ms=0

# Compression (none, gzip, snappy, lz4, zstd)
compression.type=lz4

# Retries
retries=2147483647

# Idempotence
enable.idempotence=true

# Max in-flight
max.in.flight.requests.per.connection=5
```

### Consumer Configs
```properties
# Group ID
group.id=my-group

# Auto offset reset (earliest, latest, none)
auto.offset.reset=earliest

# Auto commit
enable.auto.commit=true
auto.commit.interval.ms=5000

# Max poll records
max.poll.records=500

# Session timeout
session.timeout.ms=45000

# Max poll interval
max.poll.interval.ms=300000
```

### Topic Configs
```properties
# Retention time
retention.ms=604800000

# Retention size
retention.bytes=-1

# Segment size
segment.bytes=1073741824

# Cleanup policy (delete, compact)
cleanup.policy=delete

# Min in-sync replicas
min.insync.replicas=2
```

---

## 🐍 Python Quick Reference

### Producer
```python
from kafka import KafkaProducer

producer = KafkaProducer(
    bootstrap_servers=['localhost:19092'],
    value_serializer=lambda v: v.encode('utf-8')
)

producer.send('my-topic', 'Hello Kafka')
producer.flush()
producer.close()
```

### Consumer
```python
from kafka import KafkaConsumer

consumer = KafkaConsumer(
    'my-topic',
    bootstrap_servers=['localhost:19092'],
    auto_offset_reset='earliest',
    group_id='my-group',
    value_deserializer=lambda m: m.decode('utf-8')
)

for message in consumer:
    print(message.value)
```

---

## 🌐 URLs & Ports

| Service | URL | Port |
|---------|-----|------|
| Kafka UI | http://localhost:8080 | 8080 |
| Broker 1 (External) | localhost:19092 | 19092 |
| Broker 2 (External) | localhost:19093 | 19093 |
| Broker 3 (External) | localhost:19094 | 19094 |
| Schema Registry | http://localhost:8081 | 8081 |
| Kafka Connect | http://localhost:8083 | 8083 |

---

## 🎯 Common Patterns

### High Throughput Producer
```properties
acks=1
compression.type=lz4
batch.size=32768
linger.ms=10
buffer.memory=67108864
```

### Low Latency Producer
```properties
acks=1
compression.type=none
batch.size=0
linger.ms=0
```

### Exactly-Once Producer
```properties
enable.idempotence=true
acks=all
max.in.flight.requests.per.connection=5
transactional.id=unique-id
```

---

## 🔍 Troubleshooting

### Check Cluster Health
```bash
docker ps
docker-compose logs kafka-broker-1
```

### Test Connection
```bash
docker exec kafka-broker-1 \
  kafka-broker-api-versions --bootstrap-server localhost:19092
```

### Check Disk Usage
```bash
docker exec kafka-broker-1 df -h
```

---

## 📊 Key Metrics to Monitor

- `record-send-rate` - Producer throughput
- `records-lag-max` - Consumer lag
- `request-latency-avg` - Latency
- `UnderReplicatedPartitions` - Health
- `OfflinePartitionsCount` - Availability

---

## 💡 Interview Quick Tips

1. **Ordering**: Per-partition only
2. **Max Consumers**: = Number of partitions
3. **Replication**: Typically 3 in production
4. **ISR**: Must be > min.insync.replicas
5. **acks=all**: Strongest durability guarantee
6. **Idempotence**: Prevents duplicates on retry
7. **KRaft**: Modern (no ZooKeeper)
8. **Offset**: Consumer position in partition
9. **Partitioning**: Key-based or round-robin
10. **Consumer Group**: Load balancing

---

## 🎓 Remember

- **Kafka = Distributed Log**
- **Topics** are divided into **Partitions**
- **Partitions** are replicated across **Brokers**
- **Consumers** in a **Group** share partitions
- **One partition** → **One consumer** (in group)
- **Leader** handles I/O, **Followers** replicate
- **ISR** = In-Sync Replicas
- **Offset** = Message position in partition

---

*Keep this open while practicing!* 📖

