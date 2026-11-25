package com.codemastery.infrastructure.storage.s3;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import com.codemastery.infrastructure.storage.FileStorage;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.regex.Pattern;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.http.SdkHttpService;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.utils.AttributeMap;

/**
 * CT09 S3-compatible adapter. Optional until injected; validation precedes client allocation.
 * Upload streams remain caller-owned. The caller owns each successful open result.
 */
@Component
@Lazy
public final class S3FileStorage implements FileStorage {
    private static final Pattern KEY = Pattern.compile(
            "^(materials|submissions)/[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");
    private static final Pattern BUCKET = Pattern.compile("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]");
    // Explicit Leader approval in the SH05 task, 25 November 2026; no new config names.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final String DIAGNOSTIC_FILTER = "SH05-provider-confidentiality";

    private final String bucket;
    private final SdkHttpClient http;
    private final S3Client client;

    public S3FileStorage(Environment environment) {
        silenceProviderDiagnostics();
        String endpoint = setting(environment, "endpoint", "STORAGE_ENDPOINT");
        URI uri;
        try {
            uri = URI.create(endpoint);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !(uri.getRawPath().isEmpty() || "/".equals(uri.getRawPath()))
                    || uri.getPort() == 0 || uri.getPort() > 65535 || uri.getPort() < -1) {
                throw invalid("STORAGE_ENDPOINT");
            }
        } catch (RuntimeException failure) {
            throw invalid("STORAGE_ENDPOINT");
        }
        String region = setting(environment, "region", "STORAGE_REGION");
        bucket = setting(environment, "bucket", "STORAGE_BUCKET");
        if (!BUCKET.matcher(bucket).matches() || bucket.contains("..") || bucket.contains(".-")
                || bucket.contains("-.") || bucket.matches("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+")) {
            throw invalid("STORAGE_BUCKET");
        }
        String access = setting(environment, "access-key", "STORAGE_ACCESS_KEY");
        String secret = setting(environment, "secret-key", "STORAGE_SECRET_KEY");
        String pathStyle = setting(environment, "path-style-access", "STORAGE_PATH_STYLE_ACCESS");
        if (!"true".equalsIgnoreCase(pathStyle) && !"false".equalsIgnoreCase(pathStyle)) {
            throw invalid("STORAGE_PATH_STYLE_ACCESS");
        }

        SdkHttpClient allocated = null;
        try {
            // Apache is already a runtime dependency of s3. Use its public SPI to configure
            // the existing transport without adding a compile dependency or changing the POM.
            SdkHttpService service = ServiceLoader.load(SdkHttpService.class).stream()
                    .filter(provider -> provider.type().getName().equals(
                            "software.amazon.awssdk.http.apache.ApacheSdkHttpService"))
                    .findFirst().orElseThrow().get();
            allocated = service.createHttpClientBuilder().buildWithDefaults(AttributeMap.builder()
                    .put(SdkHttpConfigurationOption.CONNECTION_TIMEOUT, CONNECT_TIMEOUT)
                    .put(SdkHttpConfigurationOption.CONNECTION_ACQUIRE_TIMEOUT, CONNECT_TIMEOUT)
                    .put(SdkHttpConfigurationOption.READ_TIMEOUT, READ_TIMEOUT).build());
            client = S3Client.builder().httpClient(allocated).endpointOverride(uri)
                    .region(Region.of(region))
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(access, secret)))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(Boolean.parseBoolean(pathStyle)).build())
                    .crossRegionAccessEnabled(false)
                    .overrideConfiguration(configuration -> configuration
                            .retryStrategy(StandardRetryStrategy.builder().maxAttempts(1).build())
                            .apiCallAttemptTimeout(REQUEST_TIMEOUT).apiCallTimeout(REQUEST_TIMEOUT))
                    .build();
            http = allocated;
        } catch (RuntimeException failure) {
            if (allocated != null) {
                try { allocated.close(); } catch (RuntimeException ignored) { /* No provider diagnostics. */ }
            }
            throw new FileStorageException();
        }
    }

    @Override
    public void store(String objectKey, InputStream content, long contentLength, String mediaType) {
        requireKey(objectKey);
        if (content == null || contentLength < 1 || !validMediaType(objectKey, mediaType)) {
            throw badArguments();
        }
        ExactLengthStream stream = new ExactLengthStream(content, contentLength);
        try {
            // Both SDK RequestBody factories silently truncate an overlong input. Check EOF
            // BEFORE returning the final declared byte, so the provider cannot commit a prefix.
            client.putObject(PutObjectRequest.builder().bucket(bucket).key(objectKey)
                            .contentLength(contentLength).contentType(mediaType).build(),
                    RequestBody.fromContentProvider(() -> stream, contentLength, mediaType));
            if (!stream.complete) {
                throw new FileStorageException();
            }
        } catch (RuntimeException failure) {
            if (stream.mismatch) {
                throw badArguments();
            }
            throw new FileStorageException();
        }
    }

    @Override
    public StoredObject open(String objectKey) {
        requireKey(objectKey);
        ResponseInputStream<GetObjectResponse> response = null;
        try {
            response = client.getObject(GetObjectRequest.builder().bucket(bucket).key(objectKey).build());
            GetObjectResponse metadata = response.response();
            Long length = metadata.contentLength();
            if (length == null || length < 1 || !validMediaType(objectKey, metadata.contentType())) {
                throw new FileStorageException();
            }
            return new StoredObject(length, metadata.contentType(), new SafeDownloadStream(response));
        } catch (RuntimeException failure) {
            if (response != null) {
                try { response.abort(); } catch (RuntimeException ignored) { /* Preserve safe primary failure. */ }
            }
            if (missingKey(failure)) {
                throw new ObjectNotFoundException();
            }
            throw new FileStorageException();
        }
    }

    @Override
    public void delete(String objectKey) {
        requireKey(objectKey);
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
        } catch (RuntimeException failure) {
            if (!missingKey(failure)) {
                throw new FileStorageException();
            }
        }
    }

    @PreDestroy
    private void shutdown() {
        try {
            client.close();
        } catch (RuntimeException ignored) {
            // Shutdown must not expose a provider exception or prevent transport cleanup.
        } finally {
            try { http.close(); } catch (RuntimeException ignored) { /* No provider diagnostics. */ }
        }
    }

    private static boolean missingKey(RuntimeException failure) {
        return failure instanceof S3Exception s3 && s3.statusCode() == 404
                && s3.awsErrorDetails() != null && "NoSuchKey".equals(s3.awsErrorDetails().errorCode());
    }

    private static void requireKey(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw badArguments();
        }
    }

    private static boolean validMediaType(String key, String type) {
        return "application/pdf".equals(type)
                || (key.startsWith("submissions/") && "application/zip".equals(type));
    }

    private static IllegalArgumentException badArguments() {
        return new IllegalArgumentException("Invalid file storage arguments.");
    }

    private static String setting(Environment environment, String property, String name) {
        String value;
        try {
            value = environment.getProperty("codemastery.storage." + property);
        } catch (RuntimeException failure) {
            throw invalid(name);
        }
        if (value == null || value.isBlank() || value.contains("${")
                || value.toUpperCase(Locale.ROOT).contains("<SET_ME")
                || value.equalsIgnoreCase("changeme") || value.equalsIgnoreCase("placeholder")) {
            throw invalid(name);
        }
        return value;
    }

    private static IllegalStateException invalid(String name) {
        return new IllegalStateException("Missing, placeholder or invalid consumed configuration: " + name);
    }

    private static void silenceProviderDiagnostics() {
        // Provider DEBUG/TRACE includes keys, canonical signed requests and wire headers.
        // A source filter also denies child loggers explicitly set to TRACE; application
        // diagnostics remain available. The project uses Boot's Logback backend.
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        synchronized (context) {
            if (context.getTurboFilterList().stream().noneMatch(filter -> DIAGNOSTIC_FILTER.equals(filter.getName()))) {
                TurboFilter filter = new TurboFilter() {
                    @Override
                    public FilterReply decide(Marker marker, Logger logger, Level level,
                                              String format, Object[] arguments, Throwable failure) {
                        String name = logger.getName();
                        return name.equals("software.amazon.awssdk") || name.startsWith("software.amazon.awssdk.")
                                || name.equals("org.apache.http") || name.startsWith("org.apache.http.")
                                ? FilterReply.DENY : FilterReply.NEUTRAL;
                    }
                };
                filter.setName(DIAGNOSTIC_FILTER);
                filter.start();
                context.addTurboFilter(filter);
            }
        }
    }

    private static final class ExactLengthStream extends InputStream {
        private final InputStream source;
        private long remaining;
        private boolean mismatch;
        private boolean complete;

        private ExactLengthStream(InputStream source, long length) {
            this.source = source;
            this.remaining = length;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            return read(one, 0, 1) == -1 ? -1 : Byte.toUnsignedInt(one[0]);
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, buffer.length);
            if (length == 0) { return 0; }
            if (complete) { return -1; }
            int count = source.read(buffer, offset, (int) Math.min(remaining, length));
            if (count == -1) {
                mismatch = true;
                throw new IOException("Invalid upload length.");
            }
            remaining -= count;
            if (remaining == 0) {
                if (source.read() != -1) {
                    mismatch = true;
                    throw new IOException("Invalid upload length.");
                }
                complete = true;
            }
            return count;
        }
        // InputStream.close() is a no-op: the upload source belongs to the caller.
    }

    private static final class SafeDownloadStream extends InputStream {
        private final InputStream source;

        private SafeDownloadStream(InputStream source) { this.source = source; }

        @Override
        public int read() throws IOException {
            try { return source.read(); }
            catch (IOException | RuntimeException failure) { throw safeFailure(); }
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, buffer.length);
            try { return source.read(buffer, offset, length); }
            catch (IOException | RuntimeException failure) { throw safeFailure(); }
        }

        @Override
        public void close() throws IOException {
            try { source.close(); }
            catch (IOException | RuntimeException failure) { throw safeFailure(); }
        }

        private static IOException safeFailure() {
            return new IOException("File storage operation failed.");
        }
    }
}
