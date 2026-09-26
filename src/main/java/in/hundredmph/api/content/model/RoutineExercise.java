package in.hundredmph.api.content.model;

/** One line of a routine: which movement, in what order, and what it asks for. */
public record RoutineExercise(
        String exerciseId,
        Integer sortOrder,
        String prescription) {
}
