package in.hundredmph.api.exercise;

import in.hundredmph.api.content.model.Exercise;
import in.hundredmph.api.exercise.dto.CreateExerciseRequest;
import in.hundredmph.api.exercise.dto.UpdateExerciseRequest;
import in.hundredmph.api.exercise.dto.UploadRequest;
import in.hundredmph.api.exercise.dto.UploadResponse;
import in.hundredmph.api.media.MediaUploads;
import in.hundredmph.api.security.AuthPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The admin's side of the exercise library — backs app/admin/exercises.
 * Admin-only by path: SecurityConfig gates everything under /v1/admin.
 */
@RestController
@RequestMapping("/v1/admin/exercises")
public class AdminExerciseController {

    private final ExerciseLibrary library;
    private final MediaUploads uploads;

    public AdminExerciseController(ExerciseLibrary library, MediaUploads uploads) {
        this.library = library;
        this.uploads = uploads;
    }

    /** Everything, drafts and hidden movements included. */
    @GetMapping
    public List<Exercise> all() {
        return library.all();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Exercise create(@AuthenticationPrincipal AuthPrincipal principal,
                           @Valid @RequestBody CreateExerciseRequest request) {
        return library.create(request, principal.userId());
    }

    @PatchMapping("/{exerciseId}")
    public Exercise update(@AuthenticationPrincipal AuthPrincipal principal,
                           @PathVariable String exerciseId,
                           @Valid @RequestBody UpdateExerciseRequest request) {
        return library.update(exerciseId, request, principal.userId());
    }

    /** A one-time URL to PUT a video or poster to. Attach the returned key with a PATCH. */
    @PostMapping("/uploads")
    public UploadResponse upload(@Valid @RequestBody UploadRequest request) {
        return uploads.presign(request);
    }
}
