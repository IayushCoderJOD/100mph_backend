package in.hundredmph.api.content.model;

import java.util.List;

/**
 * A named set of exercises a coach assigns to a member whole. Authored content
 * like a session type, but deliberately not tied to a program: the same knee
 * routine serves a runner and a desk worker, and the coach picks it for the
 * person in front of them rather than for a category.
 */
public record Routine(
        String id,
        String name,
        /** One line on who it is for. */
        String description,
        String icon,
        Integer approxDurationMin,
        List<RoutineExercise> exercises) {
}
