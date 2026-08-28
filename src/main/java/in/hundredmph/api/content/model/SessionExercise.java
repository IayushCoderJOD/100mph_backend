package in.hundredmph.api.content.model;

/** Which exercises make up a session type, in the order they are performed. */
public record SessionExercise(
        String id,
        String sessionTypeId,
        String exerciseId,
        int sortOrder,
        /** What this session asks for, e.g. "2 x 1m holds". */
        String prescription) {
}
