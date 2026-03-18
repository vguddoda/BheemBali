# Kafka Interview Preparation - 7 Day Master Plan

## 🎯 Goal
Master Apache Kafka concepts and gain hands-on experience to ace technical interviews in one week.

## 📋 Prerequisites
- Docker and Docker Compose installed
- Basic understanding of distributed systems
- Familiarity with Java/Python (for hands-on exercises)

## 🗓️ 7-Day Learning Plan

### Day 1: Kafka Fundamentals
- **Theory** (2-3 hours)
  - What is Kafka and why is it used?
  - Core concepts: Topics, Partitions, Brokers, Producers, Consumers
  - Message anatomy and storage
  - Kafka vs traditional message queues
  
- **Hands-on** (2-3 hours)
  - Setup Kafka cluster with Docker
  - Create topics and understand partition distribution
  - Send and receive messages using CLI tools
  - **Lab**: [Day 1 Lab](./labs/day1-fundamentals/)

- **Interview Questions**: [Day 1 Questions](./interview-questions/day1-fundamentals.md)

### Day 2: Producers Deep Dive
- **Theory** (2-3 hours)
  - Producer architecture and workflow
  - Partitioning strategies (round-robin, key-based, custom)
  - Acknowledgments (acks=0, 1, all)
  - Idempotent and transactional producers
  - Producer configurations and tuning
  
- **Hands-on** (2-3 hours)
  - Implement producers in Java/Python
  - Test different partitioning strategies
  - Experiment with acks settings
  - Measure throughput and latency
  - **Lab**: [Day 2 Lab](./labs/day2-producers/)

- **Interview Questions**: [Day 2 Questions](./interview-questions/day2-producers.md)

### Day 3: Consumers and Consumer Groups
- **Theory** (2-3 hours)
  - Consumer architecture and workflow
  - Consumer groups and partition assignment
  - Offset management (auto-commit vs manual)
  - Rebalancing and partition strategies
  - Consumer configurations and tuning
  
- **Hands-on** (2-3 hours)
  - Implement consumers in Java/Python
  - Create consumer groups
  - Test rebalancing scenarios
  - Manual offset management
  - **Lab**: [Day 3 Lab](./labs/day3-consumers/)

- **Interview Questions**: [Day 3 Questions](./interview-questions/day3-consumers.md)

### Day 4: Kafka Architecture & Durability
- **Theory** (2-3 hours)
  - Cluster architecture and ZooKeeper/KRaft
  - Replication and ISR (In-Sync Replicas)
  - Leader election and failover
  - Log segments and retention policies
  - Kafka storage internals
  
- **Hands-on** (2-3 hours)
  - Setup multi-broker cluster
  - Test replication and failover
  - Configure retention policies
  - Analyze log files
  - **Lab**: [Day 4 Lab](./labs/day4-architecture/)

- **Interview Questions**: [Day 4 Questions](./interview-questions/day4-architecture.md)

### Day 5: Kafka Streams & Processing
- **Theory** (2-3 hours)
  - Kafka Streams API fundamentals
  - Stream processing topology
  - Stateless vs stateful operations
  - Windowing and aggregations
  - Kafka Connect basics
  
- **Hands-on** (2-3 hours)
  - Build stream processing applications
  - Implement aggregations and joins
  - Setup Kafka Connect
  - **Lab**: [Day 5 Lab](./labs/day5-streams/)

- **Interview Questions**: [Day 5 Questions](./interview-questions/day5-streams.md)

### Day 6: Performance, Monitoring & Best Practices
- **Theory** (2-3 hours)
  - Performance tuning (producer, consumer, broker)
  - Monitoring metrics and tools
  - Common pitfalls and anti-patterns
  - Security (SSL/TLS, SASL, ACLs)
  - Schema Registry and Avro
  
- **Hands-on** (2-3 hours)
  - Performance benchmarking
  - Setup monitoring with Prometheus/Grafana
  - Implement security features
  - Use Schema Registry
  - **Lab**: [Day 6 Lab](./labs/day6-production/)

- **Interview Questions**: [Day 6 Questions](./interview-questions/day6-production.md)

### Day 7: Real-world Scenarios & System Design
- **Theory** (2-3 hours)
  - Common use cases and patterns
  - Event sourcing and CQRS
  - Exactly-once semantics
  - Multi-datacenter replication
  - Kafka in microservices architecture
  
- **Hands-on** (2-3 hours)
  - Build end-to-end real-world project
  - Design a system using Kafka
  - **Lab**: [Day 7 Lab](./labs/day7-realworld/)

- **Interview Questions**: [Day 7 Questions](./interview-questions/day7-system-design.md)

## 🚀 Quick Start

```bash
# Clone and navigate to repository
cd kafka

# Start Kafka cluster
docker-compose up -d

# Verify cluster is running
docker ps

# Access Kafka container
docker exec -it kafka-broker-1 bash
```

## 📚 Additional Resources
- [Official Kafka Documentation](https://kafka.apache.org/documentation/)
- [Confluent Documentation](https://docs.confluent.io/)
- [Kafka: The Definitive Guide (Book)](https://www.confluent.io/resources/kafka-the-definitive-guide/)
- [Cheat Sheet](./cheat-sheet.md)

## 🎓 Interview Preparation Tips
1. Focus on understanding **why** Kafka makes certain design choices
2. Be ready to explain trade-offs in configurations
3. Practice explaining concepts with diagrams
4. Know common failure scenarios and recovery strategies
5. Understand when to use Kafka vs other messaging systems

## 📊 Progress Tracking
- [ ] Day 1: Fundamentals
- [ ] Day 2: Producers
- [ ] Day 3: Consumers
- [ ] Day 4: Architecture
- [ ] Day 5: Streams & Processing
- [ ] Day 6: Production & Monitoring
- [ ] Day 7: Real-world & System Design

## 🔗 Quick Links
- [All Interview Questions](./interview-questions/)
- [Code Examples](./examples/)
- [Troubleshooting Guide](./troubleshooting.md)
- [Common Interview Patterns](./interview-patterns.md)

---

**Time Commitment**: ~4-6 hours per day for 7 days
**Difficulty**: Beginner to Advanced

Good luck with your interview preparation! 🚀

