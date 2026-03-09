import java.time.*;
import java.time.format.*;

/**
 * DATE & TIME API - Modern date/time handling (replaces java.util.Date)
 */
public class DateTimeDemo {
    public static void main(String[] args) {

        // 1. LOCAL DATE/TIME - Without timezone
        System.out.println("=== LOCAL DATE/TIME ===");
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        LocalDateTime dateTime = LocalDateTime.now();
        System.out.println("Date: " + today + ", Time: " + now);
        System.out.println("DateTime: " + dateTime);

        // 2. CREATE specific date/time
        System.out.println("\n=== CREATE ===");
        LocalDate birthday = LocalDate.of(1990, 1, 15);
        LocalTime meeting = LocalTime.of(14, 30); // 2:30 PM
        System.out.println("Birthday: " + birthday + ", Meeting: " + meeting);

        // 3. ADD/SUBTRACT days/months/years
        System.out.println("\n=== MANIPULATE ===");
        LocalDate nextWeek = today.plusWeeks(1);
        LocalDate lastMonth = today.minusMonths(1);
        LocalDateTime tomorrow = dateTime.plusDays(1);
        System.out.println("Next week: " + nextWeek);
        System.out.println("Last month: " + lastMonth);

        // 4. FORMAT dates
        System.out.println("\n=== FORMAT ===");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
        String formatted = today.format(formatter);
        System.out.println("Formatted: " + formatted);

        // 5. PERIOD - Difference between dates
        System.out.println("\n=== PERIOD (difference) ===");
        LocalDate start = LocalDate.of(2020, 1, 1);
        LocalDate end = LocalDate.now();
        Period period = Period.between(start, end);
        System.out.println("Time since 2020: " + period.getYears() + " years, " +
                          period.getMonths() + " months");
    }
}        // 7. Formatting
        System.out.println("\n7. Formatting:");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
        String formatted = today.format(formatter);
        System.out.println("Formatted: " + formatted);

        // 8. Parsing
        System.out.println("\n8. Parsing:");
        LocalDate parsed = LocalDate.parse("2024-12-25");
        System.out.println("Parsed: " + parsed);

        // 9. Adding/Subtracting
        System.out.println("\n9. Date manipulation:");
        LocalDate nextWeek = today.plusWeeks(1);
        LocalDate lastMonth = today.minusMonths(1);
        System.out.println("Next week: " + nextWeek);
        System.out.println("Last month: " + lastMonth);

        // 10. Getting date components
        System.out.println("\n10. Date components:");
        System.out.println("Year: " + today.getYear());
        System.out.println("Month: " + today.getMonth());
        System.out.println("Day: " + today.getDayOfMonth());
        System.out.println("Day of week: " + today.getDayOfWeek());

        // 11. Instant - Timestamp
        System.out.println("\n11. Instant:");
        Instant timestamp = Instant.now();
        System.out.println("Timestamp: " + timestamp);

        // 12. TemporalAdjusters
        System.out.println("\n12. TemporalAdjusters:");
        LocalDate firstDayOfMonth = today.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate lastDayOfMonth = today.with(TemporalAdjusters.lastDayOfMonth());
        LocalDate nextMonday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        System.out.println("First day of month: " + firstDayOfMonth);
        System.out.println("Last day of month: " + lastDayOfMonth);
        System.out.println("Next Monday: " + nextMonday);

        // 13. Comparing dates
        System.out.println("\n13. Comparing dates:");
        LocalDate date1 = LocalDate.of(2024, 1, 1);
        LocalDate date2 = LocalDate.of(2024, 12, 31);
        System.out.println("date1 before date2? " + date1.isBefore(date2));
        System.out.println("date1 after date2? " + date1.isAfter(date2));

        // 14. Age calculation
        System.out.println("\n14. Calculate age:");
        LocalDate birthDate = LocalDate.of(1990, 5, 15);
        Period age = Period.between(birthDate, today);
        System.out.println("Age: " + age.getYears() + " years");

        // 15. Custom formatting
        System.out.println("\n15. Custom formatting:");
        DateTimeFormatter custom = DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy");
        System.out.println("Today: " + today.format(custom));
    }
}

