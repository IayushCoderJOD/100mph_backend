package in.hundredmph.api.content.model;

/** A lesson or article in the Learn tab. */
public record LearnContent(
        String id,
        String programId,
        /** "mini_lesson" or "longform". */
        String kind,
        String title,
        String subtitle,
        String description,
        String thumbnailUrl,
        String videoUrl,
        Integer durationSec,
        int sortOrder) {
}
