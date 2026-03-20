# Python Consumer Examples

## Setup

```bash
pip install kafka-python
```

## Basic Consumer

```python
from kafka import KafkaConsumer
import json

# Create consumer
consumer = KafkaConsumer(
    'python-topic',
    bootstrap_servers=['localhost:19092', 'localhost:19093', 'localhost:19094'],
    auto_offset_reset='earliest',
    value_deserializer=lambda m: json.loads(m.decode('utf-8'))
)

print("Listening for messages...")
for message in consumer:
    print(f"Topic: {message.topic}")
    print(f"Partition: {message.partition}")
    print(f"Offset: {message.offset}")
    print(f"Key: {message.key}")
    print(f"Value: {message.value}")
    print("---")

consumer.close()
```

## Consumer Group

```python
from kafka import KafkaConsumer

consumer = KafkaConsumer(
    'my-topic',
    bootstrap_servers=['localhost:19092'],
    group_id='my-consumer-group',
    auto_offset_reset='earliest',
    value_deserializer=lambda m: m.decode('utf-8')
)

print(f"Consumer group: {consumer.config['group_id']}")
print(f"Subscribed to: {consumer.subscription()}")

for message in consumer:
    print(f"[{message.partition}:{message.offset}] {message.value}")

consumer.close()
```

## Manual Offset Management

```python
from kafka import KafkaConsumer, TopicPartition

consumer = KafkaConsumer(
    bootstrap_servers=['localhost:19092'],
    group_id='manual-offset-group',
    enable_auto_commit=False,
    auto_offset_reset='earliest',
    value_deserializer=lambda m: m.decode('utf-8')
)

consumer.subscribe(['manual-offset-topic'])

for message in consumer:
    try:
        # Process message
        print(f"Processing: {message.value}")
        
        # Simulate processing
        # process_message(message.value)
        
        # Manually commit offset after successful processing
        consumer.commit()
        print(f"Committed offset: {message.offset}")
        
    except Exception as e:
        print(f"Error processing message: {e}")
        # Don't commit on error - will reprocess on restart
        break

consumer.close()
```

## Batch Processing

```python
from kafka import KafkaConsumer
import time

consumer = KafkaConsumer(
    'batch-topic',
    bootstrap_servers=['localhost:19092'],
    group_id='batch-group',
    enable_auto_commit=False,
    max_poll_records=100,  # Fetch up to 100 records per poll
    value_deserializer=lambda m: m.decode('utf-8')
)

batch = []
batch_size = 50
last_commit_time = time.time()

for message in consumer:
    batch.append(message.value)
    
    # Process batch when size reached or timeout
    if len(batch) >= batch_size or (time.time() - last_commit_time) > 5:
        print(f"Processing batch of {len(batch)} messages")
        
        # Process batch
        # process_batch(batch)
        
        # Commit
        consumer.commit()
        print(f"Committed up to offset: {message.offset}")
        
        # Reset batch
        batch = []
        last_commit_time = time.time()

consumer.close()
```

## Multiple Topics

```python
from kafka import KafkaConsumer

consumer = KafkaConsumer(
    bootstrap_servers=['localhost:19092'],
    group_id='multi-topic-group',
    auto_offset_reset='earliest',
    value_deserializer=lambda m: m.decode('utf-8')
)

# Subscribe to multiple topics
consumer.subscribe(['topic1', 'topic2', 'topic3'])

print(f"Subscribed topics: {consumer.subscription()}")

for message in consumer:
    print(f"[{message.topic}] {message.value}")

consumer.close()
```

## Partition-Specific Consumer

```python
from kafka import KafkaConsumer, TopicPartition

consumer = KafkaConsumer(
    bootstrap_servers=['localhost:19092'],
    auto_offset_reset='earliest',
    value_deserializer=lambda m: m.decode('utf-8')
)

# Manually assign specific partitions
partition_0 = TopicPartition('my-topic', 0)
partition_1 = TopicPartition('my-topic', 1)

consumer.assign([partition_0, partition_1])

print(f"Assigned partitions: {consumer.assignment()}")

for message in consumer:
    print(f"[P{message.partition}:{message.offset}] {message.value}")

consumer.close()
```

## Seek to Specific Offset

```python
from kafka import KafkaConsumer, TopicPartition

consumer = KafkaConsumer(
    'my-topic',
    bootstrap_servers=['localhost:19092'],
    auto_offset_reset='earliest',
    value_deserializer=lambda m: m.decode('utf-8')
)

# Wait for partition assignment
consumer.poll(timeout_ms=1000)

# Seek to specific offset
for partition in consumer.assignment():
    consumer.seek(partition, 10)  # Start from offset 10
    print(f"Seeking {partition} to offset 10")

for message in consumer:
    print(f"[{message.partition}:{message.offset}] {message.value}")
    if message.offset > 20:  # Read a few messages
        break

consumer.close()
```

## Consume from Specific Timestamp

```python
from kafka import KafkaConsumer, TopicPartition
from datetime import datetime, timedelta

consumer = KafkaConsumer(
    'my-topic',
    bootstrap_servers=['localhost:19092'],
    value_deserializer=lambda m: m.decode('utf-8')
)

# Consume from 1 hour ago
one_hour_ago = int((datetime.now() - timedelta(hours=1)).timestamp() * 1000)

# Get partitions
consumer.poll(timeout_ms=1000)
partitions = consumer.assignment()

# Seek to timestamp
timestamp_dict = {partition: one_hour_ago for partition in partitions}
offsets = consumer.offsets_for_times(timestamp_dict)

for partition, offset_and_timestamp in offsets.items():
    if offset_and_timestamp:
        consumer.seek(partition, offset_and_timestamp.offset)
        print(f"Seeking {partition} to offset {offset_and_timestamp.offset}")

for message in consumer:
    print(f"[{message.partition}:{message.offset}] {message.value}")

consumer.close()
```

## Consumer with Pattern Subscription

```python
from kafka import KafkaConsumer
import re

consumer = KafkaConsumer(
    bootstrap_servers=['localhost:19092'],
    group_id='pattern-group',
    auto_offset_reset='earliest',
    value_deserializer=lambda m: m.decode('utf-8')
)

# Subscribe to topics matching pattern
pattern = re.compile(r'logs-.*')
consumer.subscribe(pattern=pattern)

print(f"Subscribed to pattern: {pattern.pattern}")

for message in consumer:
    print(f"[{message.topic}] {message.value}")

consumer.close()
```

## Consumer with Timeout

```python
from kafka import KafkaConsumer
import time

consumer = KafkaConsumer(
    'my-topic',
    bootstrap_servers=['localhost:19092'],
    group_id='timeout-group',
    auto_offset_reset='earliest',
    value_deserializer=lambda m: m.decode('utf-8'),
    consumer_timeout_ms=10000  # Stop after 10 seconds of no messages
)

try:
    for message in consumer:
        print(f"[{message.partition}:{message.offset}] {message.value}")
except StopIteration:
    print("No more messages, timeout reached")

consumer.close()
```

## Error Handling and Retry

```python
from kafka import KafkaConsumer
from kafka.errors import KafkaError
import logging
import time

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def process_message(message):
    """Simulate message processing with potential failures"""
    # Simulate processing
    if 'error' in message.value.lower():
        raise ValueError("Simulated processing error")
    
    print(f"Processed: {message.value}")

consumer = KafkaConsumer(
    'error-handling-topic',
    bootstrap_servers=['localhost:19092'],
    group_id='error-handling-group',
    enable_auto_commit=False,
    max_poll_interval_ms=300000,
    session_timeout_ms=45000,
    value_deserializer=lambda m: m.decode('utf-8')
)

MAX_RETRIES = 3

for message in consumer:
    retry_count = 0
    success = False
    
    while retry_count < MAX_RETRIES and not success:
        try:
            process_message(message)
            consumer.commit()
            success = True
            logger.info(f"Successfully processed offset {message.offset}")
            
        except ValueError as e:
            retry_count += 1
            logger.warning(f"Error processing message (attempt {retry_count}/{MAX_RETRIES}): {e}")
            time.sleep(1)  # Wait before retry
            
        except Exception as e:
            logger.error(f"Unexpected error: {e}")
            break
    
    if not success:
        logger.error(f"Failed to process message after {MAX_RETRIES} attempts")
        # Send to dead letter queue
        # dead_letter_producer.send('dlq-topic', message.value)
        consumer.commit()  # Commit to move forward

consumer.close()
```

## Monitoring Consumer Lag

```python
from kafka import KafkaConsumer, TopicPartition

consumer = KafkaConsumer(
    'my-topic',
    bootstrap_servers=['localhost:19092'],
    group_id='monitoring-group',
    auto_offset_reset='earliest',
    value_deserializer=lambda m: m.decode('utf-8')
)

# Poll to get assignment
consumer.poll(timeout_ms=1000)

# Get current position and end offsets
for partition in consumer.assignment():
    current_offset = consumer.position(partition)
    end_offset = consumer.end_offsets([partition])[partition]
    lag = end_offset - current_offset
    
    print(f"Partition {partition.partition}:")
    print(f"  Current offset: {current_offset}")
    print(f"  End offset: {end_offset}")
    print(f"  Lag: {lag} messages")

consumer.close()
```

## Complete Consumer with Best Practices

```python
from kafka import KafkaConsumer
from kafka.errors import KafkaError
import json
import logging
import signal
import sys
from datetime import datetime

# Setup logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

class GracefulKiller:
    """Handle graceful shutdown"""
    def __init__(self):
        self.kill_now = False
        signal.signal(signal.SIGINT, self.exit_gracefully)
        signal.signal(signal.SIGTERM, self.exit_gracefully)
    
    def exit_gracefully(self, signum, frame):
        logger.info("Shutdown signal received")
        self.kill_now = True

class KafkaConsumerWrapper:
    def __init__(self, bootstrap_servers, topics, group_id):
        self.consumer = KafkaConsumer(
            *topics,
            bootstrap_servers=bootstrap_servers,
            group_id=group_id,
            enable_auto_commit=False,
            auto_offset_reset='earliest',
            max_poll_records=100,
            session_timeout_ms=45000,
            heartbeat_interval_ms=15000,
            max_poll_interval_ms=300000,
            value_deserializer=lambda m: json.loads(m.decode('utf-8')),
            key_deserializer=lambda k: k.decode('utf-8') if k else None
        )
        self.processed_count = 0
        self.error_count = 0
        self.killer = GracefulKiller()
    
    def process_message(self, message):
        """Process individual message"""
        try:
            # Add your processing logic here
            logger.info(f"Processing message: {message.key} -> {message.value}")
            self.processed_count += 1
            return True
        except Exception as e:
            logger.error(f"Error processing message: {e}")
            self.error_count += 1
            return False
    
    def run(self):
        """Main consumer loop"""
        logger.info("Starting consumer...")
        
        try:
            while not self.killer.kill_now:
                # Poll for messages
                records = self.consumer.poll(timeout_ms=1000)
                
                if not records:
                    continue
                
                # Process messages
                for topic_partition, messages in records.items():
                    for message in messages:
                        success = self.process_message(message)
                        
                        if not success:
                            logger.warning(f"Failed to process message at offset {message.offset}")
                            # Could implement DLQ here
                    
                    # Commit after processing partition batch
                    self.consumer.commit()
                    logger.debug(f"Committed offsets for {topic_partition}")
                
                # Log stats periodically
                if self.processed_count % 100 == 0:
                    logger.info(f"Processed: {self.processed_count}, Errors: {self.error_count}")
        
        except KafkaError as e:
            logger.error(f"Kafka error: {e}")
        
        finally:
            self.close()
    
    def close(self):
        """Graceful shutdown"""
        logger.info(f"Closing consumer. Stats - Processed: {self.processed_count}, Errors: {self.error_count}")
        self.consumer.close()
        logger.info("Consumer closed successfully")

# Usage
if __name__ == '__main__':
    consumer_wrapper = KafkaConsumerWrapper(
        bootstrap_servers=['localhost:19092'],
        topics=['best-practices-topic'],
        group_id='best-practices-group'
    )
    
    consumer_wrapper.run()
```

## Read Committed (Transactional)

```python
from kafka import KafkaConsumer

consumer = KafkaConsumer(
    'transactional-topic',
    bootstrap_servers=['localhost:19092'],
    group_id='transactional-group',
    isolation_level='read_committed',  # Only read committed messages
    auto_offset_reset='earliest',
    value_deserializer=lambda m: m.decode('utf-8')
)

for message in consumer:
    # Only sees committed transactional messages
    print(f"[{message.partition}:{message.offset}] {message.value}")

consumer.close()
```

## Run Examples

```bash
# Make sure Kafka cluster is running
docker-compose up -d

# Run consumer in one terminal
python consumer_basic.py

# Run producer in another terminal to send messages
python producer_basic.py
```

