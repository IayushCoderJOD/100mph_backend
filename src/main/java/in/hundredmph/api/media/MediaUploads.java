package in.hundredmph.api.media;

import in.hundredmph.api.common.ApiException;
import in.hundredmph.api.common.ErrorCode;
import in.hundredmph.api.exercise.ExerciseLibrary;
import in.hundredmph.api.exercise.dto.UploadRequest;
import in.hundredmph.api.exercise.dto.UploadResponse;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Decides what may be uploaded and where it goes.
 *
 * <p>Only what every browser and phone can play is accepted for video — MP4,
 * plus MOV and WebM, which the admin screen checks the browser can actually
 * decode before it asks. Size is capped here and again by the signature, so a
 * client cannot sign for 1 MB and send 1 GB.
 */
@Service
public class MediaUploads {

    private static final Map<String, String> VIDEO_TYPES = Map.of(
            "video/mp4", "mp4",
            "video/quicktime", "mov",
            "video/webm", "webm");

    private static final Map<String, String> POSTER_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/webp", "webp",
            "image/png", "png");

    private static final SecureRandom RANDOM = new SecureRandom();

    private final MediaStorage storage;

    public MediaUploads(MediaStorage storage) {
        this.storage = storage;
    }

    public UploadResponse presign(UploadRequest request) {
        if (!storage.isConfigured()) {
            throw ApiException.of(ErrorCode.MEDIA_NOT_CONFIGURED, "Video uploads are not set up on this server");
        }

        String kind = request.kind().trim().toLowerCase(Locale.ROOT);
        String contentType = request.contentType().trim().toLowerCase(Locale.ROOT);
        MediaProperties properties = storage.properties();

        String extension;
        long maxBytes;
        switch (kind) {
            case "video" -> {
                extension = VIDEO_TYPES.get(contentType);
                maxBytes = properties.maxVideoBytes();
            }
            case "poster" -> {
                extension = POSTER_TYPES.get(contentType);
                maxBytes = properties.maxPosterBytes();
            }
            default -> throw ApiException.of(ErrorCode.VALIDATION_FAILED, "kind must be video or poster");
        }
        if (extension == null) {
            throw ApiException.of(ErrorCode.UNSUPPORTED_MEDIA,
                    kind.equals("video") ? "Upload an MP4 video" : "Posters must be JPEG, WebP or PNG");
        }
        if (request.sizeBytes() > maxBytes) {
            throw ApiException.of(ErrorCode.UNSUPPORTED_MEDIA, kind.equals("video")
                    ? "Videos can be up to " + properties.maxVideoMb() + " MB"
                    : "Posters can be up to " + properties.maxPosterKb() + " KB");
        }

        // demos/uploads/glute-bridge-3f9a1c2e.mp4 — readable, and unique, so a
        // replaced video never collides with the file members have cached.
        String name = request.fileName() == null ? "" : request.fileName().replaceFirst("\\.[A-Za-z0-9]{1,5}$", "");
        String key = properties.uploadPrefix().replaceAll("/+$", "") + "/"
                + ExerciseLibrary.slug(name.isBlank() ? kind : name, "-")
                + "-" + randomHex(4)
                + (kind.equals("poster") ? "-poster" : "")
                + "." + extension;

        MediaStorage.PresignedUpload signed = storage.presignUpload(key, contentType, request.sizeBytes());
        return new UploadResponse(signed.url(), "PUT", signed.headers(), signed.key(), signed.expiresAt());
    }

    private static String randomHex(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return HexFormat.of().formatHex(buffer);
    }
}
