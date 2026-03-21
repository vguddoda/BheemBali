# Day 6: Performance, Monitoring & Production Best Practices - Interview Questions

## 🎯 START HERE - Study Plan (4-5 hours)

### What You'll Learn
- ✅ Performance Tuning - Producer, consumer, broker optimization
- ✅ Monitoring & Metrics - JMX, Prometheus, key metrics
- ✅ Security - SSL/TLS, SASL, ACLs, encryption
- ✅ Schema Registry - Avro, schema evolution
- ✅ Best Practices - Anti-patterns, common pitfalls
- ✅ Multi-DC Replication - MirrorMaker, disaster recovery

### How to Study
**Morning (2-3 hours):** Read questions 1-30 below
**Afternoon (2 hours):** Run hands-on labs (`labs/day6-production/README.md`)

### Quick Self-Test
1. How to tune producer throughput?
2. Key metrics to monitor in production?
3. How to secure Kafka?
4. What is Schema Registry?
5. Common anti-patterns?

**After studying, 8/10 correct = Mastered!** ✅

---

## 📋 Quick Reference (Print This!)

**Performance Tuning:**
- Producer: batch.size, linger.ms, compression, acks=1
- Consumer: fetch.min.bytes, max.poll.records, multi-threading
- Broker: num.io.threads, num.network.threads, log.flush.interval

**Key Metrics:**
- UnderReplicatedPartitions (should be 0)
- OfflinePartitionsCount (should be 0)
- Consumer lag (track with kafka-consumer-groups)
- Request latency (p99, p95)
- Bytes in/out rate

**Security:**
- SSL/TLS: Encryption in transit
- SASL: Authentication (PLAIN, SCRAM, GSSAPI)
- ACLs: Authorization (who can read/write topics)
- Encryption at rest: OS-level disk encryption

**Commands:**
```bash
# Performance test
kafka-producer-perf-test --topic test --num-records 1000000

# Check lag
kafka-consumer-groups --describe --group mygroup

# List ACLs
kafka-acls --list

# JMX metrics
jconsole <broker-host>:9999
```

---

## Performance Tuning

### 1. How to optimize producer throughput?
**Answer:**

**Configuration for Maximum Throughput:**

```properties
# Batching - More messages per request
batch.size=65536                     # 64 KB (default: 16 KB)
linger.ms=20                         # Wait 20ms to fill batch

# Compression - Reduce network bandwidth
compression.type=lz4                 # Fast compression (or snappy)

# In-flight requests - Pipeline multiple requests
max.in.flight.requests.per.connection=5

# Buffer - Avoid blocking
buffer.memory=134217728              # 128 MB

# Acks - Trade durability for speed
acks=1                              # Leader only (not all replicas)

# Retries
retries=2147483647                  # Max retries
max.in.flight.requests.per.connection=5

# Enable idempotence for ordering
enable.idempotence=true
```

**What each setting does:**

```
batch.size=64KB:
- Accumulates messages before sending
- Fewer network requests
- Better compression ratio
- 4x improvement over default

linger.ms=20:
- Waits up to 20ms to fill batch
- Small latency penalty (+20ms)
- Significant throughput gain
- Batches fill more completely

compression.type=lz4:
- Reduces message size by 50-70%
- Less network bandwidth
- Faster transfer
- Small CPU cost

max.in.flight=5:
- 5 requests in flight simultaneously
- Network pipe stays full
- 5x throughput improvement
- With idempotence: ordering preserved

acks=1:
- Wait for leader only (not replicas)
- 3x faster than acks=all
- Lower durability guarantee
- Good for logs, metrics
```

**Benchmarking Results:**

```
Test: 1 million messages, 1 KB each

Conservative settings:
- batch.size=16384
- linger.ms=0
- acks=all
- Result: 10,000 msg/sec

Optimized settings (above):
- Result: 100,000+ msg/sec
- 10x improvement! ✓
```

### 2. How to optimize consumer throughput?
**Answer:**

**Configuration for Fast Consumption:**

```properties
# Fetch size - Pull more data per request
fetch.min.bytes=1048576              # 1 MB (default: 1 byte)
fetch.max.wait.ms=500                # Max wait 500ms

# Poll records - Process more per poll
max.poll.records=1000                # 1000 records (default: 500)

# Partition fetch size
max.partition.fetch.bytes=5242880    # 5 MB

# Timeouts - Avoid unnecessary rebalancing
session.timeout.ms=30000             # 30 sec
heartbeat.interval.ms=3000           # 3 sec  
max.poll.interval.ms=600000          # 10 min

# Manual commit
enable.auto.commit=false             # Manual control
```

**Multi-Threading Pattern:**

```java
// Pattern 1: One consumer, thread pool for processing
ExecutorService executor = Executors.newFixedThreadPool(10);

while (true) {
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
    
    List<Future<?>> futures = new ArrayList<>();
    for (ConsumerRecord<String, String> record : records) {
        futures.add(executor.submit(() -> process(record)));
    }
    
    // Wait for all
    for (Future<?> future : futures) {
        future.get();
    }
    
    consumer.commitSync();  // Commit after all processed
}
```

```java
// Pattern 2: Multiple consumer instances
// Run 10 instances of this consumer
// Each gets different partitions
// 10x parallelism

for (int i = 0; i < 10; i++) {
    Thread thread = new Thread(() -> {
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Arrays.asList("my-topic"));
        
        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
            processRecords(records);
            consumer.commitSync();
        }
    });
    thread.start();
}
```

**Batch Processing Pattern:**

```java
List<Record> batch = new ArrayList<>();

while (true) {
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
    
    for (ConsumerRecord<String, String> record : records) {
        batch.add(convert(record));
        
        if (batch.size() >= 1000) {
            database.batchInsert(batch);  // 1 DB call for 1000 records
            batch.clear();
            consumer.commitSync();
        }
    }
}
```

**Performance Comparison:**

```
Scenario: 100,000 messages/sec

Single-threaded, default settings:
- Throughput: 10,000 msg/sec
- Can't keep up! Lag growing ✗

Optimized (above settings + 10 threads):
- Throughput: 150,000 msg/sec
- Keeping up! Zero lag ✓
```

### 3. How to tune broker performance?
**Answer:**

**Critical Broker Configurations:**

```properties
########################## I/O Threads ##########################
# Number of threads for disk I/O
num.io.threads=8                     # Default: 8, increase for high load

# Number of threads for network requests
num.network.threads=3                # Default: 3

########################## Replication ##########################
# Reduce ISR shrinking
replica.lag.time.max.ms=30000        # 30 sec (increase for slow networks)

# Background threads for replication
num.replica.fetchers=4               # Parallel replication

########################## Log Flushing ##########################
# How often to flush to disk
log.flush.interval.messages=10000    # Every 10K messages
log.flush.interval.ms=1000           # Or every 1 second

# Rely on OS page cache (recommended)
# log.flush.interval.messages=Long.MAX_VALUE  # Never flush (let OS decide)

########################## Log Retention ##########################
# Segment size
log.segment.bytes=1073741824         # 1 GB segments

# Retention
log.retention.hours=168              # 7 days
log.retention.bytes=1073741824       # 1 GB per partition

########################## Compaction ##########################
# Background threads for compaction
log.cleaner.threads=1
log.cleaner.io.max.bytes.per.second=unlimited

########################## Replication ##########################
min.insync.replicas=2                # Require 2 ISR for acks=all
unclean.leader.election.enable=false # No data loss

########################## Memory ##########################
# Socket buffer sizes
socket.send.buffer.bytes=102400      # 100 KB
socket.receive.buffer.bytes=102400   # 100 KB
socket.request.max.bytes=104857600   # 100 MB
```

**JVM Tuning:**

```bash
# Kafka startup with JVM options
export KAFKA_HEAP_OPTS="-Xmx6g -Xms6g"  # 6 GB heap
export KAFKA_JVM_PERFORMANCE_OPTS="-XX:+UseG1GC -XX:MaxGCPauseMillis=20 -XX:InitiatingHeapOccupancyPercent=35"

# G1GC is recommended for Kafka
# Low pause times
# Good for large heaps
```

**OS-Level Tuning:**

```bash
# Increase file descriptors
ulimit -n 100000

# Disable swap
sudo swapoff -a

# Increase max file handles
echo "* soft nofile 100000" >> /etc/security/limits.conf
echo "* hard nofile 100000" >> /etc/security/limits.conf

# Page cache tuning
vm.swappiness=1
vm.dirty_ratio=80
vm.dirty_background_ratio=5
```

### 4. How to benchmark Kafka performance?
**Answer:**

**Producer Performance Test:**

```bash
# Test producer throughput
kafka-producer-perf-test \
  --topic perf-test \
  --num-records 1000000 \
  --record-size 1024 \
  --throughput -1 \
  --producer-props \
    bootstrap.servers=localhost:9092 \
    acks=1 \
    batch.size=65536 \
    linger.ms=10 \
    compression.type=lz4

# Output:
# 1000000 records sent, 95238.095238 records/sec (92.98 MB/sec)
# Average latency: 5.34 ms
# Max latency: 234.00 ms
# 50th percentile: 4 ms
# 95th percentile: 12 ms
# 99th percentile: 45 ms
```

**Consumer Performance Test:**

```bash
# Test consumer throughput
kafka-consumer-perf-test \
  --bootstrap-server localhost:9092 \
  --topic perf-test \
  --messages 1000000 \
  --threads 1 \
  --group perf-consumer-group

# Output:
# data consumed in 10.5 seconds
# MB/sec: 90.48
# messages/sec: 95238.10
```

**End-to-End Latency Test:**

```bash
# Measure produce → consume latency
kafka-run-class kafka.tools.EndToEndLatency \
  localhost:9092 \
  test-topic \
  10000 \
  1 \
  1024 \
  acks=1

# Output:
# Avg latency: 5.2 ms
# Percentiles: 50th=4ms, 99th=23ms, 99.9th=67ms
```

**Benchmark Scenarios:**

```
Scenario 1: Maximum Throughput
- Large batches (batch.size=256KB)
- Compression (lz4)
- acks=1
- Multiple partitions (10+)
Result: 500K+ msg/sec per broker

Scenario 2: Low Latency
- Small batches (batch.size=16KB)
- linger.ms=0
- acks=1
- Fewer partitions
Result: < 5ms p99 latency

Scenario 3: Durability
- acks=all
- min.insync.replicas=2
- replication.factor=3
Result: No data loss, ~30% slower
```

---

## Monitoring & Metrics

### 5. What are the most critical metrics to monitor?
**Answer:**

**Top 10 Critical Metrics:**

**1. UnderReplicatedPartitions (Broker)**
```
Metric: kafka.server:type=ReplicaManager,name=UnderReplicatedPartitions
Expected: 0
Alert if: > 0 for 5 minutes

Meaning: Partitions where replicas are not caught up
Causes: Slow broker, network issues, disk problems
Action: Check broker health, network, disk I/O
```

**2. OfflinePartitionsCount (Controller)**
```
Metric: kafka.controller:type=KafkaController,name=OfflinePartitionsCount
Expected: 0
Alert if: > 0

Meaning: Partitions with no leader
Causes: All replicas failed, unclean election disabled
Action: CRITICAL! Bring replicas back online
```

**3. ActiveControllerCount (Broker)**
```
Metric: kafka.controller:type=KafkaController,name=ActiveControllerCount
Expected: 1 (only one broker should be 1, others 0)
Alert if: = 0 on all brokers OR > 1 total

Meaning: Controller election status
Causes: Split brain, network partition
Action: Check cluster health, restart brokers if needed
```

**4. Consumer Lag (Consumer)**
```
Command: kafka-consumer-groups --describe --group mygroup

Metric: LAG column
Expected: < 1000 (or 0 for caught up)
Alert if: Growing over time

Meaning: How far behind consumer is
Causes: Slow processing, not enough consumers
Action: Add consumers, optimize processing
```

**5. Request Latency (Broker)**
```
Metric: kafka.network:type=RequestMetrics,name=TotalTimeMs,request=Produce
Percentiles: p50, p95, p99

Expected: 
- p50 < 5ms
- p95 < 20ms
- p99 < 50ms

Alert if: p99 > 100ms

Causes: Disk slow, high load, GC pauses
Action: Check disk I/O, CPU, memory
```

**6. Bytes In/Out Rate (Broker)**
```
Metric: kafka.server:type=BrokerTopicMetrics,name=BytesInPerSec
        kafka.server:type=BrokerTopicMetrics,name=BytesOutPerSec

Monitor: Sudden spikes or drops
Expected: Steady rate for predictable workloads

Use for: Capacity planning, detecting issues
```

**7. ISR Shrink/Expand Rate (Broker)**
```
Metric: kafka.server:type=ReplicaManager,name=IsrShrinksPerSec
        kafka.server:type=ReplicaManager,name=IsrExpandsPerSec

Expected: Near 0 (stable ISR)
Alert if: Frequent shrinking

Meaning: Replicas falling out of sync
Causes: Slow broker, network issues
Action: Check replica lag, broker health
```

**8. Failed Produce/Fetch Requests (Broker)**
```
Metric: kafka.server:type=BrokerTopicMetrics,name=FailedProduceRequestsPerSec
        kafka.server:type=BrokerTopicMetrics,name=FailedFetchRequestsPerSec

Expected: 0
Alert if: > 0

Causes: Client errors, broker overload, auth failures
```

**9. GC Pause Time (JVM)**
```
Metric: java.lang:type=GarbageCollector,name=G1 Young Generation
        CollectionTime, CollectionCount

Expected: Pauses < 100ms
Alert if: Pauses > 200ms or frequent

Causes: Heap too small, memory leak
Action: Tune JVM, increase heap
```

**10. Disk Usage (OS)**
```
Command: df -h

Expected: < 80% full
Alert if: > 85%

Causes: Retention too long, high volume
Action: Increase retention cleanup, add disk
```

### 6. How to setup monitoring with Prometheus and Grafana?
**Answer:**

**Architecture:**

```
Kafka Brokers (JMX metrics)
    ↓
JMX Exporter (converts to Prometheus format)
    ↓
Prometheus (scrapes and stores metrics)
    ↓
Grafana (visualizes metrics)
```

**Step-by-Step Setup:**

**1. Enable JMX on Kafka Brokers:**

```bash
# In docker-compose.yml or broker config
environment:
  KAFKA_JMX_OPTS: "-Dcom.sun.management.jmxremote 
                   -Dcom.sun.management.jmxremote.port=9999
                   -Dcom.sun.management.jmxremote.authenticate=false
                   -Dcom.sun.management.jmxremote.ssl=false"
  KAFKA_JMX_PORT: 9999
```

**2. Deploy JMX Exporter:**

```yaml
# docker-compose.yml
services:
  jmx-exporter:
    image: sscaling/jmx-prometheus-exporter
    ports:
      - "5556:5556"
    environment:
      SERVICE_PORT: 5556
    volumes:
      - ./jmx-exporter-config.yml:/etc/jmx-exporter/config.yml
    command: 5556 /etc/jmx-exporter/config.yml
```

**3. JMX Exporter Config:**

```yaml
# jmx-exporter-config.yml
lowercaseOutputName: true
lowercaseOutputLabelNames: true
whitelistObjectNames:
  - kafka.server:type=BrokerTopicMetrics,*
  - kafka.server:type=ReplicaManager,*
  - kafka.controller:type=KafkaController,*
  - kafka.network:type=RequestMetrics,*
rules:
  - pattern: kafka.server<type=(.+), name=(.+), topic=(.+)><>Count
    name: kafka_server_$1_$2_count
    labels:
      topic: "$3"
```

**4. Prometheus Configuration:**

```yaml
# prometheus.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'kafka'
    static_configs:
      - targets:
        - 'jmx-exporter:5556'
        labels:
          cluster: 'kafka-prod'
```

**5. Grafana Dashboard:**

```json
{
  "dashboard": {
    "title": "Kafka Monitoring",
    "panels": [
      {
        "title": "Messages In Per Second",
        "targets": [
          {
            "expr": "rate(kafka_server_brokertopicmetrics_messagesin_total[5m])"
          }
        ]
      },
      {
        "title": "Under Replicated Partitions",
        "targets": [
          {
            "expr": "kafka_server_replicamanager_underreplicatedpartitions"
          }
        ],
        "alert": {
          "conditions": [
            {
              "evaluator": {
                "params": [0],
                "type": "gt"
              }
            }
          ]
        }
      }
    ]
  }
}
```

**6. Key Grafana Queries:**

```promql
# Throughput
rate(kafka_server_brokertopicmetrics_bytesin_total[5m])

# Consumer Lag
kafka_consumergroup_lag

# Under-replicated partitions
kafka_server_replicamanager_underreplicatedpartitions

# Request latency p99
histogram_quantile(0.99, 
  rate(kafka_network_requestmetrics_totaltimems_bucket{request="Produce"}[5m])
)

# ISR shrink rate
rate(kafka_server_replicamanager_isrshrinks_total[5m])
```

---

## Security

### 7. How to secure Kafka with SSL/TLS?
**Answer:**

**SSL/TLS encrypts data in transit between clients and brokers.**

**Step-by-Step Setup:**

**1. Generate CA (Certificate Authority):**

```bash
# Create CA key and certificate
openssl req -new -x509 -keyout ca-key -out ca-cert -days 365 \
  -subj "/CN=KafkaCA" -passout pass:password

# Import CA into truststore (for clients)
keytool -keystore kafka.client.truststore.jks -alias CARoot \
  -import -file ca-cert -storepass password -noprompt
```

**2. Generate Broker Certificates:**

```bash
# For each broker, create keystore
keytool -keystore kafka.broker1.keystore.jks -alias broker1 \
  -validity 365 -genkey -keyalg RSA \
  -dname "CN=broker1.kafka.local" \
  -storepass password -keypass password

# Create certificate signing request
keytool -keystore kafka.broker1.keystore.jks -alias broker1 \
  -certreq -file broker1-cert-request \
  -storepass password

# Sign with CA
openssl x509 -req -CA ca-cert -CAkey ca-key -in broker1-cert-request \
  -out broker1-cert-signed -days 365 -CAcreateserial \
  -passin pass:password

# Import CA and signed cert into keystore
keytool -keystore kafka.broker1.keystore.jks -alias CARoot \
  -import -file ca-cert -storepass password -noprompt

keytool -keystore kafka.broker1.keystore.jks -alias broker1 \
  -import -file broker1-cert-signed -storepass password
```

**3. Broker Configuration:**

```properties
# server.properties
listeners=SSL://broker1.kafka.local:9093
advertised.listeners=SSL://broker1.kafka.local:9093

# SSL settings
ssl.keystore.location=/etc/kafka/ssl/kafka.broker1.keystore.jks
ssl.keystore.password=password
ssl.key.password=password
ssl.truststore.location=/etc/kafka/ssl/kafka.broker1.truststore.jks
ssl.truststore.password=password

# Client authentication (optional)
ssl.client.auth=required  # or 'requested' or 'none'
```

**4. Client Configuration:**

```properties
# producer.properties / consumer.properties
security.protocol=SSL
ssl.truststore.location=/path/to/kafka.client.truststore.jks
ssl.truststore.password=password

# If client auth required
ssl.keystore.location=/path/to/kafka.client.keystore.jks
ssl.keystore.password=password
ssl.key.password=password
```

**5. Test Connection:**

```bash
# Test SSL connection
openssl s_client -connect broker1.kafka.local:9093 -tls1_2

# Produce with SSL
kafka-console-producer --bootstrap-server broker1.kafka.local:9093 \
  --topic test \
  --producer.config /path/to/client-ssl.properties
```

### 8. How to implement authentication with SASL?
**Answer:**

**SASL** = Simple Authentication and Security Layer

**Types:**
1. SASL/PLAIN - Username/password (simple)
2. SASL/SCRAM - Username/password (more secure, salted)
3. SASL/GSSAPI (Kerberos) - Enterprise SSO

**SASL/SCRAM Setup (Recommended):**

**1. Create SCRAM Credentials:**

```bash
# Create user in ZooKeeper/KRaft
kafka-configs --bootstrap-server localhost:9092 \
  --alter \
  --add-config 'SCRAM-SHA-256=[iterations=8192,password=alice-secret],SCRAM-SHA-512=[password=alice-secret]' \
  --entity-type users \
  --entity-name alice

kafka-configs --bootstrap-server localhost:9092 \
  --alter \
  --add-config 'SCRAM-SHA-256=[password=admin-secret]' \
  --entity-type users \
  --entity-name admin
```

**2. Broker Configuration:**

```properties
# server.properties
listeners=SASL_SSL://broker1:9093
advertised.listeners=SASL_SSL://broker1:9093

# SASL mechanism
sasl.enabled.mechanisms=SCRAM-SHA-256
sasl.mechanism.inter.broker.protocol=SCRAM-SHA-256

# SSL (if using SASL_SSL)
ssl.keystore.location=/etc/kafka/ssl/kafka.broker1.keystore.jks
ssl.keystore.password=password
ssl.key.password=password

# Broker authentication with itself
security.inter.broker.protocol=SASL_SSL
```

**3. JAAS Configuration (Broker):**

```ini
# kafka_server_jaas.conf
KafkaServer {
    org.apache.kafka.common.security.scram.ScramLoginModule required
    username="admin"
    password="admin-secret";
};
```

**4. Client Configuration:**

```properties
# client.properties
security.protocol=SASL_SSL
sasl.mechanism=SCRAM-SHA-256
sasl.jaas.config=org.apache.kafka.common.security.scram.ScramLoginModule required \
  username="alice" \
  password="alice-secret";

# SSL truststore
ssl.truststore.location=/path/to/kafka.client.truststore.jks
ssl.truststore.password=password
```

**5. Client JAAS Config:**

```ini
# client_jaas.conf
KafkaClient {
    org.apache.kafka.common.security.scram.ScramLoginModule required
    username="alice"
    password="alice-secret";
};
```

**6. Use with Clients:**

```bash
# Set JAAS config
export KAFKA_OPTS="-Djava.security.auth.login.config=/path/to/client_jaas.conf"

# Produce
kafka-console-producer --bootstrap-server broker1:9093 \
  --topic test \
  --producer.config client.properties

# Consume
kafka-console-consumer --bootstrap-server broker1:9093 \
  --topic test \
  --consumer.config client.properties
```

### 9. How to implement authorization with ACLs?
**Answer:**

**ACLs** = Access Control Lists (who can do what)

**ACL Format:**
```
Principal: User:alice
Operation: READ, WRITE, CREATE, DELETE, DESCRIBE, ALTER
Resource: Topic:orders, Group:consumer-group, Cluster
Permission: ALLOW or DENY
```

**Common ACL Patterns:**

**1. Producer Permission:**

```bash
# Alice can write to 'orders' topic
kafka-acls --bootstrap-server localhost:9092 \
  --add \
  --allow-principal User:alice \
  --operation Write \
  --topic orders

# Alice can describe topic (needed for metadata)
kafka-acls --bootstrap-server localhost:9092 \
  --add \
  --allow-principal User:alice \
  --operation Describe \
  --topic orders
```

**2. Consumer Permission:**

```bash
# Bob can read from 'orders' topic
kafka-acls --bootstrap-server localhost:9092 \
  --add \
  --allow-principal User:bob \
  --operation Read \
  --topic orders

# Bob can use consumer group 'order-processors'
kafka-acls --bootstrap-server localhost:9092 \
  --add \
  --allow-principal User:bob \
  --operation Read \
  --group order-processors
```

**3. Admin Permission:**

```bash
# Admin can do everything on cluster
kafka-acls --bootstrap-server localhost:9092 \
  --add \
  --allow-principal User:admin \
  --operation All \
  --cluster

# Admin can create topics
kafka-acls --bootstrap-server localhost:9092 \
  --add \
  --allow-principal User:admin \
  --operation Create \
  --cluster
```

**4. Wildcard ACLs:**

```bash
# Alice can read from all topics starting with 'test-'
kafka-acls --bootstrap-server localhost:9092 \
  --add \
  --allow-principal User:alice \
  --operation Read \
  --topic test- \
  --resource-pattern-type prefixed
```

**5. List ACLs:**

```bash
# List all ACLs
kafka-acls --bootstrap-server localhost:9092 --list

# List ACLs for specific principal
kafka-acls --bootstrap-server localhost:9092 \
  --list \
  --principal User:alice

# List ACLs for specific topic
kafka-acls --bootstrap-server localhost:9092 \
  --list \
  --topic orders
```

**6. Remove ACLs:**

```bash
kafka-acls --bootstrap-server localhost:9092 \
  --remove \
  --allow-principal User:alice \
  --operation Write \
  --topic orders
```

**Broker Configuration:**

```properties
# server.properties
# Enable ACL authorizer
authorizer.class.name=kafka.security.authorizer.AclAuthorizer

# Super users (bypass ACLs)
super.users=User:admin;User:kafka

# Allow everyone if no ACL (not recommended for production!)
allow.everyone.if.no.acl.found=false  # Default: false
```

**ACL Storage:**

```
ACLs stored in:
- ZooKeeper: /kafka-acl/...
- KRaft: __cluster_metadata topic

Replicated across brokers
Persistent across restarts
```

---

## Schema Registry

### 10. What is Schema Registry and why use it?
**Answer:**

**Schema Registry** = Central repository for message schemas (Avro, Protobuf, JSON Schema)

**Problems it Solves:**

```
Without Schema Registry:
Producer → Sends JSON: {"name":"Alice","age":25}
Consumer → Expects: {"firstName":"Alice","age":25}
Result: Field mismatch! ✗

With Schema Registry:
Producer → Registers schema v1
         → Sends: [schema_id:1] + binary data
Consumer → Fetches schema 1 from registry
         → Deserializes correctly ✓
```

**Benefits:**

1. **Schema Validation**: Producer validates before sending
2. **Schema Evolution**: Backward/forward compatibility rules
3. **Storage Efficiency**: Binary format (Avro), smaller than JSON
4. **Type Safety**: Strongly typed, compile-time checks
5. **Documentation**: Self-documenting messages

**Architecture:**

```
┌─────────────┐
│ Producer    │
│  1. Register│  POST /subjects/users-value/versions
│     schema  │     {"schema": "..."}
│             │  Response: {"id": 1}
│  2. Serialize
│     with ID │  [magic_byte][schema_id][binary_data]
└─────────────┘
        ↓
┌─────────────┐
│ Kafka Topic │  [1][binary_data]
└─────────────┘
        ↓
┌─────────────┐
│ Consumer    │
│  1. Extract │  schema_id = 1
│     schema  │
│             │
│  2. Fetch   │  GET /schemas/ids/1
│     schema  │  Response: {"schema": "..."}
│             │
│  3. Deserial│  Decode binary with schema
│     -ize    │
└─────────────┘
```

**Avro Schema Example:**

```json
{
  "type": "record",
  "name": "User",
  "namespace": "com.example",
  "fields": [
    {"name": "id", "type": "int"},
    {"name": "name", "type": "string"},
    {"name": "email", "type": "string"},
    {"name": "age", "type": ["null", "int"], "default": null}
  ]
}
```

**Schema Evolution:**

```
Version 1:
{
  "fields": [
    {"name": "id", "type": "int"},
    {"name": "name", "type": "string"}
  ]
}

Version 2 (Backward Compatible - add optional field):
{
  "fields": [
    {"name": "id", "type": "int"},
    {"name": "name", "type": "string"},
    {"name": "email", "type": ["null", "string"], "default": null}  ← NEW
  ]
}

Old consumers can still read new messages (ignore new field) ✓
New consumers can read old messages (use default value) ✓
```

**Producer Code:**

```java
// Configure producer with Schema Registry
Properties props = new Properties();
props.put("bootstrap.servers", "localhost:9092");
props.put("key.serializer", StringSerializer.class);
props.put("value.serializer", KafkaAvroSerializer.class);  // Avro serializer
props.put("schema.registry.url", "http://localhost:8081");

KafkaProducer<String, GenericRecord> producer = new KafkaProducer<>(props);

// Create Avro record
String schemaString = "{...avro schema...}";
Schema.Parser parser = new Schema.Parser();
Schema schema = parser.parse(schemaString);

GenericRecord user = new GenericData.Record(schema);
user.put("id", 1);
user.put("name", "Alice");
user.put("email", "alice@example.com");

// Send (serializer auto-registers schema)
ProducerRecord<String, GenericRecord> record = 
    new ProducerRecord<>("users", "user-1", user);
producer.send(record);
```

**Consumer Code:**

```java
// Configure consumer
Properties props = new Properties();
props.put("bootstrap.servers", "localhost:9092");
props.put("key.deserializer", StringDeserializer.class);
props.put("value.deserializer", KafkaAvroDeserializer.class);  // Avro deserializer
props.put("schema.registry.url", "http://localhost:8081");

KafkaConsumer<String, GenericRecord> consumer = new KafkaConsumer<>(props);
consumer.subscribe(Arrays.asList("users"));

while (true) {
    ConsumerRecords<String, GenericRecord> records = consumer.poll(Duration.ofMillis(100));
    for (ConsumerRecord<String, GenericRecord> record : records) {
        GenericRecord user = record.value();
        System.out.println("User: " + user.get("name"));  // Auto-deserialized
    }
}
```

---

## Best Practices & Anti-Patterns

### 11. What are common Kafka anti-patterns?
**Answer:**

**Anti-Pattern 1: Using Kafka as a Database**

```
❌ BAD:
- Store all user data in Kafka long-term
- Query Kafka like a database
- Use Kafka as primary data store

Why bad:
- Kafka is for streaming, not storage
- No indexing, slow queries
- Retention limits

✓ GOOD:
- Use Kafka for events/changes
- Store state in database
- Use Kafka Streams with state stores for materialized views
```

**Anti-Pattern 2: Too Many Partitions**

```
❌ BAD:
- Topic with 1000 partitions for low volume
- "More partitions = better performance" thinking

Why bad:
- Each partition = files, memory overhead
- Slower leader election
- Slower replication
- More ZooKeeper/KRaft load

✓ GOOD:
- Start with moderate partitions (10-30)
- Increase based on actual load
- Rule of thumb: (target throughput / max partition throughput)
```

**Anti-Pattern 3: Large Messages**

```
❌ BAD:
- Sending 10 MB messages to Kafka
- Storing files/images in messages

Why bad:
- Memory pressure on broker
- Slow replication
- Network congestion
- Consumer OOM errors

✓ GOOD:
- Keep messages < 1 MB
- Store large data in S3/object storage
- Send reference/URL in Kafka message
```

**Anti-Pattern 4: Not Handling Rebalancing**

```
❌ BAD:
- Long processing in poll loop
- No graceful shutdown
- Ignoring rebalancing

Why bad:
- max.poll.interval.ms exceeded
- Consumer kicked out
- Constant rebalancing

✓ GOOD:
- Process quickly in poll loop
- Use separate thread pool for heavy processing
- Implement ConsumerRebalanceListener
- Graceful shutdown on SIGTERM
```

**Anti-Pattern 5: Synchronous Producer**

```
❌ BAD:
producer.send(record).get();  // Blocks!

Why bad:
- Kills throughput
- Only 1 message in flight
- No batching

✓ GOOD:
// Async with callback
producer.send(record, (metadata, exception) -> {
    if (exception != null) {
        log.error("Failed", exception);
    }
});
```

**Anti-Pattern 6: No Monitoring**

```
❌ BAD:
- Deploy to production without metrics
- No alerting
- Can't see problems

✓ GOOD:
- Monitor: UnderReplicatedPartitions, consumer lag, throughput
- Alert on critical metrics
- Dashboard for visibility
```

**Anti-Pattern 7: Ignoring Consumer Lag**

```
❌ BAD:
- Consumer lag growing
- "It'll catch up eventually"

Why bad:
- May never catch up
- Data loss if messages expire
- Increased latency

✓ GOOD:
- Alert on lag > threshold
- Add consumers if needed
- Optimize processing
- Use parallel processing
```

### 12. What are Kafka best practices for production?
**Answer:**

**Best Practices:**

**1. Replication:**
```properties
# Always use replication factor ≥ 3
replication.factor=3
min.insync.replicas=2

# For critical data
acks=all
```

**2. Monitoring:**
```
- UnderReplicatedPartitions = 0
- OfflinePartitionsCount = 0
- Consumer lag < 1000
- Set up alerts
- Use Grafana dashboards
```

**3. Capacity Planning:**
```
Disk: retention * daily volume * replication factor
Network: (bytes in + bytes out) * safety factor
CPU: For compression, encryption
Memory: Leave 50%+ for page cache
```

**4. Security:**
```
- Enable SSL/TLS for encryption
- Use SASL for authentication
- Implement ACLs for authorization
- Regular security audits
```

**5. Upgrades:**
```
- Rolling upgrades (one broker at a time)
- Test in staging first
- Read release notes
- Backup configurations
```

**6. Topic Design:**
```
- Partition by natural key (user_id, order_id)
- 10-30 partitions for most topics
- Use compaction for changelogs
- Descriptive names (service.entity.action)
```

**7. Consumer Design:**
```
- Idempotent processing
- Manual offset commits
- Graceful shutdown
- Handle rebalancing
- Monitor lag
```

**8. Producer Design:**
```
- Async sends with callbacks
- Handle failures/retries
- Enable idempotence
- Batch for throughput
```

---

## Advanced Topics

### 13. How to implement multi-datacenter replication?
**Answer:**

**What is MirrorMaker 2.0?**

**MirrorMaker 2.0 (MM2)** is Kafka's built-in tool for **replicating data between Kafka clusters** across different datacenters, regions, or clouds.

**Key Difference from MirrorMaker 1.0:**

```
MirrorMaker 1.0 (Legacy):
- Simple consumer + producer
- Only replicates messages
- Doesn't sync offsets or metadata
- Hard to manage failover
- Manual offset tracking

MirrorMaker 2.0 (Modern - Kafka 2.4+):
- Built on Kafka Connect framework
- Replicates messages + offsets + metadata
- Automatic failover support
- Syncs consumer groups (CRITICAL!)
- Better monitoring and management
- Topic config replication
- ACL replication (optional)
```

**What MM2 Actually Replicates:**

```
Source Cluster (DC1)                Target Cluster (DC2)
┌─────────────────────┐            ┌─────────────────────┐
│ 1. Topics & Data:   │            │ Topics:             │
│  - orders           │  ───────►  │  - dc1.orders       │
│  - users            │            │  - dc1.users        │
│                     │            │                     │
│ 2. Consumer Groups: │            │ Consumer Groups:    │
│  - analytics-group  │  ───────►  │  - analytics-group  │
│    (offset: 1000)   │            │    (offset: 1000)   │
│                     │            │    ← Same position! │
│ 3. Topic Configs:   │            │ Topic Configs:      │
│  - partitions: 10   │  ───────►  │  - partitions: 10   │
│  - retention: 7d    │            │  - retention: 7d    │
│                     │            │                     │
│ 4. ACLs (optional): │            │ ACLs:               │
│  - User:alice       │  ───────►  │  - User:alice       │
│    can write        │            │    can write        │
└─────────────────────┘            └─────────────────────┘

Everything needed for seamless failover! ✓
```

**Why Use MirrorMaker 2.0?**

**1. Disaster Recovery (DR):**
```
Primary DC fails → Failover to DR DC
Consumers resume from last synced offset
No data loss, minimal downtime (minutes)
```

**2. Geo-Distribution:**
```
US Cluster ──► EU Cluster ──► Asia Cluster
Users read from nearest cluster (low latency)
Global applications with local data
```

**3. Cloud Migration:**
```
On-Prem Kafka ──► AWS MSK / Confluent Cloud
Zero-downtime migration
Test in cloud before cutover
Gradual transition
```

**4. Data Aggregation:**
```
Regional Clusters ──► Central Analytics Cluster
Collect data from all regions
Unified analytics and reporting
ML training on global data
```

**5. Active-Active Setup:**
```
Cluster A ←──► Cluster B
Both accept writes (bidirectional)
High availability
Load distribution
```

**Internal Architecture - How MM2 Works:**

```
┌─────────────────────────────────────────────────────────┐
│ MirrorMaker 2.0 Process (Built on Kafka Connect)       │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌──────────────────────────────────────────────────┐  │
│  │ 1. MirrorSourceConnector                         │  │
│  │    - Reads messages from source topics           │  │
│  │    - Writes to target topics (with prefix)       │  │
│  │    - Preserves keys, values, headers, timestamps │  │
│  │    - Maintains partition ordering                │  │
│  └──────────────────────────────────────────────────┘  │
│                                                         │
│  ┌──────────────────────────────────────────────────┐  │
│  │ 2. MirrorCheckpointConnector                     │  │
│  │    - Reads consumer group offsets from source    │  │
│  │    - Translates offset mapping source → target   │  │
│  │    - Writes to target's __consumer_offsets       │  │
│  │    - Enables seamless consumer failover          │  │
│  └──────────────────────────────────────────────────┘  │
│                                                         │
│  ┌──────────────────────────────────────────────────┐  │
│  │ 3. MirrorHeartbeatConnector                      │  │
│  │    - Sends periodic heartbeat messages           │  │
│  │    - Monitors cluster connectivity               │  │
│  │    - Detects network partitions                  │  │
│  │    - Tracks replication lag                      │  │
│  └──────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

**The MAGIC: Offset Translation**

This is the killer feature that makes MM2 superior to MM1:

```
Source Cluster:
┌──────────────────────────────────┐
│ Topic: orders                    │
│ Partition 0:                     │
│   offset 998: msg A              │
│   offset 999: msg B              │
│   offset 1000: msg C  ← Consumer │
│   offset 1001: msg D             │
└──────────────────────────────────┘

Consumer Group: analytics
Current offset: 1000

Target Cluster (after replication):
┌──────────────────────────────────┐
│ Topic: dc1.orders                │
│ Partition 0:                     │
│   offset 998: msg A              │
│   offset 999: msg B              │
│   offset 1000: msg C  ← Mapped!  │
│   offset 1001: msg D             │
└──────────────────────────────────┘

Consumer Group: analytics
Offset checkpoint: 1000 (synced by MM2!)

If failover happens:
1. Consumer switches to target cluster
2. Subscribes to "dc1.orders"
3. MM2 already synced offset 1000
4. Consumer resumes from offset 1000
5. No duplicate processing! ✓
6. No missed messages! ✓

Without MM2:
- Consumer would start from beginning (offset 0) ✗
- Or start from latest (miss messages) ✗
- No way to resume from exact position ✗
```

**Architecture:**

```
DC1 (Primary):                  DC2 (DR):
┌──────────────┐               ┌──────────────┐
│ Kafka        │               │ Kafka        │
│ Cluster      │               │ Cluster      │
│  - topic-A   │               │  - topic-A   │
│  - topic-B   │               │  - topic-B   │
└──────────────┘               └──────────────┘
        ↓                              ↑
        └──────── MirrorMaker 2 ───────┘
              (Replicates DC1 → DC2)
```

**Complete MirrorMaker 2 Configuration:**

**Complete MirrorMaker 2 Configuration:**

```properties
# mm2.properties - Comprehensive Configuration

########################## Cluster Definitions ##########################
# Define all clusters involved in replication
clusters = primary, backup

# Primary cluster (source - where data originates)
primary.bootstrap.servers = dc1-broker1:9092,dc1-broker2:9092,dc1-broker3:9092

# Backup cluster (target - where data is replicated to)
backup.bootstrap.servers = dc2-broker1:9092,dc2-broker2:9092,dc2-broker3:9092

########################## Replication Flow ##########################
# Enable replication from primary to backup
primary->backup.enabled = true

# Which topics to replicate (regex pattern)
primary->backup.topics = .*                    # All topics
# primary->backup.topics = orders|users|payments  # Specific topics only
# primary->backup.topics = prod-.*             # Topics starting with "prod-"

# Exclude topics (topics that should NOT be replicated)
primary->backup.topics.blacklist = internal-.*,test-.*,__.*

########################## Source Consumer Config ##########################
# Consumer that reads from PRIMARY cluster
primary->backup.consumer.group.id = mm2-primary-to-backup
primary->backup.consumer.auto.offset.reset = earliest  # Start from beginning
primary->backup.consumer.max.poll.records = 500
primary->backup.consumer.fetch.min.bytes = 1048576     # 1 MB
primary->backup.consumer.max.poll.interval.ms = 300000 # 5 minutes

########################## Target Producer Config ##########################
# Producer that writes to BACKUP cluster
primary->backup.producer.enable.idempotence = true
primary->backup.producer.acks = all                    # Wait for all replicas
primary->backup.producer.compression.type = lz4        # Compress for network
primary->backup.producer.batch.size = 65536            # 64 KB batches
primary->backup.producer.linger.ms = 10                # Wait 10ms for batching

########################## Offset Sync (CRITICAL for Failover!) ##########################
# Sync consumer group offsets - THIS IS THE MAGIC!
sync.group.offsets.enabled = true
sync.group.offsets.interval.seconds = 60        # Sync every 60 seconds
emit.checkpoints.enabled = true                 # Enable checkpoint emission
emit.checkpoints.interval.seconds = 60          # Emit checkpoints every 60s

# Which consumer groups to sync
groups = .*                                     # All groups
# groups = analytics-group|reporting-group     # Specific groups only
# groups.blacklist = test-.*                   # Exclude groups

########################## Topic Configuration Sync ##########################
# Replicate topic configurations (partitions, retention, etc.)
sync.topic.configs.enabled = true
sync.topic.configs.interval.seconds = 300       # Sync every 5 minutes

# Which topic configs to sync (blacklist those that should differ)
config.properties.blacklist = segment.bytes,segment.ms,follower.replication.throttled.replicas

########################## ACL Sync (Optional) ##########################
# Replicate ACLs (user permissions)
sync.topic.acls.enabled = false                 # Disabled by default
sync.topic.acls.interval.seconds = 600          # If enabled, sync every 10 min

########################## Naming & Prefixes ##########################
# How to name topics in target cluster
replication.policy.class = org.apache.kafka.connect.mirror.DefaultReplicationPolicy
# Result: primary.topic-name (prefixed with source cluster name)

# Separator character between cluster name and topic name
replication.policy.separator = .                # Default: dot

########################## Replication Settings ##########################
# Replication factor for replicated topics in target cluster
replication.factor = 3

# Replication factor for MM2 internal topics
offset-syncs.topic.replication.factor = 3       # Offset mapping topic
heartbeats.topic.replication.factor = 3         # Heartbeat monitoring topic
checkpoints.topic.replication.factor = 3        # Consumer group checkpoints

########################## Performance Tuning ##########################
# Number of parallel tasks (increases throughput)
tasks.max = 4                                   # 4 parallel replication tasks

# Topic refresh settings
refresh.topics.enabled = true                   # Auto-detect new topics
refresh.topics.interval.seconds = 600           # Check every 10 minutes

# Consumer group refresh
refresh.groups.enabled = true                   # Auto-detect new groups
refresh.groups.interval.seconds = 600           # Check every 10 minutes

########################## Monitoring & Health ##########################
# Heartbeat for monitoring cluster connectivity
emit.heartbeats.enabled = true
emit.heartbeats.interval.seconds = 5            # Heartbeat every 5 seconds

# Metrics configuration
metrics.sample.window.ms = 30000
metrics.num.samples = 2
metrics.recording.level = INFO

########################## Error Handling ##########################
# Retry settings for transient failures
errors.retry.timeout = 60000                    # Retry for 60 seconds
errors.retry.delay.max.ms = 5000                # Max 5 seconds between retries

# Error tolerance
errors.tolerance = none                         # Fail on any error (safe)
# errors.tolerance = all                        # Continue on errors (risky)
errors.log.enable = true                        # Log errors
errors.log.include.messages = true              # Include message details

########################## Exactly-Once Semantics ##########################
# Enable exactly-once delivery (requires Kafka 2.5+)
exactly.once.support = disabled                 # or 'enabled'
transaction.timeout.ms = 900000                 # 15 minutes
```

**Key Settings Explained:**

```
1. sync.group.offsets.enabled = true
   → THE critical setting for disaster recovery
   → Without this, consumers can't resume after failover
   → MM2 maintains offset mapping between clusters

2. primary->backup.topics = .*
   → Regex pattern for topic selection
   → .* = all topics
   → prod-.* = topics starting with "prod-"
   → ^(?!test).*$ = all except topics starting with "test"

3. replication.policy.class
   → Controls naming convention in target cluster
   → Default prefixes with source cluster name
   → Prevents conflicts in multi-cluster scenarios
   → primary.orders (not just "orders")

4. tasks.max = 4
   → Number of parallel replication workers
   → More tasks = higher throughput
   → Limited by partition count
   → Rule: tasks ≤ total partitions across all topics

5. emit.checkpoints.interval.seconds = 60
   → How often to sync consumer group offsets
   → Lower = more frequent sync, less data loss risk
   → Higher = less overhead, but more reprocessing on failover
   → 60 seconds = good balance

6. errors.tolerance = none
   → Fail-fast on errors (production recommended)
   → Prevents silent data loss
   → Alternative: 'all' to continue (use with caution!)
```

**Running MirrorMaker 2:**

**Method 1: Standalone Mode (Simple)**
```bash
# Start MM2 as dedicated process
connect-mirror-maker.sh mm2.properties

# With specific JVM settings
export KAFKA_HEAP_OPTS="-Xmx4g -Xms4g"
connect-mirror-maker.sh mm2.properties

# As background daemon
nohup connect-mirror-maker.sh mm2.properties > /var/log/mm2.log 2>&1 &

# Check it's running
ps aux | grep mirror-maker
jps | grep ConnectDistributed
```

**Method 2: Distributed Mode (Production HA)**
```bash
# Start multiple Kafka Connect workers (for redundancy)
# Worker 1
connect-distributed.sh connect-distributed.properties

# Worker 2 (on different server)
connect-distributed.sh connect-distributed.properties

# Deploy MM2 connectors via REST API
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d '{
    "name": "mm2-source-connector",
    "config": {
      "connector.class": "org.apache.kafka.connect.mirror.MirrorSourceConnector",
      "source.cluster.alias": "primary",
      "target.cluster.alias": "backup",
      "source.cluster.bootstrap.servers": "dc1-broker:9092",
      "target.cluster.bootstrap.servers": "dc2-broker:9092",
      "topics": ".*",
      "replication.factor": "3"
    }
  }'
```

**Method 3: Docker/Kubernetes**
```yaml
# docker-compose.yml
version: '3'
services:
  mirrormaker2:
    image: confluentinc/cp-kafka:7.5.0
    container_name: mm2
    command: connect-mirror-maker /etc/mm2/mm2.properties
    volumes:
      - ./mm2.properties:/etc/mm2/mm2.properties
    environment:
      KAFKA_HEAP_OPTS: "-Xmx2g -Xms2g"
      KAFKA_LOG4J_ROOT_LOGLEVEL: INFO
    restart: unless-stopped
```

**Topic Naming in Target Cluster:**

```
┌─────────────────────────────────────────────────────────────┐
│ Why MM2 Prefixes Topics with Source Cluster Name           │
└─────────────────────────────────────────────────────────────┘

Source Cluster (primary):           Target Cluster (backup):
┌──────────────────┐               ┌──────────────────────┐
│ orders           │  ───────►     │ primary.orders       │ ← Prefixed!
│ users            │  ───────►     │ primary.users        │
│ payments         │  ───────►     │ primary.payments     │
└──────────────────┘               └──────────────────────┘

Benefits of Prefixing:

1. Origin Identification:
   - Instantly know data came from "primary" cluster
   - Useful for debugging and monitoring
   - Trace data lineage

2. Avoid Name Collisions:
   - Aggregate data from multiple sources
   - DC1 → central: dc1.orders
   - DC2 → central: dc2.orders
   - DC3 → central: dc3.orders
   - All coexist in same cluster!

3. Bidirectional Replication:
   Cluster A:                     Cluster B:
   - orders (local)               - orders (local)
   - backup.orders (from B)       - primary.orders (from A)
   
   No circular loops! Each cluster knows which is local vs remote.

4. Failback Support:
   Primary fails → failover to backup
   Primary recovers:
   - Can replicate back: backup → primary
   - Primary sees "backup.orders"
   - Can sync what it missed!

5. Testing in Production:
   - Replicate prod → staging
   - Staging cluster has "prod.orders"
   - Test consumers against prod data
   - No impact on production!
```

**Custom Replication Policy (Advanced):**

```java
// Implement custom naming logic
public class CustomReplicationPolicy implements ReplicationPolicy {
    
    @Override
    public String formatRemoteTopic(String sourceClusterAlias, String topic) {
        // Custom naming logic
        
        // Option 1: Use underscore instead of dot
        return sourceClusterAlias + "_" + topic;
        // Result: primary_orders
        
        // Option 2: Add timestamp
        return sourceClusterAlias + "." + topic + ".v" + System.currentTimeMillis();
        // Result: primary.orders.v1234567890
        
        // Option 3: No prefix (just topic name)
        return topic;
        // Result: orders (same name)
        // WARNING: Can cause conflicts!
    }
    
    @Override
    public String topicSource(String topic) {
        // Extract source cluster from topic name
        return topic.split("\\.")[0];
    }
}

// Configure in mm2.properties
replication.policy.class = com.example.CustomReplicationPolicy
```

**Deployment Patterns:**

### 14. How to handle disaster recovery?
**Answer:**

**Disaster Recovery Strategy:**

**1. Multi-DC Replication (MirrorMaker)**
```
- Replicate to DR datacenter
- RPO: ~seconds (replication lag)
- RTO: ~minutes (failover time)
```

**2. Backup to Object Storage:**
```bash
# Use Kafka Connect S3 Sink
{
  "name": "s3-sink",
  "config": {
    "connector.class": "io.confluent.connect.s3.S3SinkConnector",
    "topics": "important-topic",
    "s3.bucket.name": "kafka-backup",
    "flush.size": "1000",
    "storage.class": "io.confluent.connect.s3.storage.S3Storage",
    "format.class": "io.confluent.connect.s3.format.json.JsonFormat",
    "partitioner.class": "io.confluent.connect.storage.partitioner.TimeBasedPartitioner",
    "path.format": "'year'=YYYY/'month'=MM/'day'=dd"
  }
}
```

**3. Snapshot Broker State:**
```bash
# Stop broker gracefully
kafka-server-stop.sh

# Backup broker data directory
tar -czf kafka-backup-$(date +%Y%m%d).tar.gz /var/lib/kafka/data/

# Upload to S3
aws s3 cp kafka-backup-*.tar.gz s3://kafka-dr-backups/
```

**4. Document Runbooks:**
```
- Failover procedure
- Failback procedure
- Contact list
- System diagrams
- Configuration backups
```

**5. Test DR Regularly:**
```
- Monthly DR drills
- Test failover
- Test recovery time
- Update procedures
```

---

## Summary

**Key Takeaways:**

✅ **Performance**: Tune batch.size, linger.ms, compression for throughput
✅ **Monitoring**: Watch UnderReplicatedPartitions, lag, latency
✅ **Security**: SSL + SASL + ACLs for production
✅ **Schema Registry**: Use Avro for schema evolution
✅ **Best Practices**: Replication ≥ 3, monitor everything, handle failures
✅ **DR**: MirrorMaker 2 for multi-DC, regular backups

**Interview Quick Answers:**

1. **"How to optimize Kafka?"** → Batch, compress, pipeline (max.in.flight=5)
2. **"Key metrics to monitor?"** → UnderReplicatedPartitions, lag, offline partitions
3. **"How to secure Kafka?"** → SSL (encryption) + SASL (auth) + ACLs (authz)
4. **"What is Schema Registry?"** → Central schema store, enables evolution
5. **"Common anti-patterns?"** → Large messages, too many partitions, no monitoring
6. **"Multi-DC replication?"** → MirrorMaker 2.0
7. **"Disaster recovery?"** → Multi-DC + backups + regular testing

**You're now production-ready!** 🚀



