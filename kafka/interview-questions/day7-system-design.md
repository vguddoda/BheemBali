# Day 7: System Design & Real-World Architecture - Interview Questions

## 🎯 START HERE - Study Plan (4-5 hours)

### What You'll Learn
- ✅ System Design with Kafka - End-to-end architectures
- ✅ Real-World Use Cases - E-commerce, payments, analytics
- ✅ Capacity Planning - Storage, throughput, partitions
- ✅ Trade-offs & Decisions - When to use Kafka vs alternatives
- ✅ Common Patterns - Event sourcing, CQRS, CDC, saga
- ✅ Interview Scenarios - How to ace system design rounds

### How to Study
**Morning (3 hours):** Read questions 1-25 below
**Afternoon (2 hours):** Practice designing systems on paper

### Quick Self-Test
1. Design a real-time analytics system
2. How to handle payment processing with Kafka?
3. Capacity planning for 1M events/sec?
4. When NOT to use Kafka?
5. Event sourcing vs CQRS?

**After studying, 8/10 correct = Mastered!** ✅

---

## 📋 Quick Reference (Print This!)

**System Design Framework:**
1. Requirements (functional, non-functional)
2. Capacity estimates (QPS, storage, bandwidth)
3. High-level design (components, data flow)
4. Deep dive (bottlenecks, optimizations)
5. Trade-offs (discuss alternatives)

**Kafka Design Patterns:**
- Event Sourcing: Store all state changes as events
- CQRS: Separate read/write models
- Saga: Distributed transactions across services
- CDC: Capture database changes
- Stream Processing: Real-time transformations

**Capacity Planning:**
```
Partitions: (target throughput / partition throughput)
Storage: daily volume × retention days × replication factor
Brokers: partitions / partitions per broker
```

**When to Use Kafka:**
✓ Event streaming, real-time analytics
✓ Log aggregation, metrics collection
✓ Microservices communication
✓ CDC, data integration

**When NOT to Use:**
✗ Request-response (use REST/gRPC)
✗ Low-latency trading (< 1ms)
✗ Small scale (< 1000 msg/sec)
✗ Simple pub-sub (use RabbitMQ/SNS)

---

## System Design Questions

### 1. Design a Real-Time Analytics System
**Question:** Design a system that processes millions of user events per second and provides real-time analytics dashboards.

**Answer:**

**Requirements:**

**Functional:**
- Collect user events (clicks, page views, purchases)
- Process events in real-time
- Aggregate metrics (page views per minute, conversion rate)
- Display on dashboard with < 5 second latency
- Historical queries (last 7 days)

**Non-Functional:**
- 1 million events/second peak
- < 5 second end-to-end latency
- 99.9% availability
- No data loss

**High-Level Architecture:**

```
┌──────────────────────────────────────────────────────────────┐
│ Data Collection Layer                                        │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  Web Apps     Mobile Apps     Backend Services              │
│     │              │                  │                      │
│     └──────────────┴──────────────────┘                      │
│                    │                                         │
│              ┌─────▼─────┐                                   │
│              │   CDN/     │  (Collect events)                │
│              │  Gateway   │                                  │
│              └─────┬─────┘                                   │
└────────────────────┼──────────────────────────────────────────┘
                     │
┌────────────────────▼──────────────────────────────────────────┐
│ Stream Ingestion Layer                                        │
├────────────────────────────────────────────────────────────────┤
│              ┌─────────────┐                                  │
│              │   Kafka     │  Topics:                         │
│              │   Cluster   │  - raw-events                    │
│              │  (10 nodes) │  - processed-events              │
│              └──────┬──────┘  - aggregated-metrics            │
└─────────────────────┼─────────────────────────────────────────┘
                      │
        ┌─────────────┼─────────────┐
        │             │             │
┌───────▼────┐  ┌────▼─────┐  ┌───▼──────┐
│ Processing │  │  Kafka   │  │  Stream  │
│   Layer    │  │ Streams  │  │  Join    │
├────────────┤  │ (ETL)    │  │  Service │
│            │  └──────────┘  └──────────┘
│ - Filter   │       │              │
│ - Enrich   │       │              │
│ - Validate │       │              │
└────────────┘       │              │
                     │              │
        ┌────────────┴──────────────┘
        │
┌───────▼─────────────────────────────────────────────────┐
│ Storage & Serving Layer                                 │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌──────────────┐    ┌──────────────┐   ┌──────────┐  │
│  │ TimeSeries DB│    │ Elasticsearch│   │  Redis   │  │
│  │ (InfluxDB)   │    │ (Search)     │   │ (Cache)  │  │
│  │              │    │              │   │          │  │
│  │ - Metrics    │    │ - Events     │   │ - Hot    │  │
│  │ - Aggregates │    │ - Full-text  │   │   Data   │  │
│  └──────────────┘    └──────────────┘   └──────────┘  │
└─────────────────────────────────────────────────────────┘
                          │
┌─────────────────────────▼────────────────────────────┐
│ Presentation Layer                                   │
├──────────────────────────────────────────────────────┤
│  ┌──────────────┐         ┌──────────────┐          │
│  │   Grafana    │         │   Custom     │          │
│  │  Dashboard   │         │   Web App    │          │
│  └──────────────┘         └──────────────┘          │
└──────────────────────────────────────────────────────┘
```

**Component Details:**

**1. Data Collection (1M events/sec):**
```javascript
// Client-side tracking
analytics.track('page_view', {
  page: '/products/123',
  userId: 'user-456',
  timestamp: Date.now()
});

// Gateway batches and sends to Kafka
POST /events/batch
{
  "events": [...100 events...]
}
```

**2. Kafka Topics:**
```
raw-events:
- 30 partitions (for parallelism)
- Retention: 7 days
- RF: 3
- Key: userId (for ordering per user)

processed-events:
- 30 partitions
- Retention: 7 days
- Enriched with user metadata

aggregated-metrics:
- 10 partitions
- Compacted (latest metrics per key)
- Key: metric_name + time_bucket
```

**3. Stream Processing (Kafka Streams):**
```java
StreamsBuilder builder = new StreamsBuilder();

// Read raw events
KStream<String, Event> events = builder.stream("raw-events");

// Real-time aggregation: page views per minute
KTable<Windowed<String>, Long> pageViewsPerMinute = events
    .filter((key, event) -> "page_view".equals(event.getType()))
    .groupBy((key, event) -> event.getPage())
    .windowedBy(TimeWindows.of(Duration.ofMinutes(1)))
    .count();

// Write to aggregated-metrics topic
pageViewsPerMinute.toStream()
    .map((window, count) -> 
        new KeyValue<>(window.key() + "@" + window.window().start(), count))
    .to("aggregated-metrics");

// Enrich with user data (stream-table join)
KTable<String, User> users = builder.table("users");
KStream<String, EnrichedEvent> enriched = events
    .selectKey((k, event) -> event.getUserId())
    .join(users, (event, user) -> 
        new EnrichedEvent(event, user.getSegment(), user.getCountry()));

enriched.to("processed-events");
```

**4. Storage Strategy:**
```
InfluxDB (Time-series):
- Aggregated metrics (page views, conversion rate)
- Fast range queries (last 1 hour, last 24 hours)
- Retention: 30 days

Elasticsearch:
- Full event data (for ad-hoc queries)
- Search and filtering
- Retention: 7 days

Redis:
- Hot data cache (last 5 minutes)
- < 10ms query latency
- Real-time dashboard queries
```

**5. API Layer:**
```java
@GetMapping("/analytics/pageviews")
public PageViewStats getPageViews(
    @RequestParam String page,
    @RequestParam long startTime,
    @RequestParam long endTime) {
    
    // Try cache first
    String cacheKey = "pv:" + page + ":" + startTime;
    PageViewStats cached = redis.get(cacheKey);
    if (cached != null) return cached;
    
    // Query InfluxDB
    PageViewStats stats = influxDB.query(
        "SELECT sum(count) FROM page_views " +
        "WHERE page = ? AND time >= ? AND time <= ?",
        page, startTime, endTime
    );
    
    // Cache for 60 seconds
    redis.setex(cacheKey, 60, stats);
    
    return stats;
}
```

**Capacity Planning:**

```
Events: 1M events/sec
Event size: 1 KB average

Throughput:
- Ingress: 1M × 1 KB = 1 GB/sec
- With replication (RF=3): 3 GB/sec
- Network: 25 Gbps minimum

Storage (raw-events, 7 days):
- Daily: 1M × 1KB × 86400 sec = 86.4 TB/day
- 7 days: 86.4 × 7 = 605 TB
- With RF=3: 1.8 PB
- Solution: Use compression (lz4, 70% reduction)
- Final: ~540 TB

Partitions:
- Target: 50 MB/sec per partition
- Required: 1 GB/sec / 50 MB/sec = 20 partitions
- Use 30 partitions (growth buffer)

Brokers:
- Max partitions per broker: 2000
- 30 partitions × 3 RF = 90 total partition replicas
- Minimum: 3 brokers (HA)
- Recommended: 5-10 brokers (load distribution)

Kafka Streams:
- 30 partitions → 30 tasks
- Run 10 instances × 3 tasks each
- Total processing capacity: 10 instances

InfluxDB:
- Aggregated metrics: ~1 GB/day
- 30 days: 30 GB (manageable)

Elasticsearch:
- Full events: 86.4 TB/day
- 7 days: 605 TB
- With compression: ~200 TB
- Cluster: 20 nodes × 10 TB each
```

**Trade-offs Discussed:**

```
1. Kafka vs Kinesis:
   ✓ Kafka: Open source, cheaper, more control
   ✗ Kinesis: Managed, simpler ops
   Decision: Kafka (cost, flexibility)

2. InfluxDB vs Cassandra:
   ✓ InfluxDB: Purpose-built for time-series
   ✗ Cassandra: More general, complex
   Decision: InfluxDB (simpler, faster queries)

3. Processing: Kafka Streams vs Flink:
   ✓ Kafka Streams: Embedded, simpler
   ✗ Flink: Separate cluster, more features
   Decision: Kafka Streams (simple aggregations)

4. Storage: Hot vs Cold:
   ✓ Redis (hot, < 5 min): Fast, expensive
   ✓ InfluxDB (warm, 30 days): Balanced
   ✓ S3 (cold, > 30 days): Cheap, archived
```

---

### 2. Design a Payment Processing System
**Question:** Design a distributed payment processing system that ensures exactly-once processing and handles 10,000 transactions/second.

**Answer:**

**Requirements:**

**Functional:**
- Process payments (credit card, debit, wallet)
- Validate payment details
- Call payment gateway API
- Update order status
- Send notifications
- Handle refunds

**Non-Functional:**
- 10,000 transactions/second
- Exactly-once processing (no double charges!)
- < 3 second end-to-end latency
- 99.99% availability
- Audit trail (compliance)

**Architecture:**

```
┌─────────────────────────────────────────────────────────┐
│ Client Layer                                            │
├─────────────────────────────────────────────────────────┤
│  Web/Mobile Apps → API Gateway → Payment Service       │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────┐
│ Payment Service (Producer)                              │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  POST /payments                                         │
│  {                                                      │
│    "orderId": "order-123",                             │
│    "amount": 99.99,                                    │
│    "method": "credit_card"                             │
│  }                                                      │
│                                                         │
│  1. Validate input                                     │
│  2. Check duplicate (idempotency key)                  │
│  3. Create payment record (DB)                         │
│  4. Send to Kafka (idempotent producer)                │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────┐
│ Kafka Cluster                                           │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  Topic: payment-requests                               │
│  - Partitions: 30 (key: orderId)                       │
│  - RF: 3, min.insync.replicas: 2                       │
│  - Idempotent producer enabled                         │
│  - Transactions enabled                                │
│                                                         │
│  Topic: payment-results                                │
│  Topic: payment-events (audit log)                     │
└────────────────────────┬────────────────────────────────┘
                         │
        ┌────────────────┴────────────────┐
        │                                 │
┌───────▼────────┐             ┌─────────▼────────┐
│ Payment        │             │ Notification     │
│ Processor      │             │ Service          │
│ (Consumer)     │             │                  │
├────────────────┤             └──────────────────┘
│                │
│ 1. Dedup check │
│ 2. Call gateway│
│ 3. Update DB   │
│ 4. Produce     │
│    result      │
└────────────────┘
```

**Exactly-Once Implementation:**

**1. Idempotent Producer:**
```java
Properties props = new Properties();
props.put("bootstrap.servers", "localhost:9092");
props.put("key.serializer", StringSerializer.class);
props.put("value.serializer", JsonSerializer.class);

// Enable idempotence (prevents duplicates)
props.put("enable.idempotence", "true");
props.put("acks", "all");
props.put("retries", Integer.MAX_VALUE);
props.put("max.in.flight.requests.per.connection", 5);

KafkaProducer<String, PaymentRequest> producer = 
    new KafkaProducer<>(props);

// Send with key = orderId (ensures ordering)
ProducerRecord<String, PaymentRequest> record = 
    new ProducerRecord<>("payment-requests", 
        payment.getOrderId(), 
        payment);

producer.send(record, (metadata, exception) -> {
    if (exception != null) {
        log.error("Failed to send payment", exception);
        // Retry logic
    } else {
        log.info("Payment queued at offset {}", metadata.offset());
    }
});
```

**2. Transactional Consumer-Producer:**
```java
// Consumer config
Properties consumerProps = new Properties();
consumerProps.put("bootstrap.servers", "localhost:9092");
consumerProps.put("group.id", "payment-processor");
consumerProps.put("enable.auto.commit", "false");
consumerProps.put("isolation.level", "read_committed");  // Only read committed

KafkaConsumer<String, PaymentRequest> consumer = 
    new KafkaConsumer<>(consumerProps);

// Producer config (transactional)
Properties producerProps = new Properties();
producerProps.put("bootstrap.servers", "localhost:9092");
producerProps.put("enable.idempotence", "true");
producerProps.put("transactional.id", "payment-processor-1");  // Unique per instance

KafkaProducer<String, PaymentResult> producer = 
    new KafkaProducer<>(producerProps);

// Initialize transactions
producer.initTransactions();

while (true) {
    ConsumerRecords<String, PaymentRequest> records = 
        consumer.poll(Duration.ofMillis(100));
    
    // Begin transaction
    producer.beginTransaction();
    
    try {
        for (ConsumerRecord<String, PaymentRequest> record : records) {
            // Process payment
            PaymentResult result = processPayment(record.value());
            
            // Send result (within transaction)
            producer.send(new ProducerRecord<>(
                "payment-results", 
                record.key(), 
                result
            ));
            
            // Update database
            updateDatabase(result);
        }
        
        // Commit offsets + send results atomically
        producer.sendOffsetsToTransaction(
            getOffsets(records), 
            consumer.groupMetadata()
        );
        
        // Commit transaction
        producer.commitTransaction();
        
    } catch (Exception e) {
        // Abort transaction on error
        producer.abortTransaction();
        log.error("Transaction aborted", e);
        // Seek to last committed offset
        consumer.seek(partition, lastCommittedOffset);
    }
}
```

**3. Deduplication:**
```java
public PaymentResult processPayment(PaymentRequest request) {
    String orderId = request.getOrderId();
    
    // Check if already processed (database unique constraint)
    PaymentResult existing = paymentRepository.findByOrderId(orderId);
    if (existing != null) {
        log.info("Payment {} already processed, returning cached result", orderId);
        return existing;  // Idempotent!
    }
    
    // Call payment gateway
    GatewayResponse response = paymentGateway.charge(
        request.getAmount(),
        request.getCardToken()
    );
    
    // Save result (unique constraint on orderId)
    PaymentResult result = new PaymentResult();
    result.setOrderId(orderId);
    result.setStatus(response.getStatus());
    result.setTransactionId(response.getTransactionId());
    result.setTimestamp(System.currentTimeMillis());
    
    paymentRepository.save(result);  // Throws exception if duplicate
    
    return result;
}
```

**4. State Machine for Payment Status:**
```
Payment States:
PENDING → PROCESSING → SUCCESS
               ↓
            FAILED → RETRY (max 3 times) → FAILED_PERMANENT

State transitions stored in:
- Database (source of truth)
- Kafka topic (audit log, event sourcing)
```

**Handling Failures:**

```
Scenario 1: Payment Gateway Timeout
1. Consumer polls payment request
2. Calls gateway → timeout (30 sec)
3. Doesn't commit offset (stays in transaction)
4. Transaction aborted
5. Reprocessed on next poll
6. Gateway idempotency key prevents double charge

Scenario 2: Kafka Broker Fails
1. Producer sends payment request
2. Leader broker fails before replication
3. Producer retries automatically
4. New leader elected
5. Message written to new leader
6. No data loss (acks=all, min.insync.replicas=2)

Scenario 3: Consumer Crashes Mid-Processing
1. Consumer processes payment
2. Calls gateway successfully
3. Crashes before committing
4. Another consumer picks up partition
5. Reprocesses same message
6. Dedup check in processPayment() detects duplicate
7. Returns cached result
8. Commits offset
9. No double charge!
```

**Capacity Planning:**

```
Throughput: 10,000 TPS

Kafka:
- 30 partitions (333 TPS per partition)
- 5 brokers
- Storage (7 days): 10K × 1KB × 86400 × 7 = 6 TB

Consumers:
- 30 partitions → 30 consumer instances (max)
- Use 10 instances × 3 partitions each
- Each processes 1000 TPS

Database:
- PostgreSQL with replication
- 10K writes/sec (payments table)
- 5K writes/sec (audit_log table)
- Use connection pooling (100 connections)

Payment Gateway:
- External API rate limit: 20K TPS
- Plenty of headroom ✓
```

---

### 3. Design an E-Commerce Order System with Kafka
**Question:** Design a complete e-commerce order processing system using event-driven architecture.

**Answer:**

**Components:**

```
┌────────────────────────────────────────────────────────────┐
│                    Event-Driven Architecture                │
└────────────────────────────────────────────────────────────┘

Order Service → order-events → 
    ├→ Inventory Service (reserve items)
    ├→ Payment Service (charge customer)
    ├→ Shipping Service (create shipment)
    ├→ Notification Service (send emails)
    └→ Analytics Service (track metrics)

All services publish their own events:
- inventory-events (reserved, released)
- payment-events (charged, failed, refunded)
- shipping-events (created, shipped, delivered)
```

**Event Flow:**

```
1. Customer Places Order
   ↓
   Order Service publishes:
   Topic: order-events
   Event: OrderCreated
   {
     "orderId": "order-123",
     "customerId": "cust-456",
     "items": [
       {"productId": "prod-789", "quantity": 2}
     ],
     "total": 99.99,
     "status": "PENDING"
   }

2. Inventory Service consumes OrderCreated
   ↓
   Reserves inventory
   ↓
   Publishes: InventoryReserved
   {
     "orderId": "order-123",
     "reserved": true,
     "reservationId": "res-999"
   }

3. Payment Service consumes InventoryReserved
   ↓
   Charges customer
   ↓
   Publishes: PaymentCompleted
   {
     "orderId": "order-123",
     "paid": true,
     "transactionId": "txn-888"
   }

4. Shipping Service consumes PaymentCompleted
   ↓
   Creates shipment
   ↓
   Publishes: ShipmentCreated
   {
     "orderId": "order-123",
     "trackingNumber": "TRK-777"
   }

5. Notification Service consumes all events
   ↓
   Sends email confirmations at each step
```

**Saga Pattern for Distributed Transactions:**

```java
// Orchestration-based Saga
public class OrderSaga {
    
    public void createOrder(Order order) {
        // Step 1: Create order
        orderService.createOrder(order);
        publishEvent("OrderCreated", order);
        
        // Step 2: Reserve inventory (compensatable)
        try {
            inventoryService.reserve(order.getItems());
            publishEvent("InventoryReserved", order);
        } catch (Exception e) {
            // Compensate: Cancel order
            orderService.cancelOrder(order.getId());
            publishEvent("OrderCancelled", order);
            return;
        }
        
        // Step 3: Charge payment (compensatable)
        try {
            paymentService.charge(order.getTotal());
            publishEvent("PaymentCompleted", order);
        } catch (Exception e) {
            // Compensate: Release inventory, cancel order
            inventoryService.release(order.getItems());
            orderService.cancelOrder(order.getId());
            publishEvent("OrderCancelled", order);
            return;
        }
        
        // Step 4: Create shipment
        shippingService.createShipment(order);
        publishEvent("ShipmentCreated", order);
        
        // Step 5: Complete order
        orderService.completeOrder(order.getId());
        publishEvent("OrderCompleted", order);
    }
}
```

**CQRS Pattern - Separate Read/Write Models:**

```
Write Model (Commands):
- OrderService: CreateOrder, CancelOrder, CompleteOrder
- Publishes events to Kafka
- Optimized for writes

Read Model (Queries):
- OrderQueryService: Consumes events
- Builds materialized views in Elasticsearch
- Optimized for reads (search, filter, aggregate)

Example:
Command: POST /orders (write to DB + Kafka)
Query: GET /orders?status=pending (read from Elasticsearch)
```

**Event Sourcing - Store All State Changes:**

```
Traditional:
orders table: {id, status, total}  ← Only current state

Event Sourcing:
order_events table:
- OrderCreated (t=0)
- InventoryReserved (t=1)
- PaymentCompleted (t=2)
- ShipmentCreated (t=3)
- OrderCompleted (t=4)

Replay events = Reconstruct current state
Benefits:
- Complete audit trail
- Time travel (state at any point)
- Debug issues (replay events)
- Build new views (replay into new service)
```

---

## Capacity Planning

### 4. How to plan Kafka cluster capacity?
**Answer:**

**Capacity Planning Framework:**

```
1. Throughput Requirements
2. Storage Requirements
3. Partition Calculation
4. Broker Calculation
5. Network Bandwidth
6. Consumer Group Sizing
```

**Step-by-Step Example:**

**Given:**
- 100,000 messages/second peak
- Average message size: 1 KB
- Retention: 7 days
- Replication factor: 3

**1. Throughput:**
```
Ingress: 100K msg/sec × 1 KB = 100 MB/sec
With RF=3: 100 MB × 3 = 300 MB/sec write
Egress (2 consumer groups): 100 MB/sec × 2 = 200 MB/sec
Total: 500 MB/sec
```

**2. Storage:**
```
Daily: 100K × 1KB × 86400 sec = 8.64 TB/day
7 days: 8.64 × 7 = 60.5 TB
With RF=3: 60.5 × 3 = 181.5 TB

With compression (lz4, 70% reduction):
181.5 × 0.3 = 54.5 TB total storage needed
```

**3. Partitions:**
```
Rule of thumb: 50 MB/sec per partition

Partitions needed: 100 MB/sec / 50 MB/sec = 2 partitions (minimum)

For growth and better parallelism:
Use 10-30 partitions

Choice: 20 partitions
- Each handles 5 MB/sec
- Room for growth
- Good consumer parallelism
```

**4. Brokers:**
```
Constraints:
- Max 2000 partitions per broker (best practice)
- Max 4000 partitions per broker (hard limit)
- Min 3 brokers for HA (RF=3)

Calculation:
- 20 partitions × 3 RF = 60 partition replicas
- 60 / 2000 = 0.03 brokers (way under limit)

Storage per broker: 54.5 TB / 3 brokers = 18.2 TB each

Recommendation: 5 brokers
- Better load distribution
- Easier maintenance (can take 1-2 down)
- Each stores: 54.5 TB / 5 = 10.9 TB
```

**5. Network:**
```
Peak bandwidth: 500 MB/sec = 4 Gbps
With 5 brokers: 4 Gbps / 5 = 800 Mbps per broker

Recommendation: 10 Gbps network cards
- 8x headroom
- Handle bursts
- Other traffic (replication, monitoring)
```

**6. Consumers:**
```
Max parallel consumers: 20 (one per partition)

For 100K msg/sec:
- 20 consumers × 5K msg/sec each

If processing is heavy:
- Use fewer consumers with thread pools
- Example: 5 consumers × 4 partitions each
```

**Hardware Specs:**

```
Per Broker:
- CPU: 16 cores (for network threads, compression)
- RAM: 64 GB (32 GB JVM heap, 32 GB page cache)
- Disk: 12 TB NVMe SSD (fast I/O)
- Network: 10 Gbps
- OS: Linux (Ubuntu/CentOS)

Total Cluster (5 brokers):
- 80 CPU cores
- 320 GB RAM
- 60 TB storage
```

**Cost Estimation:**

```
On-Premise:
- 5 servers × $5,000 each = $25,000
- Network switches, racks = $10,000
- Power, cooling = $500/month
- Total: $35,000 + $6,000/year

AWS (MSK):
- kafka.m5.2xlarge (8 vCPU, 32 GB) × 5 = $3,650/month
- Storage: 60 TB × $0.10/GB = $6,000/month
- Data transfer: 500 MB/sec × 86400 × 30 × $0.09/GB = $11,664/month
- Total: ~$21,000/month = $252,000/year

Decision: On-premise for this scale (cheaper long-term)
```

---

### 5. When NOT to use Kafka?
**Answer:**

**Scenarios Where Kafka is NOT the Best Choice:**

**1. Request-Response Communication:**
```
❌ BAD: Microservice A sends request via Kafka, waits for response
Why bad:
- High latency (Kafka not designed for sync req-res)
- Complexity (correlation IDs, timeouts)
- Awkward pattern

✓ GOOD: Use REST API or gRPC
- Direct communication
- Lower latency
- Simpler error handling
```

**2. Low-Latency Trading (< 1ms):**
```
❌ BAD: High-frequency trading with Kafka
Why bad:
- Kafka latency: 2-10ms typical
- Too slow for HFT (need < 1ms)

✓ GOOD: Use specialized messaging (29West, Solace, ZeroMQ)
- Sub-millisecond latency
- Optimized for low latency
```

**3. Small Scale (< 1000 msg/sec):**
```
❌ BAD: Internal app with 100 msg/sec using Kafka
Why bad:
- Operational overhead too high
- Over-engineering
- Expensive for small scale

✓ GOOD: Use RabbitMQ, Redis Pub/Sub, or AWS SNS/SQS
- Simpler to operate
- Lower overhead
- Cheaper
```

**4. Task Queues with Complex Routing:**
```
❌ BAD: Job queue with priority, delayed delivery, routing rules
Why bad:
- Kafka doesn't support message priority
- No delayed delivery
- Limited routing (only partitioning)

✓ GOOD: Use RabbitMQ, Celery, or AWS SQS
- Priority queues
- Delayed delivery
- Complex routing (exchanges, bindings)
```

**5. Short-Lived Event Streams:**
```
❌ BAD: Temporary event stream for single task
Why bad:
- Kafka topics are persistent
- Overhead of topic management
- Retention cleanup needed

✓ GOOD: Use Redis Streams or in-memory queue
- Temporary
- Auto-cleanup
- Simpler
```

**6. Strong Consistency Requirements:**
```
❌ BAD: Banking ledger requiring immediate consistency
Why bad:
- Kafka is eventually consistent
- Replication lag (milliseconds)
- Not suitable for strict consistency

✓ GOOD: Use relational DB with ACID transactions
- Strong consistency
- Immediate consistency
- ACID guarantees
```

**7. Binary/Large File Distribution:**
```
❌ BAD: Distributing video files via Kafka
Why bad:
- Large messages (MB/GB) overwhelm Kafka
- Not designed for large payloads
- Impacts performance

✓ GOOD: Use object storage (S3) + notification
- Store file in S3
- Send URL/reference via Kafka
- Efficient and scalable
```

**Decision Matrix:**

```
Use Kafka when:
✓ Event streaming (user events, logs, metrics)
✓ Data integration (CDC, ETL)
✓ Microservices communication (async, event-driven)
✓ Real-time analytics (stream processing)
✓ High throughput (> 1K msg/sec)
✓ Replay capability needed
✓ Order preservation important
✓ Multiple consumers reading same data

Use Alternative when:
✗ Request-response pattern → REST/gRPC
✗ Ultra-low latency (< 1ms) → Specialized messaging
✗ Small scale (< 1K msg/sec) → RabbitMQ/SNS
✗ Complex routing/priority → RabbitMQ
✗ Strong consistency → Database
✗ Temporary streams → Redis
✗ Large files → Object storage
```

---

## Interview System Design Tips

### 6. How to approach Kafka system design interviews?
**Answer:**

**Framework (30-45 minutes):**

**1. Requirements (5 minutes):**
```
Ask clarifying questions:
- What's the throughput? (QPS, msg/sec)
- What's the latency requirement? (real-time, batch)
- What's the data volume? (msg size, retention)
- Consistency vs Availability trade-off?
- Who are the consumers? (how many)
```

**2. High-Level Design (10 minutes):**
```
Draw boxes and arrows:
┌──────┐    ┌───────┐    ┌─────────┐    ┌──────┐
│Source│ ──►│ Kafka │ ──►│Processor│ ──►│ Sink │
└──────┘    └───────┘    └─────────┘    └──────┘

Explain data flow:
1. Data collected from sources
2. Sent to Kafka topics
3. Processed by consumers
4. Stored in final destination
```

**3. Deep Dive (15 minutes):**
```
Discuss critical components:
- Topics & partitioning strategy
- Replication factor & ISR
- Producer configuration (acks, idempotence)
- Consumer groups & parallelism
- Exactly-once semantics (if needed)
- Monitoring & alerting
```

**4. Capacity Planning (5 minutes):**
```
Show calculations:
- Partitions: throughput / partition_throughput
- Storage: daily_volume × retention × RF
- Brokers: partitions / max_partitions_per_broker
```

**5. Trade-offs (5 minutes):**
```
Discuss alternatives:
- Why Kafka vs RabbitMQ/Kinesis?
- Kafka Streams vs Flink?
- InfluxDB vs Cassandra?
- Justify your choices
```

**Key Points to Mention:**

```
✓ Partitioning strategy (for order, parallelism)
✓ Replication factor (HA, durability)
✓ Exactly-once (if money involved)
✓ Monitoring (UnderReplicatedPartitions, lag)
✓ Scaling (add partitions, brokers)
✓ Failure scenarios (broker down, consumer crash)
✓ Capacity planning (show numbers)
```

---

## Summary

**You've now learned:**

✅ **System Design Patterns:**
- Real-time analytics with Kafka Streams
- Payment processing with exactly-once
- E-commerce with event sourcing, CQRS, saga

✅ **Capacity Planning:**
- Throughput, storage, partitions calculation
- Broker sizing and hardware specs
- Cost estimation

✅ **When to Use Kafka:**
- Event streaming, real-time processing
- Data integration, CDC
- Microservices communication

✅ **When NOT to Use:**
- Request-response (use REST)
- Low-latency trading (use specialized)
- Small scale (use RabbitMQ)

✅ **Interview Skills:**
- Framework for system design
- How to discuss trade-offs
- Capacity planning approach

**You're now at 95-100% Kafka knowledge!** 🎉

Ready for **Staff/Principal Engineer** level interviews! 🚀

---

## Interview Quick Answers

1. **"Design real-time analytics"** → Kafka + Streams + TimeSeries DB + Grafana
2. **"How to handle payments"** → Exactly-once with transactions, idempotent producer
3. **"Capacity planning?"** → Show calculations for partitions, storage, brokers
4. **"When NOT Kafka?"** → Request-response, ultra-low latency, small scale
5. **"Event sourcing?"** → Store all state changes as events, replay to rebuild state
6. **"CQRS?"** → Separate write model (commands) from read model (queries)
7. **"Saga pattern?"** → Distributed transaction with compensations
8. **"How many partitions?"** → target_throughput / partition_throughput

**Congratulations! You've completed all 7 days!** 🎊

