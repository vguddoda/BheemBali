import java.util.*;
import java.util.function.*;

/**
 * LAMBDA EXPRESSIONS - Java 8 Feature
 * Syntax: (parameters) -> expression OR (parameters) -> { statements }
 */
public class LambdaDemo {
    public static void main(String[] args) {
        List<Integer> nums = Arrays.asList(1, 2, 3, 4, 5);

        // 1. PREDICATE - Test condition, returns boolean
        // Real use: Filtering data
        System.out.println("=== PREDICATE (test -> true/false) ===");
        Predicate<Integer> isEven = n -> n % 2 == 0;
        nums.stream().filter(isEven).forEach(System.out::println); // Output: 2, 4

        // 2. FUNCTION - Transform input to output
        // Real use: Converting/mapping data
        System.out.println("\n=== FUNCTION (transform) ===");
        Function<Integer, String> toSquare = n -> "Square of " + n + " = " + (n * n);
        nums.stream().map(toSquare).forEach(System.out::println);

        // 3. CONSUMER - Accept input, no return (void)
        // Real use: Printing, logging, saving
        System.out.println("\n=== CONSUMER (process) ===");
        Consumer<Integer> printer = n -> System.out.println("Processing: " + n);
        nums.forEach(printer);

        // 4. SUPPLIER - No input, returns output
        // Real use: Lazy initialization, factories
        System.out.println("\n=== SUPPLIER (generate) ===");
        Supplier<Double> random = () -> Math.random();
        System.out.println("Random: " + random.get());

        // 5. METHOD REFERENCE - Shorthand for lambdas
        System.out.println("\n=== METHOD REFERENCE ===");
        nums.forEach(System.out::println); // Same as: n -> System.out.println(n)
    }
}

