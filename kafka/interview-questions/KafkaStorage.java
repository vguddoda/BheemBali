import java.util.*;

/**
 * INTERVIEW QUESTION: Implement Kafka Storage & Indexing System
 * 
 * Demonstrates how Kafka stores and retrieves messages:
 * 1. Write messages to .log file (append-only)
 * 2. Build sparse .index file (offset -> position)
 * 3. Search by offset using binary search + sequential scan
 * 4. Segment rolling when size limit reached
 * 
 * Run: javac KafkaStorage.java && java KafkaStorage
 */
public class KafkaStorage {
    
    // Represents a single message with metadata
    static class Message {
        long offset;
        String key;
        String value;
        long timestamp;
        int sizeInBytes;
        
        Message(long offset, String key, String value) {
            this.offset = offset;
            this.key = key;
            this.value = value;
            this.timestamp = System.currentTimeMillis();
            // Simplified size calculation (header + key + value)
            this.sizeInBytes = 50 + (key != null ? key.length() : 0) + (value != null ? value.length() : 0);
        }
        
        @Override
        public String toString() {
            return String.format("Offset: %d | Key: %s | Value: %s | Size: %d bytes", 
                offset, key, value, sizeInBytes);
        }
    }
    
    // Represents an index entry (offset -> file position)
    static class IndexEntry {
        long offset;           // Message offset
        int filePosition;      // Byte position in .log file
        
        IndexEntry(long offset, int filePosition) {
            this.offset = offset;
            this.filePosition = filePosition;
        }
        
        @Override
        public String toString() {
            return String.format("Offset: %d -> Position: %d", offset, filePosition);
        }
    }
    
    // Represents a log segment (.log + .index)
    static class LogSegment {
        String name;                    // e.g., "00000000000000000000"
        long baseOffset;                // First offset in this segment
        List<Message> logFile;          // Simulates .log file
        List<IndexEntry> indexFile;     // Simulates .index file (sparse)
        int currentPosition;            // Current byte position in log
        int maxSegmentSize;             // Max size before rolling
        int indexInterval;              // Add index entry every N bytes
        int lastIndexedPosition;        // Last position we indexed
        
        LogSegment(long baseOffset, int maxSegmentSize, int indexInterval) {
            this.name = String.format("%020d", baseOffset);
            this.baseOffset = baseOffset;
            this.logFile = new ArrayList<>();
            this.indexFile = new ArrayList<>();
            this.currentPosition = 0;
            this.maxSegmentSize = maxSegmentSize;
            this.indexInterval = indexInterval;
            this.lastIndexedPosition = 0;
        }
        
        // Append message to log and update index (sparse)
        boolean append(Message msg) {
            // Check if segment is full
            if (currentPosition >= maxSegmentSize) {
                return false;  // Signal to roll to new segment
            }
            
            int messagePosition = currentPosition;
            
            // Write to .log file (append-only)
            logFile.add(msg);
            currentPosition += msg.sizeInBytes;
            
            // Add to sparse .index file (every ~indexInterval bytes)
            if (messagePosition - lastIndexedPosition >= indexInterval || indexFile.isEmpty()) {
                indexFile.add(new IndexEntry(msg.offset, messagePosition));
                lastIndexedPosition = messagePosition;
                System.out.println("  📍 Index entry added: offset " + msg.offset + " -> position " + messagePosition);
            }
            
            return true;
        }
        
        // Binary search in index to find starting point for offset
        IndexEntry findIndexEntry(long targetOffset) {
            if (indexFile.isEmpty()) {
                return null;
            }
            
            // Binary search for largest offset <= targetOffset
            int left = 0;
            int right = indexFile.size() - 1;
            IndexEntry result = indexFile.get(0);
            
            System.out.println("\n  🔍 Binary search in .index file:");
            while (left <= right) {
                int mid = left + (right - left) / 2;
                IndexEntry entry = indexFile.get(mid);
                
                System.out.println("    Checking index[" + mid + "]: offset=" + entry.offset);
                
                if (entry.offset <= targetOffset) {
                    result = entry;
                    left = mid + 1;
                } else {
                    right = mid - 1;
                }
            }
            
            System.out.println("  ✓ Found starting point: offset " + result.offset + " at position " + result.filePosition);
            return result;
        }
        
        // Search for message by offset (binary search + sequential scan)
        Message search(long targetOffset) {
            System.out.println("\n🔎 SEARCHING for offset " + targetOffset + " in segment " + name);
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            
            // Step 1: Binary search in .index file
            IndexEntry startEntry = findIndexEntry(targetOffset);
            if (startEntry == null) {
                System.out.println("  ✗ Offset not in this segment");
                return null;
            }
            
            // Step 2: Sequential scan from starting position
            System.out.println("\n  📖 Sequential scan in .log file from offset " + startEntry.offset + ":");
            
            boolean foundStart = false;
            int scannedCount = 0;
            
            for (Message msg : logFile) {
                // Find starting offset first
                if (!foundStart) {
                    if (msg.offset == startEntry.offset) {
                        foundStart = true;
                    } else {
                        continue;
                    }
                }
                
                scannedCount++;
                System.out.println("    Scanning: " + msg);
                
                if (msg.offset == targetOffset) {
                    System.out.println("  ✓ FOUND! Scanned " + scannedCount + " messages");
                    return msg;
                }
                
                if (msg.offset > targetOffset) {
                    System.out.println("  ✗ Offset " + targetOffset + " not found (passed it)");
                    return null;
                }
            }
            
            System.out.println("  ✗ Offset " + targetOffset + " not found (end of segment)");
            return null;
        }
        
        void displayLogFile() {
            System.out.println("\n=== " + name + ".log (Message Data) ===");
            System.out.println("Total messages: " + logFile.size());
            System.out.println("Total size: " + currentPosition + " bytes");
            for (Message msg : logFile) {
                System.out.println(msg);
            }
        }
        
        void displayIndexFile() {
            System.out.println("\n=== " + name + ".index (Offset -> Position Mapping) ===");
            System.out.println("Total index entries: " + indexFile.size());
            System.out.println("Index interval: ~" + indexInterval + " bytes");
            for (IndexEntry entry : indexFile) {
                System.out.println(entry);
            }
        }
    }
    
    // Manages multiple segments (like a partition)
    static class Partition {
        String topicName;
        int partitionNumber;
        List<LogSegment> segments;
        long nextOffset;
        int maxSegmentSize;
        int indexInterval;
        
        Partition(String topicName, int partitionNumber, int maxSegmentSize, int indexInterval) {
            this.topicName = topicName;
            this.partitionNumber = partitionNumber;
            this.segments = new ArrayList<>();
            this.nextOffset = 0;
            this.maxSegmentSize = maxSegmentSize;
            this.indexInterval = indexInterval;
            
            // Create first segment
            rollSegment();
        }
        
        // Create new segment (segment rolling)
        void rollSegment() {
            LogSegment newSegment = new LogSegment(nextOffset, maxSegmentSize, indexInterval);
            segments.add(newSegment);
            System.out.println("\n📁 New segment created: " + newSegment.name + ".log");
        }
        
        // Append message to partition
        void append(String key, String value) {
            Message msg = new Message(nextOffset, key, value);
            
            // Get active segment (last one)
            LogSegment activeSegment = segments.get(segments.size() - 1);
            
            // Try to append
            boolean success = activeSegment.append(msg);
            
            if (!success) {
                // Segment full, roll to new segment
                System.out.println("⚠️  Segment full! Rolling to new segment...");
                rollSegment();
                activeSegment = segments.get(segments.size() - 1);
                activeSegment.append(msg);
            }
            
            System.out.println("✓ Appended: " + msg);
            nextOffset++;
        }
        
        // Search for message by offset across all segments
        Message search(long targetOffset) {
            System.out.println("\n" + "=".repeat(60));
            System.out.println("SEARCH REQUEST: Topic=" + topicName + ", Partition=" + partitionNumber + ", Offset=" + targetOffset);
            System.out.println("=".repeat(60));
            
            // Find which segment contains this offset
            LogSegment targetSegment = null;
            for (int i = segments.size() - 1; i >= 0; i--) {
                LogSegment seg = segments.get(i);
                if (seg.baseOffset <= targetOffset) {
                    targetSegment = seg;
                    break;
                }
            }
            
            if (targetSegment == null) {
                System.out.println("✗ Offset " + targetOffset + " is before first segment");
                return null;
            }
            
            return targetSegment.search(targetOffset);
        }
        
        void displayAllSegments() {
            System.out.println("\n" + "=".repeat(60));
            System.out.println("PARTITION: " + topicName + "-" + partitionNumber);
            System.out.println("Total segments: " + segments.size());
            System.out.println("Total messages: " + nextOffset);
            System.out.println("=".repeat(60));
            
            for (LogSegment seg : segments) {
                seg.displayLogFile();
                seg.displayIndexFile();
            }
        }
    }
    
    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════╗");
        System.out.println("║  Kafka Storage & Indexing - Interview Demo    ║");
        System.out.println("╚════════════════════════════════════════════════╝");
        
        // Create partition with:
        // - Max segment size: 500 bytes (small for demo)
        // - Index interval: 100 bytes (add index entry every 100 bytes)
        Partition partition = new Partition("orders", 0, 500, 100);
        
        System.out.println("\n📝 WRITING MESSAGES (Simulating Producer)");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        // Write messages (will trigger segment rolling)
        partition.append("order-1", "Coffee - $5");
        partition.append("order-2", "Tea - $3");
        partition.append("order-3", "Sandwich - $8");
        partition.append("order-4", "Burger - $12");
        partition.append("order-5", "Salad - $10");
        partition.append("order-6", "Pizza - $15");
        partition.append("order-7", "Pasta - $14");
        partition.append("order-8", "Sushi - $20");
        partition.append("order-9", "Ramen - $13");
        partition.append("order-10", "Steak - $25");
        partition.append("order-11", "Lobster - $35");
        partition.append("order-12", "Cake - $8");
        
        // Display all segments and indexes
        partition.displayAllSegments();
        
        // Test searching
        System.out.println("\n\n" + "=".repeat(60));
        System.out.println("TESTING SEARCH OPERATIONS");
        System.out.println("=".repeat(60));
        
        // Search test 1: Message in first segment
        Message result1 = partition.search(2);
        if (result1 != null) {
            System.out.println("\n✅ Search Result: " + result1);
        }
        
        // Search test 2: Message in second segment
        Message result2 = partition.search(8);
        if (result2 != null) {
            System.out.println("\n✅ Search Result: " + result2);
        }
        
        // Search test 3: Last message
        Message result3 = partition.search(11);
        if (result3 != null) {
            System.out.println("\n✅ Search Result: " + result3);
        }
        
        // Search test 4: Non-existent offset
        Message result4 = partition.search(99);
        if (result4 == null) {
            System.out.println("\n✅ Correctly returned null for non-existent offset");
        }
        
        // Performance analysis
        System.out.println("\n\n" + "=".repeat(60));
        System.out.println("PERFORMANCE ANALYSIS");
        System.out.println("=".repeat(60));
        
        System.out.println("\n📊 Storage Efficiency:");
        int totalMessages = (int) partition.nextOffset;
        int totalIndexEntries = 0;
        for (LogSegment seg : partition.segments) {
            totalIndexEntries += seg.indexFile.size();
        }
        
        System.out.println("Total messages: " + totalMessages);
        System.out.println("Total index entries: " + totalIndexEntries);
        System.out.println("Index ratio: 1 entry per " + (totalMessages / totalIndexEntries) + " messages");
        System.out.println("Space savings: " + ((1.0 - (double)totalIndexEntries / totalMessages) * 100) + "%");
        
        System.out.println("\n⚡ Search Complexity:");
        System.out.println("Binary search in index: O(log n) where n = index entries");
        System.out.println("Sequential scan: O(m) where m = messages between index entries");
        System.out.println("Total: O(log n + m) - Very efficient!");
        
        // Interview questions
        System.out.println("\n\n" + "=".repeat(60));
        System.out.println("INTERVIEW FOLLOW-UP QUESTIONS:");
        System.out.println("=".repeat(60));
        
        System.out.println("\nQ1: Why use sparse index instead of full index?");
        System.out.println("A1: Space efficiency! Sparse index is much smaller, fits in RAM.");
        System.out.println("    Full index: " + totalMessages + " entries");
        System.out.println("    Sparse index: " + totalIndexEntries + " entries");
        System.out.println("    Savings: " + (totalMessages - totalIndexEntries) + " entries!");
        
        System.out.println("\nQ2: Why segment rolling (multiple .log files)?");
        System.out.println("A2: - Deletion: Delete entire old segments (fast)");
        System.out.println("    - Compaction: Compact segments independently");
        System.out.println("    - Recovery: Recover from last good segment");
        System.out.println("    - Management: Easier to manage smaller files");
        
        System.out.println("\nQ3: What if .index file corrupted?");
        System.out.println("A3: Rebuild from .log file:");
        System.out.println("    1. Sequential scan .log");
        System.out.println("    2. Record offset -> position every N bytes");
        System.out.println("    3. Write new .index file");
        
        System.out.println("\nQ4: How does this achieve fast writes AND reads?");
        System.out.println("A4: Writes: Append-only to .log (sequential I/O = fast!)");
        System.out.println("    Reads: Binary search .index + small sequential scan");
        System.out.println("    Result: Both operations optimized!");
        
        System.out.println("\nQ5: Time complexity for search?");
        System.out.println("A5: Binary search: O(log " + totalIndexEntries + ") = ~" + 
            (int)Math.ceil(Math.log(totalIndexEntries) / Math.log(2)) + " comparisons");
        System.out.println("    Sequential scan: ~" + (totalMessages / totalIndexEntries) + " messages");
        System.out.println("    Total: < 5ms for most lookups!");
        
        System.out.println("\n" + "=".repeat(60));
        System.out.println("✨ This demonstrates Kafka's core storage architecture!");
        System.out.println("=".repeat(60));
    }
}

