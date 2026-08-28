package in.hundredmph.api.content.model;

/** The movement a program is built around, and progresses through. */
public record SignatureExercise(
        String id,
        String programId,
        String name,
        String description,
        boolean isCentral) {
}
