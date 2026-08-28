package in.hundredmph.api.content.model;

/** A kind of training day — Flow, Mobility — within a program. */
public record SessionType(
        String id,
        String programId,
        String name,
        String description,
        String icon,
        boolean isPrimary,
        int frequencyPerWeek,
        int approxDurationMin) {
}
