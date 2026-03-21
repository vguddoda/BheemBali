# Kafka Streams Examples

Real, runnable Kafka Streams applications demonstrating key concepts.

## Examples Included

1. **WordCount.java** - Classic word count (stateful aggregation)
2. **WindowedCount.java** - Tumbling window aggregation
3. **StreamJoin.java** - Stream-stream join with time window
4. **StreamTableJoin.java** - Stream-table join (enrichment pattern)

---

## Quick Start

### Step 1: Build
```bash
cd examples/java/streams
mvn clean package
```

### Step 2: Create Topics

```bash
# For WordCount
docker exec kafka-broker-1 kafka-topics --create \
  --bootstrap-server localhost:19092 \
  --topic streams-plaintext-input \
  --partitions 3 --replication-factor 2

docker exec kafka-broker-1 kafka-topics --create \
  --bootstrap-server localhost:19092 \
  --topic streams-wordcount-output \
  --partitions 3 --replication-factor 2 \
  --config cleanup.policy=compact

# For WindowedCount
docker exec kafka-broker-1 kafka-topics --create \
  --bootstrap-server localhost:19092 \
  --topic events-input \
  --partitions 3 --replication-factor 2

docker exec kafka-broker-1 kafka-topics --create \
  --bootstrap-server localhost:19092 \
  --topic windowed-counts \
  --partitions 3 --replication-factor 2

# For StreamJoin
docker exec kafka-broker-1 kafka-topics --create \
  --bootstrap-server localhost:19092 \
  --topic orders \
  --partitions 3 --replication-factor 2

docker exec kafka-broker-1 kafka-topics --create \
  --bootstrap-server localhost:19092 \
  --topic payments \
  --partitions 3 --replication-factor 2

docker exec kafka-broker-1 kafka-topics --create \
  --bootstrap-server localhost:19092 \
  --topic order-payment-joined \
  --partitions 3 --replication-factor 2

# For StreamTableJoin
docker exec kafka-broker-1 kafka-topics --create \
  --bootstrap-server localhost:19092 \
  --topic customers-table \
  --partitions 3 --replication-factor 2 \
  --config cleanup.policy=compact

docker exec kafka-broker-1 kafka-topics --create \
  --bootstrap-server localhost:19092 \
  --topic orders-stream \
  --partitions 3 --replication-factor 2

docker exec kafka-broker-1 kafka-topics --create \
  --bootstrap-server localhost:19092 \
  --topic enriched-orders \
  --partitions 3 --replication-factor 2
```

---

## Example 1: Word Count (Stateful Aggregation)

### Run the Stream App
```bash
mvn exec:java -Dexec.mainClass="WordCount"
```

### Send Test Data (new terminal)
```bash
docker exec -i kafka-broker-1 kafka-console-producer \
  --bootstrap-server localhost:19092 \
  --topic streams-plaintext-input << EOF
hello kafka streams
kafka is awesome
hello world
kafka streams rocks
EOF
```

### Consume Results (new terminal)
```bash
docker exec kafka-broker-1 kafka-console-consumer \
  --bootstrap-server localhost:19092 \
  --topic streams-wordcount-output \
  --from-beginning \
  --property print.key=true \
  --property key.separator=: \
  --property value.deserializer=org.apache.kafka.common.serialization.LongDeserializer
```

**Expected Output:**
```
hello:1
kafka:1
streams:1
is:1
awesome:1
hello:2
world:1
kafka:2
streams:2
rocks:1
```

---

## Example 2: Windowed Count (1-Minute Tumbling Window)

### Run the Stream App
```bash
mvn exec:java -Dexec.mainClass="WindowedCount"
```

### Send Events (new terminal)
```bash
docker exec -i kafka-broker-1 kafka-console-producer \
  --bootstrap-server localhost:19092 \
  --topic events-input \
  --property "parse.key=true" \
  --property "key.separator=:" << EOF
user1:click
user2:click
user1:purchase
user1:click
user3:click
EOF
```

### Observe Output
Stream app will print windowed counts every minute:
```
✓ Key: user1 | Window: [1234567800000 - 1234567860000] | Count: 3
✓ Key: user2 | Window: [1234567800000 - 1234567860000] | Count: 1
✓ Key: user3 | Window: [1234567800000 - 1234567860000] | Count: 1
```

---

## Example 3: Stream-Stream Join (5-Minute Window)

### Run the Stream App
```bash
mvn exec:java -Dexec.mainClass="StreamJoin"
```

### Send Orders (new terminal)
```bash
docker exec -i kafka-broker-1 kafka-console-producer \
  --bootstrap-server localhost:19092 \
  --topic orders \
  --property "parse.key=true" \
  --property "key.separator=:" << EOF
order1:{"orderId":"order1","amount":100}
order2:{"orderId":"order2","amount":200}
EOF
```

### Send Payments (within 5 minutes!)
```bash
docker exec -i kafka-broker-1 kafka-console-producer \
  --bootstrap-server localhost:19092 \
  --topic payments \
  --property "parse.key=true" \
  --property "key.separator=:" << EOF
order1:{"paymentId":"pay1","status":"success"}
order2:{"paymentId":"pay2","status":"success"}
EOF
```

### Observe Joins
Stream app will print:
```
✓ Joined: Order: {"orderId":"order1","amount":100} | Payment: {"paymentId":"pay1","status":"success"}
✓ Joined: Order: {"orderId":"order2","amount":200} | Payment: {"paymentId":"pay2","status":"success"}
```

---

## Example 4: Stream-Table Join (Enrichment)

### Populate Customer Table First
```bash
docker exec -i kafka-broker-1 kafka-console-producer \
  --bootstrap-server localhost:19092 \
  --topic customers-table \
  --property "parse.key=true" \
  --property "key.separator=:" << EOF
cust1:{"name":"Alice","tier":"Gold"}
cust2:{"name":"Bob","tier":"Silver"}
cust3:{"name":"Charlie","tier":"Bronze"}
EOF
```

### Run the Stream App
```bash
mvn exec:java -Dexec.mainClass="StreamTableJoin"
```

### Send Orders
```bash
docker exec -i kafka-broker-1 kafka-console-producer \
  --bootstrap-server localhost:19092 \
  --topic orders-stream \
  --property "parse.key=true" \
  --property "key.separator=:" << EOF
cust1:{"orderId":"order1","amount":100}
cust2:{"orderId":"order2","amount":200}
cust1:{"orderId":"order3","amount":150}
EOF
```

### Observe Enrichment
Stream app will print:
```
✓ Enriched: Order: {"orderId":"order1","amount":100} | Customer: {"name":"Alice","tier":"Gold"}
✓ Enriched: Order: {"orderId":"order2","amount":200} | Customer: {"name":"Bob","tier":"Silver"}
✓ Enriched: Order: {"orderId":"order3","amount":150} | Customer: {"name":"Alice","tier":"Gold"}
```

---

## Key Concepts Demonstrated

### Stateless Operations
- `flatMapValues()` - Split into multiple records
- `peek()` - Debug without modifying stream

### Stateful Operations
- `count()` - Aggregate with state (uses RocksDB)
- `join()` - Requires buffering (state)

### Windowing
- `TimeWindows.of(Duration.ofMinutes(1))` - Tumbling window
- Events grouped by time

### Joins
- **Stream-Stream**: Both unbounded, needs time window
- **Stream-Table**: Stream enrichment with table lookup, no window needed

### State Management
- Automatically backed by changelog topics
- Survives restarts
- Can be queried with Interactive Queries

---

## Interview Demo Commands

### Show Topology
Every app prints topology on startup:
```
Topologies:
   Sub-topology: 0
    Source: KSTREAM-SOURCE-0000000000 (topics: [streams-plaintext-input])
      --> KSTREAM-FLATMAPVALUES-0000000001
    ...
```

### Check State Stores
```bash
# State stored in /tmp/kafka-streams/<app-id>/
ls -lh /tmp/kafka-streams/wordcount-app/
```

### View Changelog Topics
```bash
docker exec kafka-broker-1 kafka-topics --list \
  --bootstrap-server localhost:19092 | grep -E "wordcount|repartition|changelog"
```

---

## Troubleshooting

### "Topic doesn't exist"
Create topics first (see Step 2 above)

### No output appearing
- Check topics are created
- Send more test data
- Disable caching: `CACHE_MAX_BYTES_BUFFERING_CONFIG = 0`

### Join not working
- Ensure both streams have **same key**
- Check events are within join window
- Verify topics are co-partitioned (same partition count)

---

## What Each Example Teaches

| Example | Concept | Interview Value |
|---------|---------|-----------------|
| WordCount | Stateful aggregation, KTable | Classic, must-know example |
| WindowedCount | Tumbling windows, time-based | Advanced windowing |
| StreamJoin | Stream-stream join, buffering | Join semantics |
| StreamTableJoin | Enrichment pattern, KTable lookup | Real-world pattern |

---

## Next Steps

1. Run all 4 examples
2. Modify window sizes
3. Try different join types (left, outer)
4. Add custom business logic
5. Implement exactly-once semantics

**You're now ready to explain and demo Kafka Streams in interviews!** 🚀

