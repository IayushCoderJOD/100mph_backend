package in.hundredmph.api.media;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where uploaded demonstration videos go: a Cloudflare R2 bucket, reached
 * through its S3-compatible API.
 *
 * <p>All optional. Without credentials the rest of the app works exactly as
 * before and the upload endpoint answers 503, so a laptop needs no bucket.
 */
@ConfigurationProperties(prefix = "app.media")
public record MediaProperties(
        /** Cloudflare account id — the <id> in https://<id>.r2.cloudflarestorage.com. */
        String accountId,
        String accessKeyId,
        String secretAccessKey,
        @DefaultValue("100mph-media") String bucket,
        /** Overrides the R2 endpoint, for a local S3 stand-in. Blank means R2. */
        String endpoint,
        /** Uploaded keys live under here, beside the hand-encoded demos/. */
        @DefaultValue("demos/uploads") String uploadPrefix,
        @DefaultValue("150") int maxVideoMb,
        @DefaultValue("2048") int maxPosterKb,
        @DefaultValue("PT15M") Duration uploadUrlTtl) {

    public boolean isConfigured() {
        return notBlank(accessKeyId) && notBlank(secretAccessKey)
                && (notBlank(endpoint) || notBlank(accountId));
    }

    public String resolvedEndpoint() {
        return notBlank(endpoint) ? endpoint.trim() : "https://" + accountId.trim() + ".r2.cloudflarestorage.com";
    }

    public long maxVideoBytes() {
        return maxVideoMb * 1024L * 1024L;
    }

    public long maxPosterBytes() {
        return maxPosterKb * 1024L;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
