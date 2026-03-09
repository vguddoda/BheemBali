# MAP vs REDUCE - Complete Guide with Examples

## 🎯 Visual Comparison

```
┌─────────────────────────────────────────────────────────────┐
│                         MAP                                 │
│  Transform each element (1-to-1 conversion)                 │
│                                                             │
│  [1, 2, 3, 4, 5]                                           │
│   ↓  ↓  ↓  ↓  ↓   map(n → n * 2)                         │
│  [2, 4, 6, 8, 10]                                          │
│                                                             │
│  Input: 5 items → Output: 5 items (transformed)            │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                       REDUCE                                │
│  Combine all elements into ONE result                       │
│                                                             │
│  [1, 2, 3, 4, 5]                                           │
│   │  │  │  │  │                                            │
│   └──┼──┼──┼──┘   reduce(0, (a,b) → a + b)               │
│      └──┼──┼──┘                                            │
│         └──┼──┘                                            │
│            └──┘                                            │
│             15                                              │
│                                                             │
│  Input: 5 items → Output: 1 value (aggregated)             │
└─────────────────────────────────────────────────────────────┘
```

---

## 📊 MAP - Transformation Examples

### Example 1: Double Numbers
```java
List<Integer> nums = Arrays.asList(1, 2, 3, 4, 5);

List<Integer> doubled = nums.stream()
    .map(n -> n * 2)
    .collect(Collectors.toList());

// Input:  [1, 2, 3, 4, 5]
// Output: [2, 4, 6, 8, 10]
```

### Example 2: Extract Property
```java
List<User> users = getUsers();

List<String> emails = users.stream()
    .map(user -> user.getEmail())  // or .map(User::getEmail)
    .collect(Collectors.toList());

// Input:  [User1, User2, User3]
// Output: ["user1@email.com", "user2@email.com", "user3@email.com"]
```

### Example 3: Convert Type
```java
List<Integer> nums = Arrays.asList(1, 2, 3);

List<String> strings = nums.stream()
    .map(n -> "Number: " + n)
    .collect(Collectors.toList());

// Input:  [1, 2, 3]
// Output: ["Number: 1", "Number: 2", "Number: 3"]
```

---

## 🔢 REDUCE - Aggregation Examples

### Example 1: Sum All Numbers
```java
List<Integer> nums = Arrays.asList(1, 2, 3, 4, 5);

int sum = nums.stream()
    .reduce(0, (a, b) -> a + b);
    
// Process:
// Step 1: 0 + 1 = 1
// Step 2: 1 + 2 = 3
// Step 3: 3 + 3 = 6
// Step 4: 6 + 4 = 10
// Step 5: 10 + 5 = 15 ✓

// Input:  [1, 2, 3, 4, 5]
// Output: 15
```

### Example 2: Find Maximum
```java
List<Integer> nums = Arrays.asList(3, 7, 2, 9, 1);

int max = nums.stream()
    .reduce(Integer.MIN_VALUE, (a, b) -> a > b ? a : b);
    
// Or simpler:
int max = nums.stream()
    .reduce(Integer.MIN_VALUE, Integer::max);

// Input:  [3, 7, 2, 9, 1]
// Output: 9
```

### Example 3: Multiply All Numbers
```java
List<Integer> nums = Arrays.asList(2, 3, 4);

int product = nums.stream()
    .reduce(1, (a, b) -> a * b);

// Process:
// Step 1: 1 × 2 = 2
// Step 2: 2 × 3 = 6
// Step 3: 6 × 4 = 24 ✓

// Input:  [2, 3, 4]
// Output: 24
```

### Example 4: Concatenate Strings
```java
List<String> words = Arrays.asList("Hello", "World", "Java");

String sentence = words.stream()
    .reduce("", (a, b) -> a + " " + b);

// Process:
// Step 1: "" + "Hello" = "Hello"
// Step 2: "Hello" + " World" = "Hello World"
// Step 3: "Hello World" + " Java" = "Hello World Java"

// Input:  ["Hello", "World", "Java"]
// Output: " Hello World Java"
```

---

## 🔥 MAP + REDUCE Combined

### Example 1: Sum of Squares
```java
List<Integer> nums = Arrays.asList(1, 2, 3, 4, 5);

int sumOfSquares = nums.stream()
    .map(n -> n * n)           // MAP: [1,2,3,4,5] → [1,4,9,16,25]
    .reduce(0, (a, b) -> a + b);  // REDUCE: 1+4+9+16+25 = 55

// Output: 55
```

### Example 2: Total Price of Cart
```java
List<Product> cart = getShoppingCart();

double total = cart.stream()
    .map(product -> product.getPrice())    // MAP: Extract prices
    .reduce(0.0, (a, b) -> a + b);        // REDUCE: Sum prices

// Input:  [Laptop($1000), Mouse($25), Keyboard($75)]
// MAP:    [1000, 25, 75]
// REDUCE: 1100
// Output: 1100.0
```

### Example 3: Count Characters in Words
```java
List<String> words = Arrays.asList("Hello", "World");

int totalChars = words.stream()
    .map(word -> word.length())     // MAP: ["Hello","World"] → [5,5]
    .reduce(0, (a, b) -> a + b);   // REDUCE: 5 + 5 = 10

// Output: 10
```

---

## 🎓 Understanding reduce() Parameters

```java
.reduce(identity, accumulator)
```

- **`identity`**: Starting value (like 0 for sum, 1 for product)
- **`accumulator`**: Function `(result, element) -> newResult`

### Visual Example:
```java
[1, 2, 3, 4].reduce(0, (a, b) -> a + b)

a=0, b=1 → 0+1=1   (identity + first element)
a=1, b=2 → 1+2=3   (previous result + next element)
a=3, b=3 → 3+3=6
a=6, b=4 → 6+4=10  ✓ FINAL RESULT
```

---

## 📝 When to Use What?

| Scenario | Use | Example |
|----------|-----|---------|
| Convert each item | `map()` | Numbers → Strings |
| Extract property | `map()` | Users → Emails |
| Transform data | `map()` | Celsius → Fahrenheit |
| Calculate total | `reduce()` | Sum, Product |
| Find min/max | `reduce()` or `min()/max()` | Largest number |
| Count something | `reduce()` or `count()` | Total items |
| Combine strings | `reduce()` or `Collectors.joining()` | Concatenate |
| Transform then aggregate | `map()` + `reduce()` | Sum of squares |

---

## 🚀 Common Patterns

### Pattern 1: Calculate Average
```java
List<Integer> nums = Arrays.asList(10, 20, 30, 40, 50);

double average = nums.stream()
    .mapToInt(Integer::intValue)
    .average()
    .orElse(0.0);

// Or manually:
int sum = nums.stream().reduce(0, Integer::sum);
double avg = sum / (double) nums.size();

// Output: 30.0
```

### Pattern 2: Filter then Reduce
```java
List<Integer> nums = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

int sumOfEvens = nums.stream()
    .filter(n -> n % 2 == 0)        // [2,4,6,8,10]
    .reduce(0, (a, b) -> a + b);    // 2+4+6+8+10 = 30

// Output: 30
```

### Pattern 3: Map Different Types
```java
List<String> prices = Arrays.asList("10.99", "25.50", "5.00");

double total = prices.stream()
    .map(Double::parseDouble)       // String → Double
    .reduce(0.0, (a, b) -> a + b);  // Sum

// Output: 41.49
```

---

## ⚡ Method References Shorthand

```java
// Verbose
.reduce(0, (a, b) -> a + b)
// Shorthand
.reduce(0, Integer::sum)

// Verbose
.reduce(Integer.MIN_VALUE, (a, b) -> a > b ? a : b)
// Shorthand
.reduce(Integer.MIN_VALUE, Integer::max)

// Verbose
.map(user -> user.getName())
// Shorthand
.map(User::getName)
```

---

## 🎯 Practice Exercises

Try these yourself:

1. **MAP**: Convert `[1,2,3,4,5]` to `["Odd","Even","Odd","Even","Odd"]`
2. **REDUCE**: Find minimum of `[8,3,9,1,5]`
3. **MAP+REDUCE**: Get total length of `["Java","Python","C++"]`
4. **REAL**: Calculate total salary from list of employees

---

## 💡 Key Takeaways

✅ **MAP** = Transform EACH element (N → N items)  
✅ **REDUCE** = Combine ALL elements (N → 1 value)  
✅ **MAP + REDUCE** = Transform then aggregate  
✅ **Identity** in reduce = Starting value  
✅ **Accumulator** in reduce = How to combine two values  

---

**Run `MapReduceExamples.java` to see all examples in action!**

```bash
javac MapReduceExamples.java && java MapReduceExamples
```

