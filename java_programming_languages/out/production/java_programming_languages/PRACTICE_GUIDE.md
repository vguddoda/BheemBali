# Java 8 Practice Guide

## Quick Reference

### 1. Lambda Expressions (LambdaDemo.java)
- Basic syntax: `(params) -> expression`
- Functional interfaces: Predicate, Function, Consumer, Supplier
- Method references: `Class::method`

### 2. Streams API (StreamsDemo.java)
- **Intermediate**: filter, map, sorted, distinct, limit, skip
- **Terminal**: forEach, collect, reduce, count, anyMatch, allMatch
- **Collectors**: toList, toSet, groupingBy, partitioningBy, joining

### 3. Optional (OptionalDemo.java)
- Creation: `Optional.of()`, `Optional.empty()`, `Optional.ofNullable()`
- Operations: `map()`, `flatMap()`, `filter()`
- Retrieval: `orElse()`, `orElseGet()`, `orElseThrow()`

### 4. Date/Time API (DateTimeDemo.java)
- **LocalDate**: Date without time
- **LocalTime**: Time without date
- **LocalDateTime**: Both date and time
- **ZonedDateTime**: With timezone
- **Period**: Date difference
- **Duration**: Time difference

### 5. Collectors (CollectorsDemo.java)
- Grouping: `groupingBy()`
- Partitioning: `partitioningBy()`
- Aggregating: `counting()`, `summingInt()`, `averagingInt()`
- Joining: `joining()`

## Practice Exercises

### Exercise 1: Lambdas
1. Create a list of integers, filter evens, multiply by 3
2. Sort strings by length using lambda
3. Create custom functional interface for calculations

### Exercise 2: Streams
1. Find max value in a list
2. Group employees by department
3. Calculate average salary per city

### Exercise 3: Optional
1. Handle null user lookups safely
2. Chain multiple optional operations
3. Provide default values for missing data

### Exercise 4: Date/Time
1. Calculate days between two dates
2. Find all Mondays in current month
3. Format date in multiple patterns

### Exercise 5: Collectors
1. Group and count items
2. Create custom collector
3. Partition data by condition

## Run Examples

```bash
# Compile
javac LambdaDemo.java

# Run
java LambdaDemo
```

## Key Interview Questions

1. **Lambda vs Anonymous Class**: Lambdas are more concise, can access effectively final variables
2. **Stream vs Collection**: Streams don't store data, support lazy evaluation
3. **Optional.get() risk**: Can throw NoSuchElementException, use orElse() instead
4. **Parallel Streams**: Use for CPU-intensive operations on large datasets
5. **Collectors.groupingBy**: Groups elements by classifier function into Map

