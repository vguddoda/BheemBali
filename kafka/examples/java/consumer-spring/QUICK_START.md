# Quick Reference: Spring Kafka @KafkaListener with Concurrency

## 🎯 What Was Added

New folder: `examples/java/consumer-spring/`

**3 Listener Examples:**
1. `ConcurrentKafkaListener` - 3 threads, auto-commit
2. `AdvancedKafkaListener` - 5 threads, manual ack
3. `BatchKafkaListener` - 10 threads, batch mode

**Configuration:**
- `KafkaConsumerConfig` - 4 different container factories
- `application.properties` - All settings

---

## 🚀 Quick Start

```bash
cd examples/java/consumer-spring
mvn clean package
mvn spring-boot:run
```

---

## 💡 Key Concepts

### Concurrency Setting
```java
@Bean
public ConcurrentKafkaListenerContainerFactory<String, String> 
    kafkaListenerContainerFactory() {
    factory.setConcurrency(3);  // 3 consumer threads
    return factory;
}
```

### Usage
```java
@KafkaListener(
    topics = "test-topic",
    concurrency = "10"  // Override to 10 threads
)
public void listen(String message) {
    // This method runs in 10 parallel threads!
}
```

---

## 📊 Concurrency Levels Explained

| Factory | Threads | Use Case |
|---------|---------|----------|
| kafkaListenerContainerFactory | 3 | Basic concurrent processing |
| manualCommitContainerFactory | 5 | Manual offset control |
| batchContainerFactory | 10 | Batch DB operations |
| highThroughputContainerFactory | 20 | Maximum throughput |

---

## 🎓 Interview Answers

### "How to scale Kafka consumers?"

**Answer:**
```
1. Horizontal scaling:
   - Run multiple consumer instances
   - Each gets different partitions
   
2. Vertical scaling (what we did):
   - Use concurrency in @KafkaListener
   - Multiple threads in one JVM
   
Example:
@KafkaListener(concurrency = "10")
Creates 10 threads in single application
```

### "Concurrency vs Partitions?"

**Answer:**
```
Max useful concurrency = number of partitions

Example:
- 3 partitions + concurrency=3 → 100% utilization
- 3 partitions + concurrency=10 → Only 3 active, 7 idle
- 10 partitions + concurrency=10 → 100% utilization

Rule: Set concurrency = partition count for optimal use
```

Simple way to think about it:
- One partition can be consumed by only one thread in the same consumer group at a time
- One thread can handle one or more partitions
- If threads > partitions, extra threads stay idle

Example:
```
1 pod with concurrency=10
topic has 2 partitions

Result:
- 2 threads will be active
- 8 threads will be idle
- Partition 0 goes to one thread
- Partition 1 goes to another thread
```

### "Manual vs Auto Commit with Concurrency?"

**Answer:**
```java
// Spring commits after the records from the poll are processed
factory.getContainerProperties()
    .setAckMode(ContainerProperties.AckMode.BATCH);

// Your code decides when to commit
factory.getContainerProperties()
    .setAckMode(ContainerProperties.AckMode.MANUAL);

@KafkaListener
public void listen(String msg, Acknowledgment ack) {
    process(msg);
    ack.acknowledge();  // Explicit commit
}
```

In simple words:
- `AckMode.BATCH` → Spring commits for you after the listener finishes processing the records it just received. Less code, good for simple consumers.
- `AckMode.MANUAL` → you commit only when *you* call `ack.acknowledge()`. Better when you want to commit only after DB save, API call, or any important business logic succeeds.
- If the app fails before commit, Kafka can redeliver those records later. That is why manual ack is often called safer.

---

## 🔥 Demo Commands

### Basic Test
```bash
# Terminal 1
cd consumer-spring
mvn spring-boot:run

# Terminal 2
cd ../producer
mvn exec:java -Dexec.mainClass="SimpleProducer"
```

### See Concurrent Processing
Look for different thread names in output:
```
[pool-1-thread-1] Received: message-0
[pool-1-thread-2] Received: message-1
[pool-1-thread-3] Received: message-2
```

---

## 📁 Files Structure

```
consumer-spring/
├── pom.xml                          Spring Boot + Kafka
├── src/main/java/com/kafka/
│   ├── SpringKafkaConsumerApplication.java
│   ├── consumer/
│   │   ├── ConcurrentKafkaListener.java      3 threads
│   │   ├── AdvancedKafkaListener.java        5 threads, manual
│   │   └── BatchKafkaListener.java           10 threads, batch
│   └── config/
│       └── KafkaConsumerConfig.java          All configs
└── src/main/resources/
    └── application.properties                Settings
```

---

## ⚙️ Configuration Cheat Sheet

### In application.properties:
```properties
# Basic
spring.kafka.bootstrap-servers=localhost:19092
spring.kafka.consumer.group-id=my-group

# Concurrency (default for all listeners)
spring.kafka.listener.concurrency=3

# Performance
spring.kafka.consumer.fetch-min-size=1048576
spring.kafka.consumer.max-poll-records=500
```

### In Java Config:
```java
// Override per listener
@KafkaListener(concurrency = "10")

// Or set in factory
factory.setConcurrency(10);

// Batch mode
factory.setBatchListener(true);

// Manual commit
factory.getContainerProperties()
    .setAckMode(ContainerProperties.AckMode.MANUAL);
```

---

## ✅ What You Can Now Do

1. ✅ Explain @KafkaListener concurrency
2. ✅ Configure multiple consumer threads
3. ✅ Implement manual acknowledgment
4. ✅ Process messages in batches
5. ✅ Compare Spring vs plain Kafka
6. ✅ Optimize for high throughput
7. ✅ Demo concurrent processing

---

## 🎯 Interview Ready!

You now have:
- **Plain Kafka examples** (low-level control)
- **Spring Kafka examples** (production standard)
- **Concurrency patterns** (3, 5, 10, 20 threads)
- **Working code** (ready to run)

**This is exactly what companies use in production!** 🚀

---

Read full guide: `consumer-spring/README.md`

Rebalancing short guide: `consumer-spring/REBALANCING_SHORT_GUIDE.md`

**How same listener code routes to different partitions?** `consumer-spring/HOW_THREADS_GET_PARTITIONS.md`

**Concurrency vs CPU cores?** `consumer-spring/CONCURRENCY_VS_CPU_CORES.md`

