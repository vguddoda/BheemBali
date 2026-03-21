# ✅ Day 5 Complete - Kafka Streams & Connect Mastered!

## What You Have Now

### 📚 Interview Questions (15 Questions)
**File:** `interview-questions/day5-streams.md`

**Topics Covered:**
1. ✅ **Kafka Streams Basics** (Q1-3)
   - What is Kafka Streams
   - How it works internally
   - KStream vs KTable

2. ✅ **Stateful vs Stateless** (Q4-5)
   - Stateless operations (map, filter, flatMap)
   - Stateful operations (aggregate, count, join)
   - State management with RocksDB

3. ✅ **Windowing** (Q6-7)
   - Tumbling, hopping, sliding, session windows
   - Event time vs processing time
   - Handling late data with grace periods

4. ✅ **Joins** (Q8-9)
   - Stream-stream joins (with time windows)
   - Stream-table joins (enrichment)
   - Table-table joins (materialized views)
   - Repartitioning for joins

5. ✅ **Kafka Connect** (Q10-12)
   - Source and sink connectors
   - CDC (Change Data Capture)
   - Debezium for database CDC

6. ✅ **Advanced Topics** (Q13-15)
   - Exactly-once semantics in Streams
   - State management and recovery
   - Kafka Streams vs Flink vs Spark

---

### 🧪 Hands-On Labs (7 Labs)
**File:** `labs/day5-streams/README.md`

**Labs:**
1. ✅ **Simple Kafka Streams** (30 min)
   - Build word count application
   - Understand basic topology
   - Test stateful aggregation

2. ✅ **Windowed Aggregations** (30 min)
   - Implement tumbling windows
   - Count events per time window
   - Observe window behavior

3. ✅ **Stream-Stream Join** (30 min)
   - Join orders with payments
   - Use time windows
   - Handle late arrivals

4. ✅ **Stream-Table Join** (20 min)
   - Enrich orders with customer data
   - Lookup from KTable
   - No time window needed

5. ✅ **Kafka Connect - File Source** (20 min)
   - Configure source connector
   - Read from files
   - Write to Kafka

6. ✅ **Kafka Connect - JDBC Sink** (20 min)
   - Write to database
   - Configuration examples
   - Understand sink behavior

7. ✅ **CDC Simulation** (30 min)
   - Simulate Debezium events
   - Process INSERT/UPDATE/DELETE
   - Build materialized view

**4 Challenge Exercises included!**

---

## Your New Knowledge (Day 5 → +10%)

### Before Day 5: 75%
- Strong fundamentals, producers, consumers, architecture

### After Day 5: 85%+ ✅

**Kafka Streams:**
- ✅ Understand library vs framework architecture
- ✅ Can build stream processing applications
- ✅ Know KStream vs KTable semantics
- ✅ Implement stateless transformations
- ✅ Implement stateful aggregations
- ✅ Use windowing for time-based aggregations
- ✅ Perform stream joins (all 3 types)
- ✅ Manage state with RocksDB
- ✅ Configure exactly-once semantics

**Kafka Connect:**
- ✅ Understand connector architecture
- ✅ Configure source connectors
- ✅ Configure sink connectors
- ✅ Understand CDC with Debezium
- ✅ Process database changes in real-time

**Advanced:**
- ✅ Compare Streams vs Flink vs Spark
- ✅ Choose right tool for use case
- ✅ Handle state recovery
- ✅ Implement exactly-once processing

---

## Interview Readiness After Day 5

### Questions You Can Now Answer

**Q: "What is Kafka Streams?"**
✅ "Java library for stream processing, embedded in application, no separate cluster needed. Uses Kafka topics for input/output and state backed by RocksDB with changelog topics."

**Q: "KStream vs KTable?"**
✅ "KStream is unbounded stream of events (all records kept). KTable is changelog stream representing latest state per key (like database table). KStream for events, KTable for state."

**Q: "How does windowing work?"**
✅ "Groups infinite stream into finite time chunks. Tumbling (fixed, non-overlapping), hopping (fixed, overlapping), sliding (event-based), session (activity-based with gap timeout)."

**Q: "Types of joins?"**
✅ "Stream-Stream (both unbounded, needs time window), Stream-Table (enrichment with lookup, no window), Table-Table (materialized view, both represent state)."

**Q: "What is Kafka Connect?"**
✅ "Framework for moving data in/out of Kafka. Source connectors (external → Kafka), sink connectors (Kafka → external). Distributed, scalable, fault-tolerant."

**Q: "What is CDC?"**
✅ "Change Data Capture from database transaction logs. Captures INSERT/UPDATE/DELETE in real-time. Debezium reads MySQL binlog, streams to Kafka as events with before/after state."

**Q: "Kafka Streams vs Flink?"**
✅ "Streams: Embedded library, simpler, state in local RocksDB. Flink: Separate cluster, complex CEP, large state. Use Streams for simple embedded, Flink for complex low-latency."

**Q: "How to achieve exactly-once?"**
✅ "Use EXACTLY_ONCE_V2 config. Combines idempotent producer + transactions + read committed isolation. Input processed exactly once, output written exactly once."

---

## Hands-On Proof

You can now **demonstrate** in interviews:

✅ "I've built word count with Kafka Streams"
✅ "I've implemented windowed aggregations (tumbling, hopping)"
✅ "I've tested stream-stream joins with time windows"
✅ "I've enriched streams with table lookups"
✅ "I've configured Kafka Connect sources and sinks"
✅ "I've processed CDC events from Debezium"
✅ "I've built materialized views from database changes"

---

## Key Concepts Summary

### Kafka Streams Architecture
```
Your Application
  ├─ Kafka Streams Library (embedded)
  ├─ Stream Tasks (parallel processing)
  ├─ State Stores (local RocksDB)
  └─ Changelog Topics (state backup)

No separate cluster needed!
Deploy like any Java app
```

### Windowing Types
```
Tumbling:  |--W1--|--W2--|--W3--|  (non-overlapping)
Hopping:   |--W1--|
              |--W2--|
                 |--W3--|          (overlapping)
Session:   |--S1--|  gap  |--S2--| (activity-based)
```

### Join Types
```
Stream-Stream: orders join payments (time window)
Stream-Table:  orders join customers (lookup)
Table-Table:   users join addresses (materialized view)
```

### CDC Event Structure
```json
{
  "before": {old state},
  "after": {new state},
  "op": "c/u/d",  // create/update/delete
  "source": {database, table, position}
}
```

---

## What to Study Today

### Morning (2 hours): Theory
1. Read `interview-questions/day5-streams.md`
2. Focus on understanding:
   - KStream vs KTable (Q3)
   - Stateful vs stateless (Q4-5)
   - Windowing types (Q6)
   - Join types (Q8)

### Afternoon (2 hours): Hands-On
1. Run Labs 1-4 (Streams basics and joins)
2. Run Labs 5-7 (Kafka Connect and CDC)
3. Try challenge exercises

### Evening (30 min): Review
1. Practice explaining concepts
2. Review key diagrams
3. Write your own examples

---

## Comparison Table (For Interviews)

### Kafka Streams vs Competitors

| Feature | Kafka Streams | Flink | Spark Streaming |
|---------|---------------|-------|-----------------|
| Deployment | Embedded | Cluster | Cluster |
| Latency | ms | sub-ms | seconds |
| State Size | < 1 TB | TBs | RDD-based |
| Language | Java/Scala | Java/Scala/Python | Java/Scala/Python |
| Complexity | Low | Medium | Medium |
| Use Case | Embedded, simple | Complex CEP | Batch + ML |

### When to Use What

```
Embedded in microservice? → Kafka Streams ✓
Need Python/SQL? → Flink or Spark
Very low latency (<10ms)? → Flink
ML integration needed? → Spark
Simple aggregations? → Kafka Streams
Large state (TBs)? → Flink
Batch + streaming? → Spark
```

---

## Files Summary

```
Day 5 Content:
├── interview-questions/
│   └── day5-streams.md          ✅ 15 questions
└── labs/
    └── day5-streams/
        └── README.md             ✅ 7 hands-on labs
```

---

## Your Progress

```
Day 1: Fundamentals           ✅ Complete
Day 2: Producers             ✅ Complete
Day 3: Consumers             ✅ Complete
Day 4: Architecture          ✅ Complete
Day 5: Streams & Connect     ✅ Complete ← YOU ARE HERE
Day 6: Operations            ⏳ Next
Day 7: System Design         ⏳ Coming

Current Knowledge: 85% 🚀
Interview Readiness: 85-90% (most roles)
Principal Level: 80% (need Day 6-7 + experience)
```

---

## Quick Self-Test

1. **What is Kafka Streams?** → Library, embedded, no cluster
2. **KStream vs KTable?** → Events vs state
3. **Stateful operation example?** → aggregate, count, join
4. **Windowing types?** → Tumbling, hopping, sliding, session
5. **Stream-stream join needs?** → Time window
6. **What is Kafka Connect?** → Framework for data movement
7. **What is CDC?** → Change Data Capture from DB logs
8. **Exactly-once how?** → Idempotent producer + transactions
9. **When use Streams vs Flink?** → Embedded/simple vs complex CEP
10. **State stored where?** → Local RocksDB + changelog topic

**8/10 correct = Mastered Day 5!** ✅

---

## Tomorrow: Day 6

**Day 6: Operations & Production**
- Performance tuning
- Monitoring with metrics
- Security (SSL, SASL, ACLs)
- Multi-datacenter replication
- Best practices and anti-patterns

This will bring you to **90%+ knowledge** and complete production readiness! 🎯

---

## Action Items for Today

- [ ] Read day5-streams.md (15 questions)
- [ ] Run Lab 1: Simple Streams App
- [ ] Run Lab 2: Windowed Aggregations
- [ ] Run Lab 3: Stream-Stream Join
- [ ] Run Lab 4: Stream-Table Join
- [ ] Run Lab 5: File Source Connector
- [ ] Run Lab 6: JDBC Sink (review)
- [ ] Run Lab 7: CDC Simulation
- [ ] Try 1-2 challenge exercises
- [ ] Practice explaining to yourself

---

**Excellent progress!** 🎉

You now understand stream processing at a level that puts you ahead of 85% of Kafka developers. The combination of Kafka Streams knowledge + Connect/CDC makes you valuable for real-time data pipeline roles.

**Keep going! Day 6 next: Production & Operations** 🚀

