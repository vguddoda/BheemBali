import java.util.*;
import java.util.stream.*;

/**
 * MAP vs REDUCE - Deep Dive with Visual Examples
 */
public class MapReduceExamples {
    public static void main(String[] args) {
        List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5);

        System.out.println("╔════════════════════════════════════════════╗");
        System.out.println("║           MAP - TRANSFORMATION             ║");
        System.out.println("╚════════════════════════════════════════════╝\n");

        // ═══════════════════════════════════════════════════════════
        // MAP EXAMPLE 1: Double each number
        // ═══════════════════════════════════════════════════════════
        System.out.println("1️⃣  MAP: Double each number");
        System.out.println("   Input:  " + numbers);
        List<Integer> doubled = numbers.stream()
            .map(n -> n * 2)  // Transform: n → n*2
            .collect(Collectors.toList());
        System.out.println("   Output: " + doubled);
        System.out.println("   Process: 1→2, 2→4, 3→6, 4→8, 5→10\n");

        // ═══════════════════════════════════════════════════════════
        // MAP EXAMPLE 2: Square each number
        // ═══════════════════════════════════════════════════════════
        System.out.println("2️⃣  MAP: Square each number");
        System.out.println("   Input:  " + numbers);
        List<Integer> squared = numbers.stream()
            .map(n -> n * n)  // Transform: n → n²
            .collect(Collectors.toList());
        System.out.println("   Output: " + squared);
        System.out.println("   Process: 1→1, 2→4, 3→9, 4→16, 5→25\n");

        // ═══════════════════════════════════════════════════════════
        // MAP EXAMPLE 3: Convert to String
        // ═══════════════════════════════════════════════════════════
        System.out.println("3️⃣  MAP: Convert numbers to strings");
        System.out.println("   Input:  " + numbers);
        List<String> strings = numbers.stream()
            .map(n -> "Number: " + n)  // Transform: Integer → String
            .collect(Collectors.toList());
        System.out.println("   Output: " + strings);
        System.out.println("   Process: 1→\"Number: 1\", 2→\"Number: 2\", etc.\n");

        System.out.println("\n╔════════════════════════════════════════════╗");
        System.out.println("║         REDUCE - AGGREGATION               ║");
        System.out.println("╚════════════════════════════════════════════╝\n");

        // ═══════════════════════════════════════════════════════════
        // REDUCE EXAMPLE 1: Sum all numbers
        // ═══════════════════════════════════════════════════════════
        System.out.println("4️⃣  REDUCE: Sum all numbers");
        System.out.println("   Input:  " + numbers);
        int sum = numbers.stream()
            .reduce(0, (a, b) -> a + b);  // Accumulate: sum
        System.out.println("   Output: " + sum);
        System.out.println("   Process:");
        System.out.println("      Step 1: 0 + 1 = 1");
        System.out.println("      Step 2: 1 + 2 = 3");
        System.out.println("      Step 3: 3 + 3 = 6");
        System.out.println("      Step 4: 6 + 4 = 10");
        System.out.println("      Step 5: 10 + 5 = 15 ✓\n");

        // ═══════════════════════════════════════════════════════════
        // REDUCE EXAMPLE 2: Product of all numbers
        // ═══════════════════════════════════════════════════════════
        System.out.println("5️⃣  REDUCE: Product of all numbers");
        System.out.println("   Input:  " + numbers);
        int product = numbers.stream()
            .reduce(1, (a, b) -> a * b);  // Accumulate: multiply
        System.out.println("   Output: " + product);
        System.out.println("   Process:");
        System.out.println("      Step 1: 1 × 1 = 1");
        System.out.println("      Step 2: 1 × 2 = 2");
        System.out.println("      Step 3: 2 × 3 = 6");
        System.out.println("      Step 4: 6 × 4 = 24");
        System.out.println("      Step 5: 24 × 5 = 120 ✓\n");

        // ═══════════════════════════════════════════════════════════
        // REDUCE EXAMPLE 3: Find maximum
        // ═══════════════════════════════════════════════════════════
        System.out.println("6️⃣  REDUCE: Find maximum number");
        System.out.println("   Input:  " + numbers);
        int max = numbers.stream()
            .reduce(Integer.MIN_VALUE, (a, b) -> a > b ? a : b);
        System.out.println("   Output: " + max);
        System.out.println("   Process: Keep the larger of (a, b) at each step\n");

        System.out.println("\n╔════════════════════════════════════════════╗");
        System.out.println("║       MAP + REDUCE COMBINED                ║");
        System.out.println("╚════════════════════════════════════════════╝\n");

        // ═══════════════════════════════════════════════════════════
        // COMBINED EXAMPLE: Square then sum
        // ═══════════════════════════════════════════════════════════
        System.out.println("7️⃣  MAP + REDUCE: Sum of squares");
        System.out.println("   Input:  " + numbers);
        int sumOfSquares = numbers.stream()
            .map(n -> n * n)           // Step 1: Square each → [1,4,9,16,25]
            .reduce(0, (a, b) -> a + b);  // Step 2: Sum all → 55
        System.out.println("   Process:");
        System.out.println("      MAP:    [1,2,3,4,5] → [1,4,9,16,25]");
        System.out.println("      REDUCE: 1+4+9+16+25 = 55");
        System.out.println("   Output: " + sumOfSquares + "\n");

        System.out.println("\n╔════════════════════════════════════════════╗");
        System.out.println("║          REAL-WORLD EXAMPLES               ║");
        System.out.println("╚════════════════════════════════════════════╝\n");

        // ═══════════════════════════════════════════════════════════
        // REAL EXAMPLE: Calculate total price
        // ═══════════════════════════════════════════════════════════
        List<Product> cart = Arrays.asList(
            new Product("Laptop", 1000),
            new Product("Mouse", 25),
            new Product("Keyboard", 75)
        );

        System.out.println("8️⃣  Shopping Cart Total");
        System.out.println("   Cart: " + cart);

        double total = cart.stream()
            .map(p -> p.price)           // MAP: Extract prices
            .reduce(0.0, (a, b) -> a + b);  // REDUCE: Sum prices

        System.out.println("   MAP:    Extract prices → [1000, 25, 75]");
        System.out.println("   REDUCE: Sum → $" + total);

        System.out.println("\n\n╔════════════════════════════════════════════╗");
        System.out.println("║             KEY DIFFERENCES                ║");
        System.out.println("╚════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("MAP:");
        System.out.println("  ✓ Transforms EACH element");
        System.out.println("  ✓ Input: N elements → Output: N elements");
        System.out.println("  ✓ Example: [1,2,3] → map(×2) → [2,4,6]");
        System.out.println("  ✓ Use when: Converting/transforming data");
        System.out.println();
        System.out.println("REDUCE:");
        System.out.println("  ✓ Combines ALL elements");
        System.out.println("  ✓ Input: N elements → Output: 1 value");
        System.out.println("  ✓ Example: [1,2,3] → reduce(+) → 6");
        System.out.println("  ✓ Use when: Calculating totals, max, min, average");
    }

    static class Product {
        String name;
        double price;

        Product(String name, double price) {
            this.name = name;
            this.price = price;
        }

        @Override
        public String toString() {
            return name + "($" + price + ")";
        }
    }
}

