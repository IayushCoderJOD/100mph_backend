package in.hundredmph.api.content;

import in.hundredmph.api.common.ApiException;
import in.hundredmph.api.common.ErrorCode;
import in.hundredmph.api.content.model.Exercise;
import in.hundredmph.api.content.model.LearnContent;
import in.hundredmph.api.content.model.ProgramContent;
import in.hundredmph.api.content.model.Routine;
import in.hundredmph.api.domain.content.Program;
import in.hundredmph.api.exercise.ExerciseLibrary;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The read-only catalogue.
 *
 * <p>{@code /programs} is public because the program-selection screen is
 * reached before a member has one. Everything below it needs a token: the
 * content is not secret, but it is what a membership buys, so it is not handed
 * to anonymous callers either.
 */
@RestController
@RequestMapping("/v1")
public class ContentController {

    private final ContentCatalogue catalogue;
    private final ExerciseLibrary exercises;

    public ContentController(ContentCatalogue catalogue, ExerciseLibrary exercises) {
        this.catalogue = catalogue;
        this.exercises = exercises;
    }

    /** Public — backs the program picker. */
    @GetMapping("/programs")
    public List<Program> programs() {
        return catalogue.programs();
    }

    /**
     * Everything needed to render one program, in one request: session types,
     * exercises, their running order, the progression ladder and the lessons.
     */
    @GetMapping("/programs/{programId}/content")
    public ProgramContent programContent(@PathVariable String programId) {
        Program program = catalogue.program(programId)
                .orElseThrow(() -> ApiException.of(ErrorCode.PROGRAM_NOT_FOUND,
                        "No program with id " + programId));

        return new ProgramContent(
                program,
                catalogue.sessionTypesFor(programId),
                exercises.publishedFor(programId),
                catalogue.sessionExercisesForProgram(programId),
                catalogue.signatureExercisesFor(programId),
                catalogue.signatureExercisesFor(programId).stream()
                        .flatMap(signature -> catalogue.progressionLevelsFor(signature.id()).stream())
                        .toList(),
                catalogue.learnContentFor(programId),
                catalogue.learnTopics(),
                catalogue.defaultScheduleFor(programId));
    }

    /** Every routine a coach can set — backs the picker on the client screen. */
    @GetMapping("/routines")
    public List<Routine> routines() {
        return catalogue.routines();
    }

    @GetMapping("/routines/{routineId}")
    public Routine routine(@PathVariable String routineId) {
        return catalogue.routine(routineId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND,
                        "No routine with id " + routineId));
    }

    /** The published library — what a coach can prescribe. Filmed and not hidden. */
    @GetMapping("/exercises")
    public List<Exercise> exercises() {
        return exercises.published();
    }

    /** One exercise, for the guide screen opened from a session or a plan — hidden ones too. */
    @GetMapping("/exercises/{exerciseId}")
    public Exercise exercise(@PathVariable String exerciseId) {
        return exercises.find(exerciseId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND,
                        "No exercise with id " + exerciseId));
    }

    @GetMapping("/learn/{contentId}")
    public LearnContent learnContent(@PathVariable String contentId) {
        return catalogue.learnContentById(contentId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND,
                        "No lesson with id " + contentId));
    }
}
