import java.util.*;

/**
 * OPTIONAL - Null safety, avoid NullPointerException
 */
public class OptionalDemo {
    public static void main(String[] args) {

        // 1. CREATE Optional
        System.out.println("=== CREATE ===");
        Optional<String> empty = Optional.empty();
        Optional<String> present = Optional.of("Hello");
        Optional<String> nullable = Optional.ofNullable(null);
        System.out.println("Empty: " + empty + ", Present: " + present);

        // 2. CHECK if value exists
        System.out.println("\n=== CHECK ===");
        System.out.println("present.isPresent(): " + present.isPresent());
        System.out.println("empty.isEmpty(): " + empty.isEmpty());

        // 3. GET value or default
        System.out.println("\n=== GET VALUE ===");
        String val1 = present.orElse("default");         // Returns "Hello"
        String val2 = empty.orElse("default");           // Returns "default"
        String val3 = empty.orElseGet(() -> "computed"); // Lazy evaluation
        System.out.println("val1: " + val1 + ", val2: " + val2 + ", val3: " + val3);

        // 4. TRANSFORM with map
        System.out.println("\n=== TRANSFORM ===");
        Optional<Integer> length = present.map(String::length);
        System.out.println("Length: " + length.get()); // 5

        // 5. EXECUTE if present
        System.out.println("\n=== EXECUTE ===");
        present.ifPresent(s -> System.out.println("Value found: " + s));
        empty.ifPresent(s -> System.out.println("This won't print"));

        // Real-world example: Find user by ID
        System.out.println("\n=== REAL EXAMPLE ===");
        Optional<User> user = findUserById(1);
        String userName = user.map(User::getName).orElse("Guest");
        System.out.println("User: " + userName);
    }

    static Optional<User> findUserById(int id) {
        return id == 1 ? Optional.of(new User("Alice")) : Optional.empty();
    }

    static class User {
        String name;
        User(String name) { this.name = name; }
        String getName() { return name; }
    }
}

