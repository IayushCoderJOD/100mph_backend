package in.hundredmph.api.domain.schedule;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;

/**
 * Mirrors DayOfWeek in src/data/types.ts.
 *
 * <p>Training plans are written Monday → Sunday, so {@link #ordered()} starts
 * on Monday rather than following java.time's or JavaScript's conventions.
 */
public enum DayOfWeek {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY;

    private static final List<DayOfWeek> ORDERED =
            List.of(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY);

    /** The week in the order a plan is written and rendered. */
    public static List<DayOfWeek> ordered() {
        return ORDERED;
    }

    public static DayOfWeek of(java.time.LocalDate date) {
        return valueOf(date.getDayOfWeek().name());
    }

    @JsonValue
    public String wire() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static DayOfWeek from(String value) {
        return valueOf(value.trim().toUpperCase());
    }
}
