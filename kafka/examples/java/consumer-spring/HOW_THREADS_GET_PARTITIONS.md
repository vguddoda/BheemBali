# How Kafka Routes Messages to Threads - Deep Dive

## Your Question
Same `@KafkaListener` method, but Thread 1 always gets Partition 0, Thread 2 always gets Partition 1.
How does it differentiate when it's the **same code**?

## The Answer: It Happens **Before** Your Code Runs

The key is: **Kafka partition assignment happens at the consumer/thread level**, not inside your listener method.

## The Flow (Simplified)

```
                              Kafka Broker
                    ┌─────────────────────────────┐
                    │  test-topic                 │
                    │  ├─ Partition 0 (offset 0)  │
                    │  └─ Partition 1 (offset 0)  │
                    └─────────────────────────────┘
                              ↓
                    Consumer Group Coordinator
                    (broker-side assignment logic)
                              ↓
          ┌──────────────────────────────────────────────┐
          │  advanced-group assignment                   │
          │  ├─ Thread-1 → owns Partition 0              │
          │  └─ Thread-2 → owns Partition 1              │
          └──────────────────────────────────────────────┘
                              ↓
          ┌──────────────────────────────────────────────┐
          │  Pod 1 (2 consumer threads)                  │
          │  ├─ Thread-1                                 │
          │  │   ├─ Consumer instance #1                 │
          │  │   └─ poll() always gets Partition 0       │
          │  │       messages only                       │
          │  │                                           │
          │  └─ Thread-2                                 │
          │      ├─ Consumer instance #2                 │
          │      └─ poll() always gets Partition 1       │
          │          messages only                       │
          └──────────────────────────────────────────────┘
```

## How Spring Creates These Threads

In your `KafkaConsumerConfig.java`:

```java
@Bean
public ConcurrentKafkaListenerContainerFactory<String, String> 
    manualCommitContainerFactory() {
    
    factory.setConcurrency(2);  // Creates 2 internal consumer instances
    
    // Spring creates 2 Kafka consumer threads:
    // - Thread-1 with consumer instance subscribes to topic -> Kafka assigns Partition 0
    // - Thread-2 with consumer instance subscribes to topic -> Kafka assigns Partition 1
    
    return factory;
}
```

Each thread has its **own Kafka Consumer instance** internally.

## What Happens Inside Each Thread

**Thread-1:**
```
while(true) {
    ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofSeconds(1));
    // kafkaConsumer assigned to Partition 0
    // So records ONLY contains messages from Partition 0
    
    for (ConsumerRecord record : records) {
        // This fires your @KafkaListener method
        listenWithManualCommit(record.value(), record.partition(), ...);
        // record.partition() == 0 ALWAYS for this thread
    }
}
```

**Thread-2:**
```
while(true) {
    ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofSeconds(1));
    // kafkaConsumer assigned to Partition 1
    // So records ONLY contains messages from Partition 1
    
    for (ConsumerRecord record : records) {
        // This fires your @KafkaListener method
        listenWithManualCommit(record.value(), record.partition(), ...);
        // record.partition() == 1 ALWAYS for this thread
    }
}
```

## Why Same Code Works

Your listener method is **generic and agnostic**:

```java
@KafkaListener(concurrency = "2")
public void listenWithManualCommit(
    @Payload String message,
    @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
    @Header(KafkaHeaders.OFFSET) long offset,
    Acknowledgment acknowledgment
) {
    String threadName = Thread.currentThread().getName();
    // Same code, but different input data from different partitions
}
```

- **Partition 0 messages** flow through the method via Thread-1.
- **Partition 1 messages** flow through the method via Thread-2.
- The method itself doesn't care; it just processes what the thread's consumer gives it.

The **differentiation is built into the poll() call**, not your code.

## Guarantees Kafka Makes

1. **Exclusive assignment:** Partition 0 goes to Thread-1, Partition 1 to Thread-2. Never both.
2. **Deterministic ordering within partition:** Messages from Partition 0 always come in order.
3. **No message loss:** Every message from every partition gets assigned to exactly one thread.
4. **Sticky assignment:** Same thread keeps the same partition (unless rebalance happens).

## What Changes on Rebalance

If a pod crashes or joins:

```
BEFORE (stable):
├─ Thread-1 → Partition 0
└─ Thread-2 → Partition 1

REBALANCE HAPPENS (1 pod crashes):
├─ New Thread-1 → Partition 0 + Partition 1 (now owns both)

AFTER (new assignment):
└─ Single Thread-1 → [Partition 0, Partition 1]
   (same method code still works, poll() returns records from both partitions)
```

## Analogy

Think of it like a **post office with 2 mail carriers**:

- Partition 0 = "North District"
- Partition 1 = "South District"
- Each carrier has **one assigned territory** (set by management).
- Same mail carrier code (deliver mail, collect signature, move to next address).
- But Carrier 1 always delivers in North, Carrier 2 always in South.
- Management (Kafka coordinator) decides the territory split, not the carriers.

## Code-Level View

This happens in Spring Kafka under the hood:

```java
// In Spring's ConcurrentMessageListenerContainer
for (int i = 0; i < concurrency; i++) {  // concurrency = 2
    Thread thread = new Thread(() -> {
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config);
        consumer.subscribe(topics);  // "test-topic"
        
        // Kafka broker assigns partitions to this consumer instance
        // Based on consumer group logic
        
        while (running) {
            ConsumerRecords<String, String> records = consumer.poll(...);
            for (ConsumerRecord record : records) {
                // Invoke the same listener method with different data
                listenWithManualCommit(
                    record.value(), 
                    record.partition(),  // <-- Partition info comes from record
                    record.offset(), 
                    ack
                );
            }
        }
    });
    thread.start();
}
```

## TL;DR

- **One listener method** = **multiple thread instances** calling it.
- **Kafka broker** (not your code) assigns partitions to consumer threads.
- Each thread's `poll()` **filters records** to only its assigned partition(s).
- Same method code, **different input data per thread**.
- That's the magic.

