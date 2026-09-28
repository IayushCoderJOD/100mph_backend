package in.hundredmph.api.content.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * A movement in the exercise library, as every screen reads it.
 *
 * <p>The library itself lives in Mongo (see
 * {@link in.hundredmph.api.exercise.ExerciseLibrary}): the authored catalogue
 * seeds it, and admins add to and edit it from the app. This record is the
 * shape on the wire and in the catalogue file alike.
 */
public record Exercise(
        String id,
        /** The program it was written for. Optional: category, not program, is how it is found. */
        String programId,
        String name,
        /** How the picker shelves it: back, core, hips_glutes, lower_body, ankle_calf, upper_body, mobility, athletic. */
        String category,
        /** One line on what it works, for the list and the guide subtitle. */
        String focus,
        /** A media key (demos/…mp4) resolved against the client's media host. Null until filmed. */
        String videoUrl,
        String thumbnailUrl,
        String prerequisites,
        String instructions,
        String purpose,
        /** The sets a coach starts from when adding it to a day, e.g. "3 x 12 reps". */
        String suggestedSets,
        /** Out of the picker. Still resolves on plans that already use it. */
        boolean hidden) {

    /** Offered in the picker: filmed, and not retired. Derived, so not on the wire. */
    @JsonIgnore
    public boolean isPublished() {
        return !hidden && videoUrl != null && !videoUrl.isBlank();
    }
}
