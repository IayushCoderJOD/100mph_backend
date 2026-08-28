package in.hundredmph.api.content.model;

import java.util.List;

/**
 * Everything the client needs to render one program, in a single response.
 * The app fetches this once on entering a program rather than assembling it
 * from five round trips.
 */
public record ProgramContent(
        in.hundredmph.api.domain.content.Program program,
        List<SessionType> sessionTypes,
        List<Exercise> exercises,
        List<SessionExercise> sessionExercises,
        List<SignatureExercise> signatureExercises,
        List<ProgressionLevel> progressionLevels,
        List<LearnContent> learnContent,
        List<LearnTopic> learnTopics,
        /** The program's starting week, used to seed a new member's schedule. */
        List<DefaultScheduleEntry> defaultSchedule) {
}
