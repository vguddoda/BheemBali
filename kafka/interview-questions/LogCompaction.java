import java.util.*;
import java.util.stream.Collectors;

/**
 * INTERVIEW QUESTION: Implement Kafka Log Compaction Algorithm
 * 
 * Demonstrates how Kafka compaction works:
 * 1. Read old segment (append-only, immutable)
 * 2. Build hash table of key -> latest offset
 * 3. Create NEW segment with only latest values
 * 4. Delete old segment
 * 
 * Run: javac LogCompaction.java && java LogCompaction
 */
public class LogCompaction {
    
    // Represents a message in the log
    static class Message {
        long offset;
        String key;
        String value;
        
        Message(long offset, String key, String value) {
            this.offset = offset;
            this.key = key;
            this.value = value;
        }
        
        @Override
        public String toString() {
            return String.format("Offset: %d | Key: %s | Value: %s", offset, key, value);
        }
    }
    
    // Represents a log segment (immutable once created)
    static class LogSegment {
        String name;
        List<Message> messages;
        
        LogSegment(String name) {
            this.name = name;
            this.messages = new ArrayList<>();
        }
        
        void append(Message msg) {
            messages.add(msg);
        }
        
        void display() {
            System.out.println("\n=== " + name + " ===");
            System.out.println("Total messages: " + messages.size());
            for (Message msg : messages) {
                System.out.println(msg);
            }
        }
    }
    
    /**
     * CORE COMPACTION ALGORITHM
     * 
     * Key insight: Does NOT modify old segment!
     * Creates new segment with only latest values
     */
    public static LogSegment compact(LogSegment oldSegment) {
        System.out.println("\n🔄 COMPACTION STARTED");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        // Phase 1: Build hash table (key -> latest offset)
        System.out.println("\nPhase 1: Scanning old segment and building hash table...");
        Map<String, Long> latestOffsets = new HashMap<>();
        
        for (Message msg : oldSegment.messages) {
            // Tombstone (null value) means delete
            if (msg.value == null) {
                latestOffsets.put(msg.key, msg.offset);
                System.out.println("  Found tombstone: " + msg.key + " at offset " + msg.offset);
            } else {
                latestOffsets.put(msg.key, msg.offset);
                System.out.println("  Key: " + msg.key + " -> Latest offset: " + msg.offset);
            }
        }
        
        System.out.println("\nHash table built:");
        latestOffsets.forEach((key, offset) -> 
            System.out.println("  " + key + " -> offset " + offset));
        
        // Phase 2: Create NEW segment with only latest values
        System.out.println("\nPhase 2: Creating new compacted segment...");
        LogSegment newSegment = new LogSegment(oldSegment.name + ".cleaned");
        
        for (Message msg : oldSegment.messages) {
            // Is this the latest offset for this key?
            if (latestOffsets.get(msg.key).equals(msg.offset)) {
                // Skip tombstones (null values can be removed after retention)
                if (msg.value != null) {
                    newSegment.append(msg);
                    System.out.println("  ✓ Keeping: " + msg);
                } else {
                    System.out.println("  ✗ Skipping tombstone: " + msg.key);
                }
            } else {
                System.out.println("  ✗ Skipping old value: " + msg);
            }
        }
        
        // Phase 3: Swap (in real Kafka: atomic rename)
        System.out.println("\nPhase 3: Would rename .cleaned -> .log (atomic operation)");
        System.out.println("Phase 4: Would delete old segment");
        
        System.out.println("\n✅ COMPACTION COMPLETE");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        return newSegment;
    }
    
    /**
     * Interview question: Can you implement compaction?
     * Follow-up: Why not modify existing segment?
     */
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║  Kafka Log Compaction - Interview Demo  ║");
        System.out.println("╚══════════════════════════════════════════╝");
        
        // Create old segment (simulating producer writes)
        LogSegment oldSegment = new LogSegment("00000000000000000000.log");
        
        // Simulate message stream
        System.out.println("\n📝 Producer writes (append-only):");
        oldSegment.append(new Message(0, "user1", "Alice"));
        oldSegment.append(new Message(1, "user2", "Bob"));
        oldSegment.append(new Message(2, "user1", "Alice Smith"));      // Update user1
        oldSegment.append(new Message(3, "user3", "Charlie"));
        oldSegment.append(new Message(4, "user2", "Robert"));           // Update user2
        oldSegment.append(new Message(5, "user1", "Alice S. Smith"));  // Update user1 again
        oldSegment.append(new Message(6, "user4", "David"));
        oldSegment.append(new Message(7, "user2", null));               // Tombstone: delete user2
        
        // Display old segment
        oldSegment.display();
        
        System.out.println("\n💡 Notice:");
        System.out.println("- user1 has 3 versions (offsets 0, 2, 5)");
        System.out.println("- user2 has 2 versions + tombstone (offsets 1, 4, 7)");
        System.out.println("- user3 has 1 version (offset 3)");
        System.out.println("- user4 has 1 version (offset 6)");
        
        // Run compaction
        LogSegment compactedSegment = compact(oldSegment);
        
        // Display compacted segment
        compactedSegment.display();
        
        System.out.println("\n📊 RESULTS:");
        System.out.println("Before compaction: " + oldSegment.messages.size() + " messages");
        System.out.println("After compaction:  " + compactedSegment.messages.size() + " messages");
        System.out.println("Space saved: " + 
            (oldSegment.messages.size() - compactedSegment.messages.size()) + " messages");
        
        System.out.println("\n🎯 KEY INSIGHTS:");
        System.out.println("1. Old segment NEVER modified (append-only preserved)");
        System.out.println("2. New segment created with filtered messages");
        System.out.println("3. Old segment deleted after atomic swap");
        System.out.println("4. Sequential I/O throughout (fast!)");
        System.out.println("5. Tombstones (null) delete keys after retention");
        
        // Interview follow-up scenarios
        System.out.println("\n" + "=".repeat(50));
        System.out.println("INTERVIEW FOLLOW-UP QUESTIONS:");
        System.out.println("=".repeat(50));
        
        System.out.println("\nQ1: Why not update in place (binary search + update)?");
        System.out.println("A1: Violates append-only! Must stay immutable.");
        System.out.println("    Sequential I/O is 6000x faster than random I/O.");
        
        System.out.println("\nQ2: What if segment is huge (10 GB)?");
        System.out.println("A2: Still works! Sequential read + write is efficient.");
        System.out.println("    Need 2x space temporarily during compaction.");
        
        System.out.println("\nQ3: What about active segment?");
        System.out.println("A3: Never compact active segment (still being written).");
        System.out.println("    Only compact closed/inactive segments.");
        
        System.out.println("\nQ4: How to delete a key?");
        System.out.println("A4: Send tombstone (null value).");
        System.out.println("    Tombstone kept for delete.retention.ms (24h).");
        System.out.println("    Then tombstone itself removed.");
        
        System.out.println("\nQ5: Time complexity?");
        System.out.println("A5: O(n) - Single sequential scan of segment.");
        System.out.println("    Hash table build: O(n)");
        System.out.println("    Filter and write: O(n)");
        System.out.println("    Total: O(n) - Very efficient!");
        
        System.out.println("\n" + "=".repeat(50));
        System.out.println("✨ Run this code in your interview to explain compaction!");
        System.out.println("=".repeat(50));
    }
}

