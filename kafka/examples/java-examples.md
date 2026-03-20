# Java Examples - Quick Setup

## 1. Create Directories

```bash
cd /Users/vishalkumarbg/Documents/Bheembali/kafka/examples
mkdir -p java/producer/src/main/java
mkdir -p java/consumer/src/main/java
```

## 2. Producer Files

### pom.xml
Save as: `java/producer/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.kafka</groupId>
    <artifactId>producer</artifactId>
    <version>1.0</version>
    <properties>
        <maven.compiler.source>11</maven.compiler.source>
        <maven.compiler.target>11</maven.compiler.target>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.apache.kafka</groupId>
            <artifactId>kafka-clients</artifactId>
            <version>3.6.0</version>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-simple</artifactId>
            <version>2.0.9</version>
        </dependency>
    </dependencies>
</project>
```

### SimpleProducer.java
Save as: `java/producer/src/main/java/SimpleProducer.java`

```java
import org.apache.kafka.clients.producer.*;
import java.util.Properties;

public class SimpleProducer {
    public static void main(String[] args) {
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:19092");
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        
        KafkaProducer<String, String> producer = new KafkaProducer<>(props);
        
        for (int i = 0; i < 10; i++) {
            producer.send(new ProducerRecord<>("test-topic", "key-" + i, "message-" + i));
            System.out.println("Sent: message-" + i);
        }
        
        producer.close();
        System.out.println("Done!");
    }
}
```

### ProducerWithCallback.java
Save as: `java/producer/src/main/java/ProducerWithCallback.java`

```java
import org.apache.kafka.clients.producer.*;
import java.util.Properties;

public class ProducerWithCallback {
    public static void main(String[] args) {
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:19092");
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        
        KafkaProducer<String, String> producer = new KafkaProducer<>(props);
        
        for (int i = 0; i < 10; i++) {
            producer.send(new ProducerRecord<>("test-topic", "key-" + i, "msg-" + i), 
                (metadata, e) -> {
                    if (e == null) {
                        System.out.printf("✓ Partition: %d, Offset: %d%n", 
                            metadata.partition(), metadata.offset());
                    }
                });
        }
        
        producer.close();
    }
}
```

### IdempotentProducer.java
Save as: `java/producer/src/main/java/IdempotentProducer.java`

```java
import org.apache.kafka.clients.producer.*;
import java.util.Properties;

public class IdempotentProducer {
    public static void main(String[] args) {
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:19092");
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("enable.idempotence", "true");
        
        KafkaProducer<String, String> producer = new KafkaProducer<>(props);
        
        for (int i = 0; i < 5; i++) {
            producer.send(new ProducerRecord<>("test-topic", "exact-once-" + i));
            System.out.println("Sent: exact-once-" + i);
        }
        
        producer.close();
    }
}
```

## 3. Consumer Files

### pom.xml
Save as: `java/consumer/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.kafka</groupId>
    <artifactId>consumer</artifactId>
    <version>1.0</version>
    <properties>
        <maven.compiler.source>11</maven.compiler.source>
        <maven.compiler.target>11</maven.compiler.target>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.apache.kafka</groupId>
            <artifactId>kafka-clients</artifactId>
            <version>3.6.0</version>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-simple</artifactId>
            <version>2.0.9</version>
        </dependency>
    </dependencies>
</project>
```

### SimpleConsumer.java
Save as: `java/consumer/src/main/java/SimpleConsumer.java`

```java
import org.apache.kafka.clients.consumer.*;
import java.time.Duration;
import java.util.*;

public class SimpleConsumer {
    public static void main(String[] args) {
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:19092");
        props.put("group.id", "test-group");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("auto.offset.reset", "earliest");
        
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Arrays.asList("test-topic"));
        
        System.out.println("Listening...");
        
        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
            for (ConsumerRecord<String, String> record : records) {
                System.out.printf("Got: %s [partition=%d, offset=%d]%n",
                    record.value(), record.partition(), record.offset());
            }
        }
    }
}
```

### ConsumerGroup.java
Save as: `java/consumer/src/main/java/ConsumerGroup.java`

```java
import org.apache.kafka.clients.consumer.*;
import java.time.Duration;
import java.util.*;

public class ConsumerGroup {
    public static void main(String[] args) {
        String id = args.length > 0 ? args[0] : "consumer-1";
        
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:19092");
        props.put("group.id", "my-group");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("auto.offset.reset", "earliest");
        
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Arrays.asList("test-topic"));
        
        System.out.println(id + " listening...");
        
        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
            for (ConsumerRecord<String, String> record : records) {
                System.out.printf("[%s] Got: %s (partition %d)%n",
                    id, record.value(), record.partition());
            }
        }
    }
}
```

### ManualCommit.java
Save as: `java/consumer/src/main/java/ManualCommit.java`

```java
import org.apache.kafka.clients.consumer.*;
import java.time.Duration;
import java.util.*;

public class ManualCommit {
    public static void main(String[] args) {
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:19092");
        props.put("group.id", "manual-group");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("auto.offset.reset", "earliest");
        props.put("enable.auto.commit", "false");
        
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Arrays.asList("test-topic"));
        
        System.out.println("Manual commit mode...");
        
        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
            
            for (ConsumerRecord<String, String> record : records) {
                System.out.println("Processing: " + record.value());
            }
            
            if (!records.isEmpty()) {
                consumer.commitSync();
                System.out.println("✓ Committed " + records.count() + " msgs");
            }
        }
    }
}
```

## 4. Quick Test

```bash
# Build
cd java/producer && mvn clean package
cd ../consumer && mvn clean package

# Run
cd producer && mvn exec:java -Dexec.mainClass="SimpleProducer"
cd consumer && mvn exec:java -Dexec.mainClass="SimpleConsumer"
```

Done! Copy-paste and test.

