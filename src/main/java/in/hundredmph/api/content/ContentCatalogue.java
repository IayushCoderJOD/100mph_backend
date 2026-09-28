package in.hundredmph.api.content;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.hundredmph.api.content.model.DefaultScheduleEntry;
import in.hundredmph.api.content.model.Exercise;
import in.hundredmph.api.content.model.LearnContent;
import in.hundredmph.api.content.model.LearnTopic;
import in.hundredmph.api.content.model.ProgressionLevel;
import in.hundredmph.api.content.model.Routine;
import in.hundredmph.api.content.model.SessionExercise;
import in.hundredmph.api.content.model.SessionType;
import in.hundredmph.api.content.model.SignatureExercise;
import in.hundredmph.api.domain.content.Program;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The authored catalogue, held in memory.
 *
 * <p>Exercises, programs, session types and lessons are written once by the
 * practice and read by everyone. They are not user data: nothing at runtime
 * mutates them, they are the same for every member, and they are small enough
 * to sit in memory. So they ship as a JSON resource loaded at boot rather than
 * as Mongo collections — which means no query per screen, no seeding step, no
 * chance of a member's database drifting from the app they are running.
 *
 * <p>The file is generated from the app's own {@code src/data/mock.ts}, so the
 * catalogue the API serves and the one the client was built against are the
 * same content by construction.
 *
 * <p>Changing content is therefore a deploy, not a database write. That is the
 * intended trade: it is the correct shape for a catalogue that a physio edits
 * a few times a year, and the wrong one for anything a user can change — all
 * of which lives in Mongo.
 *
 * <p>Exercises are the exception. Admins add and film movements from the app,
 * so they live in the Mongo-backed {@link in.hundredmph.api.exercise.ExerciseLibrary};
 * the exercises in this file only seed it.
 */
@Component
public class ContentCatalogue {

    private static final Logger log = LoggerFactory.getLogger(ContentCatalogue.class);
    private static final String RESOURCE = "content/catalogue.json";

    private final ObjectMapper objectMapper;

    private List<Program> programs = List.of();
    private List<LearnTopic> learnTopics = List.of();
    private List<SessionType> sessionTypes = List.of();
    /** Seeds for the exercise library — filmed movements, then unfilmed drafts. */
    private List<Exercise> authoredExercises = List.of();
    private List<SessionExercise> sessionExercises = List.of();
    private List<SignatureExercise> signatureExercises = List.of();
    private List<ProgressionLevel> progressionLevels = List.of();
    private List<LearnContent> learnContent = List.of();
    private List<DefaultScheduleEntry> defaultSchedule = List.of();
    private List<Routine> routines = List.of();

    private Map<String, Program> programsById = Map.of();
    private Map<String, SessionType> sessionTypesById = Map.of();
    private Map<String, ProgressionLevel> progressionLevelsById = Map.of();
    private Map<String, Routine> routinesById = Map.of();

    public ContentCatalogue(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void load() {
        try (InputStream stream = new ClassPathResource(RESOURCE).getInputStream()) {
            CatalogueFile file = objectMapper.readValue(stream, CatalogueFile.class);

            programs = List.copyOf(file.programs());
            learnTopics = List.copyOf(file.learnTopics());
            sessionTypes = List.copyOf(file.sessionTypes());
            authoredExercises = Stream.concat(
                            file.exercises().stream(),
                            file.draftExercises() == null ? Stream.<Exercise>empty() : file.draftExercises().stream())
                    .toList();
            sessionExercises = List.copyOf(file.sessionExercises());
            signatureExercises = List.copyOf(file.signatureExercises());
            progressionLevels = List.copyOf(file.progressionLevels());
            learnContent = List.copyOf(file.learnContent());
            defaultSchedule = List.copyOf(file.defaultSchedule());
            routines = file.routines() == null ? List.of() : List.copyOf(file.routines());

            programsById = index(programs, Program::id);
            sessionTypesById = index(sessionTypes, SessionType::id);
            progressionLevelsById = index(progressionLevels, ProgressionLevel::id);
            routinesById = index(routines, Routine::id);

            log.info("Catalogue loaded: {} programs, {} exercises, {} routines, {} session types, {} lessons",
                    programs.size(), authoredExercises.size(), routines.size(), sessionTypes.size(),
                    learnContent.size());
        } catch (IOException ex) {
            // Without a catalogue there is no app, so fail the boot loudly
            // rather than serve empty screens.
            throw new IllegalStateException("Could not load " + RESOURCE, ex);
        }
    }

    private static <T> Map<String, T> index(List<T> items, Function<T, String> id) {
        return items.stream().collect(Collectors.toUnmodifiableMap(id, Function.identity()));
    }

    // ------------------------------------------------------------- lookups

    public List<Program> programs() { return programs; }

    public List<Routine> routines() { return routines; }

    public Optional<Routine> routine(String routineId) {
        return Optional.ofNullable(routinesById.get(routineId));
    }

    public List<LearnTopic> learnTopics() { return learnTopics; }

    public Optional<Program> program(String programId) {
        return Optional.ofNullable(programsById.get(programId));
    }

    public boolean hasProgram(String programId) {
        return programId != null && programsById.containsKey(programId);
    }

    /**
     * Every movement the catalogue authors, drafts included. Only the exercise
     * library reads this: it is a seed, and the library is what serves them.
     */
    public List<Exercise> authoredExercises() {
        return authoredExercises;
    }

    public Optional<SessionType> sessionType(String sessionTypeId) {
        return Optional.ofNullable(sessionTypesById.get(sessionTypeId));
    }

    public Optional<ProgressionLevel> progressionLevel(String levelId) {
        return Optional.ofNullable(progressionLevelsById.get(levelId));
    }

    // -------------------------------------------------------- per program

    public List<SessionType> sessionTypesFor(String programId) {
        return sessionTypes.stream().filter(s -> s.programId().equals(programId)).toList();
    }

    /** The running order for one session type, already sorted. */
    public List<SessionExercise> sessionExercisesFor(String sessionTypeId) {
        return sessionExercises.stream()
                .filter(link -> link.sessionTypeId().equals(sessionTypeId))
                .sorted(Comparator.comparingInt(SessionExercise::sortOrder))
                .toList();
    }

    public List<SessionExercise> sessionExercisesForProgram(String programId) {
        List<String> typeIds = sessionTypesFor(programId).stream().map(SessionType::id).toList();
        return sessionExercises.stream()
                .filter(link -> typeIds.contains(link.sessionTypeId()))
                .sorted(Comparator.comparingInt(SessionExercise::sortOrder))
                .toList();
    }

    public List<SignatureExercise> signatureExercisesFor(String programId) {
        return signatureExercises.stream().filter(s -> s.programId().equals(programId)).toList();
    }

    public List<ProgressionLevel> progressionLevelsFor(String signatureExerciseId) {
        return progressionLevels.stream()
                .filter(level -> level.signatureExerciseId().equals(signatureExerciseId))
                .sorted(Comparator.comparingInt(ProgressionLevel::level))
                .toList();
    }

    public List<LearnContent> learnContentFor(String programId) {
        return learnContent.stream()
                .filter(item -> item.programId().equals(programId))
                .sorted(Comparator.comparingInt(LearnContent::sortOrder))
                .toList();
    }

    public Optional<LearnContent> learnContentById(String id) {
        return learnContent.stream().filter(item -> item.id().equals(id)).findFirst();
    }

    public List<DefaultScheduleEntry> defaultScheduleFor(String programId) {
        return defaultSchedule.stream().filter(e -> e.programId().equals(programId)).toList();
    }

    /** Whether a session type belongs to a program — the schedule editor's key check. */
    public boolean sessionTypeBelongsTo(String sessionTypeId, String programId) {
        return sessionType(sessionTypeId)
                .map(type -> type.programId().equals(programId))
                .orElse(false);
    }

    /** The on-disk shape. Field names come from the generated JSON. */
    private record CatalogueFile(
            List<Program> programs,
            List<LearnTopic> learnTopics,
            List<SessionType> sessionTypes,
            List<Exercise> exercises,
            /** Written up but not yet filmed; seeded as drafts. Absent from older files. */
            List<Exercise> draftExercises,
            List<SessionExercise> sessionExercises,
            List<SignatureExercise> signatureExercises,
            List<ProgressionLevel> progressionLevels,
            List<LearnContent> learnContent,
            List<DefaultScheduleEntry> defaultSchedule,
            /** Absent from a catalogue written before routines existed. */
            List<Routine> routines) {
    }
}
