import java.util.*;
import java.util.stream.*;

/**
 * STREAMS API - Process collections functionally
 * Stream pipeline: source.stream() -> intermediate ops -> terminal op
 */
public class StreamsDemo {
    public static void main(String[] args) {
        List<Integer> nums = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        // 1. FILTER - Keep elements matching condition (Predicate)
        System.out.println("=== FILTER (select) ===");
        List<Integer> evens = nums.stream()
            .filter(n -> n % 2 == 0)  // Keeps: 2,4,6,8,10
            .collect(Collectors.toList());
        System.out.println("Even numbers: " + evens);

        // 2. MAP - Transform each element (Function)
        System.out.println("\n=== MAP (transform) ===");
        List<Integer> squares = nums.stream()
            .map(n -> n * n)  // 1->1, 2->4, 3->9, ...
            .collect(Collectors.toList());
        System.out.println("Squares: " + squares);

        // 3. FILTER + MAP - Chain operations
        System.out.println("\n=== FILTER + MAP (chain) ===");
        // Filter evens, then double them
        List<Integer> result = nums.stream()
            .filter(n -> n % 2 == 0)  // [2,4,6,8,10]
            .map(n -> n * 2)          // [4,8,12,16,20]
            .collect(Collectors.toList());
        System.out.println("Even numbers doubled: " + result);

        // 4. REDUCE - Aggregate to single value
        System.out.println("\n=== REDUCE (aggregate) ===");
        int sum = nums.stream().reduce(0, (a, b) -> a + b);
        int max = nums.stream().reduce(Integer.MIN_VALUE, Integer::max);
        System.out.println("Sum: " + sum + ", Max: " + max);

        // 5. SORTED - Sort elements
        System.out.println("\n=== SORTED ===");
        List<Integer> sorted = nums.stream()
            .filter(n -> n > 5)
            .sorted((a, b) -> b - a)  // Descending
            .collect(Collectors.toList());
        System.out.println("Nums > 5 (desc): " + sorted);

        // 6. COUNT, MIN, MAX - Terminal operations
        System.out.println("\n=== TERMINAL OPS ===");
        long count = nums.stream().filter(n -> n > 5).count();
        Optional<Integer> min = nums.stream().min(Integer::compare);
        System.out.println("Count > 5: " + count);
        System.out.println("Min: " + min.get());
    }
}
