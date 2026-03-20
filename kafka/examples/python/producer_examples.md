# Python Producer Examples

## Setup

```bash
pip install kafka-python
```

## Basic Producer

```python
from kafka import KafkaProducer
import json
import time

# Create producer
producer = KafkaProducer(
    bootstrap_servers=['localhost:19092', 'localhost:19093', 'localhost:19094'],
    value_serializer=lambda v: json.dumps(v).encode('utf-8')
)

# Send messages
for i in range(10):
    message = {'id': i, 'message': f'Hello Kafka {i}'}
    future = producer.send('python-topic', value=message)
    
    # Block for synchronous send
    record_metadata = future.get(timeout=10)
    
    print(f'Message {i} sent to partition {record_metadata.partition} at offset {record_metadata.offset}')

producer.flush()
producer.close()
```

## Producer with Key

```python
from kafka import KafkaProducer

producer = KafkaProducer(
    bootstrap_servers=['localhost:19092'],
    key_serializer=lambda k: k.encode('utf-8'),
    value_serializer=lambda v: v.encode('utf-8')
)

# Messages with same key go to same partition
users = ['user1', 'user2', 'user3']
for i in range(30):
    key = users[i % 3]
    value = f'Event {i} for {key}'
    producer.send('keyed-topic', key=key, value=value)

producer.flush()
producer.close()
```

## Async Producer with Callback

```python
from kafka import KafkaProducer
import logging

logging.basicConfig(level=logging.INFO)

producer = KafkaProducer(
    bootstrap_servers=['localhost:19092'],
    value_serializer=lambda v: v.encode('utf-8')
)

def on_send_success(record_metadata):
    print(f'Message sent to {record_metadata.topic}:{record_metadata.partition}:{record_metadata.offset}')

def on_send_error(excp):
    logging.error('Error sending message', exc_info=excp)

for i in range(100):
    producer.send('async-topic', value=f'Message {i}') \
        .add_callback(on_send_success) \
        .add_errback(on_send_error)

producer.flush()
producer.close()
```

## High-Throughput Producer

```python
from kafka import KafkaProducer

producer = KafkaProducer(
    bootstrap_servers=['localhost:19092'],
    compression_type='lz4',
    batch_size=32768,
    linger_ms=10,
    buffer_memory=67108864,
    acks='all',
    retries=3,
    value_serializer=lambda v: v.encode('utf-8')
)

# Send many messages quickly
for i in range(100000):
    producer.send('high-throughput', value=f'Message {i}')
    if i % 10000 == 0:
        print(f'Sent {i} messages')

producer.flush()
producer.close()
```

## Idempotent Producer

```python
from kafka import KafkaProducer

producer = KafkaProducer(
    bootstrap_servers=['localhost:19092'],
    enable_idempotence=True,
    acks='all',
    retries=2147483647,
    max_in_flight_requests_per_connection=5,
    value_serializer=lambda v: v.encode('utf-8')
)

for i in range(100):
    producer.send('idempotent-topic', value=f'Message {i}')

producer.flush()
producer.close()
```

## Custom Partitioner

```python
from kafka import KafkaProducer
from kafka.partitioner.default import DefaultPartitioner

class CountryPartitioner:
    def __init__(self, partitions):
        self.partitions = partitions
    
    def __call__(self, key, all_partitions, available):
        """
        Partition based on country code
        """
        if key is None:
            return all_partitions[0]
        
        country_code = key.decode('utf-8')
        if country_code == 'US':
            return 0
        elif country_code == 'EU':
            return 1
        else:
            return 2

producer = KafkaProducer(
    bootstrap_servers=['localhost:19092'],
    key_serializer=lambda k: k.encode('utf-8'),
    value_serializer=lambda v: v.encode('utf-8'),
    partitioner=CountryPartitioner
)

# Send to specific partitions based on country
producer.send('geo-topic', key='US', value='User from US')
producer.send('geo-topic', key='EU', value='User from EU')
producer.send('geo-topic', key='IN', value='User from India')

producer.flush()
producer.close()
```

## Producer with Headers

```python
from kafka import KafkaProducer

producer = KafkaProducer(
    bootstrap_servers=['localhost:19092'],
    value_serializer=lambda v: v.encode('utf-8')
)

headers = [
    ('source', b'mobile-app'),
    ('version', b'1.0'),
    ('user-agent', b'iOS')
]

producer.send('headers-topic', 
              value='Message with headers',
              headers=headers)

producer.flush()
producer.close()
```

## Error Handling

```python
from kafka import KafkaProducer
from kafka.errors import KafkaError
import logging

logging.basicConfig(level=logging.ERROR)

producer = KafkaProducer(
    bootstrap_servers=['localhost:19092'],
    value_serializer=lambda v: v.encode('utf-8'),
    retries=3
)

for i in range(100):
    try:
        future = producer.send('error-handling-topic', value=f'Message {i}')
        record_metadata = future.get(timeout=10)
        print(f'Success: {i} -> {record_metadata.partition}:{record_metadata.offset}')
    except KafkaError as e:
        logging.error(f'Failed to send message {i}: {e}')
    except Exception as e:
        logging.error(f'Unexpected error: {e}')

producer.close()
```

## Metrics Monitoring

```python
from kafka import KafkaProducer
import time

producer = KafkaProducer(
    bootstrap_servers=['localhost:19092'],
    value_serializer=lambda v: v.encode('utf-8')
)

# Send messages
for i in range(1000):
    producer.send('metrics-topic', value=f'Message {i}')

producer.flush()

# Get metrics
metrics = producer.metrics()
print("\nProducer Metrics:")
print(f"Total records sent: {metrics.get('record-send-total', 0)}")
print(f"Record send rate: {metrics.get('record-send-rate', 0)}")
print(f"Average batch size: {metrics.get('batch-size-avg', 0)}")
print(f"Average record size: {metrics.get('record-size-avg', 0)}")
print(f"Compression rate: {metrics.get('compression-rate-avg', 0)}")

producer.close()
```

## Complete Example with Best Practices

```python
from kafka import KafkaProducer
from kafka.errors import KafkaError
import json
import logging
import time
from datetime import datetime

# Setup logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

class KafkaProducerWrapper:
    def __init__(self, bootstrap_servers, topic):
        self.topic = topic
        self.producer = KafkaProducer(
            bootstrap_servers=bootstrap_servers,
            key_serializer=lambda k: k.encode('utf-8') if k else None,
            value_serializer=lambda v: json.dumps(v).encode('utf-8'),
            compression_type='lz4',
            acks='all',
            enable_idempotence=True,
            retries=3,
            max_in_flight_requests_per_connection=5,
            batch_size=16384,
            linger_ms=10
        )
        self.success_count = 0
        self.error_count = 0
    
    def send_message(self, key, value):
        """Send message with callback"""
        try:
            # Add timestamp
            value['timestamp'] = datetime.now().isoformat()
            
            future = self.producer.send(
                self.topic,
                key=key,
                value=value
            )
            
            # Add callbacks
            future.add_callback(self._on_send_success)
            future.add_errback(self._on_send_error)
            
        except Exception as e:
            logger.error(f"Error sending message: {e}")
            self.error_count += 1
    
    def _on_send_success(self, record_metadata):
        self.success_count += 1
        logger.debug(
            f"Message delivered to {record_metadata.topic} "
            f"[{record_metadata.partition}] at offset {record_metadata.offset}"
        )
    
    def _on_send_error(self, excp):
        self.error_count += 1
        logger.error(f"Error delivering message: {excp}")
    
    def close(self):
        """Flush and close producer"""
        self.producer.flush()
        logger.info(f"Producer stats - Success: {self.success_count}, Errors: {self.error_count}")
        self.producer.close()
        logger.info("Producer closed")

# Usage
if __name__ == '__main__':
    producer_wrapper = KafkaProducerWrapper(
        bootstrap_servers=['localhost:19092'],
        topic='best-practices-topic'
    )
    
    # Send messages
    for i in range(100):
        user_id = f"user{i % 10}"
        message = {
            'user_id': user_id,
            'event': 'page_view',
            'page': f'/page{i}',
            'session_id': f'session{i // 10}'
        }
        producer_wrapper.send_message(key=user_id, value=message)
        time.sleep(0.01)  # Simulate processing time
    
    # Close producer
    producer_wrapper.close()
```

## Run Examples

```bash
# Make sure Kafka cluster is running
docker-compose up -d

# Run any example
python producer_basic.py
python producer_with_key.py
python producer_async.py
```

## Performance Testing

```python
from kafka import KafkaProducer
import time

producer = KafkaProducer(
    bootstrap_servers=['localhost:19092'],
    compression_type='lz4',
    batch_size=32768,
    linger_ms=10,
    value_serializer=lambda v: v.encode('utf-8')
)

# Performance test
num_messages = 100000
start_time = time.time()

for i in range(num_messages):
    producer.send('perf-test', value=f'Message {i}')

producer.flush()
end_time = time.time()

duration = end_time - start_time
throughput = num_messages / duration

print(f"Sent {num_messages} messages in {duration:.2f} seconds")
print(f"Throughput: {throughput:.2f} messages/sec")

producer.close()
```

