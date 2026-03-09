# Java 8 Quick Reference Cheat Sheet

## 🎯 FILTER vs MAP - The Most Important Concept

```java
List<Integer> nums = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

// FILTER - SELECTS elements (Predicate: returns boolean)
// Think: "KEEP only what passes the test"
nums.stream()
    .filter(n -> n % 2 == 0)  // Keep only even numbers
    .collect(Collectors.toList());
// Result: [2, 4, 6, 8, 10]

// MAP - TRANSFORMS elements (Function: returns new value)
// Think: "CONVERT each element to something else"
nums.stream()
    .map(n -> n * 2)  // Double each number
    .collect(Collectors.toList());
// Result: [2, 4, 6, 8, 10, 12, 14, 16, 18, 20]

// CHAINING - Use both together
nums.stream()
    .filter(n -> n % 2 == 0)  // Step 1: Keep evens → [2,4,6,8,10]
    .map(n -> n * 2)          // Step 2: Double them → [4,8,12,16,20]
    .collect(Collectors.toList());
// Result: [4, 8, 12, 16, 20]
```

---

## 🔥 4 Functional Interfaces You MUST Know

```java
// 1. PREDICATE - Test/Filter (returns boolean)
Predicate<Integer> isEven = n -> n % 2 == 0;
isEven.test(4);  // true

// 2. FUNCTION - Transform/Map (returns different type)
Function<String, Integer> length = s -> s.length();
length.apply("hello");  // 5

// 3. CONSUMER - Process/Action (returns nothing)
Consumer<String> print = s -> System.out.println(s);
print.accept("Hello");  // Prints: Hello

// 4. SUPPLIER - Generate (no input, returns value)
Supplier<Double> random = () -> Math.random();
random.get();  // 0.742...
```

---

## 📊 Stream Operations Cheat Sheet

| Operation | Type | Purpose | Example |
|-----------|------|---------|---------|
| `filter()` | Intermediate | Select elements | `.filter(n -> n > 5)` |
| `map()` | Intermediate | Transform elements | `.map(n -> n * 2)` |
| `sorted()` | Intermediate | Sort elements | `.sorted()` |
| `distinct()` | Intermediate | Remove duplicates | `.distinct()` |
| `limit()` | Intermediate | Take first N | `.limit(5)` |
| `skip()` | Intermediate | Skip first N | `.skip(2)` |
| `collect()` | Terminal | Gather results | `.collect(Collectors.toList())` |
| `forEach()` | Terminal | Process each | `.forEach(System.out::println)` |
| `reduce()` | Terminal | Aggregate | `.reduce(0, Integer::sum)` |
| `count()` | Terminal | Count elements | `.count()` |
| `min()/max()` | Terminal | Find min/max | `.min(Integer::compare)` |

---

## 💎 Optional - Null Safety

```java
// CREATE Optional
Optional<String> empty = Optional.empty();
Optional<String> value = Optional.of("Hello");
Optional<String> maybe = Optional.ofNullable(getName());

// GET value safely
String name = optional.orElse("Guest");              // Default value
String name = optional.orElseGet(() -> "Guest");     // Lazy default
String name = optional.orElseThrow(Exception::new);  // Throw if empty

// CHECK & TRANSFORM
if (optional.isPresent()) { ... }
optional.ifPresent(val -> System.out.println(val));
Optional<Integer> length = optional.map(String::length);
```

---

## 📅 Date/Time Quick Reference

```java
// Current date/time
LocalDate today = LocalDate.now();           // 2024-03-09
LocalTime now = LocalTime.now();             // 14:30:00
LocalDateTime dateTime = LocalDateTime.now(); // 2024-03-09T14:30:00

// Create specific date/time
LocalDate date = LocalDate.of(2024, 3, 9);
LocalTime time = LocalTime.of(14, 30);

// Add/Subtract
date.plusDays(7);      // Next week
date.minusMonths(1);   // Last month

// Format
DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
String formatted = today.format(fmt);  // "09-Mar-2024"
```

---

## 🎓 Common Interview Questions

### Q: Difference between map() and flatMap()?
**A:** 
- `map()` - Transforms each element (1-to-1)
- `flatMap()` - Flattens nested structures (1-to-many)

```java
// map: [[1,2], [3,4]] → [List, List]
// flatMap: [[1,2], [3,4]] → [1, 2, 3, 4]
```

### Q: How does ArrayList resize?
**A:** 
- Starts with capacity 10
- Doubles when full: `newCapacity = oldCapacity * 2`
- Copies elements to new array
- O(n) when resizing, O(1) amortized

### Q: Why use Optional?
**A:** 
- Avoid NullPointerException
- Forces explicit null handling
- Makes API contracts clear
- Enables functional composition

---

## 🚀 Real-World Code Patterns

### Pattern 1: Filter + Map + Collect
```java
// Get emails of active users
List<String> emails = users.stream()
    .filter(User::isActive)
    .map(User::getEmail)
    .collect(Collectors.toList());
```

### Pattern 2: GroupBy
```java
// Group by department
Map<String, List<Employee>> byDept = employees.stream()
    .collect(Collectors.groupingBy(Employee::getDepartment));
```

### Pattern 3: Safe Chaining with Optional
```java
// Avoid null checks
String city = Optional.ofNullable(user)
    .map(User::getAddress)
    .map(Address::getCity)
    .orElse("Unknown");
```

---

## ⚡ Performance Tips

1. **Use parallel streams for large datasets**
   ```java
   list.parallelStream().filter(...).collect(...)
   ```

2. **Avoid repeated stream creation**
   ```java
   // Bad
   list.stream().count();
   list.stream().max();
   
   // Good
   IntSummaryStatistics stats = list.stream()
       .collect(Collectors.summarizingInt(Integer::intValue));
   ```

3. **Use method references when possible**
   ```java
   .map(String::length)  // Better than .map(s -> s.length())
   ```

---

**Practice these patterns daily! 🎯**

