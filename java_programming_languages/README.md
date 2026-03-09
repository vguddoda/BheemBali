# Java 8 Features - Hands-On Practice Guide

**Clean, concise, production-ready examples for mastering Java 8 critical features.**

## 📋 What's Inside

| File | Focus | Key Concepts |
|------|-------|-------------|
| `LambdaDemo.java` | Lambda Expressions | Predicate, Function, Consumer, Supplier, Method References |
| `StreamsDemo.java` | Streams API | filter(), map(), reduce(), sorted(), collect() |
| `MapReduceExamples.java` | **map() vs reduce()** | **Deep dive with 8 visual examples** ⭐ |
| `OptionalDemo.java` | Optional API | Null safety, orElse(), map(), ifPresent() |
| `CollectorsDemo.java` | Collectors | toList(), toSet(), groupingBy(), joining() |
| `DateTimeDemo.java` | Date/Time API | LocalDate, LocalTime, Period, Formatting |
| `MyArrayListDemo.java` | ArrayList Internals | Dynamic arrays, capacity, resizing |

---

## 🚀 Quick Start

### Compile All
```bash
javac *.java
```

### Run Individual Demos
```bash
java LambdaDemo        # Lambda expressions & functional interfaces
java StreamsDemo       # Stream operations (filter, map, reduce)
java MapReduceExamples # Deep dive: map() vs reduce() ⭐ NEW!
java OptionalDemo      # Null-safe programming
java CollectorsDemo    # Collecting stream results
java DateTimeDemo      # Modern date/time handling
java MyArrayListDemo   # How ArrayList works internally
```

### Run All at Once
```bash
./run_all.sh
```

---

## 🎯 Key Concepts Explained

### 1. **Lambda Expressions**
```java
// Old way (Anonymous class)
Predicate<Integer> isEven = new Predicate<Integer>() {
    public boolean test(Integer n) { return n % 2 == 0; }
};

// Lambda way
Predicate<Integer> isEven = n -> n % 2 == 0;
```

### 2. **filter() vs map()**
- **`filter()`** - **Selects** elements (gatekeeper)
  ```java
  [1,2,3,4,5].filter(n -> n % 2 == 0) → [2,4]
  ```
- **`map()`** - **Transforms** elements (transformer)
  ```java
  [1,2,3,4,5].map(n -> n * 2) → [2,4,6,8,10]
  ```

### 3. **Stream Pipeline**
```java
source.stream()           // Create stream
  .filter()               // Intermediate operation
  .map()                  // Intermediate operation
  .collect()              // Terminal operation (triggers execution)
```

### 4. **Optional - Null Safety**
```java
// Avoid NullPointerException
Optional<User> user = findUserById(id);
String name = user.map(User::getName).orElse("Guest");
```

---

## 💡 Real-World Use Cases

### Filter + Map (Data Processing)
```java
// Get emails of active users
List<String> emails = users.stream()
    .filter(u -> u.isActive())
    .map(u -> u.getEmail())
    .collect(Collectors.toList());
```

### Grouping (Analytics)
```java
// Group employees by department
Map<String, List<Employee>> byDept = employees.stream()
    .collect(Collectors.groupingBy(Employee::getDepartment));
```

### Optional (Database Queries)
```java
// Safe user lookup
Optional<User> user = userRepository.findById(id);
user.ifPresent(u -> sendEmail(u.getEmail()));
```

---

## 📝 Practice Tips

1. **Start with LambdaDemo** - Understand functional interfaces
2. **Master StreamsDemo** - filter() and map() are 80% of your usage
3. **Learn OptionalDemo** - Eliminate null checks
4. **Study MyArrayListDemo** - Understand dynamic arrays for interviews
5. **Practice chaining** - Combine operations for clean code

---

## 🔍 ArrayList Internals (Interview Question)

**Q: How does ArrayList work internally?**

**A:** ArrayList uses:
- **Dynamic array** (Object[]) - grows as needed
- **Initial capacity: 10**
- **Growth strategy**: doubles when full (newCap = oldCap * 2)
- **add()**: O(1) amortized, O(n) when resizing
- **get()**: O(1) - direct array access
- **remove()**: O(n) - shifts elements left

Run `MyArrayListDemo.java` to see resizing in action!

---

## 🎓 Interview Questions Covered

✅ What is a lambda expression?  
✅ Difference between filter() and map()?  
✅ What is Optional and why use it?  
✅ How does ArrayList grow dynamically?  
✅ What are Collectors in streams?  
✅ Difference between map() and flatMap()?  
✅ What is method reference?  

---

## 📚 Next Steps

After mastering these:
1. Practice on **LeetCode** streams problems
2. Refactor old code to use streams
3. Build a mini-project using all features
4. Explore parallel streams for performance

---

**Happy Coding! 🚀**

