package in.hundredmph.api.exercise;

import in.hundredmph.api.common.ApiException;
import in.hundredmph.api.common.ErrorCode;
import in.hundredmph.api.content.ContentCatalogue;
import in.hundredmph.api.content.model.Exercise;
import in.hundredmph.api.domain.exercise.ExerciseDocument;
import in.hundredmph.api.domain.exercise.ExerciseRepository;
import in.hundredmph.api.exercise.dto.CreateExerciseRequest;
import in.hundredmph.api.exercise.dto.UpdateExerciseRequest;
import in.hundredmph.api.media.MediaStorage;
import jakarta.annotation.PostConstruct;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

/**
 * The exercise library: every movement a coach can prescribe.
 *
 * <p>It lives in Mongo so an admin can add a movement, film it and put it on a
 * client's week without a deploy. The authored catalogue still seeds it on
 * every boot — new movements from mock.ts are added, and ones no admin has
 * touched keep following the catalogue — so the developer's workflow is
 * unchanged for the movements they own.
 *
 * <p>Reads come from memory. A week can carry thirty lines, and the database
 * is a region away from the API, so one round trip per line would make every
 * plan screen slow. The whole library is a few hundred small records; it is
 * loaded at boot, reloaded after every write here, and a miss falls through
 * to the database so a movement written by another instance is still found.
 */
@Service
public class ExerciseLibrary {

    private static final Logger log = LoggerFactory.getLogger(ExerciseLibrary.class);

    /** Mirrors EXERCISE_CATEGORIES in the app's src/data/categories.ts. */
    public static final Set<String> CATEGORIES = Set.of(
            "back", "core", "hips_glutes", "lower_body", "ankle_calf", "upper_body", "mobility", "athletic");

    private static final Comparator<Exercise> BY_NAME =
            Comparator.comparing(exercise -> exercise.name() == null ? "" : exercise.name().toLowerCase(Locale.ROOT));

    private final ExerciseRepository repository;
    private final MongoTemplate mongo;
    private final ContentCatalogue catalogue;
    private final MediaStorage media;

    private volatile Map<String, Exercise> byId = Map.of();

    public ExerciseLibrary(ExerciseRepository repository, MongoTemplate mongo,
                           ContentCatalogue catalogue, MediaStorage media) {
        this.repository = repository;
        this.mongo = mongo;
        this.catalogue = catalogue;
        this.media = media;
    }

    @PostConstruct
    void start() {
        syncFromCatalogue();
        reload();
    }

    // -------------------------------------------------------------- seeding

    /**
     * Brings the library up to date with the authored catalogue. Adds what is
     * new; refreshes what no admin has changed; leaves admin edits alone.
     */
    public void syncFromCatalogue() {
        Instant now = Instant.now();
        Map<String, ExerciseDocument> existing = repository.findAll().stream()
                .collect(Collectors.toMap(ExerciseDocument::getId, Function.identity()));

        List<ExerciseDocument> writes = new ArrayList<>();
        for (Exercise authored : catalogue.authoredExercises()) {
            ExerciseDocument doc = existing.get(authored.id());
            if (doc == null) {
                writes.add(ExerciseDocument.fromCatalogue(authored, now));
            } else if (!doc.isEditedByAdmin() && doc.differsFrom(authored)) {
                doc.applyCatalogue(authored, now);
                writes.add(doc);
            }
        }

        if (!writes.isEmpty()) {
            repository.saveAll(writes);
            log.info("Exercise library: {} movements added or refreshed from the catalogue", writes.size());
        }
    }

    // ---------------------------------------------------------------- reads

    public void reload() {
        byId = repository.findAll().stream()
                .map(ExerciseDocument::toExercise)
                .collect(Collectors.toUnmodifiableMap(Exercise::id, Function.identity()));
    }

    /** Any movement, hidden or not — a plan written before it was hidden still shows it. */
    public Optional<Exercise> find(String exerciseId) {
        if (exerciseId == null || exerciseId.isBlank()) {
            return Optional.empty();
        }
        Exercise cached = byId.get(exerciseId);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<Exercise> fresh = repository.findById(exerciseId).map(ExerciseDocument::toExercise);
        fresh.ifPresent(this::remember);
        return fresh;
    }

    /** What the picker offers: filmed and not hidden, A to Z. */
    public List<Exercise> published() {
        return byId.values().stream().filter(Exercise::isPublished).sorted(BY_NAME).toList();
    }

    public List<Exercise> publishedFor(String programId) {
        return published().stream().filter(exercise -> programId.equals(exercise.programId())).toList();
    }

    /** Everything, drafts and hidden included — the admin's library screen. */
    public List<Exercise> all() {
        return byId.values().stream().sorted(BY_NAME).toList();
    }

    // --------------------------------------------------------------- writes

    public Exercise create(CreateExerciseRequest request, String adminId) {
        String category = requireCategory(request.category());
        Instant now = Instant.now();

        ExerciseDocument doc = new ExerciseDocument();
        doc.setProgramId(blankToNull(request.programId()));
        doc.setName(request.name().trim());
        doc.setCategory(category);
        doc.setFocus(blankToNull(request.focus()));
        doc.setPrerequisites(blankToNull(request.prerequisites()));
        doc.setInstructions(blankToNull(request.instructions()));
        doc.setPurpose(blankToNull(request.purpose()));
        doc.setSuggestedSets(blankToNull(request.suggestedSets()));
        doc.setOrigin(ExerciseDocument.ORIGIN_ADMIN);
        doc.setCreatedAt(now);
        doc.touchedBy(adminId, now);

        // Ids are read by people in logs and URLs, so they come from the name.
        // insert (never save) so two admins naming the same movement at once
        // get two movements rather than one silently overwriting the other.
        String base = "ex_" + slug(request.name(), "_");
        for (int attempt = 1; ; attempt++) {
            doc.setId(attempt == 1 ? base : base + "_" + attempt);
            try {
                mongo.insert(doc);
                break;
            } catch (DuplicateKeyException ex) {
                if (attempt >= 50) {
                    throw ex;
                }
            }
        }

        reload();
        return doc.toExercise();
    }

    public Exercise update(String exerciseId, UpdateExerciseRequest request, String adminId) {
        ExerciseDocument doc = repository.findById(exerciseId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND, "No exercise with id " + exerciseId));

        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw ApiException.of(ErrorCode.VALIDATION_FAILED, "A movement needs a name");
            }
            doc.setName(request.name().trim());
        }
        if (request.category() != null) doc.setCategory(requireCategory(request.category()));
        if (request.focus() != null) doc.setFocus(blankToNull(request.focus()));
        if (request.prerequisites() != null) doc.setPrerequisites(blankToNull(request.prerequisites()));
        if (request.instructions() != null) doc.setInstructions(blankToNull(request.instructions()));
        if (request.purpose() != null) doc.setPurpose(blankToNull(request.purpose()));
        if (request.suggestedSets() != null) doc.setSuggestedSets(blankToNull(request.suggestedSets()));
        if (request.hidden() != null) doc.setHidden(request.hidden());

        // A file is only attached once the bucket confirms it is really there
        // and really the kind of file it claims to be. A failed or abandoned
        // upload therefore can never leave a member with a broken player.
        if (request.videoKey() != null) {
            doc.setVideoUrl(verifiedKey(request.videoKey(), "video/", media.properties().maxVideoBytes()));
        }
        if (request.thumbnailKey() != null) {
            doc.setThumbnailUrl(request.thumbnailKey().isBlank()
                    ? null
                    : verifiedKey(request.thumbnailKey(), "image/", media.properties().maxPosterBytes()));
        }

        doc.touchedBy(adminId, Instant.now());
        repository.save(doc);
        reload();
        return doc.toExercise();
    }

    private String verifiedKey(String key, String typePrefix, long maxBytes) {
        String trimmed = key.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("/") || trimmed.contains("..") || trimmed.contains("://")) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED, "That is not a media key");
        }
        if (!media.isConfigured()) {
            throw ApiException.of(ErrorCode.MEDIA_NOT_CONFIGURED, "Video uploads are not set up on this server");
        }
        MediaStorage.StoredObject stored = media.head(trimmed)
                .orElseThrow(() -> ApiException.of(ErrorCode.UPLOAD_NOT_FOUND,
                        "The upload did not finish — try again"));
        if (stored.contentType() == null || !stored.contentType().startsWith(typePrefix)) {
            throw ApiException.of(ErrorCode.UNSUPPORTED_MEDIA, "That file is not the right kind");
        }
        if (stored.sizeBytes() != null && stored.sizeBytes() > maxBytes) {
            throw ApiException.of(ErrorCode.UNSUPPORTED_MEDIA, "That file is too large");
        }
        return trimmed;
    }

    // -------------------------------------------------------------- helpers

    private void remember(Exercise exercise) {
        Map<String, Exercise> next = new HashMap<>(byId);
        next.put(exercise.id(), exercise);
        byId = Map.copyOf(next);
    }

    private static String requireCategory(String category) {
        String value = category == null ? "" : category.trim();
        if (!CATEGORIES.contains(value)) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED, "Unknown category " + value);
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** "Nordic Hamstring Curl (Assisted)" → "nordic_hamstring_curl_assisted". */
    public static String slug(String text, String separator) {
        String ascii = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        String slug = ascii.replaceAll("[^a-z0-9]+", separator)
                .replaceAll("^" + separator + "+|" + separator + "+$", "");
        if (slug.length() > 48) {
            slug = slug.substring(0, 48).replaceAll(separator + "+$", "");
        }
        return slug.isEmpty() ? "movement" : slug;
    }
}
