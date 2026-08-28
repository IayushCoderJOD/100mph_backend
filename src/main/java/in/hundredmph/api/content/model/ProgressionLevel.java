package in.hundredmph.api.content.model;

/** One rung on a signature exercise's ladder. */
public record ProgressionLevel(
        String id,
        String signatureExerciseId,
        String name,
        int level,
        String goalLabel,
        /** "time" or "reps" — how the goal is measured. */
        String metric) {
}
