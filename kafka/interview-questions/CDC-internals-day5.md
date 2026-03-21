# CDC Deep Dive: How Debezium Captures Data Internally

## 🎯 Understanding CDC at the Deepest Level

This document explains **exactly how** Change Data Capture works internally, how Debezium watches tables, and the impact on database performance.

---

## Table of Contents
1. [What is CDC Internally](#what-is-cdc-internally)
2. [How Debezium Works - Architecture](#how-debezium-works---architecture)
3. [MySQL CDC - Binlog Deep Dive](#mysql-cdc---binlog-deep-dive)
4. [PostgreSQL CDC - WAL Deep Dive](#postgresql-cdc---wal-deep-dive)
5. [How Table Watching Works](#how-table-watching-works)
6. [Performance Impact on Database](#performance-impact-on-database)
7. [Debezium Internal State Management](#debezium-internal-state-management)
8. [Handling Schema Changes](#handling-schema-changes)
9. [Exactly-Once Guarantees](#exactly-once-guarantees)
10. [Comparison: Polling vs CDC](#comparison-polling-vs-cdc)

---

## What is CDC Internally?

### Traditional Database Replication

All databases maintain a **transaction log** for crash recovery and replication:

```
Application → SQL Statement → Database Engine
                                    ↓
                              1. Execute SQL
                              2. Write to data files
                              3. Write to transaction log (for recovery)
```

**Transaction Log Purpose:**
- **Crash Recovery**: Replay log after crash
- **Replication**: Send log to replicas
- **Point-in-Time Recovery**: Restore to specific time

**CDC Insight:** 
> Why write custom triggers when the database already logs ALL changes? 
> Just read the transaction log!

---

### How Transaction Logs Work

**Every database change follows this flow:**

```
┌─────────────────────────────────────────────────┐
│ Application executes:                           │
│ UPDATE users SET name='Alice' WHERE id=1        │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│ Database Engine:                                │
│ 1. Acquires lock on row                        │
│ 2. Reads old value: {id:1, name:'Bob'}         │
│ 3. Writes new value: {id:1, name:'Alice'}      │
│ 4. Writes to Transaction Log (WAL/binlog):     │
│    - LSN/Position: 12345                       │
│    - Operation: UPDATE                          │
│    - Table: users                               │
│    - Before: {id:1, name:'Bob'}                │
│    - After: {id:1, name:'Alice'}               │
│ 5. Commits transaction                          │
│ 6. Releases lock                                │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│ Transaction Log (append-only):                  │
│ [LSN:12344] INSERT INTO orders...              │
│ [LSN:12345] UPDATE users SET name...  ← HERE   │
│ [LSN:12346] DELETE FROM products...            │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│ CDC Connector (Debezium):                      │
│ Reads transaction log sequentially             │
│ Parses log entry at LSN 12345                  │
│ Converts to Kafka event                        │
│ Sends to Kafka topic                           │
└─────────────────────────────────────────────────┘
```

**Key Point:** Database already writes EVERYTHING to transaction log. CDC just reads it!

---

## How Debezium Works - Architecture

### High-Level Architecture

```
┌──────────────────────────────────────────────────────────┐
│                    MySQL Database                         │
│  ┌────────────────┐        ┌──────────────────┐         │
│  │  Data Files    │        │  Binary Log      │         │
│  │  (InnoDB)      │◄───────│  (binlog)        │         │
│  └────────────────┘        └──────────────────┘         │
│         ▲                           │                     │
│         │                           │ Read-only           │
│    App writes                       │ (no locks!)         │
└─────────┼───────────────────────────┼─────────────────────┘
          │                           ▼
          │                  ┌─────────────────┐
          │                  │  Debezium       │
          │                  │  Connector      │
          │                  │  (Kafka Connect)│
          │                  └─────────────────┘
          │                           │
          │                           ▼
          │                  ┌─────────────────┐
          │                  │  Kafka Topic    │
          │                  │  mysql.db.table │
          │                  └─────────────────┘
          │                           │
          │                           ▼
          │                  ┌─────────────────┐
          └──────────────────│  Consumer Apps  │
                             └─────────────────┘
```

### Debezium Components

**1. MySQL Binlog Reader:**
```java
// Simplified internal flow
class MySqlBinlogReader {
    BinlogClient client;
    
    void start() {
        // Connect to MySQL as replication client
        client = new BinlogClient(host, port, user, password);
        
        // Register for binlog events
        client.registerEventListener(event -> {
            if (event instanceof WriteRowsEvent) {
                handleInsert(event);
            } else if (event instanceof UpdateRowsEvent) {
                handleUpdate(event);
            } else if (event instanceof DeleteRowsEvent) {
                handleDelete(event);
            }
        });
        
        // Start reading from saved position
        client.setBinlogFilename(lastBinlogFile);
        client.setBinlogPosition(lastPosition);
        client.connect();
    }
    
    void handleUpdate(UpdateRowsEvent event) {
        String database = event.getDatabaseName();
        String table = event.getTableName();
        
        for (Map.Entry<Serializable[], Serializable[]> row : event.getRows()) {
            Serializable[] before = row.getKey();
            Serializable[] after = row.getValue();
            
            // Create CDC event
            ChangeEvent changeEvent = new ChangeEvent();
            changeEvent.before = before;
            changeEvent.after = after;
            changeEvent.op = "u";  // update
            changeEvent.source = createSourceInfo(event);
            
            // Send to Kafka
            sendToKafka(database, table, changeEvent);
        }
    }
}
```

**2. Offset Storage:**
```
Debezium tracks position in transaction log:

MySQL: {binlog_file: "mysql-bin.000003", position: 154}
PostgreSQL: {lsn: 123456789}
MongoDB: {timestamp: 1234567890}

Stored in Kafka topic: connect-offsets
Enables resume from exact position after restart
```

---

## MySQL CDC - Binlog Deep Dive

### What is MySQL Binary Log (binlog)?

**Binary log** = Append-only file containing all database changes.

**Structure:**

```
Binary Log Files:
/var/lib/mysql/
  ├── mysql-bin.000001  (rotated, 1 GB)
  ├── mysql-bin.000002  (rotated, 1 GB)
  ├── mysql-bin.000003  (active, 500 MB)
  └── mysql-bin.index   (index of all binlog files)
```

**Binlog Entry Format:**

```
Position: 154
Timestamp: 2024-03-21 10:30:45
Server ID: 1
Event Type: UPDATE_ROWS_EVENT
Database: inventory
Table: customers
Columns: [id, name, email, updated_at]
Before: [1, 'Bob', 'bob@example.com', '2024-03-20 10:00:00']
After:  [1, 'Robert', 'robert@example.com', '2024-03-21 10:30:45']
```

### How Debezium Reads Binlog

**Step-by-Step Process:**

**1. Connect as Replication Client:**

```sql
-- Debezium connects with special privileges
GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'debezium'@'%';
GRANT SELECT ON *.* TO 'debezium'@'%';  -- For initial snapshot

-- MySQL sees Debezium as a replica
SHOW SLAVE HOSTS;
-- Output: Debezium connector listed as replica
```

**2. Initial Snapshot (First Time):**

```
When connector starts for first time:

1. Lock tables (FLUSH TABLES WITH READ LOCK)
2. Read current binlog position
3. Take snapshot of existing data
   - SELECT * FROM customers
   - SELECT * FROM orders
   - ... for all tables
4. Send snapshot as INSERT events to Kafka
5. Release locks
6. Start reading binlog from saved position

Total lock time: ~seconds to minutes (depending on data size)
```

**3. Continuous Binlog Reading:**

```java
// Pseudo-code of internal process
while (true) {
    Event event = binlogClient.getNextEvent();
    
    switch (event.getType()) {
        case ROTATE_EVENT:
            // Binlog file rotated
            currentBinlogFile = event.getNextBinlogFilename();
            currentPosition = 4;  // Start of new file
            break;
            
        case TABLE_MAP_EVENT:
            // Maps table ID to table name
            tableMap.put(event.getTableId(), event.getTableName());
            break;
            
        case WRITE_ROWS_EVENT:
            // INSERT
            processInsert(event);
            break;
            
        case UPDATE_ROWS_EVENT:
            // UPDATE
            processUpdate(event);
            break;
            
        case DELETE_ROWS_EVENT:
            // DELETE
            processDelete(event);
            break;
            
        case XID_EVENT:
            // Transaction commit
            commitTransaction();
            break;
    }
    
    // Save position after each batch
    saveOffset(currentBinlogFile, currentPosition);
}
```

**4. Example Binlog Read:**

```
MySQL executes:
UPDATE customers SET email='new@example.com' WHERE id=1;

Binlog Entry Created:
┌─────────────────────────────────────────┐
│ Position: 154                           │
│ Timestamp: 1711014645000 (milliseconds) │
│ Server ID: 1                            │
│ Event: UPDATE_ROWS_EVENT                │
│ Table ID: 108                           │
│ Flags: 0                                │
│ Extra Data: NULL                        │
│ Column Count: 4                         │
│ Columns Used (before): [1,1,1,1]        │
│ Columns Used (after): [1,1,1,1]         │
│ Rows:                                   │
│   Before: [1, 'Bob', 'old@example.com', │
│            '2024-03-20 10:00:00']       │
│   After:  [1, 'Bob', 'new@example.com', │
│            '2024-03-21 10:30:45']       │
└─────────────────────────────────────────┘

Debezium Reads:
1. Opens binlog file at position 154
2. Parses binary format
3. Extracts table mapping (ID 108 = customers)
4. Reads before/after values
5. Creates Kafka event

Kafka Event Produced:
{
  "before": {
    "id": 1,
    "name": "Bob",
    "email": "old@example.com",
    "updated_at": "2024-03-20T10:00:00Z"
  },
  "after": {
    "id": 1,
    "name": "Bob",
    "email": "new@example.com",
    "updated_at": "2024-03-21T10:30:45Z"
  },
  "source": {
    "version": "2.1.0",
    "connector": "mysql",
    "name": "dbserver1",
    "ts_ms": 1711014645000,
    "snapshot": "false",
    "db": "inventory",
    "table": "customers",
    "server_id": 1,
    "file": "mysql-bin.000003",
    "pos": 154,
    "row": 0
  },
  "op": "u",
  "ts_ms": 1711014645743
}
```

### MySQL Binlog Configuration

**Required MySQL Settings:**

```ini
# my.cnf or my.ini
[mysqld]
server-id = 1
log_bin = mysql-bin
binlog_format = ROW          # REQUIRED! (not STATEMENT or MIXED)
binlog_row_image = FULL      # Capture before AND after values
expire_logs_days = 7         # Keep binlog for 7 days
max_binlog_size = 1G         # Rotate at 1 GB
```

**Why ROW format?**

```
STATEMENT format:
  Binlog: UPDATE customers SET email='new@example.com' WHERE id=1;
  Problem: Only SQL statement, no actual values!
  
ROW format:
  Binlog: Before: [1, 'Bob', 'old@...'], After: [1, 'Bob', 'new@...']
  Advantage: Complete before/after values ✓
```

---

## PostgreSQL CDC - WAL Deep Dive

### What is PostgreSQL WAL (Write-Ahead Log)?

**WAL** = Write-Ahead Log, similar to MySQL binlog but different format.

**Structure:**

```
WAL Files:
/var/lib/postgresql/data/pg_wal/
  ├── 000000010000000000000001  (16 MB segment)
  ├── 000000010000000000000002  (16 MB segment)
  ├── 000000010000000000000003  (active)
  └── archive/                  (archived WAL files)
```

**WAL Entry Format:**

```
LSN (Log Sequence Number): 0/16B9010
Transaction ID: 12345
Resource Manager: Heap
Operation: UPDATE
Relation: inventory.customers (OID: 16384)
Old Tuple: [1, 'Bob', 'bob@example.com']
New Tuple: [1, 'Robert', 'robert@example.com']
```

### PostgreSQL Logical Decoding

PostgreSQL uses **logical decoding** to convert WAL to readable format.

**How It Works:**

```
┌────────────────────────────────────────┐
│ PostgreSQL Database                    │
│  ┌──────────┐      ┌─────────────┐    │
│  │ Data     │      │ WAL Files   │    │
│  │ Files    │◄─────│ (binary)    │    │
│  └──────────┘      └─────────────┘    │
│                           │            │
│                           ▼            │
│                    ┌─────────────┐    │
│                    │ Logical     │    │
│                    │ Decoding    │    │
│                    │ Plugin      │    │
│                    │ (pgoutput)  │    │
│                    └─────────────┘    │
│                           │            │
└───────────────────────────┼────────────┘
                            ▼
                   ┌─────────────┐
                   │ Debezium    │
                   │ Connector   │
                   └─────────────┘
                            │
                            ▼
                   ┌─────────────┐
                   │ Kafka       │
                   └─────────────┘
```

**Logical Decoding Plugins:**

1. **pgoutput** (built-in, PostgreSQL 10+)
2. **wal2json** (JSON output)
3. **decoderbufs** (Protocol Buffers)

**Debezium Setup:**

```sql
-- 1. Enable logical replication
ALTER SYSTEM SET wal_level = logical;
ALTER SYSTEM SET max_replication_slots = 4;
ALTER SYSTEM SET max_wal_senders = 4;

-- Restart PostgreSQL

-- 2. Create replication slot (Debezium does this automatically)
SELECT pg_create_logical_replication_slot('debezium', 'pgoutput');

-- 3. Grant permissions
GRANT SELECT ON ALL TABLES IN SCHEMA public TO debezium_user;
GRANT USAGE ON SCHEMA public TO debezium_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO debezium_user;
```

**How Debezium Reads WAL:**

```java
// Simplified internal flow
class PostgresConnector {
    ReplicationConnection replConn;
    
    void start() {
        // Connect to replication slot
        replConn = createReplicationConnection();
        replConn.setAutoCommit(false);
        
        // Create stream from replication slot
        PGReplicationStream stream = replConn
            .replicationStream()
            .logical()
            .withSlotName("debezium")
            .withStartPosition(lastLSN)
            .withSlotOption("proto_version", "1")
            .withSlotOption("publication_names", "dbz_publication")
            .start();
        
        // Read changes
        while (true) {
            ByteBuffer buffer = stream.readPending();
            
            if (buffer == null) {
                Thread.sleep(100);
                continue;
            }
            
            // Decode logical replication message
            LogicalMsg msg = decode(buffer);
            
            switch (msg.getType()) {
                case BEGIN:
                    startTransaction(msg.getXid());
                    break;
                case INSERT:
                    handleInsert(msg);
                    break;
                case UPDATE:
                    handleUpdate(msg);
                    break;
                case DELETE:
                    handleDelete(msg);
                    break;
                case COMMIT:
                    commitTransaction();
                    break;
            }
            
            // Acknowledge processing
            stream.setAppliedLSN(msg.getLSN());
            stream.setFlushedLSN(msg.getLSN());
        }
    }
}
```

**Example WAL Decoding Output:**

```
PostgreSQL executes:
UPDATE customers SET email='new@example.com' WHERE id=1;

WAL Entry (binary):
0/16B9010: UPDATE customers (before: [1,'Bob','old@...'], after: [1,'Bob','new@...'])

Logical Decoding (pgoutput):
{
  "action": "U",
  "schema": "public",
  "table": "customers",
  "columns": [
    {"name": "id", "type": "integer", "value": 1},
    {"name": "name", "type": "text", "value": "Bob"},
    {"name": "email", "type": "text", "value": "new@example.com"}
  ],
  "oldkeys": {
    "id": 1,
    "email": "old@example.com"
  }
}

Debezium Converts to Kafka Event:
{
  "before": {
    "id": 1,
    "name": "Bob",
    "email": "old@example.com"
  },
  "after": {
    "id": 1,
    "name": "Bob",
    "email": "new@example.com"
  },
  "source": {
    "version": "2.1.0",
    "connector": "postgresql",
    "name": "dbserver1",
    "ts_ms": 1711014645000,
    "db": "inventory",
    "schema": "public",
    "table": "customers",
    "txId": 12345,
    "lsn": 24289296,
    "xmin": null
  },
  "op": "u",
  "ts_ms": 1711014645743
}
```

---

## How Table Watching Works

### Table Filtering Configuration

**Debezium can watch:**
- All tables in database
- Specific tables only
- Tables matching patterns

**Configuration Examples:**

```json
{
  "name": "inventory-connector",
  "config": {
    "connector.class": "io.debezium.connector.mysql.MySqlConnector",
    
    // Option 1: Include specific databases
    "database.include.list": "inventory,orders",
    
    // Option 2: Exclude databases
    "database.exclude.list": "test,temp",
    
    // Option 3: Include specific tables
    "table.include.list": "inventory.customers,inventory.orders",
    
    // Option 4: Regex patterns
    "table.include.list": "inventory\\..*,orders\\..*",
    
    // Option 5: Exclude specific tables
    "table.exclude.list": "inventory.temp_.*",
    
    // Option 6: Column filtering
    "column.include.list": "inventory.customers.id,inventory.customers.name",
    "column.exclude.list": "inventory.customers.password"
  }
}
```

### Internal Filtering Mechanism

**How filtering works internally:**

```java
class EventFilter {
    List<Pattern> includePatterns;
    List<Pattern> excludePatterns;
    
    boolean shouldCapture(String database, String table) {
        String fullName = database + "." + table;
        
        // Check exclude first
        for (Pattern exclude : excludePatterns) {
            if (exclude.matcher(fullName).matches()) {
                return false;  // Excluded
            }
        }
        
        // Check include
        if (includePatterns.isEmpty()) {
            return true;  // Include all if no patterns specified
        }
        
        for (Pattern include : includePatterns) {
            if (include.matcher(fullName).matches()) {
                return true;  // Included
            }
        }
        
        return false;  // Not in include list
    }
}

// Usage in binlog reader
void handleEvent(BinlogEvent event) {
    String database = event.getDatabaseName();
    String table = event.getTableName();
    
    if (!filter.shouldCapture(database, table)) {
        // Skip this event
        return;
    }
    
    // Process event
    processEvent(event);
}
```

**Performance Optimization:**

```
Without filtering:
- Read ALL binlog events
- Filter after reading
- Still processes unwanted events

With table.include.list:
- Read ALL binlog events (can't skip at binlog level)
- Filter immediately in memory
- Don't process unwanted events
- Still some overhead

Best practice:
- Use separate databases for CDC-enabled vs non-CDC tables
- Minimize binlog writes on non-CDC tables
```

### Schema Registry Integration

**Debezium tracks table schema changes:**

```
When table schema changes:
ALTER TABLE customers ADD COLUMN phone VARCHAR(20);

Debezium detects:
1. Reads new table structure from database
2. Updates schema in Kafka Schema Registry
3. Continues capturing with new schema
4. Old events still readable (backward compatible)
```

**Schema History Topic:**

```
Debezium uses special Kafka topic to store schema history:

Topic: dbserver1.schema-changes

Events:
{
  "source": {...},
  "databaseName": "inventory",
  "ddl": "ALTER TABLE customers ADD COLUMN phone VARCHAR(20)",
  "tableChanges": [
    {
      "type": "ALTER",
      "id": "inventory.customers",
      "table": {
        "columns": [
          {"name": "id", "type": "INT"},
          {"name": "name", "type": "VARCHAR"},
          {"name": "email", "type": "VARCHAR"},
          {"name": "phone", "type": "VARCHAR"}  ← NEW!
        ]
      }
    }
  ]
}

Used for:
- Recovery after connector restart
- Schema evolution tracking
- Debugging schema mismatches
```

---

## Performance Impact on Database

### Minimal Impact - Here's Why

**1. Read-Only Access:**

```
Debezium NEVER writes to database:
- No INSERT/UPDATE/DELETE from connector
- No triggers needed
- No additional tables created
- Just reads transaction log

Impact: ~0% write overhead
```

**2. Transaction Log Already Exists:**

```
Database writes to transaction log anyway:
- For crash recovery
- For replication
- CDC just piggybacks on existing mechanism

Additional overhead: Reading log (minimal I/O)
```

**3. No Table Locks:**

```
During continuous capture:
- No locks acquired
- No blocking of application queries
- Reads historical log data

Exception: Initial snapshot (one-time)
- Short read lock during snapshot
- Configurable lock-free snapshot modes
```

### Measurable Performance Impact

**Typical Benchmarks:**

```
Database: MySQL 8.0
Load: 10,000 writes/sec
Tables: 50 tables, 1 million rows each

Without CDC:
- CPU: 40%
- Memory: 4 GB
- Disk I/O: 100 MB/s

With Debezium CDC:
- CPU: 42% (+5%)
- Memory: 4.2 GB (+5%)
- Disk I/O: 105 MB/s (+5%)
- Binlog read: 2 MB/s

Latency impact: < 1ms (negligible)
```

**What Causes the Overhead:**

```
1. Binlog Retention (Storage):
   - Without CDC: Binlog rotated every day
   - With CDC: Keep binlog until Debezium processes
   - Storage: +10-50 GB depending on change rate

2. Network Bandwidth:
   - Binlog streamed to Debezium connector
   - Bandwidth: ~1-10 MB/s for typical workload

3. Database CPU:
   - One additional replication client connection
   - Negligible CPU for log reading
```

### Optimization Techniques

**1. Binlog Configuration:**

```ini
# Minimize binlog size
binlog_row_image = MINIMAL  # Only changed columns (not FULL)
sync_binlog = 0             # Don't fsync every transaction (faster, less durable)
expire_logs_days = 1        # Rotate aggressively

Trade-off:
- MINIMAL: Smaller binlog, can't see unchanged columns
- Debezium recommendation: FULL (for complete before/after)
```

**2. Network Optimization:**

```json
{
  "max.batch.size": "2048",           // Batch more events
  "max.queue.size": "8192",           // Larger queue
  "poll.interval.ms": "100",          // Less frequent polling
  "tombstones.on.delete": "false"     // Skip tombstone events
}
```

**3. Snapshot Optimization:**

```json
{
  "snapshot.mode": "when_needed",     // Only if no offset
  "snapshot.locking.mode": "none",    // No locks (requires REPEATABLE READ)
  "snapshot.max.threads": "4",        // Parallel snapshot
  "snapshot.fetch.size": "10000"      // Larger batches
}
```

---

## Debezium Internal State Management

### Offset Storage

**What Debezium Tracks:**

```
MySQL:
{
  "binlog_filename": "mysql-bin.000003",
  "binlog_position": 154,
  "gtid_set": "3E11FA47-71CA-11E1-9E33-C80AA9429562:1-5",
  "snapshot": true/false
}

PostgreSQL:
{
  "lsn": 24289296,
  "txId": 12345,
  "snapshot": true/false
}

MongoDB:
{
  "timestamp": {
    "sec": 1234567890,
    "inc": 1
  },
  "order": 1
}
```

**Where Offsets are Stored:**

```
Kafka Connect Distributed Mode:
  Topic: connect-offsets (compacted)
  Key: ["debezium-connector", {"server": "dbserver1"}]
  Value: {binlog_filename, position, ...}

Kafka Connect Standalone Mode:
  File: /tmp/connect.offsets

Used for:
1. Resume after connector restart
2. Exactly-once processing
3. Avoid reprocessing events
```

### Transaction Boundary Handling

**Debezium groups events by transaction:**

```
MySQL Transaction:
BEGIN;
  INSERT INTO orders VALUES (1, 100);
  UPDATE inventory SET qty = qty - 1 WHERE product_id = 1;
  INSERT INTO order_items VALUES (1, 1, 1);
COMMIT;

Debezium Produces 3 Events in Order:
1. {op: "c", table: "orders", ...}
2. {op: "u", table: "inventory", ...}
3. {op: "c", table: "order_items", ...}

All events have same transaction ID:
  "source": {"txId": 12345, ...}

Consumers can:
- Process events in order
- Know transaction boundaries
- Implement exactly-once semantics
```

---

## Handling Schema Changes

### DDL Capture

**When table schema changes:**

```sql
-- DBA executes
ALTER TABLE customers ADD COLUMN phone VARCHAR(20);

Debezium Captures:
1. DDL statement from binlog
2. New table structure
3. Writes to schema history topic
4. Updates internal schema cache
5. Future events use new schema

No connector restart needed! ✓
```

**Schema Evolution Patterns:**

```
1. Add Column:
   Before: {id, name, email}
   After:  {id, name, email, phone}
   Impact: New field appears in future events
   
2. Drop Column:
   Before: {id, name, email, phone}
   After:  {id, name, email}
   Impact: Field disappears from future events
   
3. Rename Column:
   Before: {id, name, email}
   After:  {id, full_name, email}
   Impact: Old field gone, new field appears
   
4. Change Type:
   Before: {id: INT, amount: INT}
   After:  {id: INT, amount: DECIMAL(10,2)}
   Impact: Value format changes
```

**Handling in Consumers:**

```java
// Use Avro schema with defaults
{
  "type": "record",
  "name": "Customer",
  "fields": [
    {"name": "id", "type": "int"},
    {"name": "name", "type": "string"},
    {"name": "email", "type": "string"},
    {"name": "phone", "type": ["null", "string"], "default": null}  ← Optional
  ]
}

// Old events (before ALTER):
{id: 1, name: "Alice", email: "a@example.com", phone: null}  // Default

// New events (after ALTER):
{id: 2, name: "Bob", email: "b@example.com", phone: "555-1234"}
```

---

## Exactly-Once Guarantees

### How Debezium Achieves Exactly-Once

**1. Offset Tracking:**

```
Debezium commits offset AFTER event successfully sent to Kafka:

Process:
1. Read binlog entry at position 154
2. Convert to Kafka event
3. Send to Kafka
4. Wait for Kafka ACK
5. Save offset 154 to connect-offsets topic  ← Atomic
6. Continue to next entry

If crash before step 5:
- Restart from last saved offset
- Reprocess some events (at-least-once)
- But same events already in Kafka (idempotent!)
```

**2. Idempotent Events:**

```
Each event has unique identifier:

MySQL:
  source.file + source.pos + source.row = unique

PostgreSQL:
  source.lsn + source.txId = unique

If duplicate event sent to Kafka:
- Downstream can deduplicate using source metadata
- Or rely on Kafka producer idempotence
```

**3. Transaction Markers:**

```json
{
  "connector.class": "io.debezium.connector.mysql.MySqlConnector",
  "provide.transaction.metadata": "true",  ← Enable
  "transforms": "unwrap",
  "transforms.unwrap.drop.tombstones": "false"
}

Produces additional events:
- Transaction BEGIN marker
- Transaction END marker
- Consumers can implement exactly-once with these markers
```

---

## Comparison: Polling vs CDC

### Traditional Polling Approach

```java
// Poll database every minute
while (true) {
    List<Order> newOrders = db.query(
        "SELECT * FROM orders WHERE updated_at > ?",
        lastCheckTime
    );
    
    for (Order order : newOrders) {
        kafka.send("orders", order);
    }
    
    lastCheckTime = now();
    Thread.sleep(60000);  // 1 minute
}
```

**Problems:**

```
1. Missed Deletes:
   - Query can't find deleted rows
   - Can't sync deletions

2. High Database Load:
   - Full table scan on large tables
   - Adds load to primary database

3. Latency:
   - 1-minute delay minimum
   - Not real-time

4. Incomplete Picture:
   - Only sees final state
   - Misses intermediate updates
   
Example:
  10:00 AM: price = $100
  10:01 AM: price = $200
  10:02 AM: price = $150
  
  Polling at 10:03 AM only sees $150
  Missed $100 → $200 change!
```

### CDC Approach

```
Debezium captures:
1. Real-time (millisecond latency)
2. Every change (including deletes)
3. Before/after values
4. Transaction boundaries
5. Schema changes
6. Minimal database overhead
```

**Comparison Table:**

| Feature | Polling | CDC (Debezium) |
|---------|---------|----------------|
| **Latency** | Minutes | Milliseconds |
| **Captures Deletes** | ❌ No | ✅ Yes |
| **DB Overhead** | High (queries) | Low (read log) |
| **Intermediate Changes** | ❌ Missed | ✅ Captured |
| **Schema Changes** | Manual | ✅ Auto-detected |
| **Transaction Order** | ❌ Lost | ✅ Preserved |
| **Exactly-Once** | Hard to achieve | ✅ Built-in |
| **Setup Complexity** | Simple | Medium |
| **Failure Recovery** | Re-scan | Resume from offset |

---

## Real-World Example: Complete Flow

### Scenario: E-commerce Order Processing

**Setup:**

```
MySQL Database (orders DB)
  ├── orders table
  ├── order_items table
  └── inventory table

Debezium Connector → Kafka → Multiple Consumers
```

**Step-by-Step Flow:**

**1. Application Creates Order:**

```sql
-- Application executes transaction
BEGIN;
  INSERT INTO orders (id, customer_id, total, status) 
    VALUES (12345, 'C100', 99.99, 'pending');
  
  INSERT INTO order_items (order_id, product_id, quantity, price)
    VALUES (12345, 'P500', 2, 49.99);
  
  UPDATE inventory SET quantity = quantity - 2 
    WHERE product_id = 'P500';
COMMIT;
```

**2. MySQL Writes to Binlog:**

```
Position 1000: BEGIN (transaction 78901)
Position 1010: INSERT orders [id:12345, customer_id:'C100', total:99.99, status:'pending']
Position 1050: INSERT order_items [order_id:12345, product_id:'P500', qty:2, price:49.99]
Position 1090: UPDATE inventory [product_id:'P500', before:{qty:100}, after:{qty:98}]
Position 1130: COMMIT (transaction 78901)
```

**3. Debezium Reads Binlog:**

```
Connector at position 999:
1. Read entry at 1000 → BEGIN
   - Start new transaction batch
   
2. Read entry at 1010 → INSERT orders
   - Parse row data
   - Create event 1
   
3. Read entry at 1050 → INSERT order_items
   - Parse row data
   - Create event 2
   
4. Read entry at 1090 → UPDATE inventory
   - Parse before/after values
   - Create event 3
   
5. Read entry at 1130 → COMMIT
   - Send all 3 events to Kafka
   - Save offset 1130
```

**4. Kafka Events Produced:**

```
Topic: mysql.orders_db.orders
Event 1:
{
  "before": null,
  "after": {
    "id": 12345,
    "customer_id": "C100",
    "total": 99.99,
    "status": "pending",
    "created_at": "2024-03-21T10:30:00Z"
  },
  "source": {
    "connector": "mysql",
    "db": "orders_db",
    "table": "orders",
    "file": "mysql-bin.000003",
    "pos": 1010,
    "txId": 78901
  },
  "op": "c",
  "ts_ms": 1711014600000
}

Topic: mysql.orders_db.order_items
Event 2: {...}

Topic: mysql.orders_db.inventory
Event 3:
{
  "before": {
    "product_id": "P500",
    "quantity": 100
  },
  "after": {
    "product_id": "P500",
    "quantity": 98
  },
  "source": {
    "connector": "mysql",
    "db": "orders_db",
    "table": "inventory",
    "file": "mysql-bin.000003",
    "pos": 1090,
    "txId": 78901
  },
  "op": "u",
  "ts_ms": 1711014600050
}
```

**5. Consumers Process Events:**

```
Analytics Consumer:
- Reads from all topics
- Builds real-time dashboard
- Sees order creation in < 100ms

Cache Invalidation Consumer:
- Reads from inventory topic
- Updates Redis cache for product P500
- New inventory count available immediately

Search Index Consumer:
- Reads from orders topic
- Updates Elasticsearch
- Order searchable in seconds

Warehouse Consumer:
- Reads from order_items topic
- Triggers picking process
- Start fulfillment immediately
```

**6. Total Latency:**

```
Order created in MySQL: T+0ms
Binlog written: T+2ms
Debezium reads: T+10ms
Kafka receives: T+15ms
Consumer processes: T+20ms
Total: 20ms end-to-end! ✓
```

---

## Summary: Key Takeaways

### Why CDC (Debezium) is Powerful

✅ **Real-Time**: Millisecond latency from database change to Kafka
✅ **Complete**: Captures INSERT, UPDATE, DELETE with before/after values
✅ **Low Overhead**: Read-only on transaction logs, minimal DB impact
✅ **Reliable**: Exactly-once semantics, automatic recovery
✅ **Schema Evolution**: Handles DDL changes automatically
✅ **Transaction Aware**: Preserves transaction boundaries

### When to Use CDC

✅ Building event-driven architectures
✅ Real-time analytics and reporting
✅ Database replication and synchronization
✅ Cache invalidation
✅ Search index updates
✅ Microservices data distribution
✅ Audit logging and compliance

### When NOT to Use CDC

❌ Source database doesn't support transaction logs
❌ Can't enable binlog/WAL on production
❌ Near-zero tolerance for any DB overhead
❌ Batch processing is sufficient (nightly ETL)
❌ Don't need real-time data

---

## Interview Answers

**Q: "How does Debezium capture data internally?"**
> "Debezium reads the database transaction log - MySQL binlog or PostgreSQL WAL. It connects as a replication client, reads log entries sequentially, parses them, and converts to Kafka events. The database already writes all changes to the log for crash recovery, so CDC just reads what's already there. No triggers needed, no additional writes, minimal overhead."

**Q: "What's the performance impact on the database?"**
> "Minimal - typically 2-5% CPU/memory overhead. Debezium only reads the transaction log which the database writes anyway. No table locks during continuous capture, read-only access, no blocking of application queries. Main overhead is binlog retention (storage) and network bandwidth for streaming changes."

**Q: "How does it handle table schema changes?"**
> "Debezium automatically detects DDL changes from the transaction log, updates its internal schema cache, and stores schema history in a Kafka topic. Future events use the new schema. No connector restart needed. Consumers can handle schema evolution using Avro with default values."

**Q: "What happens if Debezium crashes?"**
> "Debezium saves its position (binlog offset or WAL LSN) in Kafka's connect-offsets topic after each batch. On restart, it reads the last saved position and resumes from there. No data loss, though some events might be reprocessed (at-least-once). With idempotent producers and transaction metadata, exactly-once is achievable."

---

**You now understand CDC internals at expert level!** 🚀

This knowledge puts you in the top 10% of developers who truly understand how CDC works under the hood.

