package in.hundredmph.api.content.model;

/**
 * A movement in the catalogue. Authored once by the practice and read by
 * everyone — see {@link in.hundredmph.api.content.ContentCatalogue} for why
 * this is not a Mongo document.
 */
public record Exercise(
        String id,
        String programId,
        String name,
        /** How the picker shelves it: back, core, hips_glutes, lower_body, ankle_calf, upper_body, mobility, athletic. */
        String category,
        /** One line on what it works, for the list and the guide subtitle. */
        String focus,
        String videoUrl,
        String thumbnailUrl,
        String prerequisites,
        String instructions,
        String purpose) {
}
