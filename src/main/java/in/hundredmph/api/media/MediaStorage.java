package in.hundredmph.api.media;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

/**
 * Hands out one-time upload URLs for the media bucket, and checks what landed.
 *
 * <p>The file itself never passes through this server. The admin's browser
 * PUTs it straight to R2 on a URL signed here, which is what lets a small API
 * instance accept a 100 MB video without holding it in memory or paying for
 * the bandwidth twice. The signature pins the key, the content type and the
 * exact size, so the URL cannot be reused for anything else.
 */
@Component
public class MediaStorage {

    private static final Logger log = LoggerFactory.getLogger(MediaStorage.class);

    /** Every key is unique, so a file never changes under its name. */
    static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final MediaProperties properties;
    private final S3Client client;
    private final S3Presigner presigner;

    public MediaStorage(MediaProperties properties) {
        this.properties = properties;
        if (!properties.isConfigured()) {
            this.client = null;
            this.presigner = null;
            log.info("Media uploads are off: R2 credentials are not set");
            return;
        }

        URI endpoint = URI.create(properties.resolvedEndpoint());
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKeyId().trim(), properties.secretAccessKey().trim()));
        // R2 wants path-style addressing and the "auto" region. The SDK's
        // default flexible checksums are switched off: a browser PUT cannot
        // compute them, and R2 does not need them.
        S3Configuration s3 = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .chunkedEncodingEnabled(false)
                .build();

        this.client = S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.of("auto"))
                .credentialsProvider(credentials)
                .serviceConfiguration(s3)
                .httpClient(UrlConnectionHttpClient.create())
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
        this.presigner = S3Presigner.builder()
                .endpointOverride(endpoint)
                .region(Region.of("auto"))
                .credentialsProvider(credentials)
                .serviceConfiguration(s3)
                .build();
    }

    public boolean isConfigured() {
        return presigner != null;
    }

    public MediaProperties properties() {
        return properties;
    }

    /** A URL the client PUTs the file to, with the headers it must send. */
    public PresignedUpload presignUpload(String key, String contentType, long sizeBytes) {
        requireConfigured();
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(contentType)
                .contentLength(sizeBytes)
                .cacheControl(CACHE_CONTROL)
                .build();

        PresignedPutObjectRequest signed = presigner.presignPutObject(request -> request
                .signatureDuration(properties.uploadUrlTtl())
                .putObjectRequest(put));

        // Content-Length is signed too, but a browser sets it from the body
        // and refuses to let script touch it, so it is not handed back.
        return new PresignedUpload(
                signed.url().toString(),
                key,
                Map.of("Content-Type", contentType, "Cache-Control", CACHE_CONTROL),
                signed.expiration());
    }

    /** What is stored under a key, or empty when nothing is. */
    public Optional<StoredObject> head(String key) {
        requireConfigured();
        try {
            HeadObjectResponse head = client.headObject(request -> request.bucket(properties.bucket()).key(key));
            return Optional.of(new StoredObject(key, head.contentLength(), head.contentType()));
        } catch (NoSuchKeyException ex) {
            return Optional.empty();
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return Optional.empty();
            }
            throw ex;
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("Media storage is not configured");
        }
    }

    public record PresignedUpload(String url, String key, Map<String, String> headers, Instant expiresAt) {}

    public record StoredObject(String key, Long sizeBytes, String contentType) {}
}
