# Java Examples - Quick Test

## Build & Run

```bash
# Build Producer
cd producer
mvn clean package

# Build Consumer
cd consumer
mvn clean package
```

## Run Examples

### Producer Examples (Day 2)

```bash
cd producer

# 1. Simple Producer - Just send messages
mvn exec:java -Dexec.mainClass="SimpleProducer"

# 2. With Callback - See partition and offset
mvn exec:java -Dexec.mainClass="ProducerWithCallback"

# 3. Idempotent - No duplicates
mvn exec:java -Dexec.mainClass="IdempotentProducer"
```

### Consumer Examples (Day 3)

```bash
cd consumer

# 1. Simple Consumer - Read messages
mvn exec:java -Dexec.mainClass="SimpleConsumer"

# 2. Consumer Group - Run 2 terminals
mvn exec:java -Dexec.mainClass="ConsumerGroup" -Dexec.args="c1"
mvn exec:java -Dexec.mainClass="ConsumerGroup" -Dexec.args="c2"

# 3. Manual Commit - Control when to save
mvn exec:java -Dexec.mainClass="ManualCommit"
```

## Quick Test Flow

```bash
# Terminal 1 - Start Consumer
cd consumer
mvn exec:java -Dexec.mainClass="SimpleConsumer"

# Terminal 2 - Send Messages
cd producer
mvn exec:java -Dexec.mainClass="ProducerWithCallback"
```

## Interview Tests

### Test 1: Load Balancing
Run 2 consumers in same group - they share partitions
```bash
# Terminal 1
mvn exec:java -Dexec.mainClass="ConsumerGroup" -Dexec.args="c1"

# Terminal 2
mvn exec:java -Dexec.mainClass="ConsumerGroup" -Dexec.args="c2"
```

### Test 2: Failover
Kill consumer while running - see rebalance

### Test 3: No Duplicates
Use IdempotentProducer - even network fails, no duplicates

Done!

