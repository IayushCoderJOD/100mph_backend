package in.hundredmph.api.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Signing is local maths — no bucket, no network — so it is checked directly. */
class MediaStorageTest {

    private static MediaProperties properties(String accessKey) {
        return new MediaProperties("acct123", accessKey, "secret", "100mph-media", null,
                "demos/uploads", 150, 2048, Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("without credentials, uploads are off and nothing is built")
    void unconfiguredIsOff() {
        assertThat(new MediaStorage(properties("")).isConfigured()).isFalse();
    }

    @Test
    @DisplayName("an upload URL pins the key, the type and the size, and nothing a browser cannot send")
    void presignedUrlIsBrowserSafe() {
        MediaStorage storage = new MediaStorage(properties("AKIAEXAMPLE"));
        MediaStorage.PresignedUpload upload =
                storage.presignUpload("demos/uploads/glute-bridge-1a2b3c4d.mp4", "video/mp4", 12_345_678L);

        // Path-style, on the account's R2 endpoint.
        assertThat(upload.url())
                .startsWith("https://acct123.r2.cloudflarestorage.com/100mph-media/demos/uploads/glute-bridge-1a2b3c4d.mp4?");

        // Type and size are part of the signature, so the URL cannot carry anything else.
        String signed = upload.url().replaceAll(".*X-Amz-SignedHeaders=([^&]*).*", "$1");
        assertThat(signed).contains("content-type").contains("content-length");

        // The SDK's default flexible checksums would make every browser PUT fail.
        assertThat(upload.url().toLowerCase()).doesNotContain("checksum");

        assertThat(upload.headers()).containsEntry("Content-Type", "video/mp4")
                .containsKey("Cache-Control")
                .doesNotContainKey("Content-Length");
        assertThat(upload.expiresAt()).isBetween(Instant.now().plusSeconds(13 * 60), Instant.now().plusSeconds(16 * 60));
    }
}
