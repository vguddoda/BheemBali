# Quick Start Guide - Kafka Interview Prep

## 🚀 Get Started in 5 Minutes

### Step 1: Start Kafka Cluster

```bash
cd /Users/vishalkumarbg/Documents/Bheembali/kafka

# Start all services (this will download images first time)
docker-compose up -d

# Wait 30 seconds for services to start
sleep 30

# Verify all services are running
docker ps
```

You should see 6 containers running:
- ✅ kafka-broker-1
- ✅ kafka-broker-2  
- ✅ kafka-broker-3
- ✅ kafka-schema-registry
- ✅ kafka-connect
- ✅ kafka-ui

### Step 2: Access Kafka UI

Open your browser: **http://localhost:8080**

You'll see:
- Cluster overview
- Topics
- Consumers
- Brokers

### Step 3: Send Your First Message

```bash
# Access broker container
docker exec -it kafka-broker-1 bash

# Create a topic
kafka-topics --create \
  --bootstrap-server localhost:19092 \
  --topic hello-kafka \
  --partitions 3 \
  --replication-factor 3

# Start producer and type messages
kafka-console-producer \
  --bootstrap-server localhost:19092 \
  --topic hello-kafka

> Hello Kafka!
> This is my first message
> [Press Ctrl+C to exit]
```

### Step 4: Receive Messages

Open a NEW terminal:

```bash
docker exec -it kafka-broker-1 bash

# Start consumer
kafka-console-consumer \
  --bootstrap-server localhost:19092 \
  --topic hello-kafka \
  --from-beginning
```

You'll see your messages appear! 🎉

---

## 📅 Your 7-Day Study Plan

### Day 1: Fundamentals (TODAY)
**Time: 4-6 hours**

Morning (2-3 hours):
- [ ] Read: [Day 1 Interview Questions](interview-questions/day1-fundamentals.md)
- [ ] Understand: Topics, Partitions, Brokers, Producers, Consumers
- [ ] Watch Kafka UI in action

Afternoon (2-3 hours):
- [ ] Complete: [Day 1 Lab](labs/day1-fundamentals/README.md)
- [ ] Practice: CLI commands from cheat sheet
- [ ] Test: Replication and failover

**Key Takeaways:**
- Kafka is a distributed streaming platform
- Partitions enable parallelism
- Replication provides fault tolerance
- Consumer groups enable load balancing

---

### Day 2: Producers
**Topics:**
- Producer architecture
- Partitioning strategies
- Acknowledgments (acks)
- Idempotence and transactions

**Read:** [Day 2 Questions](interview-questions/day2-producers.md)
**Practice:** [Python Examples](examples/python/producer_examples.md)

---

### Day 3: Consumers
**Topics:**
- Consumer groups
- Offset management
- Rebalancing
- Consumer configurations

**Lab:** Consumer group experiments
**Practice:** Different consumption patterns

---

### Day 4: Architecture
**Topics:**
- Cluster architecture
- Replication and ISR
- Leader election
- Storage internals
- ZooKeeper vs KRaft

**Lab:** Multi-broker setup, failover testing

---

### Day 5: Stream Processing
**Topics:**
- Kafka Streams
- Stateful/stateless operations
- Windowing
- Kafka Connect

**Lab:** Build streaming application

---

### Day 6: Production Ready
**Topics:**
- Performance tuning
- Monitoring and metrics
- Security
- Best practices

**Lab:** Monitoring setup, benchmarking

---

### Day 7: System Design
**Topics:**
- Real-world use cases
- Design patterns
- Exactly-once semantics
- Multi-datacenter

**Lab:** End-to-end project

---

## 🎯 Interview Focus Areas

### Must Know (70% of interviews)
1. **Core Concepts**
   - What is Kafka and why use it?
   - Topics, Partitions, Replication
   - Producers and Consumers
   - Consumer Groups

2. **Configuration**
   - `acks` parameter
   - `min.insync.replicas`
   - Retention policies
   - Partitioning strategy

3. **Guarantees**
   - Ordering (per-partition)
   - Durability (replication)
   - Delivery semantics (at-most-once, at-least-once, exactly-once)

4. **Failure Scenarios**
   - Broker failure
   - Network partition
   - Consumer lag
   - Rebalancing

### Advanced Topics (30% of interviews)
1. **Performance**
   - Throughput optimization
   - Latency tuning
   - Compression strategies

2. **Architecture**
   - KRaft vs ZooKeeper
   - Multi-datacenter setup
   - Storage internals

3. **Stream Processing**
   - Kafka Streams
   - State stores
   - Windowing

4. **Operations**
   - Monitoring
   - Scaling
   - Upgrades

---

## 💡 Quick Tips for Interview Success

### 1. Explain with Diagrams
Always visualize:
```
Producer → [P0|P1|P2] → Consumer Group
                          ├─ Consumer 1 (P0, P1)
                          └─ Consumer 2 (P2)
```

### 2. Know the Trade-offs
- Throughput vs Latency
- Durability vs Performance
- Scalability vs Ordering

### 3. Real-world Context
"We'd use Kafka for clickstream data because..."
- High volume (millions of events/sec)
- Need replay capability
- Multiple downstream systems
- Decoupling microservices

### 4. Configuration Examples
Don't just say "enable idempotence", show:
```properties
enable.idempotence=true
acks=all
max.in.flight.requests.per.connection=5
```

### 5. Failure Handling
Always discuss:
- What can go wrong?
- How does Kafka handle it?
- What's the recovery time?

---

## 🔧 Useful Commands

### Topic Management
```bash
# Create topic
kafka-topics --create --bootstrap-server localhost:19092 \
  --topic my-topic --partitions 3 --replication-factor 3

# List topics
kafka-topics --list --bootstrap-server localhost:19092

# Describe topic
kafka-topics --describe --bootstrap-server localhost:19092 --topic my-topic
```

### Producer/Consumer
```bash
# Produce
kafka-console-producer --bootstrap-server localhost:19092 --topic my-topic

# Consume
kafka-console-consumer --bootstrap-server localhost:19092 \
  --topic my-topic --from-beginning
```

### Consumer Groups
```bash
# List groups
kafka-consumer-groups --list --bootstrap-server localhost:19092

# Describe group
kafka-consumer-groups --describe --bootstrap-server localhost:19092 \
  --group my-group
```

---

## 📚 Resources

### Official Documentation
- [Apache Kafka Docs](https://kafka.apache.org/documentation/)
- [Confluent Docs](https://docs.confluent.io/)

### Books
- "Kafka: The Definitive Guide" by Neha Narkhede
- "Designing Data-Intensive Applications" by Martin Kleppmann

### Videos
- [Kafka Internals](https://www.youtube.com/watch?v=WuXXKNL4EXc)
- [Confluent YouTube Channel](https://www.youtube.com/c/Confluent)

---

## 🐛 Troubleshooting

### Containers won't start
```bash
# Check logs
docker-compose logs kafka-broker-1

# Common fix: Remove old volumes
docker-compose down -v
docker-compose up -d
```

### Can't connect to Kafka
```bash
# Check if running
docker ps

# Test connection
docker exec -it kafka-broker-1 \
  kafka-broker-api-versions --bootstrap-server localhost:19092
```

### Kafka UI not loading
```bash
# Restart Kafka UI
docker-compose restart kafka-ui

# Check logs
docker logs kafka-ui
```

---

## 🎓 Practice Interview Questions

### Beginner
1. What is Kafka?
2. Explain partitions
3. What is a consumer group?
4. How does replication work?

### Intermediate
1. Explain `acks` parameter and trade-offs
2. How do you handle consumer lag?
3. What is idempotence?
4. Describe the commit log structure

### Advanced
1. Design a high-throughput real-time analytics system
2. Implement exactly-once semantics
3. How would you handle million messages per second?
4. Explain KRaft and why it replaces ZooKeeper

---

## ✅ Daily Checklist

**Day 1:**
- [ ] Start Kafka cluster
- [ ] Create first topic
- [ ] Send and receive messages
- [ ] Try consumer groups
- [ ] Test broker failure
- [ ] Review 30 interview questions

**Day 2-7:**
Follow README.md plan

---

## 🚦 You're Ready When...

✅ Can explain Kafka concepts without notes
✅ Can write producer/consumer code from memory
✅ Can design systems using Kafka
✅ Can troubleshoot common issues
✅ Can explain trade-offs in configurations
✅ Can answer 80% of interview questions in this repo

---

## 📞 Need Help?

1. Check [Troubleshooting Guide](troubleshooting.md)
2. Review [Cheat Sheet](cheat-sheet.md)
3. Search [Stack Overflow](https://stackoverflow.com/questions/tagged/apache-kafka)

---

## 🎯 Next Steps

1. **Right Now:** Start Day 1 Lab
2. **This Week:** Complete all 7 days
3. **Before Interview:** Review all questions twice
4. **Day Before:** Do mock interviews

**Remember:** Understanding > Memorization

Good luck with your interview preparation! 🚀

---

## Stop the Cluster

When you're done:
```bash
# Stop containers
docker-compose down

# Stop and remove all data (start fresh)
docker-compose down -v
```

