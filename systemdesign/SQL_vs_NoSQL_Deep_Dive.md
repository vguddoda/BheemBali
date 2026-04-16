# SQL vs NoSQL — Architect-Level Deep Dive

## Table of Contents
1. [Fundamental Differences](#1-fundamental-differences)
2. [SQL Databases — When to Use What](#2-sql-databases--when-to-use-what)
3. [NoSQL Categories & When to Use What](#3-nosql-categories--when-to-use-what)
4. [NoSQL Limitations (What NoSQL Lacks)](#4-nosql-limitations-what-nosql-lacks)
5. [CAP Theorem & Database Positioning](#5-cap-theorem--database-positioning)
6. [Decision Framework](#6-decision-framework)
7. [Real-World System Design Mappings](#7-real-world-system-design-mappings)
8. [Interview Quick-Fire Answers](#8-interview-quick-fire-answers)

---

## 1. Fundamental Differences

| Dimension | SQL (Relational) | NoSQL |
|---|---|---|
| **Data Model** | Tables with rows & columns, strict schema | Document, Key-Value, Column-Family, Graph — schema-flexible |
| **Schema** | Schema-on-write (enforced at insert) | Schema-on-read (enforced at application level) |
| **Scaling** | Vertical (scale-up); horizontal is hard (sharding is manual/complex) | Horizontal (scale-out) by design; auto-sharding |
| **Transactions** | Full ACID across multiple tables | Usually single-document ACID; multi-document varies |
| **Joins** | Native, optimized JOIN operations | No joins (or expensive application-level joins) |
| **Consistency** | Strong consistency by default | Eventual consistency by default (tunable in some) |
| **Query Language** | Standardized SQL | Database-specific APIs/query languages |
| **Normalization** | Normalized (3NF+), reduce redundancy | Denormalized, embed data for read performance |
| **Indexing** | B-Tree, Hash, GiST, GIN, BRIN (rich) | LSM-Tree, B-Tree (varies by engine) |

### The Core Trade-off
```
SQL:  Optimize for WRITE correctness & data integrity → read via JOINs
NoSQL: Optimize for READ performance & horizontal scale → denormalize at write
```

---

## 2. SQL Databases — When to Use What

### 2.1 PostgreSQL vs MySQL vs SQL Server vs Oracle

#### PostgreSQL — "The Developer's Database"

**Architecture:**
- Multi-process model (not multi-threaded like MySQL)
- MVCC (Multi-Version Concurrency Control) via tuple versioning
- WAL (Write-Ahead Logging) for crash recovery
- Extensible type system — you can create custom types, operators, index methods

**Strengths:**
- **Advanced Data Types:** JSONB, Arrays, HStore, Range types, UUID, INET/CIDR, Geometric types, tsvector (full-text)
- **JSONB:** Binary JSON with indexing (GIN indexes) — gives you document-store capabilities INSIDE a relational DB
- **Full ACID + Serializable Isolation:** True serializable snapshot isolation (SSI), not just "serializable" via locking
- **CTEs & Window Functions:** Recursive CTEs, LATERAL joins, advanced analytics
- **Partitioning:** Native declarative partitioning (range, list, hash) since v10
- **Foreign Data Wrappers (FDW):** Query external data sources (other DBs, CSV, APIs) as if they were local tables
- **PostGIS:** Gold standard for geospatial queries (used by Uber, Airbnb)
- **Pub/Sub:** LISTEN/NOTIFY for real-time event streaming
- **Logical Replication:** Table-level selective replication
- **Extensions Ecosystem:** TimescaleDB (time-series), Citus (distributed), pgvector (AI embeddings), pg_trgm (fuzzy search)

**Weaknesses:**
- Slower for simple read-heavy workloads compared to MySQL (process-per-connection overhead)
- VACUUM overhead — dead tuples from MVCC need periodic cleanup
- Connection handling — needs PgBouncer or similar connection pooler at scale
- Replication is async by default (can configure synchronous but at latency cost)

**Best For:**
- Complex queries, analytics, reporting
- JSONB hybrid relational+document workloads
- Geospatial (PostGIS)
- Financial systems needing true serializable isolation
- Systems needing custom types/extensions
- Time-series with TimescaleDB
- AI/ML vector search with pgvector

---

#### MySQL (InnoDB) — "The Web's Database"

**Architecture:**
- Multi-threaded, single-process model (lighter than Postgres per-connection)
- Pluggable storage engines (InnoDB default, MyISAM legacy)
- InnoDB: Clustered index on primary key (data stored in PK order)
- Group Replication for HA clusters

**Strengths:**
- **Raw Read Performance:** Faster for simple SELECT queries, especially PK lookups (clustered index)
- **Replication Maturity:** Master-slave, master-master, group replication — battle-tested at Facebook/Meta scale
- **Connection Handling:** Thread-per-connection is lighter; thread pooling in Enterprise edition
- **MySQL 8.0+:** Window functions, CTEs, JSON support, invisible indexes, descending indexes
- **InnoDB Cluster:** Built-in HA with Group Replication + MySQL Router + MySQL Shell
- **Widely Supported:** Every hosting provider, every ORM, every framework
- **Vitess:** Horizontal sharding layer (used by YouTube, Slack, GitHub)

**Weaknesses:**
- **Limited Data Types:** No native array, no range types, no geometric types (basic)
- **JSON Support:** Inferior to PostgreSQL's JSONB — no partial updates until 8.0, less efficient indexing
- **No True Serializable:** Default InnoDB "SERIALIZABLE" is actually "consistent snapshot + shared locks" — not true SSI
- **Partial SQL Standard:** No FULL OUTER JOIN, limited CHECK constraints (enforced only since 8.0.16)
- **No FDW Equivalent:** No native federation of external data sources
- **MVCC Implementation:** Uses undo logs (rollback segments) — long transactions can cause undo tablespace bloat

**Best For:**
- High-throughput web applications (CRUD-heavy)
- Read-heavy workloads with simple queries
- When you need mature, battle-tested replication
- SaaS with Vitess for multi-tenant sharding
- LAMP/LEMP stack applications
- When broad tooling/hosting support matters

---

#### When PostgreSQL vs MySQL — Decision Matrix

| Scenario | Winner | Why |
|---|---|---|
| Simple CRUD web app | **MySQL** | Faster simple reads, lighter connections |
| Complex analytics/reporting | **PostgreSQL** | Superior query planner, window functions, CTEs |
| JSONB/document hybrid | **PostgreSQL** | JSONB with GIN indexes is unmatched |
| Geospatial queries | **PostgreSQL** | PostGIS is the industry standard |
| Read replicas at massive scale | **MySQL** | Replication is more mature and battle-tested |
| Financial/banking transactions | **PostgreSQL** | True serializable isolation (SSI) |
| Multi-tenant SaaS sharding | **MySQL + Vitess** | Vitess is production-proven at YouTube scale |
| Full-text search (basic) | **PostgreSQL** | Native tsvector/tsquery with ranking |
| Time-series data | **PostgreSQL + TimescaleDB** | Best-in-class extension |
| AI vector similarity search | **PostgreSQL + pgvector** | Native vector operations |
| Maximum hosting compatibility | **MySQL** | Available everywhere |
| Stored procedure complexity | **PostgreSQL** | PL/pgSQL + PL/Python + PL/V8 (JavaScript) |
| Horizontal distributed SQL | **Tie** | Citus (PG) vs Vitess (MySQL) |

---

#### SQL Server — "The Enterprise Microsoft Stack"

**When to Use:**
- .NET/C# ecosystem — tight integration with Entity Framework, Azure
- Enterprise BI — SSRS, SSAS, SSIS (ETL) built-in
- Windows-centric infrastructure
- Need columnstore indexes for real-time analytics (HTAP)
- Always On Availability Groups for HA

**Unique Features:**
- In-memory OLTP (Hekaton engine)
- Columnstore indexes (columnar storage within row-store DB)
- Temporal tables (system-versioned, built-in time travel queries)
- PolyBase (query external data like Hadoop/S3)
- Graph tables (node/edge modeling within SQL Server)

---

#### Oracle — "The Legacy Enterprise Beast"

**When to Use:**
- Legacy enterprise systems already on Oracle
- RAC (Real Application Clusters) for shared-disk HA
- Extreme partitioning needs (11 partition types)
- Exadata hardware-software integration
- Multi-model: JSON, XML, Spatial, Graph, Text — all in one

**Why NOT Oracle for New Systems:**
- Licensing cost is prohibitive ($47,500/processor for Enterprise)
- Vendor lock-in (proprietary SQL extensions)
- PostgreSQL has closed the feature gap significantly

---

#### CockroachDB / TiDB / YugabyteDB — "NewSQL / Distributed SQL"

| Database | Based On | Best For |
|---|---|---|
| **CockroachDB** | PostgreSQL wire protocol | Global distribution, survive region failures |
| **TiDB** | MySQL wire protocol | MySQL-compatible distributed HTAP |
| **YugabyteDB** | PostgreSQL wire protocol | PostgreSQL-compatible distributed SQL |
| **Spanner** | Google proprietary | Global consistency with TrueTime (GPS+atomic clocks) |

**When to Choose Distributed SQL over Traditional SQL:**
- Need horizontal write scaling across regions
- Require survival of entire data center failures
- Need strong consistency across geographies
- Data exceeds what a single node can handle (multi-TB write-heavy)

**When NOT to Choose:**
- Latency-sensitive single-region apps (distributed consensus adds 5-15ms per write)
- Simple applications — operational complexity not worth it
- Cost-sensitive — 3-5x more infrastructure than single-node PostgreSQL

---

## 3. NoSQL Categories & When to Use What

### 3.1 Document Stores

#### MongoDB
**Data Model:** BSON documents (JSON-like), nested objects, arrays
**Architecture:** Replica sets (HA) + sharded clusters (scale-out)

**Strengths:**
- Flexible schema — different documents in same collection can have different fields
- Rich query language with aggregation pipeline
- Multi-document ACID transactions (since 4.0)
- Change Streams for real-time event-driven architectures
- Atlas Search (Lucene-based full-text search built-in)
- Time-series collections (since 5.0)

**Weaknesses:**
- No joins (use $lookup — expensive, single-threaded)
- Denormalization leads to data duplication and update anomalies
- WiredTiger storage engine compaction issues
- Memory-mapped I/O can cause OOM under pressure
- Transactions are slower than RDBMS (distributed 2PC overhead)

**Best For:**
- Content management systems
- Product catalogs with varying attributes
- User profiles / personalization
- Real-time analytics with aggregation pipeline
- Rapid prototyping / schema evolution
- Event sourcing

**NOT For:**
- Financial ledgers needing complex multi-table transactions
- Heavy relationship traversal
- Reporting with complex joins

---

#### Amazon DynamoDB
**Data Model:** Key-value + document (items up to 400KB)
**Architecture:** Fully managed, serverless, auto-scaling

**Strengths:**
- Single-digit millisecond latency at ANY scale
- Auto-scaling with on-demand capacity mode
- Global Tables (multi-region active-active)
- DAX (in-memory cache layer)
- Streams for CDC (Change Data Capture)
- Zero operational overhead

**Weaknesses:**
- 400KB item size limit
- Query only by partition key + sort key (or GSI/LSI)
- GSI eventual consistency only
- No server-side joins or aggregations
- Scan operations are expensive and slow
- Hot partition problem if key design is poor
- Vendor lock-in (AWS only)

**Best For:**
- Serverless architectures (Lambda + DynamoDB)
- Session stores, shopping carts
- Gaming leaderboards
- IoT device state management
- High-throughput key-value access patterns
- When you need zero-ops at scale

**Access Pattern Design:**
```
Single Table Design (critical for DynamoDB):
- Model ALL access patterns in ONE table
- Use composite sort keys: USER#123#ORDER#456
- Overload GSIs for different entity types
- Think about access patterns FIRST, schema SECOND
```

---

#### Couchbase
**When over MongoDB:** Need built-in caching (memcached protocol), SQL-like queries (N1QL), mobile sync (Couchbase Lite)

---

### 3.2 Key-Value Stores

#### Redis
**Data Model:** In-memory key-value with rich data structures (strings, hashes, lists, sets, sorted sets, streams, bitmaps, HyperLogLog, geospatial)

**Architecture:** Single-threaded event loop (6.x+ I/O multi-threading), master-replica, Redis Cluster (hash slots)

**Strengths:**
- Sub-millisecond latency (in-memory)
- Rich data structures beyond simple key-value
- Lua scripting for atomic operations
- Redis Streams (Kafka-like message streaming)
- Pub/Sub for real-time messaging
- Redis Search (full-text + vector search)
- Redis TimeSeries module

**Weaknesses:**
- Memory-bound (dataset must fit in RAM)
- Persistence options (RDB snapshots + AOF) have trade-offs
- Single-threaded command processing (6.x improves I/O only)
- Redis Cluster doesn't support multi-key operations across slots
- No built-in encryption at rest (Enterprise only)

**Best For:**
- Caching (most common use case)
- Session store
- Rate limiting (sliding window with sorted sets)
- Real-time leaderboards (sorted sets)
- Distributed locks (Redlock algorithm)
- Message queues (lists or streams)
- Counting/analytics (HyperLogLog for cardinality)
- Geospatial proximity queries

---

#### Memcached
**When over Redis:**
- Pure caching only (no persistence needed)
- Multi-threaded — better for simple cache on multi-core machines
- Memory efficiency for simple string key-value
- Simpler operational model

**When Redis over Memcached:**
- Need data structures beyond strings
- Need persistence
- Need pub/sub or streams
- Need atomic operations (INCR, sorted set operations)

---

### 3.3 Wide-Column Stores

#### Apache Cassandra
**Data Model:** Partitioned row store (partition key → clustering columns → cells)
**Architecture:** Masterless ring, consistent hashing, tunable consistency (ONE, QUORUM, ALL)

**Strengths:**
- Linear horizontal scalability (add nodes → proportional throughput increase)
- No single point of failure (masterless)
- Multi-datacenter replication built-in
- Tunable consistency per query
- Optimized for write-heavy workloads (LSM-tree storage)
- Time-series data with clustering columns (natural time ordering)

**Weaknesses:**
- **No joins, no subqueries, no aggregations** (by design)
- **Query-driven data modeling** — must design tables around queries, NOT around entities
- Read performance inferior to write (compaction, read amplification)
- Tombstone accumulation for deletes (can cause read latency spikes)
- Lightweight transactions (LWT/Paxos) are expensive — avoid if possible
- Operational complexity (compaction tuning, repair, anti-entropy)
- No secondary indexes that work well at scale (use materialized views or separate tables)

**Best For:**
- Write-heavy time-series (IoT sensor data, logs, metrics)
- Messaging systems (Discord uses Cassandra for messages)
- Activity feeds / timelines
- Multi-region deployments needing active-active
- Systems needing 99.99% write availability

**Data Modeling Rules:**
```
1. One table per query pattern (denormalize aggressively)
2. Partition key = how you query (equality)
3. Clustering columns = how you sort within a partition
4. Partition size < 100MB, < 100K rows
5. No multi-partition queries in hot paths
```

---

#### Apache HBase
**When over Cassandra:**
- Hadoop ecosystem integration (HDFS storage, MapReduce/Spark processing)
- Strong consistency needed (CP in CAP — Cassandra is AP)
- Random read/write on huge datasets with Hadoop batch processing
- Used by: Facebook Messenger (moved to MyRocks since)

---

#### Google Bigtable (Cloud)
- Managed wide-column store, HBase-compatible API
- Best for: massive analytical/time-series workloads on GCP
- Used by: Google Search, Maps, Gmail internally

---

### 3.4 Graph Databases

#### Neo4j
**Data Model:** Nodes, Relationships (edges), Properties on both
**Query Language:** Cypher

**Strengths:**
- Index-free adjacency (each node stores pointers to neighbors — O(1) traversal)
- Multi-hop relationship queries are orders of magnitude faster than SQL JOINs
- Cypher is intuitive for graph patterns
- ACID transactions

**Weaknesses:**
- Not horizontally scalable for writes (single-writer architecture in Community)
- Sharding a graph is NP-hard (graph partitioning problem)
- Not suitable for tabular/aggregation queries
- Limited ecosystem compared to relational DBs

**Best For:**
- Social networks (friends-of-friends, recommendations)
- Fraud detection (ring detection, anomaly patterns)
- Knowledge graphs
- Network/IT infrastructure mapping
- Authorization/access control (RBAC/ABAC graph traversal)
- Recommendation engines

**When NOT to Use a Graph DB:**
- Simple key-value lookups
- Aggregation/analytics queries
- When relationships are not the primary query pattern
- When data doesn't have deep interconnections (depth > 2-3 hops)

---

#### Amazon Neptune
- Managed graph DB supporting both property graph (Gremlin) and RDF (SPARQL)
- Better operational story than self-managed Neo4j
- Multi-AZ HA, up to 15 read replicas

#### Dgraph
- Distributed, horizontally scalable graph DB
- GraphQL-native query language
- Better horizontal scaling story than Neo4j

---

### 3.5 Time-Series Databases

| Database | Architecture | Best For |
|---|---|---|
| **InfluxDB** | Custom TSM engine | DevOps monitoring, IoT |
| **TimescaleDB** | PostgreSQL extension | When you need SQL + time-series |
| **Prometheus** | Pull-based, local storage | Kubernetes/infrastructure monitoring |
| **ClickHouse** | Column-oriented OLAP | Real-time analytics on time-series |
| **QuestDB** | Zero-GC Java, column-based | Ultra-low-latency time-series ingestion |

---

### 3.6 Search Engines (Often Grouped with NoSQL)

#### Elasticsearch
**Data Model:** Inverted index on JSON documents
**Architecture:** Distributed, sharded, replicated (Lucene-based)

**Strengths:**
- Full-text search with relevance scoring (BM25)
- Aggregations for real-time analytics
- Near-real-time indexing (~1 second)
- Horizontal scaling
- ELK stack (Elasticsearch + Logstash + Kibana)

**Weaknesses:**
- Not a primary data store (no transactions, eventual consistency)
- Memory-hungry (JVM heap + OS page cache)
- Re-indexing for schema changes is painful
- Split-brain risk in older versions
- Write amplification from Lucene segment merges

**Best For:**
- Full-text search (e-commerce product search)
- Log analytics (ELK stack)
- Application Performance Monitoring
- Autocomplete/typeahead
- Geospatial search

---

## 4. NoSQL Limitations (What NoSQL Lacks)

### 4.1 ACID Transactions (Multi-Entity)

```
SQL:
BEGIN;
  UPDATE accounts SET balance = balance - 100 WHERE id = 1;
  UPDATE accounts SET balance = balance + 100 WHERE id = 2;
COMMIT;  -- atomic, all-or-nothing

NoSQL (most):
  → Two separate operations, NO atomicity guarantee across documents
  → Application must handle partial failures, compensating transactions (Saga pattern)
  → MongoDB 4.0+ has multi-doc transactions but with performance penalty
  → DynamoDB has TransactWriteItems but limited to 100 items, 4MB
```

**Impact:** Any system needing money transfers, inventory management, booking systems with multi-entity consistency MUST carefully evaluate NoSQL transaction limitations.

---

### 4.2 JOIN Operations

```
SQL:
SELECT u.name, o.total 
FROM users u JOIN orders o ON u.id = o.user_id
WHERE o.date > '2025-01-01';
-- Executed by query optimizer with hash join, merge join, or nested loop

NoSQL:
  → Denormalize: embed orders inside user document
  → Problem: user document grows unboundedly
  → Alternative: application-level joins (N+1 query problem)
  → MongoDB $lookup: single-threaded, no indexing on joined collection filter
```

**Impact:** Any system with complex relational queries across multiple entities suffers in NoSQL. Reporting/analytics almost always needs SQL.

---

### 4.3 Data Integrity & Constraints

| Feature | SQL | NoSQL |
|---|---|---|
| Primary Key | Enforced | Application-level |
| Foreign Key | Enforced with CASCADE | Non-existent |
| UNIQUE | Enforced | Partial (MongoDB unique index) |
| CHECK constraints | Enforced | Application-level |
| NOT NULL | Enforced | Application-level (schema validation) |
| Triggers | Database-level | Application-level (Change Streams) |

**Impact:** Data quality depends entirely on application code in NoSQL. A bug in one microservice can corrupt data with no DB-level safety net.

---

### 4.4 Ad-Hoc Querying

```
SQL:  Any query on any column combination — query planner optimizes
NoSQL: Can only query efficiently on indexed/partitioned fields
       DynamoDB: ONLY partition key + sort key (or GSI)
       Cassandra: ONLY partition key + clustering columns
       → New query pattern = new table/index (schema redesign)
```

**Impact:** Business analysts can't just "write a query" against NoSQL. Need ETL pipeline to a data warehouse.

---

### 4.5 Standardization

- SQL is ISO standardized — skills transfer across PostgreSQL, MySQL, SQL Server
- NoSQL: every database has its own API, query language, driver, operational model
- Switching from MongoDB to DynamoDB is a complete rewrite
- Switching from PostgreSQL to MySQL is mostly syntax adjustments

---

### 4.6 Mature Tooling & Ecosystem

- SQL: decades of BI tools, ETL tools, ORMs, admin tools, monitoring
- NoSQL: tooling is fragmented, less mature, vendor-specific

---

## 5. CAP Theorem & Database Positioning

```
        Consistency
           /\
          /  \
         /    \
        /  CA  \
       / (RDBMS)\
      /    SQL   \
     /____________\
    CP            AP
  (HBase,       (Cassandra,
   MongoDB*,     DynamoDB,
   Redis*)       CouchDB,
                  Riak)

* MongoDB: CP when reading from primary
* Redis Cluster: CP with cluster-bus, but can lose writes on failover

PACELC Extension (more useful):
- If Partition → choose A or C
- Else (normal operation) → choose Latency or Consistency

PostgreSQL:  PA/EC (Available during partition, Consistent normally)
Cassandra:   PA/EL (Available during partition, Low Latency normally)
DynamoDB:    PA/EL (tunable — can choose strong consistency per read)
MongoDB:     PC/EC (Consistent during partition, Consistent normally)
```

---

## 6. Decision Framework

### Step-by-Step Architect Decision Tree

```
1. What are your access patterns?
   ├── Complex joins, aggregations, ad-hoc queries → SQL
   ├── Simple key-value lookups at massive scale → NoSQL (KV/Document)
   ├── Time-ordered data, append-heavy → Time-Series DB or Wide-Column
   ├── Deep relationship traversal (3+ hops) → Graph DB
   └── Full-text search with relevance → Search Engine (+ primary DB)

2. What are your consistency requirements?
   ├── Strong consistency (financial, inventory) → SQL or CP NoSQL
   ├── Eventual consistency acceptable → AP NoSQL (Cassandra, DynamoDB)
   └── Tunable per-operation → DynamoDB, Cassandra

3. What is your scale?
   ├── < 1TB data, < 10K QPS → Single PostgreSQL/MySQL (don't over-engineer)
   ├── 1-10TB, 10K-100K QPS → SQL with read replicas + caching
   ├── 10TB+, 100K+ QPS → Consider NoSQL or Distributed SQL
   └── Multi-region active-active → DynamoDB Global Tables / Cassandra / CockroachDB

4. What is your read:write ratio?
   ├── Read-heavy (90%+ reads) → SQL + Redis cache / Read replicas
   ├── Write-heavy (50%+ writes) → Cassandra / DynamoDB / ScyllaDB
   └── Balanced → Evaluate based on other factors

5. Schema stability?
   ├── Stable, well-defined entities → SQL
   ├── Rapidly evolving, unstructured → Document DB
   └── Hybrid → PostgreSQL with JSONB columns
```

### The "PostgreSQL First" Rule
> **Start with PostgreSQL unless you have a SPECIFIC reason not to.** PostgreSQL with JSONB gives you 80% of MongoDB's flexibility with 100% of SQL's power. Add specialized databases only when PostgreSQL becomes the bottleneck for a specific access pattern.

---

## 7. Real-World System Design Mappings

| System | Primary DB | Why | Supporting DBs |
|---|---|---|---|
| **Twitter/X** | MySQL (Manhattan) | Timeline storage, tweet metadata | Redis (cache), Elasticsearch (search), FlockDB (graph) |
| **Instagram** | PostgreSQL | Relational data, mature | Redis (cache), Cassandra (feed) |
| **Uber** | MySQL + Schemaless | Trip data, geospatial | Redis (cache), Cassandra (logging), PostgreSQL (geospatial) |
| **Netflix** | Cassandra | Viewing history, billions of rows | EVCache (Redis), Elasticsearch, CockroachDB (billing) |
| **Discord** | Cassandra → ScyllaDB | Messages (write-heavy, time-ordered) | Redis (presence), PostgreSQL (metadata) |
| **Airbnb** | MySQL | Bookings, listings | Redis (cache), Elasticsearch (search), PostgreSQL (PostGIS) |
| **Stripe** | PostgreSQL | Financial transactions need ACID | Redis (idempotency keys), MongoDB (API logs) |
| **LinkedIn** | Espresso (custom) | Member profiles, feed | Voldemort (KV), Pinot (analytics), Kafka |
| **Shopify** | MySQL + Vitess | Multi-tenant SaaS sharding | Redis (cache), Elasticsearch (product search) |
| **WhatsApp** | Mnesia (Erlang) | Message delivery | Cassandra (long-term storage) |

---

## 8. Interview Quick-Fire Answers

### Q: "When would you choose MongoDB over PostgreSQL?"
**A:** When schema evolves rapidly (e.g., product catalog with varying attributes), when I need horizontal write scaling beyond a single node, or when the data is naturally document-shaped with deep nesting and I rarely need cross-document joins.

### Q: "When would you choose Cassandra over DynamoDB?"
**A:** When I need multi-cloud/on-prem deployment (DynamoDB is AWS-only), when I need more control over compaction/consistency tuning, or when I want to avoid vendor lock-in. DynamoDB wins for zero-ops serverless and when already committed to AWS.

### Q: "Can NoSQL replace SQL entirely?"
**A:** No. NoSQL lacks enforced referential integrity, standardized query language, efficient multi-entity transactions, and ad-hoc query capability. For any system requiring data correctness guarantees (finance, healthcare, inventory), SQL remains essential. The modern approach is polyglot persistence — right DB for the right access pattern.

### Q: "Why not just use PostgreSQL for everything?"
**A:** You can, up to a point. PostgreSQL handles 90% of use cases well (JSONB for documents, PostGIS for geo, TimescaleDB for time-series, pgvector for AI). But at extreme scale (millions of QPS, petabytes), specialized databases outperform. Redis for sub-millisecond caching, Cassandra for massive write throughput, Elasticsearch for full-text search — these are optimized for their specific patterns in ways a general-purpose DB can't match.

### Q: "How do you handle the need for both SQL and NoSQL in one system?"
**A:** Polyglot persistence with event-driven sync:
```
Write path: Service → PostgreSQL (source of truth)
Async sync: PostgreSQL CDC (Debezium) → Kafka → Elasticsearch (search)
                                              → Redis (cache)
                                              → DynamoDB (high-throughput reads)
```
This gives you strong consistency for writes and optimized reads from specialized stores.

### Q: "MySQL or PostgreSQL for a new startup?"
**A:** PostgreSQL, unless your team has deep MySQL expertise. PostgreSQL gives you more room to grow — JSONB for flexible data, PostGIS if you add location features, better analytics, better standards compliance. MySQL is fine too, but PostgreSQL's feature trajectory is superior.

### Q: "When to use Redis vs Memcached?"
**A:** Redis always, unless you ONLY need a simple string cache and want multi-threaded performance on a single node. Redis gives you data structures (sorted sets for leaderboards, streams for queues, pub/sub for real-time) — it's not just a cache, it's a data structure server.

### Q: "DynamoDB single-table design — is it always the right approach?"
**A:** No. Single-table design maximizes DynamoDB efficiency (fewer round trips, lower cost) but sacrifices readability and maintainability. For simple applications or teams unfamiliar with DynamoDB, multi-table design is acceptable with slightly higher cost. Single-table is essential only at scale where every millisecond and every RCU/WCU matters.

---

## Appendix: Quick Reference Cheat Sheet

```
Need ACID + complex queries          → PostgreSQL
Need simple CRUD at scale            → MySQL + Vitess
Need flexible schema + documents     → MongoDB
Need key-value at sub-ms latency     → Redis
Need massive write throughput        → Cassandra / ScyllaDB
Need serverless zero-ops             → DynamoDB
Need graph traversal                 → Neo4j / Neptune
Need full-text search                → Elasticsearch / OpenSearch
Need time-series                     → TimescaleDB / InfluxDB
Need analytics/OLAP                  → ClickHouse / BigQuery / Redshift
Need distributed SQL                 → CockroachDB / YugabyteDB / TiDB
Need global consistency              → Google Spanner / CockroachDB
Need geospatial                      → PostgreSQL + PostGIS
Need AI vector search                → PostgreSQL + pgvector / Pinecone / Weaviate
Need message queue (lightweight)     → Redis Streams
Need event streaming                 → Apache Kafka (not a DB, but often in the architecture)
```

---

*Last updated: April 2026*

