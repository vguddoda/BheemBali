# Day 5 Lab: Kafka Streams & Connect

## Lab Objectives

By the end of this lab, you will:
- ✅ Build a Kafka Streams application
- ✅ Test stateful and stateless operations
- ✅ Implement windowed aggregations
- ✅ Test stream joins
- ✅ Setup Kafka Connect with source and sink
- ✅ Implement CDC with Debezium

---

## Prerequisites

```bash
# Ensure Kafka cluster is running
cd /Users/vishalkumarbg/Documents/Bheembali/kafka
./setup.sh

# Verify
docker ps | grep kafka
```

---

## Lab 1: Simple Kafka Streams Application (30 min)

### Step 1: Create Topics
```bash
# Input topic
docker exec kafka-broker-1 kafka-topics --create \
  --bootstrap-server localhost:19092 \
  --topic streams-plaintext-input \
  --partitions 3 \
  --replication-factor 2

# Output topic
docker exec kafka-broker-1 kafka-topics --create \
  --bootstrap-server localhost:19092 \
  --topic streams-wordcount-output \
  --partitions 3 \
  --replication-factor 2 \
  --config cleanup.policy=compact
```

### Step 2: Create Word Count Application

Create file: `WordCountApp.java`

```java
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;

import java.util.Arrays;
import java.util.Properties;

public class WordCountApp {
    public static void main(String[] args) {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "wordcount-app");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:19092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        
        StreamsBuilder builder = new StreamsBuilder();
        
        KStream<String, String> textLines = builder.stream("streams-plaintext-input");
        
        KTable<String, Long> wordCounts = textLines
            .flatMapValues(line -> Arrays.asList(line.toLowerCase().split("\\s+")))
            .groupBy((key, word) -> word)
            .count();
        
        wordCounts.toStream().to("streams-wordcount-output", 
            Produced.with(Serdes.String(), Serdes.Long()));
        
        KafkaStreams streams = new KafkaStreams(builder.build(), props);
        streams.start();
        
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
    }
}
```

### Step 3: Run the Application

```bash
# Compile (you'll need kafka-streams dependency)
# For quick test, we'll use console instead

# Send test data
docker exec -i kafka-broker-1 kafka-console-producer \
  --bootstrap-server localhost:19092 \
  --topic streams-plaintext-input << EOF
hello kafka streams
kafka streams is awesome
hello world
EOF
```

### Step 4: Consume Results

```bash
docker exec kafka-broker-1 kafka-console-consumer \
  --bootstrap-server localhost:19092 \
  --topic streams-wordcount-output \
  --from-beginning \
  --property print.key=true \
  --property key.separator=: \
  --property value.deserializer=org.apache.kafka.common.serialization.LongDeserializer
```

**Expected output:**
```
hello:1
kafka:1
streams:1
kafka:2
streams:2
is:1
awesome:1
hello:2
world:1
```

---

## Lab 2: Windowed Aggregations (30 min)

### Scenario: Count events per 1-minute tumbling window

### Step 1: Create Topics
```bash
docker exec kafka-broker-1 kafka-topics --create \
  --bootstrap-server localhost:19092 \
  --topic clickstream \
  --partitions 3 \
  --replication-factor 2

docker exec kafka-broker-1 kafka-topics --create \
  --bootstrap-server localhost:19092 \
  --topic clicks-per-minute \
  --partitions 3 \
  --replication-factor 2
```

### Step 2: Send Timestamped Events

```bash
# Send events with timestamps
docker exec -i kafka-broker-1 kafka-console-producer \
  --bootstrap-server localhost:19092 \
  --topic clickstream \
  --property "parse.key=true" \
  --property "key.separator=:" << EOF
user1:product-page
user2:checkout
user1:cart
user3:product-page
user2:confirmation
EOF
```

### Step 3: Windowed Count (Pseudo-code)

```java
KStream<String, String> clicks = builder.stream("clickstream");

KTable<Windowed<String>, Long> clicksPerWindow = clicks
    .groupByKey()
    .windowedBy(TimeWindows.of(Duration.ofMinutes(1)))
    .count();

clicksPerWindow
    .toStream()
    .map((windowedKey, count) -> 
        new KeyValue<>(windowedKey.key() + "@" + windowedKey.window().start(), count))
    .to("clicks-per-minute");
```

### Step 4: Observe Window Behavior

**Key Concepts:**
- Events within 1-minute window aggregated together
- Window start/end timestamps tracked
- Late data handled based on grace period

---

## Lab 3: Stream-Stream Join (30 min)

### Scenario: Join orders with payments within 5-minute window

### Step 1: Create Topics
```bash
# Orders topic
docker exec kafka-broker-1 kafka-topics --create \
  --bootstrap-server localhost:19092 \
  --topic orders \
  --partitions 3 \
  --replication-factor 2

# Payments topic
docker exec kafka-broker-1 kafka-topics --create \
  --bootstrap-server localhost:19092 \
  --topic payments \
  --partitions 3 \
  --replication-factor 2

# Enriched orders topic
docker exec kafka-broker-1 kafka-topics --create \
  --bootstrap-server localhost:19092 \
  --topic enriched-orders \
  --partitions 3 \
  --replication-factor 2
```

### Step 2: Send Test Data

```bash
# Send orders
docker exec -i kafka-broker-1 kafka-console-producer \
  --bootstrap-server localhost:19092 \
  --topic orders \
  --property "parse.key=true" \
  --property "key.separator=:" << EOF
order1:{"orderId":"order1","amount":100,"customerId":"cust1"}
order2:{"orderId":"order2","amount":200,"customerId":"cust2"}
EOF

# Wait a few seconds, then send payments
sleep 3

docker exec -i kafka-broker-1 kafka-console-producer \
  --bootstrap-server localhost:19092 \
  --topic payments \
  --property "parse.key=true" \
  --property "key.separator=:" << EOF
order1:{"paymentId":"pay1","orderId":"order1","status":"success"}
order2:{"paymentId":"pay2","orderId":"order2","status":"success"}
EOF
```

### Step 3: Stream-Stream Join Logic (Pseudo-code)

```java
KStream<String, String> orders = builder.stream("orders");
KStream<String, String> payments = builder.stream("payments");

KStream<String, String> enriched = orders.join(
    payments,
    (orderValue, paymentValue) -> 
        orderValue + " PAID WITH " + paymentValue,
    JoinWindows.of(Duration.ofMinutes(5))
);

enriched.to("enriched-orders");
```

**Key Concepts:**
- Both streams buffered in 5-minute window
- If payment arrives within 5 minutes of order, they join
- Beyond 5 minutes, no join (or use left join to keep order)

---

## Lab 4: Stream-Table Join (Enrichment) (20 min)

### Scenario: Enrich orders with customer information

### Step 1: Create Topics
```bash
# Customers table (compacted)
docker exec kafka-broker-1 kafka-topics --create \
  --bootstrap-server localhost:19092 \
  --topic customers \
  --partitions 3 \
  --replication-factor 2 \
  --config cleanup.policy=compact

# Orders stream
docker exec kafka-broker-1 kafka-topics --create \
  --bootstrap-server localhost:19092 \
  --topic customer-orders \
  --partitions 3 \
  --replication-factor 2

# Enriched output
docker exec kafka-broker-1 kafka-topics --create \
  --bootstrap-server localhost:19092 \
  --topic enriched-customer-orders \
  --partitions 3 \
  --replication-factor 2
```

### Step 2: Populate Customer Table

```bash
docker exec -i kafka-broker-1 kafka-console-producer \
  --bootstrap-server localhost:19092 \
  --topic customers \
  --property "parse.key=true" \
  --property "key.separator=:" << EOF
cust1:{"name":"Alice","tier":"Gold"}
cust2:{"name":"Bob","tier":"Silver"}
cust3:{"name":"Charlie","tier":"Bronze"}
EOF
```

### Step 3: Send Orders

```bash
docker exec -i kafka-broker-1 kafka-console-producer \
  --bootstrap-server localhost:19092 \
  --topic customer-orders \
  --property "parse.key=true" \
  --property "key.separator=:" << EOF
cust1:{"orderId":"order1","amount":100}
cust2:{"orderId":"order2","amount":200}
cust1:{"orderId":"order3","amount":150}
EOF
```

### Step 4: Join Logic (Pseudo-code)

```java
KTable<String, String> customers = builder.table("customers");
KStream<String, String> orders = builder.stream("customer-orders");

// Enrich orders with customer info (lookup)
KStream<String, String> enriched = orders.join(
    customers,
    (orderValue, customerValue) -> 
        orderValue + " CUSTOMER: " + customerValue
);

enriched.to("enriched-customer-orders");
```

**Result:**
```
order1: {orderId:order1, amount:100} CUSTOMER: {name:Alice, tier:Gold}
order2: {orderId:order2, amount:200} CUSTOMER: {name:Bob, tier:Silver}
order3: {orderId:order3, amount:150} CUSTOMER: {name:Alice, tier:Gold}
```

---

## Lab 5: Kafka Connect - File Source (20 min)

### Step 1: Create Source File

```bash
# Create directory for source files
docker exec kafka-broker-1 mkdir -p /tmp/kafka-connect-source

# Create test file
docker exec kafka-broker-1 bash -c 'cat > /tmp/kafka-connect-source/test.txt << EOF
line one
line two
line three
EOF'
```

### Step 2: Configure File Source Connector

Create `file-source-connector.json`:

```json
{
  "name": "file-source-connector",
  "config": {
    "connector.class": "FileStreamSource",
    "tasks.max": "1",
    "file": "/tmp/kafka-connect-source/test.txt",
    "topic": "file-source-topic"
  }
}
```

### Step 3: Deploy Connector (via REST API)

```bash
# Create topic first
docker exec kafka-broker-1 kafka-topics --create \
  --bootstrap-server localhost:19092 \
  --topic file-source-topic \
  --partitions 1 \
  --replication-factor 1

# Note: For this lab, we'll simulate with console producer
# In production, use Kafka Connect REST API:
# curl -X POST -H "Content-Type: application/json" \
#   --data @file-source-connector.json \
#   http://localhost:8083/connectors
```

### Step 4: Simulate File Source

```bash
# Simulate by producing file content
docker exec -i kafka-broker-1 kafka-console-producer \
  --bootstrap-server localhost:19092 \
  --topic file-source-topic << EOF
line one
line two
line three
EOF
```

### Step 5: Consume Data

```bash
docker exec kafka-broker-1 kafka-console-consumer \
  --bootstrap-server localhost:19092 \
  --topic file-source-topic \
  --from-beginning \
  --timeout-ms 5000
```

---

## Lab 6: Kafka Connect - JDBC Sink (Simulation) (20 min)

### Scenario: Write Kafka data to database

### Step 1: Create Source Topic with Data

```bash
docker exec kafka-broker-1 kafka-topics --create \
  --bootstrap-server localhost:19092 \
  --topic user-events \
  --partitions 3 \
  --replication-factor 2

# Send structured data
docker exec -i kafka-broker-1 kafka-console-producer \
  --bootstrap-server localhost:19092 \
  --topic user-events << EOF
{"userId":"user1","action":"login","timestamp":1234567890}
{"userId":"user2","action":"purchase","timestamp":1234567891}
{"userId":"user1","action":"logout","timestamp":1234567892}
EOF
```

### Step 2: JDBC Sink Configuration (Reference)

```json
{
  "name": "jdbc-sink-connector",
  "config": {
    "connector.class": "io.confluent.connect.jdbc.JdbcSinkConnector",
    "tasks.max": "1",
    "topics": "user-events",
    "connection.url": "jdbc:postgresql://postgres:5432/mydb",
    "connection.user": "postgres",
    "connection.password": "password",
    "auto.create": "true",
    "insert.mode": "insert",
    "table.name.format": "user_events"
  }
}
```

**What it does:**
```
1. Reads from "user-events" topic
2. Parses JSON records
3. Creates table if not exists
4. Inserts rows into PostgreSQL

Result in database:
user_events table:
  userId | action   | timestamp
  -------|----------|----------
  user1  | login    | 1234567890
  user2  | purchase | 1234567891
  user1  | logout   | 1234567892
```

---

## Lab 7: Change Data Capture (CDC) Simulation (30 min)

### Scenario: Capture database changes

### Step 1: Understand CDC Event Structure

A typical Debezium CDC event:

```json
{
  "before": {
    "id": 1,
    "name": "Alice",
    "balance": 100
  },
  "after": {
    "id": 1,
    "name": "Alice",
    "balance": 150
  },
  "source": {
    "version": "1.9.0",
    "connector": "mysql",
    "name": "dbserver1",
    "ts_ms": 1639067481000,
    "db": "inventory",
    "table": "accounts"
  },
  "op": "u",
  "ts_ms": 1639067481743
}
```

### Step 2: Simulate CDC Events

```bash
# Create CDC topic
docker exec kafka-broker-1 kafka-topics --create \
  --bootstrap-server localhost:19092 \
  --topic cdc.inventory.accounts \
  --partitions 3 \
  --replication-factor 2

# Simulate INSERT event
docker exec -i kafka-broker-1 kafka-console-producer \
  --bootstrap-server localhost:19092 \
  --topic cdc.inventory.accounts << 'EOF'
{"before":null,"after":{"id":1,"name":"Alice","balance":100},"op":"c","ts_ms":1234567890}
EOF

# Simulate UPDATE event
docker exec -i kafka-broker-1 kafka-console-producer \
  --bootstrap-server localhost:19092 \
  --topic cdc.inventory.accounts << 'EOF'
{"before":{"id":1,"name":"Alice","balance":100},"after":{"id":1,"name":"Alice","balance":150},"op":"u","ts_ms":1234567900}
EOF

# Simulate DELETE event
docker exec -i kafka-broker-1 kafka-console-producer \
  --bootstrap-server localhost:19092 \
  --topic cdc.inventory.accounts << 'EOF'
{"before":{"id":2,"name":"Bob","balance":200},"after":null,"op":"d","ts_ms":1234567910}
EOF
```

### Step 3: Process CDC Events

Consume and interpret:

```bash
docker exec kafka-broker-1 kafka-console-consumer \
  --bootstrap-server localhost:19092 \
  --topic cdc.inventory.accounts \
  --from-beginning \
  --timeout-ms 10000
```

### Step 4: Build CDC Processor (Pseudo-code)

```java
KStream<String, String> cdcEvents = builder.stream("cdc.inventory.accounts");

// Extract only INSERT and UPDATE (skip DELETE)
KStream<String, String> activeRecords = cdcEvents
    .filter((key, value) -> {
        JsonNode json = parseJson(value);
        String op = json.get("op").asText();
        return op.equals("c") || op.equals("u");
    })
    .mapValues(value -> {
        JsonNode json = parseJson(value);
        return json.get("after").toString();  // Extract "after" state
    });

activeRecords.to("materialized-accounts");
```

**Result:**
```
materialized-accounts topic:
{"id":1,"name":"Alice","balance":100}  (from INSERT)
{"id":1,"name":"Alice","balance":150}  (from UPDATE, replaces previous)
(Bob deleted, not in materialized view)
```

---

## Challenge Exercises

### Challenge 1: Multi-Window Aggregation

**Task:** Count events in:
- 1-minute tumbling window
- 5-minute hopping window (advance 1 minute)
- Compare results

### Challenge 2: Left Join

**Task:** Implement left join of orders with payments
- Keep all orders, even if payment missing
- Mark unpaid orders

### Challenge 3: Stateful Transformation

**Task:** Detect duplicate events within 1-minute window
- Use state store to track seen IDs
- Filter out duplicates

### Challenge 4: CDC to Materialized View

**Task:** Build materialized view from CDC events
- Handle INSERT, UPDATE, DELETE
- Maintain current state in KTable
- Query via Interactive Queries

---

## Verification Checklist

After completing these labs, you should be able to:

- [ ] Explain KStream vs KTable with examples
- [ ] Write stateless transformations (map, filter)
- [ ] Write stateful transformations (aggregate, count)
- [ ] Implement tumbling windows
- [ ] Implement hopping windows
- [ ] Perform stream-stream joins
- [ ] Perform stream-table joins
- [ ] Understand join window semantics
- [ ] Configure Kafka Connect source
- [ ] Configure Kafka Connect sink
- [ ] Parse CDC events
- [ ] Build materialized views from CDC

---

## Key Commands Reference

```bash
# Kafka Streams (conceptual - run in Java)
# See examples in day5-streams.md

# Kafka Connect REST API
curl http://localhost:8083/connectors  # List connectors
curl http://localhost:8083/connectors/<name>/status  # Check status
curl -X DELETE http://localhost:8083/connectors/<name>  # Delete

# CDC Topics (naming pattern)
<connector>.<database>.<table>
Example: debezium.inventory.customers

# Interactive Queries (in Java app)
ReadOnlyKeyValueStore<String, Long> store = 
    streams.store(StoreQueryParameters.fromNameAndType(...));
Long value = store.get("key");
```

---

## Interview Prep from Labs

**Q: "How does windowed aggregation work?"**
→ Show tumbling window lab: "Events grouped by time, window closes, aggregate emitted"

**Q: "Difference between stream-stream and stream-table join?"**
→ "Stream-stream needs window (both unbounded), stream-table is lookup (table is state)"

**Q: "How to handle late data?"**
→ "Grace period in window definition, events within grace period still processed"

**Q: "What is CDC?"**
→ Show CDC lab: "Capture database changes from transaction log, stream to Kafka as events"

**Q: "How to build materialized view from CDC?"**
→ "Process CDC events, maintain KTable with current state, handle INSERT/UPDATE/DELETE"

---

## Next Steps

1. Complete all 7 labs (~3 hours)
2. Try challenge exercises
3. Read day5-streams.md interview questions
4. Implement word count app in Java
5. Experiment with different window sizes

**You're mastering stream processing!** 🚀

