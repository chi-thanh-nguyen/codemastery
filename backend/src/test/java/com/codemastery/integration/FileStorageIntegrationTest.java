package com.codemastery.integration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.read.ListAppender;
import com.codemastery.infrastructure.storage.FileStorage;
import com.codemastery.infrastructure.storage.FileStorage.FileStorageException;
import com.codemastery.infrastructure.storage.FileStorage.ObjectNotFoundException;
import com.codemastery.infrastructure.storage.FileStorage.StoredObject;
import com.codemastery.infrastructure.storage.s3.S3FileStorage;
import com.codemastery.infrastructure.storage.s3.S3FileStorage.ProviderDiagnosticsFilter;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.PrintStream;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SH05 adapter qualification, not downstream CO09/AS09/QA10 acceptance. The real
 * MinIO and PostgreSQL foundation follows ADR012 §19.3; provider fault cases are
 * explicitly simulated. Credentials, network, bucket and keys belong to this JVM.
 */
@Timeout(90)
class FileStorageIntegrationTest extends AbstractIntegrationTest {
    private static final String MINIO_IMAGE = "ghcr.io/golithus/minio:RELEASE.2025-10-15T17-29-55Z"
            + "@sha256:8793e960474071520bdb91bb9a6d1793eb229fdbc9dcb7ec75ad836b69bbab40";
    private static final String MC_IMAGE = "ghcr.io/golithus/mc:RELEASE.2025-08-13T08-35-41Z"
            + "@sha256:dde55ccc0bc9e65dc582c8e652d652eea1438c6e60d774524f4da15d7b26a900";
    private static final String ROOT_USER = randomSecret().substring(0, 20);
    private static final String ROOT_SECRET = randomSecret();
    private static final String APP_USER = randomSecret().substring(0, 20);
    private static final String APP_SECRET = randomSecret();
    private static final String BUCKET = "sh05-" + UUID.randomUUID();
    private static final String JWT = randomSecret();
    private static final Network NETWORK = Network.newNetwork();
    private static final GenericContainer<?> MINIO = new GenericContainer<>(DockerImageName.parse(MINIO_IMAGE))
            .withNetwork(NETWORK).withNetworkAliases("storage")
            .withEnv("MINIO_ROOT_USER", ROOT_USER).withEnv("MINIO_ROOT_PASSWORD", ROOT_SECRET)
            .withEnv("MINIO_REGION_NAME", "us-east-1").withCommand("server", "/data")
            .withExposedPorts(9000).withReuse(false)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    static { MINIO.start(); }

    @Autowired Environment environment;
    @Autowired FileStorage storage;
    private static S3Client admin;

    private static String randomSecret() {
        byte[] value = new byte[32];
        new SecureRandom().nextBytes(value);
        return HexFormat.of().formatHex(value);
    }

    @DynamicPropertySource
    static void configureStorage(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> JWT);
        // This adapter suite needs PostgreSQL only for the approved application foundation.
        // Do not retain ten idle connections in the shared JVM's cached Spring context.
        // Flyway holds its migration lock connection while acquiring another connection.
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "2");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "0");
        registry.add("spring.datasource.hikari.idle-timeout", () -> "10000");
        registry.add("codemastery.storage.endpoint", FileStorageIntegrationTest::endpoint);
        registry.add("codemastery.storage.region", () -> "us-east-1");
        registry.add("codemastery.storage.bucket", () -> BUCKET);
        registry.add("codemastery.storage.access-key", () -> APP_USER);
        registry.add("codemastery.storage.secret-key", () -> APP_SECRET);
        registry.add("codemastery.storage.path-style-access", () -> "true");
    }

    @BeforeAll
    static void provisionIsolatedApplicationIdentity() throws IOException {
        String compose = Files.readString(Path.of("..", "infrastructure", "docker-compose.yml"));
        assertTrue(compose.contains(MINIO_IMAGE), "Test must follow the pinned Compose server");
        assertTrue(compose.contains(MC_IMAGE), "Test must follow the pinned Compose client");
        // Bootstrap only: the production adapter never receives these administrator credentials.
        admin = S3Client.builder().endpointOverride(java.net.URI.create(endpoint())).region(Region.US_EAST_1)
                .forcePathStyle(true).credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(ROOT_USER, ROOT_SECRET))).build();
        admin.createBucket(request -> request.bucket(BUCKET));
        try (GenericContainer<?> initializer = new GenericContainer<>(DockerImageName.parse(MC_IMAGE))
                .withNetwork(NETWORK).withEnv("ROOT_USER", ROOT_USER).withEnv("ROOT_SECRET", ROOT_SECRET)
                .withEnv("APP_USER", APP_USER).withEnv("APP_SECRET", APP_SECRET).withEnv("BUCKET", BUCKET)
                .withCreateContainerCmdModifier(command -> command.withEntrypoint("/bin/sh"))
                .withCommand("-ec", """
                        mc alias set local http://storage:9000 "$ROOT_USER" "$ROOT_SECRET" >/dev/null 2>&1
                        mc admin user add local "$APP_USER" "$APP_SECRET" >/dev/null 2>&1
                        printf '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Action":["s3:GetBucketLocation","s3:ListBucket"],"Resource":["arn:aws:s3:::%s"]},{"Effect":"Allow","Action":["s3:GetObject","s3:PutObject","s3:DeleteObject"],"Resource":["arn:aws:s3:::%s/*"]}]}' "$BUCKET" "$BUCKET" >/tmp/policy.json
                        mc admin policy create local sh05 /tmp/policy.json >/dev/null 2>&1
                        mc admin policy attach local sh05 --user "$APP_USER" >/dev/null 2>&1
                        """)
                .withStartupCheckStrategy(new OneShotStartupCheckStrategy().withTimeout(Duration.ofSeconds(60)))) {
            initializer.start();
            assertEquals(0L, initializer.getCurrentContainerInfo().getState().getExitCodeLong());
        }
        // Non-secret evidence only. Real-provider identity is distinct from test-double cases below.
        System.out.println("SH05 real provider: container=" + MINIO.getContainerId()
                + " image=" + MINIO_IMAGE + " imageId=" + MINIO.getContainerInfo().getImageId());
    }

    @AfterAll
    static void closeAdministratorClient() {
        if (admin != null) { admin.close(); }
        // Containers have JVM scope, like AbstractIntegrationTest; Testcontainers reaps them.
    }

    private static String endpoint() {
        return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
    }

    private static String key(String prefix) { return prefix + "/" + UUID.randomUUID(); }

    @Test
    void exactNormativeInterfaceAndCheckedClose() throws Exception {
        assertEquals(3, FileStorage.class.getDeclaredMethods().length);
        assertEquals(void.class, FileStorage.class.getMethod("store", String.class, InputStream.class,
                long.class, String.class).getReturnType());
        assertEquals(StoredObject.class, FileStorage.class.getMethod("open", String.class).getReturnType());
        assertEquals(void.class, FileStorage.class.getMethod("delete", String.class).getReturnType());
        assertArrayEquals(new Class<?>[]{IOException.class}, StoredObject.class.getMethod("close").getExceptionTypes());
        assertEquals(FileStorageException.class, ObjectNotFoundException.class.getSuperclass());
        IOException readFailure = new IOException("synthetic copy");
        IOException closeFailure = new IOException("synthetic close");
        AtomicInteger closes = new AtomicInteger();
        StoredObject result = new StoredObject(1, "application/pdf", new InputStream() {
            @Override public int read() throws IOException { throw readFailure; }
            @Override public void close() throws IOException { closes.incrementAndGet(); throw closeFailure; }
        });
        IOException observed = assertThrows(IOException.class, () -> {
            try (result) { result.content().read(); }
        });
        assertSame(readFailure, observed);
        assertArrayEquals(new Throwable[]{closeFailure}, observed.getSuppressed());
        assertEquals(1, closes.get());
    }

    @ParameterizedTest
    @CsvSource({"materials,application/pdf", "submissions,application/pdf", "submissions,application/zip"})
    void realRoundTripHasExactContentMetadataAndIdempotentDelete(String prefix, String type) throws IOException {
        String key = key(prefix);
        byte[] bytes = new byte[2 * 1024 * 1024 + 17];
        new SecureRandom().nextBytes(bytes);
        AtomicInteger uploadCloses = new AtomicInteger();
        InputStream upload = new ByteArrayInputStream(bytes) {
            @Override public boolean markSupported() { return false; }
            @Override public void reset() { throw new AssertionError("Upload must not be replayed"); }
            @Override public void close() { uploadCloses.incrementAndGet(); }
        };
        try {
            storage.store(key, upload, bytes.length, type);
            assertEquals(0, uploadCloses.get(), "Upload remains caller-owned");
            try (StoredObject result = storage.open(key)) {
                assertEquals(bytes.length, result.contentLength());
                assertEquals(type, result.mediaType());
                assertArrayEquals(bytes, result.content().readAllBytes());
            }
            storage.delete(key);
            storage.delete(key);
            safeException(ObjectNotFoundException.class, () -> storage.open(key));
        } finally { storage.delete(key); upload.close(); }
    }

    @ParameterizedTest
    @CsvSource({"0,1", "1,2", "3,2", "131073,131072", "131071,131072"})
    void realProviderRejectsShortAndOverlongStreamsWithoutCommittingAPrefix(int actual, int declared) {
        String key = key("materials");
        try {
            safeException(IllegalArgumentException.class,
                    () -> storage.store(key, new ByteArrayInputStream(new byte[actual]), declared, "application/pdf"));
            safeException(ObjectNotFoundException.class, () -> storage.open(key));
        } finally { storage.delete(key); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"materials/00000000-0000-4000-8000-000000000000\n", "materials/../secret",
            "materials/00000000-0000-1000-8000-000000000000", "submissions/00000000-0000-4000-7000-000000000000",
            "materials/AAAAAAAA-0000-4000-8000-000000000000", "other/00000000-0000-4000-8000-000000000000", ""})
    void invalidKeysAreRejectedBeforeProviderAccess(String key) throws Exception {
        try (MockAdapter adapter = mocked()) {
            safeException(IllegalArgumentException.class, () -> adapter.storage.open(key));
            safeException(IllegalArgumentException.class, () -> adapter.storage.delete(key));
            safeException(IllegalArgumentException.class,
                    () -> adapter.storage.store(key, InputStream.nullInputStream(), 1, "application/pdf"));
            verifyNoInteractions(adapter.client);
        }
    }

    @Test
    void invalidArgumentsDoNotReadOrCloseTheCallerStream() throws Exception {
        try (MockAdapter adapter = mocked()) {
            InputStream source = mock(InputStream.class);
            String key = key("materials");
            for (String type : Arrays.asList(null, "application/zip", "application/pdf; charset=UTF-8", "image/png", "")) {
                safeException(IllegalArgumentException.class, () -> adapter.storage.store(key, source, 1, type));
            }
            safeException(IllegalArgumentException.class, () -> adapter.storage.store(key, source, 0, "application/pdf"));
            safeException(IllegalArgumentException.class, () -> adapter.storage.store(key, source, -1, "application/pdf"));
            safeException(IllegalArgumentException.class, () -> adapter.storage.store(key, null, 1, "application/pdf"));
            safeException(IllegalArgumentException.class, () -> adapter.storage.open(null));
            verifyNoInteractions(adapter.client, source);
        }
    }

    @ParameterizedTest
    @CsvSource({"endpoint,STORAGE_ENDPOINT", "region,STORAGE_REGION", "bucket,STORAGE_BUCKET",
            "access-key,STORAGE_ACCESS_KEY", "secret-key,STORAGE_SECRET_KEY", "path-style-access,STORAGE_PATH_STYLE_ACCESS"})
    void consumptionRejectsMissingPlaceholderAndUnresolvableConfiguration(String property, String name) {
        for (String value : List.of("", " ", "<SET_ME_SYNTHETIC>", "changeme", "placeholder", "${absent}")) {
            MockEnvironment config = configuration(endpoint()).withProperty("codemastery.storage." + property, value);
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> new S3FileStorage(config));
            assertEquals("Missing, placeholder or invalid consumed configuration: " + name, failure.getMessage());
            assertNull(failure.getCause());
        }
        MockEnvironment config = configuration(endpoint());
        config.getPropertySources().remove("mockProperties");
        assertThrows(IllegalStateException.class, () -> new S3FileStorage(config));
    }

    @ParameterizedTest
    @ValueSource(strings = {"relative", "ftp://storage.test", "http:///path", "http://user:secret@storage.test",
            "https://storage.test?secret=value", "https://storage.test#secret", "https://storage.test/other",
            "http://storage.test:0", "http://storage.test:65536", "http://storage.test/%2f"})
    void endpointValidationDoesNotExposeTheRejectedValue(String value) {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new S3FileStorage(configuration(value)));
        assertEquals("Missing, placeholder or invalid consumed configuration: STORAGE_ENDPOINT", failure.getMessage());
        assertNull(failure.getCause());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "Uppercase", "a..b", "a.-b", "a-.b", "127.0.0.1", "-abc", "abc-", "bad/bucket"})
    void bucketValidationMatchesTheInitializer(String value) {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new S3FileStorage(configuration(endpoint()).withProperty("codemastery.storage.bucket", value)));
        assertTrue(failure.getMessage().endsWith("STORAGE_BUCKET"));
        assertNull(failure.getCause());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "yes", "false ", " true"})
    void pathStyleMustBeAnExplicitBoolean(String value) {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new S3FileStorage(configuration(endpoint()).withProperty("codemastery.storage.path-style-access", value)));
        assertTrue(failure.getMessage().endsWith("STORAGE_PATH_STYLE_ACCESS"));
    }

    @Test
    void unusedAdapterDoesNotResolveOptionalConfigurationAndConsumptionFailsClosed() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(S3FileStorage.class);
            context.refresh();
            assertFalse(context.getBeanFactory().containsSingleton("s3FileStorage"));
            assertThrows(org.springframework.beans.factory.BeanCreationException.class,
                    () -> context.getBean(FileStorage.class));
        }
    }

    @ParameterizedTest
    @CsvSource({"NoSuchKey,404,true", "NoSuchBucket,404,false", "AccessDenied,404,false",
            "Other,404,false", "NoSuchKey,500,false", "AccessDenied,403,false", "InternalError,503,false"})
    void simulatedProviderErrorsAreClassifiedByBothCodeAndStatus(String code, int status, boolean missing) throws Exception {
        try (MockAdapter adapter = mocked()) {
            RuntimeException failure = S3Exception.builder().statusCode(status).message(APP_SECRET)
                    .awsErrorDetails(AwsErrorDetails.builder().errorCode(code).errorMessage(APP_SECRET).build()).build();
            when(adapter.client.getObject(any(GetObjectRequest.class))).thenThrow(failure);
            if (missing) {
                safeException(ObjectNotFoundException.class, () -> adapter.storage.open(key("materials")));
            } else {
                safeException(FileStorageException.class, () -> adapter.storage.open(key("materials")));
            }
            when(adapter.client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(failure);
            if (missing) { adapter.storage.delete(key("materials")); }
            else { safeException(FileStorageException.class, () -> adapter.storage.delete(key("materials"))); }
        }
    }

    @Test
    void simulatedTransportAndUnclassified404FailuresAreSafeAndNeverMissingObjects() throws Exception {
        try (MockAdapter adapter = mocked()) {
            for (RuntimeException failure : List.of(S3Exception.builder().statusCode(404).message(APP_SECRET).build(),
                    SdkClientException.builder().message(APP_SECRET).cause(new javax.net.ssl.SSLException(APP_SECRET)).build(),
                    SdkClientException.builder().message(APP_SECRET).cause(new java.net.ConnectException(APP_SECRET)).build(),
                    SdkClientException.builder().message(APP_SECRET).cause(new java.net.SocketTimeoutException(APP_SECRET)).build(),
                    new IllegalStateException(APP_SECRET))) {
                doThrow(failure).when(adapter.client).getObject(any(GetObjectRequest.class));
                safeException(FileStorageException.class, () -> adapter.storage.open(key("materials")));
                doThrow(failure).when(adapter.client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
                safeException(FileStorageException.class,
                        () -> adapter.storage.store(key("materials"), InputStream.nullInputStream(), 1, "application/pdf"));
            }
        }
    }

    @ParameterizedTest
    @CsvSource(value = {"-1|application/pdf", "0|application/pdf", "2|application/zip", "2|text/plain",
            "2|application/pdf; charset=UTF-8", "2|NULL", "NULL|application/pdf"}, delimiter = '|', nullValues = "NULL")
    void invalidReturnedMetadataAbortsTheResponseStream(Long length, String type) throws Exception {
        try (MockAdapter adapter = mocked()) {
            AtomicInteger aborts = new AtomicInteger();
            ResponseInputStream<GetObjectResponse> response = new ResponseInputStream<>(
                    GetObjectResponse.builder().contentLength(length).contentType(type).build(),
                    AbortableInputStream.create(new ByteArrayInputStream(new byte[2]), aborts::incrementAndGet));
            when(adapter.client.getObject(any(GetObjectRequest.class))).thenReturn(response);
            safeException(FileStorageException.class, () -> adapter.storage.open(key("materials")));
            assertEquals(1, aborts.get());
        }
    }

    @Test
    void simulatedReadAndCloseFailuresKeepCheckedSemanticsWithoutProviderDiagnostics() throws Exception {
        try (MockAdapter adapter = mocked()) {
            AtomicInteger closes = new AtomicInteger();
            InputStream source = new InputStream() {
                @Override public int read() throws IOException { throw new IOException(APP_SECRET); }
                @Override public void close() throws IOException { closes.incrementAndGet(); throw new IOException(APP_SECRET); }
            };
            when(adapter.client.getObject(any(GetObjectRequest.class))).thenReturn(new ResponseInputStream<>(
                    GetObjectResponse.builder().contentLength(1L).contentType("application/pdf").build(), source));
            IOException failure = assertThrows(IOException.class, () -> {
                try (StoredObject result = adapter.storage.open(key("materials"))) { result.content().read(); }
            });
            assertEquals("File storage operation failed.", failure.getMessage());
            assertNull(failure.getCause());
            assertEquals(1, closes.get());
            assertEquals(1, failure.getSuppressed().length);
            assertFalse(stackTrace(failure).contains(APP_SECRET));
            verify(adapter.client, never()).deleteObject(any(DeleteObjectRequest.class));
        }
    }

    @Test
    void successfulCallerCleanupClosesExactlyOnceIncludingEarlyExit() throws Exception {
        try (MockAdapter adapter = mocked()) {
            InputStream source = mock(InputStream.class);
            when(adapter.client.getObject(any(GetObjectRequest.class))).thenReturn(new ResponseInputStream<>(
                    GetObjectResponse.builder().contentLength(1L).contentType("application/pdf").build(), source));
            try (StoredObject ignored = adapter.storage.open(key("materials"))) { /* Header preparation / disconnect. */ }
            verify(source).close();
        }
    }

    @Test
    void realApplicationIdentityCannotAccessAnotherBucket() {
        String other = "sh05-denied-" + UUID.randomUUID();
        admin.createBucket(request -> request.bucket(other));
        try (AnnotationConfigApplicationContext context = adapterContext(
                configuration(endpoint()).withProperty("codemastery.storage.bucket", other))) {
            safeException(FileStorageException.class, () -> context.getBean(FileStorage.class).open(key("materials")));
        } finally { admin.deleteBucket(request -> request.bucket(other)); }
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 307, 403, 404, 429, 500, 503})
    void syntheticHttpFailuresAreNotRetriedOrLoggedEvenAtTrace(int status) throws Exception {
        AtomicInteger requests = new AtomicInteger();
        List<String> methods = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            methods.add(exchange.getRequestMethod());
            exchange.getRequestBody().readAllBytes();
            byte[] response = ("<Error><Code>InternalError</Code><Message>" + APP_SECRET + "</Message></Error>")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/xml");
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + server.getAddress().getPort() + "/redirect");
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        LoggingConcurrencyProbe.protectOutput(logs);
        logs.start();
        root.addAppender(logs);
        List<Logger> providerLoggers = List.of((Logger) LoggerFactory.getLogger("software.amazon.awssdk"),
                (Logger) LoggerFactory.getLogger("org.apache.http.wire"),
                (Logger) LoggerFactory.getLogger("software.amazon.awssdk.request"));
        List<Level> previous = providerLoggers.stream().map(Logger::getLevel).toList();
        providerLoggers.forEach(logger -> logger.setLevel(Level.TRACE));
        String objectKey = key("materials");
        try (AnnotationConfigApplicationContext context = adapterContext(configuration(
                "http://127.0.0.1:" + server.getAddress().getPort()))) {
            FileStorage adapter = context.getBean(FileStorage.class);
            safeException(FileStorageException.class, () -> adapter.open(objectKey));
            assertEquals(1, requests.get());
            safeException(FileStorageException.class, () -> adapter.delete(objectKey));
            assertEquals(2, requests.get());
            safeException(FileStorageException.class, () -> adapter.store(objectKey,
                    new ByteArrayInputStream(new byte[]{1}), 1, "application/pdf"));
            assertEquals(3, requests.get());
            assertEquals(List.of("GET", "DELETE", "PUT"), methods);
            LoggerFactory.getLogger("com.codemastery.sh05-test").warn("SH05 application logging positive control");
            assertTrue(logs.list.stream().anyMatch(event -> event.getFormattedMessage().contains("positive control")));
            assertTrue(logs.list.stream().noneMatch(event -> event.getLoggerName().startsWith("software.amazon.awssdk")
                    || event.getLoggerName().startsWith("org.apache.http")));
            String captured = logs.list.toString();
            for (String sensitive : List.of(APP_SECRET, APP_USER, objectKey, "Authorization:", "AWS4-HMAC-SHA256 Credential=")) {
                assertFalse(captured.contains(sensitive));
            }
        } finally {
            for (int i = 0; i < providerLoggers.size(); i++) { providerLoggers.get(i).setLevel(previous.get(i)); }
            root.detachAppender(logs);
            logs.stop();
            server.stop(0);
        }
    }

    @Test
    void diagnosticConfidentialitySurvivesLoggingLifecycleInIsolatedJvm() throws Exception {
        runLoggingProbe(LoggingLifecycleProbe.class);
    }

    @Test
    void diagnosticConfidentialityDuringConcurrentLoggingInIsolatedJvm() throws Exception {
        runLoggingProbe(LoggingConcurrencyProbe.class);
    }

    @Test
    void loggingProbeReportsOnlyAllowlistedFailureEvidence() throws Exception {
        var failure = assertThrows(AssertionError.class, () -> runLoggingProbe(LoggingProbeFailureControl.class));
        assertEquals("Isolated logging probe failed during OUTPUT_BOUNDARY (exit 1)", failure.getMessage());
        var incomplete = assertThrows(AssertionError.class, () -> runLoggingProbe(LoggingProbeIncompleteControl.class));
        assertEquals("Isolated logging probe terminated without a valid result (exit 0)", incomplete.getMessage());
    }

    private static void runLoggingProbe(Class<?> probe) throws Exception {
        Path result = Files.createTempFile("sh05-logging-probe-", ".result");
        Process child = null;
        try {
            child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                    probe.getName(), result.toString()).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!child.waitFor(60, TimeUnit.SECONDS)) {
                fail("Isolated logging probe timed out");
            }
            String reported = Files.readString(result);
            int exit = child.exitValue();
            if (exit == 0 && ProbePhase.PASS.name().equals(reported)) {
                return;
            }
            if (exit == 1) {
                for (ProbePhase phase : ProbePhase.values()) {
                    if (phase != ProbePhase.PASS && phase.name().equals(reported)) {
                        fail("Isolated logging probe failed during " + phase + " (exit 1)");
                    }
                }
            }
            fail("Isolated logging probe terminated without a valid result (exit " + exit + ")");
        } finally {
            try {
                if (child != null && child.isAlive()) {
                    child.destroyForcibly();
                    assertTrue(child.waitFor(5, TimeUnit.SECONDS), "Isolated logging child cleanup failed");
                }
            } finally { Files.deleteIfExists(result); }
        }
    }

    private enum ProbePhase {
        SETUP, LIFECYCLE_REQUESTS, LIFECYCLE_RECONFIGURATION, LIFECYCLE_IN_FLIGHT_IO,
        LIFECYCLE_FINAL_ASSERTIONS, OUTPUT_BOUNDARY, DEFERRED_DEFAULT, DEFERRED_ECS,
        WORKER_START, CONCURRENT_RESETS, WORKER_SHUTDOWN, CONCURRENT_ASSERTIONS, CLEANUP, PASS
    }

    @FunctionalInterface
    private interface ProbeAssertions {
        void run(ProbeProgress progress) throws Exception;
    }

    private static final class ProbeProgress {
        private ProbePhase phase = ProbePhase.SETUP;

        private static void run(String[] args, ProbeAssertions assertions) throws IOException {
            Path result = Path.of(args[0]);
            var progress = new ProbeProgress();
            try {
                assertions.run(progress);
            } catch (Throwable failure) {
                // Assertion text, causes and console output may contain protected fixture values.
                Files.writeString(result, progress.phase.name());
                System.exit(1);
                return;
            }
            Files.writeString(result, ProbePhase.PASS.name());
        }
    }

    public static final class LoggingProbeFailureControl {
        public static void main(String[] args) throws IOException {
            ProbeProgress.run(args, progress -> {
                progress.phase = ProbePhase.OUTPUT_BOUNDARY;
                System.err.println("SH05 synthetic credential and object key");
                throw new AssertionError("SH05 synthetic authorization value");
            });
        }
    }

    public static final class LoggingProbeIncompleteControl {
        public static void main(String[] args) {
            System.out.println(ProbePhase.PASS.name());
        }
    }

    /** No access to outer static fixtures: this child owns only synthetic loopback resources. */
    public static final class LoggingLifecycleProbe {
        public static void main(String[] args) throws Exception {
            ProbeProgress.run(args, LoggingLifecycleProbe::verify);
        }

        private static void verify(ProbeProgress progress) throws Exception {
            String access = UUID.randomUUID().toString();
            String secret = UUID.randomUUID().toString();
            String objectKey = "materials/" + UUID.randomUUID();
            ByteArrayOutputStream console = new ByteArrayOutputStream();
            PrintStream previousOut = System.out;
            PrintStream previousErr = System.err;
            CountDownLatch bodyRelease = new CountDownLatch(1);
            CountDownLatch deleteAdmitted = new CountDownLatch(1);
            CountDownLatch deleteRelease = new CountDownLatch(1);
            var download = new java.util.concurrent.atomic.AtomicBoolean();
            var pauseDelete = new java.util.concurrent.atomic.AtomicBoolean();
            AtomicInteger requests = new AtomicInteger();
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            try (var workers = Executors.newVirtualThreadPerTaskExecutor();
                 PrintStream capture = new PrintStream(console, true, StandardCharsets.UTF_8)) {
                System.setOut(capture);
                System.setErr(capture);
                server.setExecutor(workers);
                server.createContext("/", exchange -> {
                    requests.incrementAndGet();
                    try {
                        exchange.getRequestBody().readAllBytes();
                        if ("DELETE".equals(exchange.getRequestMethod()) && pauseDelete.compareAndSet(true, false)) {
                            deleteAdmitted.countDown();
                            if (!deleteRelease.await(20, TimeUnit.SECONDS)) { throw new IOException("Test DELETE stalled"); }
                        }
                        if (download.get() && "GET".equals(exchange.getRequestMethod())) {
                            exchange.getResponseHeaders().add("Content-Type", "application/pdf");
                            exchange.sendResponseHeaders(200, 4096);
                            exchange.getResponseBody().write(1);
                            exchange.getResponseBody().flush();
                            if (!bodyRelease.await(20, TimeUnit.SECONDS)) { throw new IOException("Test body stalled"); }
                            exchange.getResponseBody().write(new byte[4095]);
                        } else {
                            byte[] body = ("<Error><Code>AccessDenied</Code><Message>" + secret + "</Message></Error>")
                                    .getBytes(StandardCharsets.UTF_8);
                            exchange.getResponseHeaders().add("Content-Type", "application/xml");
                            exchange.sendResponseHeaders(403, body.length);
                            exchange.getResponseBody().write(body);
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    } finally { exchange.close(); }
                });
                server.start();
                MockEnvironment environment = new MockEnvironment()
                        .withProperty("codemastery.storage.endpoint", "http://127.0.0.1:" + server.getAddress().getPort())
                        .withProperty("codemastery.storage.region", "us-east-1")
                        .withProperty("codemastery.storage.bucket", "sh05-lifecycle")
                        .withProperty("codemastery.storage.access-key", access)
                        .withProperty("codemastery.storage.secret-key", secret)
                        .withProperty("codemastery.storage.path-style-access", "true");
                LoggerContext loggingContext = (LoggerContext) LoggerFactory.getILoggerFactory();
                List<ILoggingEvent> events = Collections.synchronizedList(new ArrayList<>());
                LoggingSystem logging = LoggingSystem.get(LoggingLifecycleProbe.class.getClassLoader());
                // As in application startup, configure output before consuming the adapter.
                LoggingConcurrencyProbe.initialize(logging, false);
                try (var first = context(environment); var second = context(environment)) {
                    progress.phase = ProbePhase.LIFECYCLE_REQUESTS;
                    FileStorage firstAdapter = first.getBean(FileStorage.class);
                    FileStorage retained = second.getBean(FileStorage.class);
                    captureEvents(loggingContext, events);
                    operations(firstAdapter, objectKey);
                    assertConfidential(events, console, access, secret, objectKey);

                    progress.phase = ProbePhase.LIFECYCLE_RECONFIGURATION;
                    for (int iteration = 0; iteration < 3; iteration++) {
                        logging.cleanUp();
                        logging.beforeInitialize();
                        MockEnvironment loggingEnvironment = new MockEnvironment();
                        if (iteration == 1) {
                            loggingEnvironment.withProperty("logging.structured.format.console", "ecs");
                        }
                        logging.initialize(new LoggingInitializationContext(loggingEnvironment), null, null);
                        captureEvents(loggingContext, events);
                        operations(retained, objectKey); // Same instance created before Boot initialization.
                        assertConfidential(events, console, access, secret, objectKey);
                    }

                    var configurator = new ch.qos.logback.classic.joran.JoranConfigurator();
                    configurator.setContext(loggingContext);
                    var configurationLock = loggingContext.getConfigurationLock();
                    configurationLock.lock();
                    try {
                        loggingContext.reset();
                        configurator.doConfigure(new ByteArrayInputStream("""
                                <configuration>
                                  <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
                                    <filter class="com.codemastery.infrastructure.storage.s3.S3FileStorage$ProviderDiagnosticsFilter"/>
                                    <encoder><pattern>%logger %msg%n</pattern></encoder>
                                  </appender>
                                  <root level="TRACE"><appender-ref ref="CONSOLE"/></root>
                                </configuration>
                                """.getBytes(StandardCharsets.UTF_8)));
                    } finally { configurationLock.unlock(); }
                    captureEvents(loggingContext, events);
                    operations(retained, objectKey);
                    assertConfidential(events, console, access, secret, objectKey);

                    // A response/operation admitted before reconfiguration stays protected afterwards.
                    progress.phase = ProbePhase.LIFECYCLE_IN_FLIGHT_IO;
                    download.set(true);
                    try (StoredObject opened = retained.open(objectKey)) {
                        assertEquals(1, opened.content().read());
                        var reading = workers.submit(() -> opened.content().readAllBytes());
                        pauseDelete.set(true);
                        var deleting = workers.submit(() -> {
                            for (int i = 0; i < 20; i++) {
                                assertThrowsExactly(FileStorageException.class, () -> retained.delete(objectKey));
                            }
                        });
                        assertTrue(deleteAdmitted.await(5, TimeUnit.SECONDS), "DELETE entered provider before reset");
                        logging.cleanUp();
                        logging.beforeInitialize();
                        logging.initialize(new LoggingInitializationContext(new MockEnvironment()
                                .withProperty("logging.structured.format.console", "ecs")), null, null);
                        captureEvents(loggingContext, events);
                        for (int iteration = 0; iteration < 20; iteration++) {
                            var lock = loggingContext.getConfigurationLock();
                            lock.lock();
                            try {
                                loggingContext.reset();
                                captureEvents(loggingContext, events);
                            } finally { lock.unlock(); }
                            // Exercise provider/background logger paths independently of operation entry.
                            LoggerFactory.getLogger("software.amazon.awssdk.request").trace("{} {}", objectKey, access);
                            LoggerFactory.getLogger("org.apache.http.wire").trace("{}", secret);
                        }
                        first.close(); // Does not own the shared logging guard or the second adapter.
                        bodyRelease.countDown();
                        deleteRelease.countDown();
                        assertEquals(4095, reading.get(10, TimeUnit.SECONDS).length);
                        deleting.get(10, TimeUnit.SECONDS);
                    } finally { bodyRelease.countDown(); deleteRelease.countDown(); }
                    download.set(false);
                    operations(retained, objectKey);

                    progress.phase = ProbePhase.LIFECYCLE_FINAL_ASSERTIONS;
                    try (var invalid = context(new MockEnvironment())) {
                        var failure = assertThrows(org.springframework.beans.factory.BeanCreationException.class,
                                () -> invalid.getBean(FileStorage.class));
                        assertFalse(failure.toString().contains(secret));
                    }
                    operations(retained, objectKey);
                    LoggingConcurrencyProbe.assertProtectedOutputs(loggingContext);
                    assertTrue(loggingContext.getCopyOfListenerList().stream()
                            .noneMatch(listener -> listener.getClass().getName().endsWith("$ProviderDiagnostics")));
                    assertConfidential(events, console, access, secret, objectKey);
                    assertTrue(requests.get() >= 42, "Actual SDK requests must have reached the provider");
                }
                LoggerFactory.getLogger("org.apache.http.wire").trace("{}", secret);
                assertConfidential(events, console, access, secret, objectKey);
                progress.phase = ProbePhase.CLEANUP;
            } finally {
                bodyRelease.countDown();
                deleteRelease.countDown();
                server.stop(0);
                System.setOut(previousOut);
                System.setErr(previousErr);
            }
        }

        private static AnnotationConfigApplicationContext context(MockEnvironment environment) {
            var context = new AnnotationConfigApplicationContext();
            context.setEnvironment(environment);
            context.register(S3FileStorage.class);
            context.refresh();
            return context;
        }

        private static void operations(FileStorage adapter, String key) {
            assertThrowsExactly(FileStorageException.class, () -> adapter.open(key));
            assertThrowsExactly(FileStorageException.class, () -> adapter.delete(key));
            assertThrowsExactly(FileStorageException.class,
                    () -> adapter.store(key, new ByteArrayInputStream(new byte[]{1}), 1, "application/pdf"));
        }

        private static void captureEvents(LoggerContext context, List<ILoggingEvent> events) {
            Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
            // The synthetic server also logs its request URI through JUL after Boot
            // installs the bridge. It is the fixture, not the production transport.
            context.getLogger("com.sun.net.httpserver").setLevel(Level.OFF);
            root.setLevel(Level.TRACE);
            for (String name : List.of("software.amazon.awssdk", "software.amazon.awssdk.request",
                    "software.amazon.awssdk.auth.signer.internal.AbstractAws4Signer", "org.apache.http", "org.apache.http.wire")) {
                context.getLogger(name).setLevel(Level.TRACE);
            }
            AppenderBase<ILoggingEvent> appender = new AppenderBase<>() {
                @Override protected void append(ILoggingEvent event) {
                    event.prepareForDeferredProcessing();
                    events.add(event);
                }
            };
            appender.setContext(context);
            LoggingConcurrencyProbe.protectOutput(appender);
            appender.start();
            root.addAppender(appender);
        }

        private static void assertConfidential(List<ILoggingEvent> events, ByteArrayOutputStream console,
                                               String access, String secret, String key) {
            int previous = events.size();
            LoggerFactory.getLogger("com.codemastery.sh05-lifecycle-control").warn("SH05 ordinary application control");
            List<ILoggingEvent> snapshot;
            synchronized (events) { snapshot = List.copyOf(events); }
            assertTrue(snapshot.size() > previous, "Application logging/capture remains functional");
            assertTrue(snapshot.stream().noneMatch(event -> event.getLoggerName().startsWith("software.amazon.awssdk")
                    || event.getLoggerName().startsWith("org.apache.http")), "Provider diagnostics escaped");
            String captured = snapshot.stream().map(ILoggingEvent::getFormattedMessage)
                    .collect(java.util.stream.Collectors.joining("\n")) + console.toString(StandardCharsets.UTF_8);
            for (String sensitive : List.of(access, secret, key, "AWS4-HMAC-SHA256 Credential=")) {
                assertFalse(captured.contains(sensitive), "Sensitive diagnostics escaped");
            }
        }
    }

    /** Network-free child: deterministic deferred delivery plus concurrent ordinary logging. */
    public static final class LoggingConcurrencyProbe {
        public static void main(String[] args) throws Exception {
            ProbeProgress.run(args, LoggingConcurrencyProbe::verify);
        }

        private static void verify(ProbeProgress progress) throws Exception {
            PrintStream previousOut = System.out;
            PrintStream previousErr = System.err;
            ByteArrayOutputStream console = new ByteArrayOutputStream();
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            try (PrintStream capture = new PrintStream(console, true, StandardCharsets.UTF_8)) {
                System.setOut(capture);
                System.setErr(capture);
                progress.phase = ProbePhase.OUTPUT_BOUNDARY;
                verifyOutputBoundary();
                progress.phase = ProbePhase.SETUP;
                LoggingSystem logging = LoggingSystem.get(LoggingConcurrencyProbe.class.getClassLoader());
                initialize(logging, false);
                for (boolean structured : new boolean[]{false, true}) {
                    progress.phase = structured ? ProbePhase.DEFERRED_ECS : ProbePhase.DEFERRED_DEFAULT;
                    setTrace(context);
                    String pending = "SH05 synthetic deferred provider diagnostic";
                    CountDownLatch admitted = new CountDownLatch(1);
                    CountDownLatch release = new CountDownLatch(1);
                    var loggingThread = new java.util.concurrent.atomic.AtomicReference<Thread>();
                    var previousSequence = context.getSequenceNumberGenerator();
                    // Logback constructs LoggingEvent after the source-filter decision and
                    // calls this public hook before looking up the current appenders.
                    context.setSequenceNumberGenerator(new ch.qos.logback.core.spi.BasicSequenceNumberGenerator() {
                        @Override public long nextSequenceNumber() {
                            if (Thread.currentThread() == loggingThread.get()) {
                                admitted.countDown();
                                try {
                                    assertTrue(release.await(15, TimeUnit.SECONDS), "Deferred event must be released");
                                } catch (InterruptedException failure) {
                                    Thread.currentThread().interrupt();
                                    throw new AssertionError(failure);
                                }
                            }
                            return super.nextSequenceNumber();
                        }
                    });
                    try (var worker = Executors.newSingleThreadExecutor()) {
                        try {
                            var event = worker.submit(() -> {
                                loggingThread.set(Thread.currentThread());
                                LoggerFactory.getLogger("software.amazon.awssdk.synthetic").debug(pending);
                            });
                            assertTrue(admitted.await(5, TimeUnit.SECONDS), "Provider event passed source admission");
                            initialize(logging, structured);
                            setTrace(context);
                            release.countDown();
                            event.get(5, TimeUnit.SECONDS);
                            String control = "SH05 post-reconfiguration application control " + structured;
                            LoggerFactory.getLogger("com.codemastery.sh05-concurrent-control").warn(control);
                            String output = console.toString(StandardCharsets.UTF_8);
                            assertFalse(output.contains(pending), "Deferred provider event reached new output");
                            assertTrue(output.contains(control), "New output must emit application logging");
                            var appender = (ch.qos.logback.core.OutputStreamAppender<?>)
                                    context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("CONSOLE");
                            assertEquals(structured, appender.getEncoder() instanceof
                                    org.springframework.boot.logging.logback.StructuredLogEncoder,
                                    "Boot default/ECS format must be preserved");
                            assertProtectedOutputs(context);
                        } finally {
                            release.countDown();
                            context.setSequenceNumberGenerator(previousSequence);
                        }
                    }
                }
                verifyConcurrentResets(context, progress);
                assertFalse(console.toString(StandardCharsets.UTF_8).contains("SH05 synthetic provider"));
                progress.phase = ProbePhase.CLEANUP;
            } finally {
                context.stop();
                System.setOut(previousOut);
                System.setErr(previousErr);
            }
        }

        private static void initialize(LoggingSystem logging, boolean structured) {
            logging.cleanUp();
            logging.beforeInitialize();
            MockEnvironment environment = new MockEnvironment()
                    .withProperty("logging.structured.format.console", structured ? "ecs" : "");
            logging.initialize(new LoggingInitializationContext(environment), null, null);
        }

        private static void setTrace(LoggerContext context) {
            context.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.TRACE);
            context.getLogger("software.amazon.awssdk.synthetic").setLevel(Level.TRACE);
            context.getLogger("org.apache.http.wire").setLevel(Level.TRACE);
        }

        private static void verifyOutputBoundary() {
            // An event already admitted at the source must be denied at delivery.
            // An otherwise identical unfiltered sink is a mandatory negative control.
            LoggerContext context = new LoggerContext();
            try {
                var provider = context.getLogger("software.amazon.awssdk.synthetic");
                var pending = new ch.qos.logback.classic.spi.LoggingEvent(
                        LoggingConcurrencyProbe.class.getName(), provider, Level.DEBUG,
                        "SH05 harmless pending diagnostic", null, null);
                ListAppender<ILoggingEvent> unfiltered = new ListAppender<>();
                unfiltered.start();
                unfiltered.doAppend(pending);
                assertEquals(1, unfiltered.list.size(), "Unfiltered negative control must detect the pending event");
                unfiltered.stop();
                ListAppender<ILoggingEvent> guarded = new ListAppender<>();
                protectOutput(guarded);
                guarded.start();
                for (String name : List.of("software.amazon.awssdk", "software.amazon.awssdk.synthetic",
                        "org.apache.http", "org.apache.http.wire")) {
                    guarded.doAppend(new ch.qos.logback.classic.spi.LoggingEvent(
                            LoggingConcurrencyProbe.class.getName(), context.getLogger(name), Level.DEBUG,
                            "SH05 harmless pending diagnostic", null, null));
                }
                assertTrue(guarded.list.isEmpty(), "Every approved provider namespace must be denied");
                guarded.doAppend(new ch.qos.logback.classic.spi.LoggingEvent(LoggingConcurrencyProbe.class.getName(),
                        context.getLogger("com.codemastery.synthetic"), Level.WARN, "application control", null, null));
                assertEquals(1, guarded.list.size(), "Guard must preserve application output");
                guarded.stop();
            } finally { context.stop(); }
        }

        private static void verifyConcurrentResets(LoggerContext context, ProbeProgress progress) throws Exception {
            List<ILoggingEvent> events = Collections.synchronizedList(new ArrayList<>());
            var running = new java.util.concurrent.atomic.AtomicBoolean(true);
            CountDownLatch active = new CountDownLatch(4);
            var tasks = new ArrayList<java.util.concurrent.Future<?>>();
            progress.phase = ProbePhase.WORKER_START;
            try (var workers = Executors.newFixedThreadPool(4)) {
                try {
                    for (int i = 0; i < 4; i++) {
                        int worker = i;
                        tasks.add(workers.submit(() -> {
                            var provider = LoggerFactory.getLogger(worker % 2 == 0
                                    ? "software.amazon.awssdk.synthetic" : "org.apache.http.wire");
                            provider.debug("SH05 synthetic provider diagnostic");
                            active.countDown();
                            while (running.get()) { provider.debug("SH05 synthetic provider diagnostic"); }
                        }));
                    }
                    assertTrue(active.await(5, TimeUnit.SECONDS), "All logging workers must be active");
                    progress.phase = ProbePhase.CONCURRENT_RESETS;
                    for (int i = 0; i < 1000; i++) {
                        var lock = context.getConfigurationLock();
                        lock.lock();
                        try {
                            context.reset();
                            LoggingLifecycleProbe.captureEvents(context, events);
                        } finally { lock.unlock(); }
                        if (i % 100 == 0) {
                            LoggerFactory.getLogger("com.codemastery.sh05-concurrent-control").warn("reset control");
                        }
                    }
                } finally {
                    running.set(false);
                }
                progress.phase = ProbePhase.WORKER_SHUTDOWN;
                for (var task : tasks) { task.get(5, TimeUnit.SECONDS); }
            }
            progress.phase = ProbePhase.CONCURRENT_ASSERTIONS;
            LoggerFactory.getLogger("com.codemastery.sh05-concurrent-control").warn("final application control");
            synchronized (events) {
                assertTrue(events.stream().anyMatch(event -> "final application control".equals(event.getFormattedMessage())));
                assertTrue(events.stream().noneMatch(event -> event.getLoggerName().startsWith("software.amazon.awssdk")
                        || event.getLoggerName().startsWith("org.apache.http")), "Concurrent provider event escaped");
            }
            assertProtectedOutputs(context);
        }

        private static void protectOutput(ch.qos.logback.core.Appender<ILoggingEvent> appender) {
            ProviderDiagnosticsFilter filter = new ProviderDiagnosticsFilter();
            filter.start();
            appender.addFilter(filter);
        }

        private static void assertProtectedOutputs(LoggerContext context) {
            var appenders = context.getLogger(Logger.ROOT_LOGGER_NAME).iteratorForAppenders();
            int count = 0;
            while (appenders.hasNext()) {
                var appender = appenders.next();
                assertTrue(appender.isStarted(), "Configured output must be active");
                assertEquals(1, appender.getCopyOfAttachedFiltersList().stream()
                        .filter(ProviderDiagnosticsFilter.class::isInstance).count(), "One deny boundary per output");
                count++;
            }
            assertTrue(count > 0, "Logging must have an output");
        }
    }

    @Test
    void approvedRequestDeadlineTerminatesAnUnresponsiveProvider() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch admitted = new CountDownLatch(1);
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            server.setExecutor(workers);
            server.createContext("/", exchange -> {
                requests.incrementAndGet();
                admitted.countDown();
                try { release.await(80, TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                finally { exchange.close(); }
            });
            server.start();
            try (AnnotationConfigApplicationContext context = adapterContext(configuration(
                    "http://127.0.0.1:" + server.getAddress().getPort()))) {
                long started = System.nanoTime();
                safeException(FileStorageException.class, () -> context.getBean(FileStorage.class).open(key("materials")));
                long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                assertTrue(admitted.await(1, TimeUnit.SECONDS));
                assertTrue(millis >= 25_000 && millis < 70_000, "Qualified finite socket/request timeout");
                assertEquals(1, requests.get());
            } finally { release.countDown(); server.stop(0); }
        }
    }

    @Test
    void contextShutdownClosesClientAndTransportEvenWhenClientCloseFails() throws Exception {
        MockAdapter adapter = mocked();
        SdkHttpClient transport = mock(SdkHttpClient.class);
        replace(adapter.storage, "http", transport);
        doThrow(new IllegalStateException(APP_SECRET)).when(adapter.client).close();
        adapter.close();
        verify(adapter.client).close();
        verify(transport).close();
    }

    private static MockEnvironment configuration(String endpoint) {
        return new MockEnvironment().withProperty("codemastery.storage.endpoint", endpoint)
                .withProperty("codemastery.storage.region", "us-east-1")
                .withProperty("codemastery.storage.bucket", BUCKET)
                .withProperty("codemastery.storage.access-key", APP_USER)
                .withProperty("codemastery.storage.secret-key", APP_SECRET)
                .withProperty("codemastery.storage.path-style-access", "true");
    }

    private static AnnotationConfigApplicationContext adapterContext(MockEnvironment environment) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.setEnvironment(environment);
        context.register(S3FileStorage.class);
        context.refresh();
        return context;
    }

    private static MockAdapter mocked() throws Exception {
        AnnotationConfigApplicationContext context = adapterContext(configuration(endpoint()));
        S3FileStorage storage = context.getBean(S3FileStorage.class);
        Field field = S3FileStorage.class.getDeclaredField("client");
        field.setAccessible(true);
        ((S3Client) field.get(storage)).close();
        S3Client client = mock(S3Client.class);
        field.set(storage, client);
        return new MockAdapter(context, storage, client);
    }

    private static void replace(S3FileStorage storage, String name, Object replacement) throws Exception {
        Field field = S3FileStorage.class.getDeclaredField(name);
        field.setAccessible(true);
        Object previous = field.get(storage);
        if (previous instanceof SdkHttpClient transport) { transport.close(); }
        field.set(storage, replacement);
    }

    private record MockAdapter(AnnotationConfigApplicationContext context, S3FileStorage storage, S3Client client)
            implements AutoCloseable {
        @Override public void close() { context.close(); }
    }

    private static <T extends Throwable> T safeException(Class<T> type, org.junit.jupiter.api.function.Executable action) {
        T failure = assertThrowsExactly(type, action);
        assertNull(failure.getCause());
        assertEquals(0, failure.getSuppressed().length);
        assertEquals(type == IllegalArgumentException.class ? "Invalid file storage arguments."
                : "File storage operation failed.", failure.getMessage());
        assertFalse(stackTrace(failure).contains(APP_SECRET));
        assertFalse(stackTrace(failure).contains(APP_USER));
        return failure;
    }

    private static String stackTrace(Throwable failure) {
        StringWriter result = new StringWriter();
        failure.printStackTrace(new PrintWriter(result));
        return result.toString();
    }
}
