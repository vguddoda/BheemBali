# ✅ ALL DONE - Quick Start Guide

## What You Have Now

### ✅ Interview Questions (Day 1-3)
- `interview-questions/day1-fundamentals.md` - 25 questions
- `interview-questions/day2-producers.md` - 25+ questions  
- `interview-questions/day3-consumers.md` - 25+ questions **NEW!**

### ✅ Java Examples (Ready to Run)
```
examples/java/
├── producer/
│   ├── pom.xml
│   └── src/main/java/
│       ├── SimpleProducer.java
│       ├── ProducerWithCallback.java
│       └── IdempotentProducer.java
└── consumer/
    ├── pom.xml
    └── src/main/java/
        ├── SimpleConsumer.java
        ├── ConsumerGroup.java
        └── ManualCommit.java
```

### ✅ Python Examples
- `examples/python/producer_examples.md`
- `examples/python/consumer_examples.md`

### ✅ Docker Setup
- Multi-broker cluster (3 brokers)
- Kafka UI at http://localhost:8080
- KRaft mode (no ZooKeeper needed)

---

## Quick Test - 5 Minutes

### 1. Start Kafka
```bash
cd /Users/vishalkumarbg/Documents/Bheembali/kafka
./setup.sh
```

### 2. Test Java Producer
```bash
cd examples/java/producer
mvn clean package
mvn exec:java -Dexec.mainClass="ProducerWithCallback"
```
You'll see: ✓ Partition: 0, Offset: 0

### 3. Test Java Consumer (new terminal)
```bash
cd examples/java/consumer
mvn clean package
mvn exec:java -Dexec.mainClass="SimpleConsumer"
```
You'll see messages being consumed!

### 4. Test Consumer Groups (2 terminals)
```bash
# Terminal 1
mvn exec:java -Dexec.mainClass="ConsumerGroup" -Dexec.args="c1"

# Terminal 2  
mvn exec:java -Dexec.mainClass="ConsumerGroup" -Dexec.args="c2"

# Terminal 3 - Send messages
cd ../producer
mvn exec:java -Dexec.mainClass="SimpleProducer"
```
Watch how c1 and c2 share the work!

---

## Interview Prep Workflow

### Day 1: Fundamentals
1. Read `interview-questions/day1-fundamentals.md`
2. Test with CLI:
```bash
docker exec -it kafka-broker-1 kafka-topics --create \
  --bootstrap-server localhost:19092 --topic test --partitions 3

docker exec -it kafka-broker-1 kafka-console-producer \
  --bootstrap-server localhost:19092 --topic test

docker exec -it kafka-broker-1 kafka-console-consumer \
  --bootstrap-server localhost:19092 --topic test --from-beginning
```

### Day 2: Producers
1. Read `interview-questions/day2-producers.md`
2. Run all 3 Java producers:
```bash
cd examples/java/producer
mvn exec:java -Dexec.mainClass="SimpleProducer"
mvn exec:java -Dexec.mainClass="ProducerWithCallback"
mvn exec:java -Dexec.mainClass="IdempotentProducer"
```
3. Test:
   - Change partition count - see distribution
   - Use same key - see same partition
   - Compare with/without idempotence

### Day 3: Consumers
1. Read `interview-questions/day3-consumers.md`
2. Run all 3 Java consumers:
```bash
cd examples/java/consumer
mvn exec:java -Dexec.mainClass="SimpleConsumer"
mvn exec:java -Dexec.mainClass="ConsumerGroup"
mvn exec:java -Dexec.mainClass="ManualCommit"
```
3. Test:
   - Run 2 consumers in same group - load balancing
   - Run 2 consumers in different groups - both get messages
   - Kill consumer - see rebalance
   - Check lag: 
```bash
docker exec kafka-broker-1 kafka-consumer-groups \
  --bootstrap-server localhost:19092 --group test-group --describe
```

---

## Key Interview Topics Covered

### Architecture
✅ Brokers, Topics, Partitions
✅ Replication, ISR, Leader Election
✅ ZooKeeper vs KRaft (your setup uses KRaft!)

### Producers
✅ Partitioning strategies
✅ Acks (0, 1, all)
✅ Idempotence
✅ Performance tuning

### Consumers
✅ Consumer groups
✅ Offset management
✅ Rebalancing
✅ At-least-once, exactly-once

### Hands-on
✅ Working Java code
✅ Docker cluster
✅ Real tests you can run

---

## Common Interview Questions You Can Answer Now

1. **"Explain Kafka architecture"**
   - Read day1-fundamentals.md Q1-5

2. **"How does producer work?"**
   - Read day2-producers.md Q1-3
   - Show SimpleProducer.java

3. **"What are consumer groups?"**
   - Read day3-consumers.md Q2
   - Demo ConsumerGroup.java

4. **"How to avoid duplicates?"**
   - Read day2-producers.md Q15-16
   - Show IdempotentProducer.java

5. **"What happens if broker fails?"**
   - Read day1-fundamentals.md Q18
   - Test: `docker stop kafka-broker-1`

6. **"How does rebalancing work?"**
   - Read day3-consumers.md Q11
   - Demo: Start 2 consumers, kill one

---

## Next Steps

### To Master This Week:
1. ✅ Day 1-3 ready (fundamentals, producers, consumers)
2. Day 4: Streams (coming)
3. Day 5: Operations (coming)
4. Day 6: Production (coming)
5. Day 7: System design (coming)

### Practice Now:
1. Run each Java example
2. Read corresponding interview questions
3. Test failure scenarios
4. Check Kafka UI: http://localhost:8080

---

## Troubleshooting

### Maven not installed?
```bash
brew install maven
```

### Port conflicts?
Edit `docker-compose.yml` ports

### Kafka not starting?
```bash
docker-compose down -v
./setup.sh
```

---

## You're Ready! 🚀

**What you can do:**
- Answer 75+ interview questions
- Run working Java code
- Explain producer/consumer patterns
- Demo failover and rebalancing
- Discuss performance tuning

**Time to practice:** Run the examples, break things, fix them, learn!

Start here: `cd examples/java && cat README.md`

