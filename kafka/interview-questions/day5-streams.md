# Day 5: Kafka Streams & Connect - Interview Questions

## 🎯 START HERE - Study Plan (3-4 hours)

### What You'll Learn
- ✅ Kafka Streams API - Build stream processing apps
- ✅ Stateful vs Stateless Operations - When to use each
- ✅ Windowing - Tumbling, hopping, sliding, session windows
- ✅ Joins - Stream-stream, stream-table, table-table
- ✅ Kafka Connect - Sources, sinks, CDC (Change Data Capture)
- ✅ When to use Streams vs Flink/Spark

### How to Study
**Morning (2 hours):** Read questions 1-25 below
**Afternoon (2 hours):** Run hands-on labs (`labs/day5-streams/README.md`)

### Quick Self-Test (Answer these first, then study)
1. What is Kafka Streams?
2. Stateful vs stateless operations?
3. What is windowing?
4. Types of joins in Kafka Streams?
5. What is Kafka Connect?

**After studying, 8/10 correct = Mastered!** ✅

---

## 📋 Quick Reference (Print This!)

**Kafka Streams:**
- Java library for stream processing (not separate cluster!)
- Runs in your application JVM
- Exactly-once semantics support
- State stores for stateful operations

**Operations:**
- Stateless: map, filter, flatMap, branch
- Stateful: aggregate, reduce, join, windowing
- State stored in local RocksDB + changelog topic

**Windowing:**
- Tumbling: Fixed, non-overlapping (e.g., every 5 min)
- Hopping: Fixed, overlapping (e.g., 5 min window, advance 1 min)
- Sliding: Event-based, overlap on event time
- Session: Activity-based, gap timeout

**Joins:**
- Stream-Stream: Both sides unbounded, time window required
- Stream-Table: Stream enrichment with table lookup
- Table-Table: Materialized view, both sides keyed

**Kafka Connect:**
- Framework for moving data in/out of Kafka
- Source connector: DB → Kafka
- Sink connector: Kafka → DB/System
- Change Data Capture (CDC): Debezium, Maxwell

**When to use:**
- Streams: Simple transformations, stateful aggregations, embedded in app
- Flink: Complex CEP, low-latency, large state
- Spark: Batch + streaming, ML integration, large-scale analytics

---

## Kafka Streams Basics

### 1. What is Kafka Streams and how is it different from other stream processing?
**Answer:**

**Kafka Streams** = Java library for building stream processing applications.

**Key Characteristics:**
```
1. Library, not framework
   - Runs inside your application
   - No separate cluster needed
   - Deploy like any Java app

2. Uses Kafka topics for everything
   - Input from Kafka topics
   - Output to Kafka topics
   - State backed by changelog topics

3. Exactly-once semantics
   - Built-in exactly-once processing
   - Automatic state recovery
```

**Architecture:**
```
Traditional (Flink/Spark):
┌─────────────────┐
│ Your App        │
│  ↓              │
│ Submit Job      │
└─────────────────┘
       ↓
┌─────────────────┐
│ Separate Cluster│ ← Need to manage this!
│ (Multiple nodes)│
└─────────────────┘

Kafka Streams:
┌─────────────────┐
│ Your App        │
│ + Streams lib   │ ← Everything in one process!
│ (Embedded)      │
└─────────────────┘
```

**vs Other Frameworks:**

| Feature | Kafka Streams | Flink | Spark Streaming |
|---------|---------------|-------|-----------------|
| **Deployment** | Library (embedded) | Separate cluster | Separate cluster |
| **State** | Local RocksDB | Managed state | RDD/DataFrame |
| **Language** | Java/Scala | Java/Scala/Python | Java/Scala/Python |
| **Latency** | Milliseconds | Milliseconds | Seconds (micro-batch) |
| **Exactly-once** | Built-in | Built-in | Requires config |
| **Complexity** | Low | Medium | Medium-High |
| **Use Case** | Simple transforms, embedded | Complex CEP, large state | Batch + streaming, ML |

**When to use Kafka Streams:**
- ✅ Embedded in microservices
- ✅ Simple transformations/aggregations
- ✅ Don't want separate cluster management
- ✅ State fits in local disk (~100s GB)
- ✅ Input/output both Kafka

**When NOT to use:**
- ❌ Complex event processing (CEP)
- ❌ Very large state (TBs)
- ❌ Need Python/SQL
- ❌ Need ML integration
- ❌ Input from non-Kafka sources

### 2. How does Kafka Streams application work?
**Answer:**

**Basic Flow:**

```
Input Topic → Kafka Streams App → Output Topic

Example:
orders (Kafka) → [Streams App: Count by region] → order-counts (Kafka)
```

**Internal Architecture:**

```
Kafka Streams Application:
┌────────────────────────────────────────┐
│  Stream Tasks (Parallel)               │
│  ┌──────────┐  ┌──────────┐           │
│  │ Task 0   │  │ Task 1   │           │
│  │ Partition│  │ Partition│           │
│  │ 0,2,4    │  │ 1,3,5    │           │
│  │          │  │          │           │
│  │ RocksDB  │  │ RocksDB  │           │
│  │ (state)  │  │ (state)  │           │
│  └──────────┘  └──────────┘           │
└────────────────────────────────────────┘
         ↓                  ↓
    Changelog          Changelog
     Topic              Topic
```

**Key Concepts:**

**1. Stream Task:**
- One task per input partition
- Processes messages from assigned partitions
- Maintains local state (if stateful)

**2. State Store:**
- Local RocksDB database
- Stores intermediate state
- Backed by changelog topic (for recovery)

**3. Changelog Topic:**
- Backup of state store
- Enables recovery after crash
- Compacted topic (latest state per key)

**Example Code:**
```java
// Simple word count
StreamsBuilder builder = new StreamsBuilder();

KStream<String, String> textLines = builder.stream("input-topic");

KTable<String, Long> wordCounts = textLines
    .flatMapValues(line -> Arrays.asList(line.toLowerCase().split(" ")))
    .groupBy((key, word) -> word)
    .count();

wordCounts.toStream().to("output-topic");

KafkaStreams streams = new KafkaStreams(builder.build(), props);
streams.start();
```

**What happens:**
1. Read from "input-topic"
2. Split lines into words
3. Group by word (repartition if needed)
4. Count occurrences (stateful - uses RocksDB)
5. Write to "output-topic"
6. State backed to changelog topic

### 3. What are KStream and KTable?
**Answer:**

Two fundamental abstractions in Kafka Streams.

**KStream (Record Stream):**
```
Unbounded, append-only stream of records
Each record = independent event

Example: User clicks
┌────────────────────────────────────────┐
│ user1 clicked "shoes"    (time: 10:00) │
│ user2 clicked "books"    (time: 10:01) │
│ user1 clicked "laptop"   (time: 10:02) │ ← user1 appears again
└────────────────────────────────────────┘

- All events preserved
- user1 can appear multiple times
- Order matters
```

**KTable (Changelog Stream):**
```
Represents latest state per key
Each record = update to state

Example: User profiles
┌────────────────────────────────────────┐
│ user1: {name: "Alice", age: 25}        │
│ user2: {name: "Bob", age: 30}          │
│ user1: {name: "Alice", age: 26}        │ ← Updates user1
└────────────────────────────────────────┘

After consuming:
user1 → {name: "Alice", age: 26}  (latest only)
user2 → {name: "Bob", age: 30}

- Only latest value per key kept
- Like database table
- Updates overwrite
```

**Comparison:**

| Feature | KStream | KTable |
|---------|---------|--------|
| **Semantics** | Insert | Upsert (Update/Insert) |
| **History** | All events | Latest only |
| **Example** | Clicks, orders, logs | User profiles, balances |
| **null value** | Valid record | Tombstone (delete) |

**Duality (KStream ↔ KTable):**
```java
// KStream → KTable (aggregate)
KTable<String, Long> counts = stream
    .groupByKey()
    .count();

// KTable → KStream (changelog)
KStream<String, Long> changes = table.toStream();
```

**Real-World Example:**

```
Banking scenario:

Transactions (KStream):
  account123: +100  (10:00)
  account456: +50   (10:01)
  account123: -30   (10:02)
  account123: +20   (10:03)
  
Balances (KTable):
  account123 → aggregate transactions → balance: 90
  account456 → aggregate transactions → balance: 50
  
KStream preserves all transactions (audit)
KTable shows current balance (query)
```

---

## Stateful vs Stateless Operations

### 4. What are stateless operations in Kafka Streams?
**Answer:**

Operations that process each record **independently**, no memory of previous records.

**Common Stateless Operations:**

**1. map / mapValues**
```java
// Transform each record
stream.mapValues(value -> value.toUpperCase())

Example:
Input:  "hello" → Output: "HELLO"
Input:  "world" → Output: "WORLD"
(No state needed)
```

**2. filter**
```java
// Keep only matching records
stream.filter((key, value) -> value.length() > 5)

Input:  "hi"     → Filtered out
Input:  "hello"  → Kept
(Decision based on current record only)
```

**3. flatMap / flatMapValues**
```java
// One input → Multiple outputs
stream.flatMapValues(line -> Arrays.asList(line.split(" ")))

Input:  "hello world"
Output: ["hello", "world"]
(No state needed)
```

**4. branch**
```java
// Split stream into multiple based on predicates
stream.split()
    .branch((key, value) -> value > 100, Branched.as("high"))
    .branch((key, value) -> value <= 100, Branched.as("low"));
```

**5. selectKey**
```java
// Change the key
stream.selectKey((key, value) -> value.userId)
```

**Characteristics:**
- ✅ No local state required
- ✅ No RocksDB needed
- ✅ Fast (just in-memory processing)
- ✅ Easy to scale (no state to transfer)
- ✅ Easy to restart (no state to recover)

### 5. What are stateful operations in Kafka Streams?
**Answer:**

Operations that maintain **state across multiple records**.

**Common Stateful Operations:**

**1. aggregate**
```java
// Accumulate values
stream
    .groupByKey()
    .aggregate(
        () -> 0,  // Initial value
        (key, value, aggregate) -> aggregate + value  // Aggregator
    )

Example:
Record 1: user1, amount=100 → state[user1] = 100
Record 2: user1, amount=50  → state[user1] = 150
Record 3: user1, amount=25  → state[user1] = 175

Requires state to remember 150!
```

**2. reduce**
```java
// Combine values
stream
    .groupByKey()
    .reduce((value1, value2) -> value1 + value2)
```

**3. count**
```java
// Count records per key
stream
    .groupByKey()
    .count()

Requires state to remember count!
```

**4. join**
```java
// Join two streams
stream1.join(stream2, 
    (value1, value2) -> value1 + value2,
    JoinWindows.of(Duration.ofMinutes(5))
)

Requires buffering records in window!
```

**5. windowed aggregations**
```java
stream
    .groupByKey()
    .windowedBy(TimeWindows.of(Duration.ofMinutes(5)))
    .count()

Requires state per window!
```

**State Management:**

```
Stateful Operation:
┌─────────────────────────────────────┐
│ Incoming Record                     │
└─────────────────────────────────────┘
           ↓
┌─────────────────────────────────────┐
│ Read current state from RocksDB     │
└─────────────────────────────────────┘
           ↓
┌─────────────────────────────────────┐
│ Compute new state                   │
│ (aggregate, reduce, etc.)           │
└─────────────────────────────────────┘
           ↓
┌─────────────────────────────────────┐
│ Write new state to:                 │
│ 1. Local RocksDB                    │
│ 2. Changelog topic (backup)         │
└─────────────────────────────────────┘
```

**Characteristics:**
- ❌ Requires local disk (RocksDB)
- ❌ Slower (disk I/O for state)
- ❌ More complex scaling (state transfer)
- ❌ Recovery needed on restart
- ✅ Enables powerful aggregations

**State Store Types:**
```java
// 1. Key-Value Store (default)
StoreBuilder<KeyValueStore<String, Long>> storeBuilder = 
    Stores.keyValueStoreBuilder(
        Stores.persistentKeyValueStore("my-store"),
        Serdes.String(),
        Serdes.Long()
    );

// 2. Window Store (for windowed operations)
StoreBuilder<WindowStore<String, Long>> windowStore = 
    Stores.windowStoreBuilder(
        Stores.persistentWindowStore("window-store", 
            Duration.ofHours(1), 
            Duration.ofMinutes(10), 
            false),
        Serdes.String(),
        Serdes.Long()
    );

// 3. Session Store (for session windows)
```

---

## Windowing

### 6. What is windowing in stream processing?
**Answer:**

**Windowing** = Grouping unbounded stream into finite chunks based on time.

**Why needed?**
```
Problem: How to count events "per hour" in infinite stream?

Without windowing:
∞ events → Can never finish counting!

With windowing:
[10:00-11:00]: 100 events ✓
[11:00-12:00]: 150 events ✓
[12:00-13:00]: 120 events ✓
Each window finite and computable!
```

**Window Types in Kafka Streams:**

**1. Tumbling Window (Fixed, Non-overlapping)**
```
Size: 5 minutes
Advance: 5 minutes (same as size)

Timeline:
|--W1--|--W2--|--W3--|--W4--|
10:00  10:05  10:10  10:15  10:20

Event at 10:03 → Goes to W1 only
Event at 10:07 → Goes to W2 only

- Fixed size
- No overlap
- No gaps
```

**2. Hopping Window (Fixed, Overlapping)**
```
Size: 10 minutes
Advance: 5 minutes

Timeline:
|----W1----|
     |----W2----|
          |----W3----|
10:00    10:05    10:10    10:15

Event at 10:07 → Goes to W1 AND W2
Event at 10:12 → Goes to W2 AND W3

- Fixed size
- Overlap controlled by advance interval
- One event can be in multiple windows
```

**3. Sliding Window (Event-time based)**
```
Size: 10 minutes
Triggered on each event within time difference

Used for joins, not aggregations

- Window slides with each event
- Continuous overlap
```

**4. Session Window (Activity-based)**
```
Inactivity gap: 5 minutes

User events:
10:00 → Start session
10:02 → Same session
10:03 → Same session
10:10 → New session (gap > 5 min)

Session 1: [10:00 - 10:03]
Session 2: [10:10 - ...]

- Variable size
- Defined by inactivity gap
- Good for user sessions
```

**Code Examples:**

```java
// 1. Tumbling Window - Count per 5 minutes
stream
    .groupByKey()
    .windowedBy(TimeWindows.of(Duration.ofMinutes(5)))
    .count();

// 2. Hopping Window - Count per 10 min, advance 5 min
stream
    .groupByKey()
    .windowedBy(TimeWindows.of(Duration.ofMinutes(10))
                           .advanceBy(Duration.ofMinutes(5)))
    .count();

// 3. Session Window - Group by user session, 30 min inactivity
stream
    .groupByKey()
    .windowedBy(SessionWindows.with(Duration.ofMinutes(30)))
    .count();
```

### 7. Event time vs Processing time - which to use?
**Answer:**

**Event Time** = When event actually happened (timestamp in event)
**Processing Time** = When event processed by stream application

**Example:**

```
Event: User clicked at 10:00 AM (event time)
       Arrives at Kafka at 10:01 AM (ingestion)
       Processed by Streams at 10:05 AM (processing time)
       
Event time: 10:00
Processing time: 10:05
Difference: 5 minutes lag
```

**When to use Event Time:**
```
✅ Business logic based on when event occurred
✅ Late arriving data common
✅ Reprocessing historical data
✅ Deterministic results

Example: "Sales per hour"
- Must use event time (when sale happened)
- Not processing time (when we processed it)
```

**When to use Processing Time:**
```
✅ Low-latency monitoring
✅ Don't care about late data
✅ Simpler (no watermarks needed)

Example: "System health checks"
- Current CPU usage
- Processing time is fine
```

**Configuration:**

```java
// Event time (default in Kafka Streams)
props.put(StreamsConfig.TIMESTAMP_EXTRACTOR_CLASS_CONFIG, 
    WallclockTimestampExtractor.class);

// Custom timestamp extractor
class OrderTimestampExtractor implements TimestampExtractor {
    public long extract(ConsumerRecord<Object, Object> record, 
                       long previousTimestamp) {
        Order order = (Order) record.value();
        return order.orderTime;  // Use business timestamp
    }
}
```

**Handling Late Data:**

```java
// Grace period - allow late data for 1 hour
stream
    .groupByKey()
    .windowedBy(
        TimeWindows.of(Duration.ofMinutes(5))
                   .grace(Duration.ofHours(1))  // Accept data up to 1hr late
    )
    .count();

Example:
Window: [10:00 - 10:05]
Event arrives at 10:30 with timestamp 10:03
→ Still counted (within 1hr grace period)

Event arrives at 11:10 with timestamp 10:03
→ Dropped (beyond grace period)
```

---

## Joins

### 8. What types of joins exist in Kafka Streams?
**Answer:**

Three main types based on what you're joining.

**1. Stream-Stream Join**

Both sides are unbounded streams. **Must specify time window.**

```java
KStream<String, Order> orders = ...;
KStream<String, Payment> payments = ...;

// Inner join - match within 5 minutes
KStream<String, OrderPayment> result = orders.join(
    payments,
    (order, payment) -> new OrderPayment(order, payment),
    JoinWindows.of(Duration.ofMinutes(5))
);
```

**How it works:**
```
Time window: 5 minutes

Orders stream:
10:00 → order1
10:03 → order2

Payments stream:
10:02 → payment-for-order1  ✓ Joined (within 5 min of 10:00)
10:09 → payment-for-order2  ✗ Not joined (> 5 min from 10:03)

Both streams buffered in window!
```

**2. Stream-Table Join**

Stream (unbounded) joined with Table (latest state). **No time window needed.**

```java
KStream<String, Order> orders = ...;
KTable<String, Customer> customers = ...;

// Enrich order with customer data
KStream<String, EnrichedOrder> enriched = orders.join(
    customers,
    (order, customer) -> new EnrichedOrder(order, customer)
);
```

**How it works:**
```
Customer Table (latest state):
cust1 → {name: "Alice", tier: "Gold"}
cust2 → {name: "Bob", tier: "Silver"}

Orders Stream:
order1 (cust1) → Lookup cust1 → Enrich with Alice, Gold
order2 (cust2) → Lookup cust2 → Enrich with Bob, Silver

Table provides point-in-time lookup!
```

**3. Table-Table Join**

Two tables (both represent latest state). **Materialized view.**

```java
KTable<String, User> users = ...;
KTable<String, Address> addresses = ...;

// Join user with address
KTable<String, UserAddress> userAddresses = users.join(
    addresses,
    (user, address) -> new UserAddress(user, address)
);
```

**How it works:**
```
Users Table:
user1 → {name: "Alice"}
user2 → {name: "Bob"}

Addresses Table:
user1 → {city: "NYC"}
user2 → {city: "LA"}

Joined Table:
user1 → {name: "Alice", city: "NYC"}
user2 → {name: "Bob", city: "LA"}

Result is also a KTable (updates when either side updates)
```

**Join Types (Inner, Left, Outer):**

| Join Type | Stream-Stream | Stream-Table | Table-Table |
|-----------|---------------|--------------|-------------|
| Inner | ✅ Both match | ✅ Stream has table match | ✅ Both match |
| Left | ✅ Keep left if no match | ✅ Keep stream even if no table | ✅ Keep left even if no right |
| Outer | ✅ Keep both sides | ❌ Not supported | ✅ Keep both sides |

**Example - Left Join:**

```java
// Stream-Table Left Join
orders.leftJoin(
    customers,
    (order, customer) -> customer != null 
        ? new EnrichedOrder(order, customer)
        : new EnrichedOrder(order, null)  // No customer found
);

Result:
order1 (cust1 exists) → Enriched with customer data
order2 (cust999 not found) → Order with null customer
```

### 9. How to handle repartitioning in joins?
**Answer:**

**Repartitioning** = Re-keying data to same key for join.

**When needed:**
```
Join requirement: Both sides must have SAME key

Example:
Orders keyed by orderId
Customers keyed by customerId

To join: Need to re-key orders by customerId!
```

**Automatic Repartitioning:**

```java
KStream<String, Order> orders = ...;  // Key: orderId
KTable<String, Customer> customers = ...;  // Key: customerId

// Re-key orders by customerId (automatic repartitioning)
KStream<String, Order> reKeyed = orders
    .selectKey((orderId, order) -> order.customerId);

// Now can join
reKeyed.join(customers, ...)
```

**What happens internally:**
```
1. selectKey triggers repartition
2. Kafka Streams creates internal topic: 
   app-id-KSTREAM-KEY-SELECT-0000000001-repartition
3. Writes re-keyed data to internal topic
4. Reads from internal topic for join
5. Data now co-located by customerId
```

**Cost of Repartitioning:**
```
- Extra write to Kafka (internal topic)
- Extra read from Kafka
- Network overhead
- Latency increase

Minimize by:
- Producing data with correct keys upfront
- Batch repartitioning (don't do multiple times)
```

**Co-partitioning Requirement:**

```
For stream-stream and table-table joins:
- Both topics must have SAME number of partitions
- SAME partitioning strategy
- Records with same key go to same partition number

Example:
orders topic: 10 partitions, key: customerId, hash partitioner
customers topic: 10 partitions, key: customerId, hash partitioner
→ Co-partitioned ✓

orders topic: 10 partitions
customers topic: 5 partitions
→ NOT co-partitioned ✗ (will fail!)
```

**Optimization:**

```java
// Bad: Multiple repartitions
stream
    .selectKey(...)  // Repartition 1
    .groupByKey()
    .aggregate(...)
    .toStream()
    .selectKey(...)  // Repartition 2 (avoid!)

// Good: Single repartition
stream
    .selectKey(...)  // Repartition 1
    .groupByKey()
    .aggregate(...)  
    // Keep same key for downstream operations
```

---

## Kafka Connect

### 10. What is Kafka Connect?
**Answer:**

**Kafka Connect** = Framework for moving data between Kafka and external systems.

**Architecture:**

```
Source Systems → Source Connector → Kafka
Kafka → Sink Connector → Target Systems

Examples:
MySQL → Debezium → Kafka → JDBC Sink → PostgreSQL
Files → File Source → Kafka → S3 Sink → AWS S3
```

**Key Concepts:**

**1. Connector:**
- Plugin that defines HOW to connect to system
- Reusable across deployments
- Pre-built for common systems (DB, S3, etc.)

**2. Task:**
- Unit of work that actually moves data
- Connector splits work into tasks
- Tasks run in parallel

**3. Worker:**
- Process that runs connectors and tasks
- Standalone or distributed mode

**Architecture:**

```
Kafka Connect Cluster:
┌────────────────────────────────────┐
│ Worker 1                           │
│  ├─ Connector A                    │
│  │   ├─ Task 0                     │
│  │   └─ Task 1                     │
│  └─ Connector B                    │
│      └─ Task 0                     │
└────────────────────────────────────┘
┌────────────────────────────────────┐
│ Worker 2                           │
│  ├─ Connector A                    │
│  │   └─ Task 2                     │
│  └─ Connector C                    │
│      ├─ Task 0                     │
│      └─ Task 1                     │
└────────────────────────────────────┘
```

**Source Connector Example:**

```json
{
  "name": "mysql-source",
  "config": {
    "connector.class": "io.debezium.connector.mysql.MySqlConnector",
    "database.hostname": "mysql",
    "database.port": "3306",
    "database.user": "debezium",
    "database.password": "dbz",
    "database.server.id": "184054",
    "database.server.name": "dbserver1",
    "database.include.list": "inventory",
    "table.include.list": "inventory.customers,inventory.orders",
    "database.history.kafka.bootstrap.servers": "kafka:9092",
    "database.history.kafka.topic": "schema-changes"
  }
}
```

**Sink Connector Example:**

```json
{
  "name": "elasticsearch-sink",
  "config": {
    "connector.class": "io.confluent.connect.elasticsearch.ElasticsearchSinkConnector",
    "topics": "orders,customers",
    "connection.url": "http://elasticsearch:9200",
    "type.name": "_doc",
    "key.ignore": "false"
  }
}
```

### 11. Source vs Sink connectors - how do they work?
**Answer:**

**Source Connector (External → Kafka):**

Pulls data from external system and writes to Kafka.

**Flow:**
```
1. Poll external system (DB, file, API)
2. Convert to Kafka records (SourceRecord)
3. Write to Kafka topic
4. Track offset/position
```

**Example - Database Source:**
```
MySQL Database
  ↓ (Debezium CDC)
Read binlog changes
  ↓
Convert to Kafka records:
  {
    "before": {"id": 1, "name": "Alice"},
    "after": {"id": 1, "name": "Alice Smith"},
    "op": "u"  (update)
  }
  ↓
Write to Kafka topic: mysql.inventory.customers
```

**Common Source Connectors:**
- **Debezium** - CDC from databases (MySQL, Postgres, MongoDB)
- **JDBC Source** - Poll databases with SQL queries
- **File Source** - Read from files
- **HTTP Source** - Poll REST APIs
- **S3 Source** - Read from S3 buckets

**Sink Connector (Kafka → External):**

Reads from Kafka and writes to external system.

**Flow:**
```
1. Read from Kafka topic
2. Transform records (optional)
3. Write to external system
4. Commit offsets
```

**Example - Elasticsearch Sink:**
```
Kafka Topic: user-events
  ↓
Read records
  ↓
Convert to Elasticsearch documents
  ↓
Bulk index to Elasticsearch
  ↓
Commit Kafka offsets
```

**Common Sink Connectors:**
- **JDBC Sink** - Write to databases
- **Elasticsearch Sink** - Index to Elasticsearch
- **S3 Sink** - Write to S3 (Parquet, JSON)
- **HDFS Sink** - Write to Hadoop
- **HTTP Sink** - POST to REST APIs

**Connector Development:**

```java
// Source Connector (simplified)
public class MySourceConnector extends SourceConnector {
    @Override
    public void start(Map<String, String> props) {
        // Initialize connector
    }
    
    @Override
    public List<Map<String, String>> taskConfigs(int maxTasks) {
        // Split work into tasks
    }
}

public class MySourceTask extends SourceTask {
    @Override
    public List<SourceRecord> poll() {
        // Read from external system
        // Return records to write to Kafka
    }
}
```

### 12. What is CDC (Change Data Capture)?
**Answer:**

**CDC** = Capturing changes from database and streaming to Kafka in real-time.

**Traditional Approach (Polling):**
```sql
-- Poll every minute
SELECT * FROM users WHERE updated_at > last_check_time

Problems:
- Misses deletes (no record!)
- Adds load to database
- Polling delay (not real-time)
- Can't detect all changes
```

**CDC Approach (Streaming):**
```
Read database transaction log (binlog, WAL)
→ Capture every INSERT, UPDATE, DELETE
→ Stream to Kafka in real-time

Benefits:
✅ Real-time (millisecond latency)
✅ Captures all changes (including deletes)
✅ Low database overhead (read-only on logs)
✅ Complete history
```

**How It Works (Debezium Example):**

```
MySQL Database:
  ↓
Transaction Log (binlog):
  pos:1000 → INSERT INTO users VALUES (1, 'Alice')
  pos:1001 → UPDATE users SET name='Alice Smith' WHERE id=1
  pos:1002 → DELETE FROM users WHERE id=2
  ↓
Debezium Connector:
  - Reads binlog
  - Converts to events
  ↓
Kafka Topics:
  mysql.mydb.users:
    {
      "before": null,
      "after": {"id": 1, "name": "Alice"},
      "op": "c"  // create
    }
    {
      "before": {"id": 1, "name": "Alice"},
      "after": {"id": 1, "name": "Alice Smith"},
      "op": "u"  // update
    }
    {
      "before": {"id": 2, "name": "Bob"},
      "after": null,
      "op": "d"  // delete
    }
```

**Event Structure:**

```json
{
  "before": {/* state before change */},
  "after": {/* state after change */},
  "source": {
    "version": "1.9.0",
    "connector": "mysql",
    "name": "dbserver1",
    "ts_ms": 1639067481000,
    "db": "inventory",
    "table": "customers",
    "file": "mysql-bin.000003",
    "pos": 154,
    "row": 0
  },
  "op": "c",  // c=create, u=update, d=delete, r=read (snapshot)
  "ts_ms": 1639067481743
}
```

**Use Cases:**

1. **Database Replication**
```
PostgreSQL → CDC → Kafka → MySQL
Real-time replica without custom code
```

2. **Event Sourcing**
```
Database changes → Kafka → Event store
Complete audit trail of all changes
```

3. **Cache Invalidation**
```
MySQL → CDC → Kafka → Redis
Invalidate cache when DB updates
```

4. **Data Warehouse ETL**
```
OLTP DB → CDC → Kafka → Data Warehouse
Real-time analytics pipeline
```

**Popular CDC Tools:**

- **Debezium** - Open source, supports MySQL, Postgres, MongoDB, SQL Server
- **Maxwell** - MySQL CDC (simpler than Debezium)
- **Oracle GoldenGate** - Enterprise CDC
- **AWS DMS** - Managed CDC service

**Configuration Example:**

```json
{
  "name": "inventory-connector",
  "config": {
    "connector.class": "io.debezium.connector.mysql.MySqlConnector",
    "tasks.max": "1",
    "database.hostname": "mysql",
    "database.port": "3306",
    "database.user": "debezium",
    "database.password": "dbz",
    "database.server.id": "184054",
    "database.server.name": "dbserver1",
    "database.include.list": "inventory",
    "database.history.kafka.bootstrap.servers": "kafka:9092",
    "database.history.kafka.topic": "dbhistory.inventory",
    "include.schema.changes": "true"
  }
}
```

---

## Advanced Topics

### 13. How to achieve exactly-once semantics in Kafka Streams?
**Answer:**

Kafka Streams supports **exactly-once semantics (EOS)** out of the box.

**Configuration:**

```java
props.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, 
    StreamsConfig.EXACTLY_ONCE_V2);  // Recommended

// Or legacy
props.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, 
    StreamsConfig.EXACTLY_ONCE);

// Default (at-least-once)
// StreamsConfig.AT_LEAST_ONCE
```

**How It Works:**

Combines three mechanisms:

**1. Idempotent Producer**
```
Producer sends same message multiple times
→ Broker deduplicates using (ProducerID, SequenceNumber)
→ Exactly-once write to Kafka
```

**2. Transactional Writes**
```
Read from input → Process → Write to output + commit offset
All in ONE transaction

Either all succeed or all fail (atomic)
```

**3. Read Committed Isolation**
```
Consumers only see committed records
Uncommitted records invisible
```

**Flow:**

```
Kafka Streams App (Exactly-Once):

1. Begin transaction
2. Read from input topic (offset 100)
3. Process record
4. Write to output topic
5. Write to state store changelog
6. Write offset commit (offset 101) to __consumer_offsets
7. Commit transaction

If crash between steps 4-6:
→ Transaction aborted
→ Nothing visible to downstream
→ Restart from offset 100
→ Reprocess (but idempotent, so no duplicates)
```

**Example:**

```java
Properties props = new Properties();
props.put(StreamsConfig.APPLICATION_ID_CONFIG, "word-count");
props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
props.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, 
    StreamsConfig.EXACTLY_ONCE_V2);  // Enable EOS

StreamsBuilder builder = new StreamsBuilder();
KStream<String, String> source = builder.stream("input");

source
    .flatMapValues(value -> Arrays.asList(value.split(" ")))
    .groupBy((key, word) -> word)
    .count()
    .toStream()
    .to("output");

KafkaStreams streams = new KafkaStreams(builder.build(), props);
streams.start();

// This app has exactly-once guarantees:
// - Each input record processed exactly once
// - Each output record written exactly once
// - State updated exactly once
```

**Trade-offs:**

| Feature | At-Least-Once | Exactly-Once |
|---------|---------------|--------------|
| **Throughput** | Higher | Lower (~20% overhead) |
| **Latency** | Lower | Higher (transaction commits) |
| **Guarantees** | May duplicate | No duplicates |
| **Use When** | Idempotent processing | Non-idempotent, critical data |

**When to use Exactly-Once:**
- ✅ Financial transactions
- ✅ Billing/metering
- ✅ Non-idempotent operations
- ✅ Aggregations (counts, sums)

**When At-Least-Once is fine:**
- ✅ Idempotent operations (set user status)
- ✅ High-throughput logging
- ✅ Can tolerate duplicates

### 14. How to handle application state and recovery?
**Answer:**

Kafka Streams manages state automatically using **local state stores + changelog topics**.

**State Store Architecture:**

```
Streams Application Instance:
┌────────────────────────────────────┐
│ Stream Tasks                       │
│  ├─ Task 0 (Partitions 0,2,4)     │
│  │   └─ RocksDB Store             │ ← Local state
│  └─ Task 1 (Partitions 1,3,5)     │
│      └─ RocksDB Store             │ ← Local state
└────────────────────────────────────┘
         ↓ Writes                ↑ Reads
    Changelog Topics           (on recovery)
```

**State Store Types:**

**1. Persistent State Store (RocksDB)**
```java
// Survives restarts (on disk)
StoreBuilder<KeyValueStore<String, Long>> storeBuilder = 
    Stores.keyValueStoreBuilder(
        Stores.persistentKeyValueStore("counts"),
        Serdes.String(),
        Serdes.Long()
    );
```

**2. In-Memory State Store**
```java
// Lost on restart (faster but volatile)
StoreBuilder<KeyValueStore<String, Long>> storeBuilder = 
    Stores.keyValueStoreBuilder(
        Stores.inMemoryKeyValueStore("counts"),
        Serdes.String(),
        Serdes.Long()
    );
```

**Changelog Topics:**

Every stateful operation creates a **changelog topic** (backup).

```
Application: word-count-app
State store: counts

Changelog topic: word-count-app-counts-changelog

State changes:
"hello" → 1   (written to RocksDB + changelog)
"world" → 1   (written to RocksDB + changelog)
"hello" → 2   (written to RocksDB + changelog)

Changelog is compacted topic (latest state per key)
```

**Recovery Process:**

**Scenario: Application crashes and restarts**

```
1. Old instance:
   - Was processing partitions 0,2,4
   - Had state in local RocksDB
   ✗ Crashes, state on disk may be corrupted

2. New instance starts:
   - Assigned partitions 0,2,4
   - Checks local RocksDB
   
3. If local state corrupt or missing:
   → Restore from changelog topic
   → Read entire changelog (compacted, so fast)
   → Rebuild RocksDB state
   
4. Once restored:
   → Resume processing from last committed offset
```

**Standby Replicas:**

For faster recovery, configure standby replicas:

```java
props.put(StreamsConfig.NUM_STANDBY_REPLICAS_CONFIG, 1);

// Now have:
Active Task (Partition 0) on Instance 1 with RocksDB
Standby Task (Partition 0) on Instance 2 with RocksDB

If Instance 1 fails:
→ Instance 2 already has warm state!
→ Much faster recovery (no restore from changelog)
```

**State Directory:**

```java
props.put(StreamsConfig.STATE_DIR_CONFIG, "/var/lib/kafka-streams");

Directory structure:
/var/lib/kafka-streams/
  word-count-app/
    0_0/           ← Task ID
      rocksdb/     ← RocksDB files
      checkpoint   ← Offset checkpoint
    1_0/
      rocksdb/
      checkpoint
```

**Interactive Queries:**

Query state store from running application:

```java
// Get state store
ReadOnlyKeyValueStore<String, Long> store = 
    streams.store(
        StoreQueryParameters.fromNameAndType(
            "counts",
            QueryableStoreTypes.keyValueStore()
        )
    );

// Query
Long count = store.get("hello");
System.out.println("Count for 'hello': " + count);

// Useful for:
- Building REST APIs on top of state
- Dashboards
- Ad-hoc queries
```

### 15. Kafka Streams vs Flink vs Spark Streaming - when to use each?
**Answer:**

Detailed comparison for choosing the right tool.

**Kafka Streams:**

✅ **Use when:**
- Embedded in microservices/applications
- Input/output both Kafka
- State fits on local disk (< TB)
- Want operational simplicity (no separate cluster)
- Java/Scala applications

❌ **Don't use when:**
- Need Python/SQL
- Very large state (multiple TBs)
- Complex CEP (Complex Event Processing)
- Non-Kafka sources

**Example:**
```
Microservice needs to:
- Read from Kafka topic
- Transform/aggregate
- Write back to Kafka
→ Perfect for Kafka Streams!
```

---

**Apache Flink:**

✅ **Use when:**
- Complex event processing (CEP)
- Very low latency (<10ms)
- Large state (TBs)
- Advanced windowing
- Need SQL/Table API
- Batch + streaming unified

❌ **Don't use when:**
- Simple transformations (overkill)
- Small scale
- Don't want separate cluster
- Only Java developers

**Example:**
```
Fraud detection:
- Complex patterns across multiple streams
- Low-latency alerts (<100ms)
- Large state (user profiles, transaction history)
→ Flink is ideal!
```

---

**Spark Streaming (Structured Streaming):**

✅ **Use when:**
- Already using Spark ecosystem
- Need ML integration (MLlib)
- Batch + streaming together
- Large-scale analytics
- Python/SQL preferred

❌ **Don't use when:**
- Need very low latency (< 1 second)
- Stream-only workload
- Want lightweight deployment

**Example:**
```
Data pipeline:
- Ingest from Kafka
- Join with batch data in S3
- Run ML model
- Write to data warehouse
→ Spark Streaming fits!
```

---

**Comparison Table:**

| Feature | Kafka Streams | Flink | Spark Streaming |
|---------|---------------|-------|-----------------|
| **Deployment** | Library (embedded) | Separate cluster | Separate cluster |
| **Latency** | Milliseconds | Sub-millisecond | Seconds (micro-batch) |
| **Throughput** | High | Very high | Very high |
| **State Management** | RocksDB (local) | Managed state backend | RDD/DataFrame |
| **Language** | Java/Scala | Java/Scala/Python/SQL | Java/Scala/Python/SQL |
| **Windowing** | Event-time, All types | Advanced, All types | Event-time, Limited |
| **Exactly-Once** | Built-in | Built-in | Requires config |
| **CEP** | Limited | Excellent | Limited |
| **ML Integration** | None | FlinkML | MLlib (excellent) |
| **Learning Curve** | Low | Medium | Medium |
| **Operational Complexity** | Low | Medium-High | Medium-High |
| **Ecosystem** | Kafka-centric | Broad | Very broad (Hadoop) |

**Decision Matrix:**

```
Start here:
├─ Need embedded in app? → Kafka Streams
├─ Need low latency (<10ms)? → Flink
├─ Need ML integration? → Spark
├─ Need SQL? → Flink or Spark
└─ Simple use case? → Kafka Streams

Scale:
├─ Small (< 100 MB/s) → Kafka Streams
├─ Medium (100 MB/s - 1 GB/s) → Any
└─ Large (> 1 GB/s) → Flink or Spark

State Size:
├─ Small (< 100 GB) → Kafka Streams
├─ Medium (100 GB - 1 TB) → Kafka Streams or Flink
└─ Large (> 1 TB) → Flink

Operational:
├─ Want simplicity → Kafka Streams
└─ Have Ops team → Flink or Spark
```

**Real-World Example:**

```
Company Tech Stack:

Microservice A (Kafka Streams):
  - User activity enrichment
  - Simple aggregations
  - Embedded in service

Fraud Detection (Flink):
  - Complex patterns
  - Low-latency alerts
  - Large state

Analytics Pipeline (Spark):
  - Batch + streaming
  - ML models
  - Data warehouse ETL
  
Use right tool for each job!
```

---

## Quick Interview Answers

1. **"What is Kafka Streams?"**
   → Java library for stream processing, embedded in app, no separate cluster

2. **"KStream vs KTable?"**
   → KStream = all events, KTable = latest state per key

3. **"Stateful vs stateless?"**
   → Stateless = independent processing, Stateful = maintains state (aggregate, join)

4. **"What is windowing?"**
   → Grouping infinite stream into finite time chunks

5. **"Types of joins?"**
   → Stream-Stream (time window), Stream-Table (enrichment), Table-Table (materialized view)

6. **"What is Kafka Connect?"**
   → Framework for moving data in/out of Kafka

7. **"What is CDC?"**
   → Change Data Capture from database transaction logs to Kafka

8. **"Exactly-once in Streams?"**
   → Idempotent producer + transactions + read committed

9. **"When to use Streams vs Flink?"**
   → Streams for embedded/simple, Flink for complex CEP/large state

10. **"How state recovery works?"**
    → Local RocksDB + changelog topic backup

---

**You now understand Kafka Streams & Connect deeply!** 🚀

See `labs/day5-streams/README.md` for hands-on practice.

