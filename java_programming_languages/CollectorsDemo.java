import java.util.*;
import java.util.stream.*;

/**
 * COLLECTORS - Convert stream results to collections/values
 */
public class CollectorsDemo {
    public static void main(String[] args) {
        List<Person> people = Arrays.asList(
            new Person("Alice", 25, "NYC"),
            new Person("Bob", 30, "LA"),
            new Person("Charlie", 25, "NYC"),
            new Person("Diana", 35, "LA")
        );

        // 1. TO LIST - Collect to ArrayList
        System.out.println("=== TO LIST ===");
        List<String> names = people.stream()
            .map(Person::getName)
            .collect(Collectors.toList());
        System.out.println("Names: " + names);

        // 2. TO SET - Collect unique values
        System.out.println("\n=== TO SET ===");
        Set<String> cities = people.stream()
            .map(Person::getCity)
            .collect(Collectors.toSet());
        System.out.println("Unique cities: " + cities);

        // 3. JOINING - Concatenate strings
        System.out.println("\n=== JOINING ===");
        String joined = people.stream()
            .map(Person::getName)
            .collect(Collectors.joining(", ", "[", "]"));
        System.out.println("Joined: " + joined);

        // 4. GROUPING BY - Group into Map
        System.out.println("\n=== GROUPING BY ===");
        Map<String, List<Person>> byCity = people.stream()
            .collect(Collectors.groupingBy(Person::getCity));
        System.out.println("Grouped by city: " + byCity);

        // 5. SUMMARIZING - Get statistics
        System.out.println("\n=== SUMMARIZING ===");
        IntSummaryStatistics stats = people.stream()
            .collect(Collectors.summarizingInt(Person::getAge));
        System.out.println("Age stats - Avg: " + stats.getAverage() +
                         ", Min: " + stats.getMin() + ", Max: " + stats.getMax());
    }
}

class Person {
    private String name;
    private int age;
    private String city;

    Person(String name, int age, String city) {
        this.name = name;
        this.age = age;
        this.city = city;
    }

    String getName() { return name; }
    int getAge() { return age; }
    String getCity() { return city; }

    @Override
    public String toString() {
        return name + "(" + age + ")";
    }
}
        IntSummaryStatistics stats = people.stream()
            .collect(Collectors.summarizingInt(Person::getAge));
        System.out.println("Count: " + stats.getCount());
        System.out.println("Sum: " + stats.getSum());
        System.out.println("Min: " + stats.getMin());
        System.out.println("Max: " + stats.getMax());
        System.out.println("Average: " + stats.getAverage());

        // 8. groupingBy()
        System.out.println("\n8. Group by city:");
        Map<String, List<Person>> byCity = people.stream()
            .collect(Collectors.groupingBy(Person::getCity));
        byCity.forEach((city, list) ->
            System.out.println(city + ": " + list.size() + " people"));

        // 9. groupingBy with counting
        System.out.println("\n9. Count by city:");
        Map<String, Long> countByCity = people.stream()
            .collect(Collectors.groupingBy(Person::getCity, Collectors.counting()));
        countByCity.forEach((city, cnt) ->
            System.out.println(city + ": " + cnt));

        // 10. partitioningBy()
        System.out.println("\n10. Partition by adult/minor:");
        Map<Boolean, List<Person>> partitioned = people.stream()
            .collect(Collectors.partitioningBy(p -> p.getAge() >= 18));
        System.out.println("Adults: " + partitioned.get(true).size());
        System.out.println("Minors: " + partitioned.get(false).size());

        // 11. mapping()
        System.out.println("\n11. Group cities and collect names:");
        Map<String, List<String>> namesByCity = people.stream()
            .collect(Collectors.groupingBy(
                Person::getCity,
                Collectors.mapping(Person::getName, Collectors.toList())
            ));
        namesByCity.forEach((city, nameList) ->
            System.out.println(city + ": " + nameList));

        // 12. maxBy() and minBy()
        System.out.println("\n12. Oldest and youngest:");
        Optional<Person> oldest = people.stream()
            .collect(Collectors.maxBy(Comparator.comparing(Person::getAge)));
        Optional<Person> youngest = people.stream()
            .collect(Collectors.minBy(Comparator.comparing(Person::getAge)));
        oldest.ifPresent(p -> System.out.println("Oldest: " + p.getName() + " (" + p.getAge() + ")"));
        youngest.ifPresent(p -> System.out.println("Youngest: " + p.getName() + " (" + p.getAge() + ")"));

        // 13. toMap()
        System.out.println("\n13. Create name-to-age map:");
        Map<String, Integer> nameAgeMap = people.stream()
            .collect(Collectors.toMap(Person::getName, Person::getAge));
        System.out.println(nameAgeMap);

        // 14. collectingAndThen()
        System.out.println("\n14. Collect and transform:");
        int size = people.stream()
            .collect(Collectors.collectingAndThen(
                Collectors.toList(),
                List::size
            ));
        System.out.println("Total people: " + size);

        // 15. Complex grouping
        System.out.println("\n15. Average age by city:");
        Map<String, Double> avgAgeByCity = people.stream()
            .collect(Collectors.groupingBy(
                Person::getCity,
                Collectors.averagingInt(Person::getAge)
            ));
        avgAgeByCity.forEach((city, avg) ->
            System.out.println(city + ": " + String.format("%.1f", avg) + " years"));
    }

    static List<Person> getPeople() {
        return Arrays.asList(
            new Person("Alice", 25, "New York"),
            new Person("Bob", 30, "Boston"),
            new Person("Charlie", 35, "New York"),
            new Person("Diana", 28, "Chicago"),
            new Person("Eve", 22, "Boston"),
            new Person("Frank", 40, "New York"),
            new Person("Grace", 15, "Chicago"),
            new Person("Henry", 32, "Boston")
        );
    }

    static class Person {
        private String name;
        private int age;
        private String city;

        public Person(String name, int age, String city) {
            this.name = name;
            this.age = age;
            this.city = city;
        }

        public String getName() { return name; }
        public int getAge() { return age; }
        public String getCity() { return city; }
    }
}

