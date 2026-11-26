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
@org.springframework.test.context.event.RecordApplicationEvents
@org.springframework.boot.test.context.SpringBootTest(classes = com.codemastery.CodeMasteryApplication.class,
        webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.context.annotation.Import(FileStorageIntegrationTest.CommitFaultConfiguration.class)
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
        registry.add("server.address", () -> "127.0.0.1");
        registry.add("spring.servlet.multipart.max-file-size", () -> "1KB");
        registry.add("spring.servlet.multipart.max-request-size", () -> "1KB");
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
                    String privateKey = "materials/00000000-0000-4000-8000-000000000001";
                    org.springframework.jdbc.core.StatementCreatorUtils.setParameterValue(
                            mock(java.sql.PreparedStatement.class), 1, java.sql.Types.VARCHAR, privateKey);
                    LoggerFactory.getLogger("org.springframework.transaction.support.TransactionTemplate")
                            .debug("Synthetic translated failure", new IllegalStateException("Synthetic row " + privateKey));
                    LoggerFactory.getLogger("com.codemastery.synthetic-control").debug("Synthetic accidental key {}", privateKey);
                    assertFalse(console.toString(StandardCharsets.UTF_8).contains(privateKey),
                            "CO09-R04: actual JDBC bind diagnostics must be denied by both output formats");
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

    // CO09 consumer qualification. Faults below decorate test beans only; real storage is retained.
    static final ThreadLocal<String> RUNTIME_ROLE = new ThreadLocal<>();
    static final java.util.Queue<com.codemastery.modules.assessment.application.event.AssignmentGradedEvent> COMMITTED_GRADES = new java.util.concurrent.ConcurrentLinkedQueue<>();
    static final ThreadLocal<String> COMMIT_FAULT = new ThreadLocal<>();
    static final AtomicInteger BORROWED = new AtomicInteger();
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class CommitFaultConfiguration {
        @org.springframework.transaction.event.TransactionalEventListener(phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT)
        public void committedGrade(com.codemastery.modules.assessment.application.event.AssignmentGradedEvent event) {
            COMMITTED_GRADES.add(event);
        }
        @org.springframework.context.annotation.Bean
        static org.springframework.beans.factory.config.BeanPostProcessor commitFaultDataSource() {
            return new org.springframework.beans.factory.config.BeanPostProcessor() {
                @Override public Object postProcessAfterInitialization(Object bean, String name) {
                    if (!(bean instanceof javax.sql.DataSource source)) return bean;
                    return new org.springframework.jdbc.datasource.DelegatingDataSource(source) {
                        @Override public java.sql.Connection getConnection() throws java.sql.SQLException { return wrap(super.getConnection()); }
                        @Override public java.sql.Connection getConnection(String u,String p) throws java.sql.SQLException { return wrap(super.getConnection(u,p)); }
                        java.sql.Connection wrap(java.sql.Connection connection) throws java.sql.SQLException {
                            String role = RUNTIME_ROLE.get();
                            if (role != null) {
                                try (var statement = connection.createStatement()) { statement.execute("SET ROLE " + role); }
                            }
                            BORROWED.incrementAndGet(); var closed = new java.util.concurrent.atomic.AtomicBoolean();
                            return (java.sql.Connection)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                                new Class<?>[]{java.sql.Connection.class}, (proxy,method,args)->{
                                    try {
                                        if (method.getName().equals("commit")) {
                                            String fault=COMMIT_FAULT.get(); COMMIT_FAULT.remove();
                                            if ("before".equals(fault)) throw new java.sql.SQLException("Synthetic commit rejection","08006");
                                            Object result=method.invoke(connection,args);
                                            if ("after".equals(fault)) throw new java.sql.SQLException("Synthetic lost acknowledgement","08006");
                                            return result;
                                        }
                                        if (method.getName().equals("close") && closed.compareAndSet(false,true)) {
                                            try {
                                                if (role != null) try (var statement = connection.createStatement()) { statement.execute("RESET ROLE"); }
                                            } finally { BORROWED.decrementAndGet(); }
                                        }
                                        return method.invoke(connection,args);
                                    } catch(java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
                                });
                        }
                    };
                }
            };
        }
    }
    @Autowired org.springframework.test.context.event.ApplicationEvents recordedMaterialEvents;
    @Autowired com.codemastery.modules.course.application.service.LearningMaterialService materials;
    @Autowired com.codemastery.modules.course.application.service.CourseManagementService materialManagement;
    @Autowired com.codemastery.modules.course.application.service.CourseAccessService materialAccess;
    @Autowired com.codemastery.modules.course.domain.repository.CourseContentRepository materialRows;
    @Autowired org.springframework.jdbc.core.JdbcTemplate materialJdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager materialTransactions;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager materialPersistence;
    @org.springframework.beans.factory.annotation.Value("${local.server.port}") int materialPort;
    @Autowired com.codemastery.modules.auth.infrastructure.security.JwtTokenProvider materialTokens;
    @Autowired org.springframework.boot.servlet.filter.OrderedFormContentFilter materialFormFilter;
    static final byte[] PDF_A={'%','P','D','F','-',0,(byte)255,10,1}, PDF_B={'%','P','D','F','-',0,(byte)128,10,2};

    final class MaterialFixture implements AutoCloseable {
        final UUID owner=UUID.randomUUID(), learner=UUID.randomUUID(), course=UUID.randomUUID(), chapter=UUID.randomUUID(), lesson=UUID.randomUUID();
        MaterialFixture() {
            materialJdbc.update("INSERT INTO users(id,email,password_hash,display_name,role,account_status) VALUES (?,?,'unused','Owner','instructor','active'),(?,?,'unused','Learner','learner','active')",owner,owner+"@example.test",learner,learner+"@example.test");
            materialJdbc.update("INSERT INTO courses(id,instructor_id,title,description,publication_status,adaptive_enabled) VALUES (?,?,'CO09','Test','published',false)",course,owner);
            materialJdbc.update("INSERT INTO chapters(id,course_id,title,position) VALUES (?,?,'C',1)",chapter,course);
            materialJdbc.update("INSERT INTO lessons(id,chapter_id,title,position,publication_status) VALUES (?,?,'L',1,'published')",lesson,chapter);
            materialJdbc.update("INSERT INTO enrollments(id,user_id,course_id,status) VALUES (?,?,?,'active')",UUID.randomUUID(),learner,course);
            signIn(owner,com.codemastery.modules.auth.domain.model.Role.INSTRUCTOR);
        }
        String key(UUID id) { return materialJdbc.queryForObject("SELECT object_key FROM learning_materials WHERE id=?",String.class,id); }
        String token(UUID user) {
            UUID session=UUID.randomUUID();
            materialJdbc.update("INSERT INTO refresh_sessions(id,user_id,created_at,expires_at) VALUES (?,?,now(),now()+interval '1 hour')",session,user);
            return materialTokens.createToken(user,session);
        }
        String path() { return "/api/instructor/courses/"+course+"/lessons/"+lesson+"/file-materials"; }
        String download(UUID id) { return "/api/courses/"+course+"/lessons/"+lesson+"/materials/"+id+"/file"; }
        public void close() {
            COMMIT_FAULT.remove();org.springframework.security.core.context.SecurityContextHolder.clearContext();
            for(String k:materialJdbc.queryForList("SELECT object_key FROM learning_materials WHERE lesson_id=? AND object_key IS NOT NULL",String.class,lesson))storage.delete(k);
            materialJdbc.update("DELETE FROM learning_materials WHERE lesson_id=?",lesson);
            materialJdbc.update("DELETE FROM enrollments WHERE course_id=?",course);
            materialJdbc.update("DELETE FROM lessons WHERE id=?",lesson);materialJdbc.update("DELETE FROM chapters WHERE id=?",chapter);
            materialJdbc.update("DELETE FROM courses WHERE id=?",course);
            materialJdbc.update("DELETE FROM refresh_tokens WHERE session_id IN (SELECT id FROM refresh_sessions WHERE user_id IN (?,?))",owner,learner);
            materialJdbc.update("DELETE FROM refresh_sessions WHERE user_id IN (?,?)",owner,learner);
            materialJdbc.update("DELETE FROM users WHERE id IN (?,?)",owner,learner);
        }
    }
    static void signIn(UUID user,com.codemastery.modules.auth.domain.model.Role role) {
        var principal=new com.codemastery.modules.auth.infrastructure.security.AuthenticatedUser(user,role);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
    }
    static com.codemastery.modules.course.application.service.LearningMaterialService.FilePart pdf(String name,byte[] bytes) {
        return new com.codemastery.modules.course.application.service.LearningMaterialService.FilePart(name,bytes.length,new ByteArrayInputStream(bytes));
    }
    com.codemastery.modules.course.api.dto.CourseStructureResponses.Material createPdf(MaterialFixture f) {
        return materials.createFileMaterial(f.course,f.lesson,()->new com.codemastery.modules.course.application.service.LearningMaterialService.FileMaterialUpload(" Slides ",pdf("Bài học.PDF",PDF_A)));
    }
    class ObservedStorage implements FileStorage {
        final List<String> stored=Collections.synchronizedList(new ArrayList<>()),deleted=Collections.synchronizedList(new ArrayList<>()); int opens;
        String commitFault; Runnable beforeOpen; boolean failStore,failDelete,providerFailure;
        public void store(String key,InputStream in,long size,String media) {
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());assertEquals(0,BORROWED.get());
            stored.add(key);if(failStore)throw new FileStorageException();storage.store(key,in,size,media);
            if(commitFault!=null)COMMIT_FAULT.set(commitFault);
        }
        public StoredObject open(String key) {
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());assertEquals(0,BORROWED.get());opens++;
            if(beforeOpen!=null){Runnable r=beforeOpen;beforeOpen=null;r.run();}
            if(providerFailure)throw new FileStorageException();return storage.open(key);
        }
        public void delete(String key) {
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            deleted.add(key);if(failDelete)throw new FileStorageException();storage.delete(key);
        }
    }
    com.codemastery.modules.course.application.service.LearningMaterialService consumer(FileStorage adapter,com.codemastery.modules.course.domain.repository.CourseContentRepository rows) {
        return new com.codemastery.modules.course.application.service.LearningMaterialService(materialManagement,materialAccess,rows,adapter,materialPersistence,materialTransactions,environment);
    }
    @Test void co09AllEightOperationsAndRealStorage() throws Exception {
        try(var f=new MaterialFixture()) {
            var text=materials.createRelationalMaterial(f.course,f.lesson,new com.codemastery.modules.course.api.dto.CourseStructureRequests.MaterialWrite(" Text ",com.codemastery.modules.course.domain.model.MaterialType.TEXT,"  code\n",null));
            assertEquals("hidden",text.publicationStatus());assertEquals("  code\n",text.textContent());
            materials.updateRelationalMaterial(f.course,f.lesson,text.id(),new com.codemastery.modules.course.api.dto.CourseStructureRequests.MaterialWrite("Edited",com.codemastery.modules.course.domain.model.MaterialType.TEXT," new ",null));
            materials.setRelationalMaterialPublication(f.course,f.lesson,text.id(),com.codemastery.modules.course.domain.model.PublishStatus.PUBLISHED);
            var video=materials.createRelationalMaterial(f.course,f.lesson,new com.codemastery.modules.course.api.dto.CourseStructureRequests.MaterialWrite("Video",com.codemastery.modules.course.domain.model.MaterialType.EXTERNAL_VIDEO,null," https://www.youtube-nocookie.com/embed/abc_-9 "));
            assertEquals("https://www.youtube-nocookie.com/embed/abc_-9",video.externalVideoUrl());
            var file=createPdf(f);String old=f.key(file.id());
            assertEquals(4,UUID.fromString(old.substring("materials/".length())).version());
            assertEquals("Slides",file.title());assertEquals("hidden",file.publicationStatus());
            assertEquals("Renamed",materials.updateFileMaterialTitle(f.course,f.lesson,file.id()," Renamed ").title());
            var replaced=materials.replaceFileMaterialFile(f.course,f.lesson,file.id(),()->pdf("new.pdf",PDF_B));
            assertEquals(file.id(),replaced.id());assertNotEquals(old,f.key(file.id()));assertThrows(ObjectNotFoundException.class,()->storage.open(old));
            materials.setFileMaterialPublication(f.course,f.lesson,file.id(),com.codemastery.modules.course.domain.model.PublishStatus.PUBLISHED);
            signIn(f.learner,com.codemastery.modules.auth.domain.model.Role.LEARNER);
            try(var download=materials.downloadMaterialFile(f.course,f.lesson,file.id())) {assertEquals("new.pdf",download.originalFilename());assertArrayEquals(PDF_B,download.content().readAllBytes());assertEquals(0,BORROWED.get());}
            materialJdbc.update("UPDATE learning_materials SET publication_status='hidden' WHERE id=?",file.id());
            assertThrows(com.codemastery.common.exception.ResourceNotFoundException.class,()->materials.downloadMaterialFile(f.course,f.lesson,file.id()));
            assertTrue(recordedMaterialEvents.stream().noneMatch(event->event.getClass().getName().endsWith("ContentPublishedEvent")),
                    "Material authoring/publication never emits the CO12 Lesson event, including in a combined checkout");
        }
    }
    @ParameterizedTest @ValueSource(strings={"after","before"})
    void co09UnknownCommitRetainsBothObjects(String fault) throws Exception {
        try(var f=new MaterialFixture()) {
            var file=createPdf(f);String old=f.key(file.id());var observed=new ObservedStorage();observed.commitFault=fault;
            assertThrows(RuntimeException.class,()->consumer(observed,materialRows).replaceFileMaterialFile(f.course,f.lesson,file.id(),()->pdf("new.pdf",PDF_B)));
            assertEquals(1,observed.stored.size());assertTrue(observed.deleted.isEmpty());String fresh=observed.stored.getFirst();
            try(var one=storage.open(old);var two=storage.open(fresh)){assertEquals(PDF_A.length,one.contentLength());assertEquals(PDF_B.length,two.contentLength());}
            assertEquals(fault.equals("after")?fresh:old,f.key(file.id()));
            try(var download=materials.downloadMaterialFile(f.course,f.lesson,file.id())){assertArrayEquals(fault.equals("after")?PDF_B:PDF_A,download.content().readAllBytes());}
            storage.delete(fresh);storage.delete(old);
        }
    }
    @Test void co09PreCommitStoreAndCleanupFailures() throws Exception {
        try(var f=new MaterialFixture()) {
            var file=createPdf(f);String old=f.key(file.id());var observed=new ObservedStorage();var rows=mock(com.codemastery.modules.course.domain.repository.CourseContentRepository.class, org.mockito.AdditionalAnswers.delegatesTo(materialRows));
            doThrow(new IllegalStateException("Synthetic statement fault")).when(rows).replaceMaterialContent(any());
            assertThrows(RuntimeException.class,()->consumer(observed,rows).replaceFileMaterialFile(f.course,f.lesson,file.id(),()->pdf("new.pdf",PDF_B)));
            assertEquals(observed.stored,observed.deleted);assertEquals(old,f.key(file.id()));assertThrows(ObjectNotFoundException.class,()->storage.open(observed.stored.getFirst()));
            observed.stored.clear();observed.deleted.clear();observed.failStore=true;
            assertThrows(FileStorageException.class,()->consumer(observed,materialRows).replaceFileMaterialFile(f.course,f.lesson,file.id(),()->pdf("new.pdf",PDF_B)));
            assertEquals(observed.stored,observed.deleted);observed.failStore=false;observed.failDelete=true;observed.stored.clear();observed.deleted.clear();
            consumer(observed,materialRows).replaceFileMaterialFile(f.course,f.lesson,file.id(),()->pdf("new.pdf",PDF_B));
            assertEquals(List.of(old),observed.deleted);try(var orphan=storage.open(old)){assertArrayEquals(PDF_A,orphan.content().readAllBytes());}storage.delete(old);
        }
    }
    @Test void co09AdmissionAndOuterTransactionPrecedeParts() {
        try(var f=new MaterialFixture()) {
            var touched=new java.util.concurrent.atomic.AtomicBoolean();signIn(f.learner,com.codemastery.modules.auth.domain.model.Role.LEARNER);
            assertThrows(org.springframework.security.access.AccessDeniedException.class,()->materials.createFileMaterial(f.course,f.lesson,()->{touched.set(true);throw new AssertionError();}));
            assertFalse(touched.get());signIn(f.owner,com.codemastery.modules.auth.domain.model.Role.INSTRUCTOR);
            new org.springframework.transaction.support.TransactionTemplate(materialTransactions).executeWithoutResult(tx->{
                assertThrows(IllegalStateException.class,()->materials.createFileMaterial(f.course,f.lesson,()->{touched.set(true);throw new AssertionError();}));assertFalse(touched.get());
            });
        }
    }
    @Test void co09DownloadRereadsOnlySupersededMissingObject() throws Exception {
        try(var f=new MaterialFixture()) {
            var file=createPdf(f);var observed=new ObservedStorage();var service=consumer(observed,materialRows);
            observed.beforeOpen=()->materials.replaceFileMaterialFile(f.course,f.lesson,file.id(),()->pdf("current.pdf",PDF_B));
            try(var download=service.downloadMaterialFile(f.course,f.lesson,file.id())){assertArrayEquals(PDF_B,download.content().readAllBytes());assertEquals("current.pdf",download.originalFilename());}
            assertEquals(2,observed.opens);observed.opens=0;observed.providerFailure=true;
            assertThrows(FileStorageException.class,()->service.downloadMaterialFile(f.course,f.lesson,file.id()));assertEquals(1,observed.opens);
            observed.providerFailure=false;storage.delete(f.key(file.id()));observed.opens=0;
            assertThrows(IllegalStateException.class,()->service.downloadMaterialFile(f.course,f.lesson,file.id()));assertEquals(1,observed.opens);
        }
    }
    java.net.http.HttpResponse<byte[]> materialHttp(String method,String path,String token,String type,byte[] body) throws Exception {
        var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:"+materialPort+path));
        if(token!=null)request.header("Authorization","Bearer "+token);if(type!=null)request.header("Content-Type",type);
        return java.net.http.HttpClient.newHttpClient().send(request.method(method,java.net.http.HttpRequest.BodyPublishers.ofByteArray(body)).build(),java.net.http.HttpResponse.BodyHandlers.ofByteArray());
    }
    @Test void co09RealTomcatMultipartPrecedenceAndBinaryHeaders() throws Exception {
        try(var f=new MaterialFixture()) {
            String owner=f.token(f.owner),learner=f.token(f.learner);byte[] malformed="unparseable".getBytes(StandardCharsets.US_ASCII);
            // CO09-R02: FormContentFilter must not parse a wrong-media replacement before Security.
            String replacement=f.path()+"/"+UUID.randomUUID()+"/file";
            byte[] badForm="x=%ZZ".getBytes(StandardCharsets.US_ASCII);
            assertEquals(401,materialHttp("PUT",replacement,null,"application/x-www-form-urlencoded",badForm).statusCode());
            assertEquals(415,materialHttp("PUT",replacement,learner,"application/x-www-form-urlencoded",badForm).statusCode());
            assertEquals(401,materialHttp("POST",f.path(),null,"multipart/form-data; boundary=x",malformed).statusCode());
            assertEquals(415,materialHttp("POST",f.path(),learner,"text/plain",malformed).statusCode());
            var denied=materialHttp("POST",f.path(),learner,"multipart/form-data; boundary=x",malformed);
            assertEquals(403,denied.statusCode());assertEquals("close",denied.headers().firstValue("Connection").orElse(""));
            assertEquals(400,materialHttp("POST",f.path(),owner,"multipart/form-data; boundary=x",malformed).statusCode());
            // CO09-R03: a GET parameter resolver must never parse hostile multipart before admission.
            assertEquals(404,materialHttp("GET",f.download(UUID.randomUUID()),learner,"multipart/form-data; boundary=x",malformed).statusCode());
            ByteArrayOutputStream body=new ByteArrayOutputStream();
            body.write("--x\r\nContent-Disposition: form-data; name=\"title\"\r\n\r\nSlides\r\n--x\r\nContent-Disposition: form-data; name=\"file\"; filename=\"Bài.pdf\"\r\nContent-Type: text/plain\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            body.write(PDF_A);body.write("\r\n--x--\r\n".getBytes(StandardCharsets.US_ASCII));
            var created=materialHttp("POST",f.path(),owner,"multipart/form-data; boundary=x",body.toByteArray());assertEquals(201,created.statusCode(),new String(created.body()));
            String json=new String(created.body(),StandardCharsets.UTF_8);assertFalse(json.contains("materials/"));assertFalse(json.contains("slides.pdf"));
            UUID id=UUID.fromString(new tools.jackson.databind.json.JsonMapper().readTree(json).get("id").asString());
            var download=materialHttp("GET",f.download(id),owner,null,new byte[0]);assertEquals(200,download.statusCode());assertArrayEquals(PDF_A,download.body());
            assertEquals("application/pdf",download.headers().firstValue("Content-Type").orElse(""));
            assertTrue(download.headers().firstValue("Content-Disposition").orElse("").contains("filename*=UTF-8''B%C3%A0i.pdf"));
            assertTrue(download.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
            assertEquals("nosniff",download.headers().firstValue("X-Content-Type-Options").orElse(""));
            assertEquals(200,materialHttp("PUT",f.path()+"/"+id,owner,"application/json","{\"title\":\"Renamed\"}".getBytes(StandardCharsets.UTF_8)).statusCode());
            assertEquals(200,materialHttp("PUT",f.path()+"/"+id+"/publication",owner,"application/json","{\"publicationStatus\":\"published\"}".getBytes(StandardCharsets.UTF_8)).statusCode());
            assertEquals(200,materialHttp("GET",f.download(id),learner,null,new byte[0]).statusCode());
            ByteArrayOutputStream replacementBody=new ByteArrayOutputStream();
            replacementBody.write("--x\r\nContent-Disposition: form-data; name=\"file\"; filename=\"replacement.pdf\"\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            replacementBody.write(PDF_B);replacementBody.write("\r\n--x--\r\n".getBytes(StandardCharsets.US_ASCII));
            assertEquals(200,materialHttp("PUT",f.path()+"/"+id+"/file",owner,"multipart/form-data; boundary=x",replacementBody.toByteArray()).statusCode());
            assertArrayEquals(PDF_B,materialHttp("GET",f.download(id),learner,null,new byte[0]).body());
            String relational=f.path().replace("file-materials","materials");
            var textCreated=materialHttp("POST",relational,owner,"application/json","{\"title\":\"Text\",\"materialType\":\"text\",\"textContent\":\"  code\"}".getBytes(StandardCharsets.UTF_8));
            assertEquals(201,textCreated.statusCode());
            UUID textId=UUID.fromString(new tools.jackson.databind.json.JsonMapper().readTree(textCreated.body()).get("id").asString());
            assertEquals(200,materialHttp("PUT",relational+"/"+textId,owner,"application/json","{\"title\":\"Edited\",\"materialType\":\"text\",\"textContent\":\" body \"}".getBytes(StandardCharsets.UTF_8)).statusCode());
            assertEquals(200,materialHttp("PUT",relational+"/"+textId+"/publication",owner,"application/json","{\"publicationStatus\":\"published\"}".getBytes(StandardCharsets.UTF_8)).statusCode());
            byte[] oversized=new byte[2048];
            assertEquals(403,materialHttp("POST",f.path(),learner,"multipart/form-data; boundary=x",oversized).statusCode());
            assertEquals(413,materialHttp("POST",f.path(),owner,"multipart/form-data; boundary=x",oversized).statusCode());
        }
    }


    @ParameterizedTest
    @ValueSource(strings = {"file-materials", "%66ile-materials", "file%2Dmaterials", "%66ile%2dmaterials"})
    void co09Ir01EquivalentUploadRoutesKeepAdmissionBeforeFormParsing(String segment) throws Exception {
        try (var f = new MaterialFixture()) {
            String owner = f.token(f.owner), learner = f.token(f.learner);
            String path = f.path().replace("file-materials", segment);
            byte[] badForm = "x=%ZZ".getBytes(StandardCharsets.US_ASCII);
            for (String target : List.of(path + "/" + UUID.randomUUID() + "/file", path)) {
                String method = target.equals(path) ? "POST" : "PUT";
                for (String token : List.of(owner, learner, "invalid")) {
                    var response = materialHttp(method, target, token, "application/x-www-form-urlencoded", badForm);
                    assertEquals(token.equals("invalid") ? 401 : 415, response.statusCode(), target);
                    assertEquals("close", response.headers().firstValue("Connection").orElse(""), target);
                }
                assertEquals(401, materialHttp(method, target, null, "application/x-www-form-urlencoded", badForm).statusCode());
                assertEquals(403, materialHttp(method, target, learner, "multipart/form-data; boundary=x", badForm).statusCode());
                assertEquals(target.equals(path) ? 400 : 404,
                        materialHttp(method, target, owner, "multipart/form-data; boundary=x", badForm).statusCode());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/publication"})
    void co09Ir02RelationalScopePrecedesInvalidBodyOnRealHttp(String suffix) throws Exception {
        try (var f = new MaterialFixture(); var foreign = new MaterialFixture()) {
            String owner = f.token(f.owner), learner = f.token(f.learner), other = foreign.token(foreign.owner);
            signIn(f.owner, com.codemastery.modules.auth.domain.model.Role.INSTRUCTOR);
            var text = materials.createRelationalMaterial(f.course, f.lesson,
                    new com.codemastery.modules.course.api.dto.CourseStructureRequests.MaterialWrite(
                            "Original", com.codemastery.modules.course.domain.model.MaterialType.TEXT, "  code\n", null));
            var file = createPdf(f);
            signIn(foreign.owner, com.codemastery.modules.auth.domain.model.Role.INSTRUCTOR);
            var foreignText = materials.createRelationalMaterial(foreign.course, foreign.lesson,
                    new com.codemastery.modules.course.api.dto.CourseStructureRequests.MaterialWrite(
                            "Foreign", com.codemastery.modules.course.domain.model.MaterialType.TEXT, "foreign", null));
            String path = f.path().replace("file-materials", "materials") + "/";
            List<String> invalid = List.of("{}", "", "{", "null", "{\"title\":\"Only\"}",
                    "{\"publicationStatus\":\"invalid\"}", "{\"publicationStatus\":true}",
                    "{\"title\":\"x\",\"title\":\"y\"}");
            for (String body : invalid) {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                for (UUID absent : List.of(UUID.randomUUID(), foreignText.id())) {
                    assertEquals(404, materialHttp("PUT", path + absent + suffix, owner, "application/json", bytes).statusCode(), body);
                }
                assertEquals(400, materialHttp("PUT", path + text.id() + suffix, owner, "application/json", bytes).statusCode());
                assertEquals(400, materialHttp("PUT", path + file.id() + suffix, owner, "application/json", bytes).statusCode());
                assertEquals(403, materialHttp("PUT", path + text.id() + suffix, learner, "application/json", bytes).statusCode());
                assertEquals(403, materialHttp("PUT", path + text.id() + suffix, other, "application/json", bytes).statusCode());
            }
            assertEquals("Original", materialRows.findMaterial(f.lesson, text.id()).orElseThrow().title());
            assertEquals("hidden", materialRows.findMaterial(f.lesson, text.id()).orElseThrow().publicationStatus().getStatus());
            assertEquals("  code\n", materialRows.findMaterial(f.lesson, text.id()).orElseThrow().textContent());
            // Same-Course but different Lesson is also a descendant mismatch.
            materialJdbc.update("UPDATE lessons SET chapter_id=?, position=2 WHERE id=?", f.chapter, foreign.lesson);
            try {
                assertEquals(404, materialHttp("PUT", path + foreignText.id() + suffix, owner, "application/json", "{}".getBytes(StandardCharsets.UTF_8)).statusCode());
            } finally {
                materialJdbc.update("UPDATE lessons SET chapter_id=?, position=1 WHERE id=?", foreign.chapter, foreign.lesson);
            }
            materialJdbc.update("UPDATE courses SET publication_status='hidden' WHERE id=?", f.course);
            assertEquals(404, materialHttp("PUT", path + text.id() + suffix, other, "application/json", "{}".getBytes(StandardCharsets.UTF_8)).statusCode());
            String valid = suffix.isEmpty() ? "{\"title\":\"Edited\",\"materialType\":\"text\",\"textContent\":\" body \"}"
                    : "{\"publicationStatus\":\"published\"}";
            assertEquals(200, materialHttp("PUT", path + text.id() + suffix, owner, "application/json", valid.getBytes(StandardCharsets.UTF_8)).statusCode());
        }
    }

    private static byte[] materialMultipart(boolean title) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        if (title) body.write("--x\r\nContent-Disposition: form-data; name=\"title\"\r\n\r\nSlides\r\n".getBytes(StandardCharsets.UTF_8));
        body.write("--x\r\nContent-Disposition: form-data; name=\"file\"; filename=\"Bài.pdf\"\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        body.write(PDF_A);
        body.write("\r\n--x--\r\n".getBytes(StandardCharsets.US_ASCII));
        return body.toByteArray();
    }

    @Test void co09Ir01AllEncodedBindingsAndUnreadBodyTransport() throws Exception {
        try (var f = new MaterialFixture()) {
            String owner = f.token(f.owner), learner = f.token(f.learner);
            String root = f.path().replace("/api/instructor/courses/", "/%61pi/%69nstructor/%63ourses/")
                    .replace("/lessons/", "/%6cessons/").replace("file-materials", "%66ile%2Dmaterials");
            var created = materialHttp("POST", root, owner, "multipart/form-data; boundary=x", materialMultipart(true));
            assertEquals(201, created.statusCode());
            var json = new tools.jackson.databind.json.JsonMapper();
            UUID id = UUID.fromString(json.readTree(created.body()).get("id").asString());
            String selected = root + "/" + id;
            assertEquals(200, materialHttp("PUT", selected, owner, "application/json", "{\"title\":\"Bài học\"}".getBytes(StandardCharsets.UTF_8)).statusCode());
            assertEquals(200, materialHttp("PUT", selected + "/%66ile", owner, "multipart/form-data; boundary=x", materialMultipart(false)).statusCode());
            assertEquals(200, materialHttp("PUT", selected + "/%70ublication", owner, "application/json", "{\"publicationStatus\":\"published\"}".getBytes(StandardCharsets.UTF_8)).statusCode());
            String download = f.download(id).replace("/api/courses/", "/%61pi/%63ourses/")
                    .replace("/lessons/", "/%6cessons/").replace("/materials/", "/%6daterials/").replace("/file", "/%66ile");
            var downloaded = materialHttp("GET", download, learner, null, new byte[0]);
            assertEquals(200, downloaded.statusCode());
            assertArrayEquals(PDF_A, downloaded.body());
            String relational = root.replace("%66ile%2Dmaterials", "%6daterials");
            byte[] text = "{\"title\":\"Text\",\"materialType\":\"text\",\"textContent\":\"  code\\n\"}".getBytes(StandardCharsets.UTF_8);
            var textCreated = materialHttp("POST", relational, owner, "application/json", text);
            assertEquals(201, textCreated.statusCode());
            UUID textId = UUID.fromString(json.readTree(textCreated.body()).get("id").asString());
            String textPath = relational + "/" + textId;
            assertEquals(200, materialHttp("PUT", textPath, owner, "application/json", text).statusCode());
            assertEquals(200, materialHttp("PUT", textPath + "/%70ublication", owner, "application/json", "{\"publicationStatus\":\"published\"}".getBytes(StandardCharsets.UTF_8)).statusCode());
            // Every JSON material route, including encoded static ancestors/suffixes, rejects media before role/body.
            for (String target : List.of(relational, textPath, textPath + "/%70ublication", selected, selected + "/%70ublication")) {
                assertEquals(415, materialHttp(target.equals(relational) ? "POST" : "PUT", target, learner,
                        "application/x-www-form-urlencoded", "x=%ZZ".getBytes(StandardCharsets.US_ASCII)).statusCode(), target);
            }
            // Deliberately send NO body: an attempted form/part read would block instead of replying.
            for (String target : List.of(root, selected + "/%66ile")) {
                try (var socket = new java.net.Socket("127.0.0.1", materialPort)) {
                    socket.setSoTimeout(5000);
                    String request = (target.equals(root) ? "POST " : "PUT ") + target
                            + " HTTP/1.1\r\nHost: localhost\r\nAuthorization: Bearer " + learner
                            + "\r\nContent-Type: multipart/form-data; boundary=x\r\nContent-Length: 100000\r\n\r\n";
                    socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                    var reader = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                    assertTrue(reader.readLine().startsWith("HTTP/1.1 403"));
                    List<String> headers = new ArrayList<>();
                    String line;
                    while ((line = reader.readLine()) != null && !line.isEmpty()) headers.add(line.toLowerCase(java.util.Locale.ROOT));
                    assertTrue(headers.contains("connection: close"));
                }
            }
            assertTrue(recordedMaterialEvents.stream().noneMatch(event -> event.getClass().getName().endsWith("ContentPublishedEvent")));
        }
    }

    @ParameterizedTest
    @CsvSource({"%2566ile-materials,401", "file%2fmaterials,400", "file-materials;extra=x,401", "file%5cmaterials,400"})
    void co09Ir01FirewallRejectionIsPreserved(String segment, int expected) throws Exception {
        try (var f = new MaterialFixture()) {
            String path = f.path().replace("file-materials", segment) + "/" + UUID.randomUUID() + "/file";
            assertEquals(expected, materialHttp("PUT", path, f.token(f.owner), "application/json",
                    "{}".getBytes(StandardCharsets.UTF_8)).statusCode(), segment);
        }
    }

    @Test void co09Ir01UnrelatedFormProcessingAndDefaultDenyArePreserved() throws Exception {
        try (var f = new MaterialFixture()) {
            String owner = f.token(f.owner);
            for (String target : List.of(f.path() + "/" + UUID.randomUUID() + "/unknown", f.path() + "/" + UUID.randomUUID() + "/file/extra")) {
                assertEquals(403, materialHttp("PUT", target, owner, "application/json", "{}".getBytes(StandardCharsets.UTF_8)).statusCode());
            }
            assertEquals(403, materialHttp("DELETE", f.path(), owner, "application/json", "{}".getBytes(StandardCharsets.UTF_8)).statusCode());
            // Exercise the actual configured filter on an unrelated form; the chain receives parsed parameters.
            var request = new org.springframework.mock.web.MockHttpServletRequest("PUT", "/unrelated-form");
            request.setContentType("application/x-www-form-urlencoded");
            request.setContent("note=one%2Btwo&space=a+b".getBytes(StandardCharsets.US_ASCII));
            var called = new java.util.concurrent.atomic.AtomicBoolean();
            materialFormFilter.doFilter(request, new org.springframework.mock.web.MockHttpServletResponse(), (parsed, response) -> {
                called.set(true);
                assertEquals("one+two", parsed.getParameter("note"));
                assertEquals("a b", parsed.getParameter("space"));
            });
            assertTrue(called.get());
        }
    }

    @Test void co09Ir02GuardRechecksAdmissionAfterPreliminaryRead() {
        try (var f = new MaterialFixture()) {
            var text = materials.createRelationalMaterial(f.course, f.lesson,
                    new com.codemastery.modules.course.api.dto.CourseStructureRequests.MaterialWrite(
                            "Original", com.codemastery.modules.course.domain.model.MaterialType.TEXT, "old", null));
            materials.requireOwnedRelational(f.course, f.lesson, text.id());
            materialJdbc.update("DELETE FROM learning_materials WHERE id=?", text.id());
            var command = new com.codemastery.modules.course.api.dto.CourseStructureRequests.MaterialWrite(
                    "Edited", com.codemastery.modules.course.domain.model.MaterialType.TEXT, "new", null);
            assertThrows(com.codemastery.common.exception.ResourceNotFoundException.class,
                    () -> materials.updateRelationalMaterial(f.course, f.lesson, text.id(), command));
            assertThrows(com.codemastery.common.exception.ResourceNotFoundException.class,
                    () -> materials.setRelationalMaterialPublication(f.course, f.lesson, text.id(), com.codemastery.modules.course.domain.model.PublishStatus.PUBLISHED));
            assertTrue(materialRows.findMaterial(f.lesson, text.id()).isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"create", "update", "publication", "file-title", "file-publication"})
    void co09Ir03StrictUtf8BytesAndUnicodeOnRealHttp(String operation) throws Exception {
        try (var f = new MaterialFixture()) {
            String owner = f.token(f.owner), learner = f.token(f.learner);
            var file = createPdf(f);
            var text = materials.createRelationalMaterial(f.course, f.lesson,
                    new com.codemastery.modules.course.api.dto.CourseStructureRequests.MaterialWrite(
                            "Original", com.codemastery.modules.course.domain.model.MaterialType.TEXT, "  code\n", null));
            String relational = f.path().replace("file-materials", "materials");
            String path = switch (operation) {
                case "create" -> relational;
                case "update" -> relational + "/" + text.id();
                case "publication" -> relational + "/" + text.id() + "/publication";
                case "file-title" -> f.path() + "/" + file.id();
                default -> f.path() + "/" + file.id() + "/publication";
            };
            String method = operation.equals("create") ? "POST" : "PUT";
            String template = operation.endsWith("publication") ? "{\"publicationStatus\":\"%s\"}"
                    : operation.equals("file-title") ? "{\"title\":\"%s\"}"
                    : "{\"title\":\"%s\",\"materialType\":\"text\",\"textContent\":\"  code\\n\"}";
            var before = materialJdbc.queryForList("SELECT * FROM learning_materials WHERE lesson_id=? ORDER BY id", f.lesson);
            for (byte[] malformed : List.of(new byte[]{(byte) 0xff}, new byte[]{(byte) 0xc3},
                    new byte[]{(byte) 0xc0, (byte) 0xaf}, new byte[]{(byte) 0xed, (byte) 0xa0, (byte) 0x80})) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                String marked = template.formatted("MARK");
                bytes.write(marked.substring(0, marked.indexOf("MARK")).getBytes(StandardCharsets.UTF_8));
                bytes.write(malformed);
                bytes.write(marked.substring(marked.indexOf("MARK") + 4).getBytes(StandardCharsets.UTF_8));
                assertEquals(400, materialHttp(method, path, owner, "application/json", bytes.toByteArray()).statusCode(), operation);
                assertEquals(before, materialJdbc.queryForList("SELECT * FROM learning_materials WHERE lesson_id=? ORDER BY id", f.lesson));
                assertEquals(403, materialHttp(method, path, learner, "application/json", bytes.toByteArray()).statusCode());
                if (!operation.equals("create")) {
                    UUID selected = operation.startsWith("file-") ? file.id() : text.id();
                    assertEquals(404, materialHttp(method, path.replace(selected.toString(), UUID.randomUUID().toString()), owner,
                            "application/json", bytes.toByteArray()).statusCode());
                }
            }
            for (String invalid : List.of("", "{}", "{", "null", "[]")) {
                assertEquals(400, materialHttp(method, path, owner, "application/json", invalid.getBytes(StandardCharsets.UTF_8)).statusCode());
                assertEquals(before, materialJdbc.queryForList("SELECT * FROM learning_materials WHERE lesson_id=? ORDER BY id", f.lesson));
            }
            for (String unicode : List.of("Bài học tiếng Việt", "学习 😀", "\uFFFD")) {
                String value = operation.endsWith("publication") ? "published" : unicode;
                var response = materialHttp(method, path, owner, "application/json", template.formatted(value).getBytes(StandardCharsets.UTF_8));
                assertEquals(operation.equals("create") ? 201 : 200, response.statusCode());
                var json = new tools.jackson.databind.json.JsonMapper().readTree(response.body());
                UUID id = UUID.fromString(json.get("id").asString());
                var persisted = materialRows.findMaterial(f.lesson, id).orElseThrow();
                if (!operation.endsWith("publication")) assertEquals(unicode, persisted.title());
                if (!operation.startsWith("file-")) assertEquals("  code\n", persisted.textContent());
            }
        }
    }

    @Test void co09InvalidPartValidationClosesInputsBeforeAnyStorage() {
        try(var f=new MaterialFixture()) {
            var observed=new ObservedStorage();var service=consumer(observed,materialRows);
            for(String name:List.of("x.zip","../x.pdf","x\n.pdf","中".repeat(84)+".pdf","x".repeat(252)+".pdf")) {
                var closed=new java.util.concurrent.atomic.AtomicBoolean();
                var input=new ByteArrayInputStream(PDF_A){public void close()throws IOException{closed.set(true);super.close();}};
                var rejected=assertThrows(com.codemastery.common.exception.BusinessRuleException.class,()->service.createFileMaterial(f.course,f.lesson,()->new com.codemastery.modules.course.application.service.LearningMaterialService.FileMaterialUpload("T",new com.codemastery.modules.course.application.service.LearningMaterialService.FilePart(name,PDF_A.length,input))));
                assertEquals(400,rejected.getStatus().value());assertTrue(closed.get());
            }
            for(byte[] bytes:List.of(new byte[0],"wrong".getBytes(StandardCharsets.US_ASCII))) {
                assertEquals(400,assertThrows(com.codemastery.common.exception.BusinessRuleException.class,()->service.createFileMaterial(f.course,f.lesson,()->new com.codemastery.modules.course.application.service.LearningMaterialService.FileMaterialUpload("T",pdf("x.pdf",bytes)))).getStatus().value());
            }
            assertEquals(413,assertThrows(org.springframework.web.multipart.MaxUploadSizeExceededException.class,()->service.createFileMaterial(f.course,f.lesson,()->new com.codemastery.modules.course.application.service.LearningMaterialService.FileMaterialUpload("T",new com.codemastery.modules.course.application.service.LearningMaterialService.FilePart("x.pdf",1025,new ByteArrayInputStream(PDF_A))))).getStatusCode().value());
            assertTrue(observed.stored.isEmpty());
        }
    }

    @Test void co09PausedDownloadLeavesTheOnlyPoolConnectionAvailable() throws Exception {
        try(var f=new MaterialFixture()) {
            var file=createPdf(f);
            var snapshotRows=mock(com.codemastery.modules.course.domain.repository.CourseContentRepository.class,org.mockito.AdditionalAnswers.delegatesTo(materialRows));
            doAnswer(call->{
                assertEquals("repeatable read",materialJdbc.queryForObject("SHOW transaction_isolation",String.class));
                assertEquals("on",materialJdbc.queryForObject("SHOW transaction_read_only",String.class));
                return materialRows.findMaterial(call.getArgument(0),call.getArgument(1));
            }).when(snapshotRows).findMaterial(any(),any());
            javax.sql.DataSource delegate=((org.springframework.jdbc.datasource.DelegatingDataSource)materialJdbc.getDataSource()).getTargetDataSource();
            var pool=delegate.unwrap(com.zaxxer.hikari.HikariDataSource.class);
            int maximum=pool.getMaximumPoolSize();long timeout=pool.getConnectionTimeout();
            CountDownLatch paused=new CountDownLatch(1),release=new CountDownLatch(1);
            pool.setMaximumPoolSize(1);pool.setConnectionTimeout(2000);pool.getHikariPoolMXBean().softEvictConnections();
            FileStorage pausedStorage=new FileStorage(){
                public void store(String key,InputStream in,long length,String type){storage.store(key,in,length,type);}
                public void delete(String key){storage.delete(key);}
                public StoredObject open(String key){
                    assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
                    assertEquals(0,BORROWED.get());
                    var opened=storage.open(key);
                    return new StoredObject(opened.contentLength(),opened.mediaType(),new java.io.FilterInputStream(opened.content()){
                        boolean first=true;
                        @Override public int read(byte[] bytes,int off,int length)throws IOException{
                            if(first){first=false;paused.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new IOException("Pause timed out");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException(interrupted);}}
                            return super.read(bytes,off,length);
                        }
                    });
                }
            };
            try(var workers=Executors.newFixedThreadPool(2)){
                var streaming=workers.submit(()->{
                    signIn(f.owner,com.codemastery.modules.auth.domain.model.Role.INSTRUCTOR);
                    try(var download=consumer(pausedStorage,snapshotRows).downloadMaterialFile(f.course,f.lesson,file.id())){
                        ByteArrayOutputStream response=new ByteArrayOutputStream();download.content().transferTo(response);return response.toByteArray();
                    }finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}
                });
                try{
                    assertTrue(paused.await(5,TimeUnit.SECONDS));
                    var another=workers.submit(()->materialJdbc.queryForObject("SELECT 1",Integer.class));
                    assertEquals(1,another.get(3,TimeUnit.SECONDS));
                }finally{release.countDown();}
                assertArrayEquals(PDF_A,streaming.get(5,TimeUnit.SECONDS));
            }finally{release.countDown();pool.setMaximumPoolSize(maximum);pool.setConnectionTimeout(timeout);}
        }
    }

    // AS09 producer/consumer qualification under the same approved PostgreSQL/MinIO foundation.
    @Autowired com.codemastery.modules.assessment.application.service.AssignmentService assignmentService;
    @Autowired com.codemastery.modules.assessment.application.service.SubmissionService submissionService;
    @Autowired com.codemastery.modules.assessment.domain.repository.AssignmentRepository assignmentRows;
    @Autowired com.codemastery.modules.auth.application.service.UserAccountService assignmentIdentities;
    @Autowired com.codemastery.modules.course.application.service.EnrollmentService assignmentEnrollments;

    final class AssignmentFixture implements AutoCloseable {
        final MaterialFixture base = new MaterialFixture();
        final java.time.Instant now = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        final UUID assignment;
        AssignmentFixture() {
            assignment = assignmentService.create(base.course, new com.codemastery.modules.assessment.application.service.AssignmentService.AssignmentInput(
                    "AS09", " Verbatim instructions\n", now.plusSeconds(3600), base.lesson)).id();
            assignmentService.setPublication(base.course, assignment, "published");
        }
        String path() { return "/api/courses/" + base.course + "/assignments/" + assignment; }
        String owned() { return "/api/courses/" + base.course + "/owned-assignments/" + assignment; }
        void learner() { signIn(base.learner, com.codemastery.modules.auth.domain.model.Role.LEARNER); }
        void owner() { signIn(base.owner, com.codemastery.modules.auth.domain.model.Role.INSTRUCTOR); }
        String key() { return materialJdbc.queryForObject("SELECT object_key FROM submissions WHERE assignment_id=?", String.class, assignment); }
        UUID submission() { return materialJdbc.queryForObject("SELECT id FROM submissions WHERE assignment_id=?", UUID.class, assignment); }
        java.time.Instant submittedAt() { return materialJdbc.queryForObject("SELECT submitted_at FROM submissions WHERE assignment_id=?", (rs, n) -> rs.getObject(1, java.time.OffsetDateTime.class).toInstant(), assignment); }
        public void close() {
            RUNTIME_ROLE.remove(); COMMIT_FAULT.remove(); COMMITTED_GRADES.clear();
            for (String key : materialJdbc.queryForList("SELECT object_key FROM submissions WHERE assignment_id=?", String.class, assignment)) storage.delete(key);
            materialJdbc.update("DELETE FROM submissions WHERE assignment_id=?", assignment);
            materialJdbc.update("DELETE FROM assignments WHERE course_id=?", base.course);
            base.close();
        }
    }
    static com.codemastery.modules.assessment.application.service.SubmissionService.FilePart assignmentPdf(String name, byte[] bytes) {
        return new com.codemastery.modules.assessment.application.service.SubmissionService.FilePart(name, bytes.length, new ByteArrayInputStream(bytes));
    }
    com.codemastery.modules.assessment.application.service.SubmissionService assignmentConsumer(FileStorage adapter, java.time.Clock clock) {
        return new com.codemastery.modules.assessment.application.service.SubmissionService(assignmentRows, materialAccess,
                assignmentIdentities, assignmentEnrollments, adapter, materialPersistence, applicationPublisher,
                materialTransactions, 1024, clock);
    }
    @Autowired org.springframework.context.ApplicationEventPublisher applicationPublisher;
    com.codemastery.modules.assessment.application.service.SubmissionService.GradeInput grade(java.time.Instant submittedAt) {
        return new com.codemastery.modules.assessment.application.service.SubmissionService.GradeInput(new java.math.BigDecimal("99.99"), " Verbatim feedback\n", submittedAt);
    }
    java.time.Clock tickingClock(java.time.Instant start) {
        var micros = new java.util.concurrent.atomic.AtomicLong();
        return new java.time.Clock() {
            public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
            public java.time.Clock withZone(java.time.ZoneId zone) { return this; }
            public java.time.Instant instant() { return start.plusNanos(micros.getAndIncrement() * 1000); }
        };
    }

    @Test void as09RealPersistenceNamesGradingAndProducerEvents() throws Exception {
        try (var f = new AssignmentFixture()) {
            var observed = new ObservedStorage(); var service = assignmentConsumer(observed, tickingClock(f.now));
            long mastery = materialJdbc.queryForObject("SELECT count(*) FROM learner_skill_masteries", Long.class);
            long progress = materialJdbc.queryForObject("SELECT count(*) FROM lesson_progress", Long.class);
            f.learner();
            var first = service.submitOrReplace(f.base.course, f.assignment, () -> assignmentPdf("Bài.PDF", PDF_A));
            assertTrue(first.created()); assertNull(first.submission().gradePercentage());
            assertEquals(f.now.plusNanos(1000), first.submission().submittedAt());
            UUID id = f.submission(); String old = f.key();
            assertEquals("Bài.PDF", assignmentService.readForLearner(f.base.course, f.assignment).submission().originalFilename());
            try (var file = service.downloadOwnSubmission(f.base.course, f.assignment)) {
                assertArrayEquals(PDF_A, file.content().readAllBytes());
            }
            var second = service.submitOrReplace(f.base.course, f.assignment, () -> assignmentPdf("new.pdf", PDF_B));
            assertFalse(second.created()); assertEquals(id, f.submission());
            assertTrue(second.submission().submittedAt().isAfter(first.submission().submittedAt()));
            assertThrows(ObjectNotFoundException.class, () -> storage.open(old));
            f.owner();
            assertEquals("Learner", service.listSubmissionsForOwner(f.base.course, f.assignment, 0, 20).content().getFirst().learnerDisplayName());
            // Stored public names survive LEFT, locked accounts and later role changes.
            materialJdbc.update("UPDATE enrollments SET status='left' WHERE course_id=?", f.base.course);
            materialJdbc.update("UPDATE users SET role='instructor',account_status='locked',display_name='Historical name' WHERE id=?", f.base.learner);
            assertEquals("Historical name", service.listSubmissionsForOwner(f.base.course, f.assignment, 0, 1).content().getFirst().learnerDisplayName());
            assertEquals(java.util.Map.of(), assignmentEnrollments.learnerDisplayNames(f.base.course, java.util.Set.of(f.base.owner)));
            materialJdbc.update("UPDATE enrollments SET status='active' WHERE course_id=?", f.base.course);
            materialJdbc.update("UPDATE users SET role='learner',account_status='active' WHERE id=?", f.base.learner);
            var graded = service.gradeSubmission(f.base.course, f.assignment, id, grade(second.submission().submittedAt()));
            assertEquals(new java.math.BigDecimal("99.99"), graded.gradePercentage());
            assertEquals(" Verbatim feedback\n", graded.feedback());
            service.gradeSubmission(f.base.course, f.assignment, id, grade(second.submission().submittedAt()));
            var events = COMMITTED_GRADES.stream().filter(e -> e.assignmentId().equals(f.assignment)).toList();
            assertEquals(2, events.size()); assertFalse(events.get(0).regrade()); assertTrue(events.get(1).regrade());
            assertEquals(f.base.lesson, events.getFirst().lessonId()); assertTrue(events.getFirst().assignmentPublished());
            assertEquals(f.base.learner, events.getFirst().learnerId());
            int committed = COMMITTED_GRADES.size();
            assertThrows(IllegalStateException.class, () -> new org.springframework.transaction.support.TransactionTemplate(materialTransactions).executeWithoutResult(tx -> {
                service.gradeSubmission(f.base.course, f.assignment, id, grade(second.submission().submittedAt()));
                throw new IllegalStateException("Synthetic rollback");
            }));
            assertEquals(committed, COMMITTED_GRADES.size());
            f.learner(); assertTrue(assignmentService.readForLearner(f.base.course, f.assignment).submission().graded());
            assertThrows(com.codemastery.common.exception.BusinessRuleException.class, () -> service.submitOrReplace(f.base.course, f.assignment, () -> assignmentPdf("new.pdf", PDF_A)));
            assertEquals(mastery, materialJdbc.queryForObject("SELECT count(*) FROM learner_skill_masteries", Long.class));
            assertEquals(progress, materialJdbc.queryForObject("SELECT count(*) FROM lesson_progress", Long.class));
            assertTrue(recordedMaterialEvents.stream().noneMatch(e -> e.getClass().getSimpleName().equals("QuizSubmittedEvent")));
        }
    }

    @Test void as09RealCommitFaultsCompensationAndOuterTransaction() throws Exception {
        try (var f = new AssignmentFixture()) {
            f.learner(); var observed = new ObservedStorage(); var service = assignmentConsumer(observed, tickingClock(f.now));
            service.submitOrReplace(f.base.course, f.assignment, () -> assignmentPdf("old.pdf", PDF_A));
            String old = f.key();
            // Confirmed not committed: a clock that did not advance, and a deadline crossed during upload.
            var fixed = assignmentConsumer(observed, java.time.Clock.fixed(f.submittedAt(), java.time.ZoneOffset.UTC));
            assertThrows(IllegalStateException.class, () -> fixed.submitOrReplace(f.base.course, f.assignment, () -> assignmentPdf("same.pdf", PDF_B)));
            String rolledBack = observed.stored.getLast(); assertTrue(observed.deleted.contains(rolledBack)); assertEquals(old, f.key());
            assertThrows(ObjectNotFoundException.class, () -> storage.open(rolledBack));
            for (String fault : List.of("before", "after")) {
                observed.commitFault = fault; int deleted = observed.deleted.size(); String previous = f.key();
                assertThrows(RuntimeException.class, () -> service.submitOrReplace(f.base.course, f.assignment, () -> assignmentPdf("unknown.pdf", PDF_B)));
                String fresh = observed.stored.getLast(); assertEquals(deleted, observed.deleted.size());
                assertEquals(fault.equals("before") ? previous : fresh, f.key());
                try (var a = storage.open(previous); var b = storage.open(fresh)) { assertTrue(a.contentLength() > 0 && b.contentLength() > 0); }
                observed.commitFault = null;
            }
            int stored = observed.stored.size();
            new org.springframework.transaction.support.TransactionTemplate(materialTransactions).executeWithoutResult(tx ->
                assertThrows(IllegalStateException.class, () -> service.submitOrReplace(f.base.course, f.assignment, () -> { throw new AssertionError("No parts"); })));
            assertEquals(stored, observed.stored.size());
            observed.failStore = true;
            assertThrows(FileStorageException.class, () -> service.submitOrReplace(f.base.course, f.assignment, () -> assignmentPdf("failed.pdf", PDF_A)));
            assertTrue(observed.deleted.contains(observed.stored.getLast()));
            for (String key : observed.stored) if (!key.equals(f.key())) storage.delete(key);
        }
    }

    @Test void as09ConcurrentFirstSubmissionsAndStaleGradeAfterReplacement() throws Exception {
        try (var f = new AssignmentFixture(); var workers = Executors.newFixedThreadPool(2)) {
            CountDownLatch bothStored = new CountDownLatch(2);
            var observed = new ObservedStorage() {
                @Override public void store(String key, InputStream in, long size, String media) {
                    assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
                    this.stored.add(key); storage.store(key, in, size, media); bothStored.countDown();
                    try { assertTrue(bothStored.await(8, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new IllegalStateException(e); }
                }
            };
            var service = assignmentConsumer(observed, tickingClock(f.now));
            java.util.concurrent.Callable<com.codemastery.modules.assessment.application.service.SubmissionService.SubmissionOutcome> submit = () -> {
                f.learner(); try { return service.submitOrReplace(f.base.course, f.assignment, () -> assignmentPdf("concurrent.pdf", PDF_A)); }
                finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
            };
            var a = workers.submit(submit); var b = workers.submit(submit);
            var one = a.get(12, TimeUnit.SECONDS); var two = b.get(12, TimeUnit.SECONDS);
            assertNotEquals(one.created(), two.created());
            assertEquals(1, materialJdbc.queryForObject("SELECT count(*) FROM submissions WHERE assignment_id=?", Integer.class, f.assignment));
            java.time.Instant stale = f.submittedAt();
            f.learner(); service.submitOrReplace(f.base.course, f.assignment, () -> assignmentPdf("replacement.pdf", PDF_B));
            f.owner();
            var conflict = assertThrows(com.codemastery.common.exception.BusinessRuleException.class,
                    () -> service.gradeSubmission(f.base.course, f.assignment, f.submission(), grade(stale)));
            assertEquals(org.springframework.http.HttpStatus.CONFLICT, conflict.getStatus());
            assertTrue(COMMITTED_GRADES.stream().noneMatch(e -> e.assignmentId().equals(f.assignment)));
        }
    }

    @Test void as09DownloadReplacementRetryAndConnectionRelease() throws Exception {
        try (var f = new AssignmentFixture()) {
            var observed = new ObservedStorage(); var service = assignmentConsumer(observed, tickingClock(f.now));
            f.learner(); service.submitOrReplace(f.base.course, f.assignment, () -> assignmentPdf("old.pdf", PDF_A));
            UUID id = f.submission(); f.owner();
            observed.beforeOpen = () -> {
                f.learner(); service.submitOrReplace(f.base.course, f.assignment, () -> assignmentPdf("new.pdf", PDF_B)); f.owner();
            };
            try (var file = service.downloadSubmissionForOwner(f.base.course, f.assignment, id)) {
                assertEquals("new.pdf", file.originalFilename()); assertArrayEquals(PDF_B, file.content().readAllBytes());
                assertEquals(0, BORROWED.get()); assertEquals(1, materialJdbc.queryForObject("SELECT 1", Integer.class));
            }
            assertEquals(2, observed.opens);
            observed.providerFailure = true;
            assertThrows(FileStorageException.class, () -> service.downloadSubmissionForOwner(f.base.course, f.assignment, id));
            assertEquals(3, observed.opens); observed.providerFailure = false;
            storage.delete(f.key());
            assertThrows(IllegalStateException.class, () -> service.downloadSubmissionForOwner(f.base.course, f.assignment, id));
            assertEquals(4, observed.opens);
        }
    }

    @Test void as09RealTomcatAdmissionRawMultipartAndGradeContracts() throws Exception {
        try (var f = new AssignmentFixture(); var foreign = new AssignmentFixture()) {
            String owner = f.base.token(f.base.owner), learner = f.base.token(f.base.learner), other = foreign.base.token(foreign.base.owner);
            String upload = f.path() + "/submission";
            byte[] body = materialMultipart(false), malformed = "x=%ZZ".getBytes(StandardCharsets.US_ASCII);
            assertEquals(401, materialHttp("PUT", upload, null, "application/x-www-form-urlencoded", malformed).statusCode());
            assertEquals(415, materialHttp("PUT", upload, owner, "application/x-www-form-urlencoded", malformed).statusCode());
            assertEquals(403, materialHttp("PUT", upload, owner, "multipart/form-data; boundary=x", malformed).statusCode());
            assertEquals(400, materialHttp("PUT", upload, learner, "multipart/form-data; boundary=x", malformed).statusCode());
            assertEquals(413, materialHttp("PUT", upload, learner, "multipart/form-data; boundary=x", new byte[2048]).statusCode());
            assertEquals(403, materialHttp("PUT", upload, owner, "multipart/form-data; boundary=x", new byte[2048]).statusCode());
            var created = materialHttp("PUT", upload, learner, "multipart/form-data; boundary=x", body);
            assertEquals(201, created.statusCode(), new String(created.body(), StandardCharsets.UTF_8));
            var json = new tools.jackson.databind.json.JsonMapper();
            String version = json.readTree(created.body()).get("submittedAt").asString();
            assertEquals(200, materialHttp("GET", f.path(), learner, null, new byte[0]).statusCode());
            assertEquals(200, materialHttp("GET", "/api/courses/" + f.base.course + "/assignments", learner, null, new byte[0]).statusCode());
            assertEquals(403, materialHttp("GET", f.path(), owner, null, new byte[0]).statusCode());
            assertEquals(403, materialHttp("GET", f.owned() + "/submissions", other, null, new byte[0]).statusCode());
            UUID id = f.submission();
            assertEquals(200, materialHttp("GET", f.owned() + "/submissions", owner, null, new byte[0]).statusCode());
            assertEquals(404, materialHttp("PUT", f.owned() + "/submissions/" + UUID.randomUUID() + "/grade", owner, "application/json", "{}".getBytes(StandardCharsets.UTF_8)).statusCode());
            byte[] grade = ("{\"gradePercentage\":99.99,\"feedback\":\"Good\\nWork\",\"submittedAt\":\"" + version + "\"}").getBytes(StandardCharsets.UTF_8);
            String grading = f.owned() + "/submissions/" + id + "/grade";
            assertEquals(200, materialHttp("PUT", grading, owner, "application/json", grade).statusCode());
            assertEquals(409, materialHttp("PUT", upload, learner, "multipart/form-data; boundary=x", body).statusCode());
            assertEquals(400, materialHttp("PUT", upload, learner, "multipart/form-data; boundary=x", malformed).statusCode());
            for (String download : List.of(f.path() + "/submission/file", f.owned() + "/submissions/" + id + "/file")) {
                var response = materialHttp("GET", download, download.contains("owned-") ? owner : learner, null, new byte[0]);
                assertEquals(200, response.statusCode()); assertArrayEquals(PDF_A, response.body());
                assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElse(""));
                assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""));
                assertTrue(response.headers().firstValue("Content-Disposition").orElse("").contains("filename*=UTF-8''B%C3%A0i.pdf"));
                assertEquals(Integer.toString(PDF_A.length), response.headers().firstValue("Content-Length").orElse(""));
            }
            // Parameter binding on a GET must not parse hostile multipart before role admission.
            assertEquals(403, materialHttp("GET", f.owned() + "/submissions", learner, "multipart/form-data; boundary=x", malformed).statusCode());
            assertEquals(403, materialHttp("DELETE", upload, learner, null, new byte[0]).statusCode());
        }
    }


    @Test void as09RestrictedRuntimeConsumerUsesTheExecutableMatrix() throws Exception {
        try (var f = new AssignmentFixture()) {
            String role = "as09_" + UUID.randomUUID().toString().replace("-", "");
            materialJdbc.execute("CREATE ROLE " + role + " NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT");
            try {
                materialJdbc.execute("GRANT USAGE ON SCHEMA public TO " + role);
                String deployment = Files.readString(Path.of("..", "infrastructure", "vm", "deploy.sh"));
                String matrix = deployment.split("RUNTIME_PRIVILEGE_MATRIX='\\n", 2)[1].split("\\n'", 2)[0];
                for (String line : matrix.split("\\n")) {
                    String[] fields = line.trim().split("\\s+");
                    if (!fields[1].equals("-")) materialJdbc.execute("GRANT " + fields[1] + " ON public." + fields[0] + " TO " + role);
                    if (!fields[2].equals("-")) for (String columns : fields[2].split("\\+"))
                        materialJdbc.execute("GRANT " + columns + " ON public." + fields[0] + " TO " + role);
                }
                var observed = new ObservedStorage(); var service = assignmentConsumer(observed, tickingClock(f.now));
                RUNTIME_ROLE.set(role);
                assertEquals(role, materialJdbc.queryForObject("SELECT current_user", String.class));
                assertFalse(materialJdbc.queryForObject("SELECT has_table_privilege(current_user,'submissions','UPDATE')", Boolean.class));
                f.learner();
                service.submitOrReplace(f.base.course, f.assignment, () -> assignmentPdf("runtime.pdf", PDF_A));
                UUID id = f.submission();
                service.submitOrReplace(f.base.course, f.assignment, () -> assignmentPdf("runtime-new.pdf", PDF_B));
                assertEquals(1, assignmentService.listForLearner(f.base.course, 0, 20).totalElements());
                try (var download = service.downloadOwnSubmission(f.base.course, f.assignment)) { assertArrayEquals(PDF_B, download.content().readAllBytes()); }
                f.owner();
                assertEquals("Learner", service.listSubmissionsForOwner(f.base.course, f.assignment, 0, 20).content().getFirst().learnerDisplayName());
                service.gradeSubmission(f.base.course, f.assignment, id, grade(f.submittedAt()));
                try (var download = service.downloadSubmissionForOwner(f.base.course, f.assignment, id)) { assertArrayEquals(PDF_B, download.content().readAllBytes()); }
                var tx = new org.springframework.transaction.support.TransactionTemplate(materialTransactions);
                assertTrue(tx.execute(status -> assignmentService.lockNotificationState(f.assignment)).isPresent());
                assertEquals(1, assignmentService.findReminderCandidates(f.now, f.now.plusSeconds(7200), null, 20).stream()
                        .filter(row -> row.assignmentId().equals(f.assignment)).count());
                assertThrows(org.springframework.dao.DataAccessException.class, () -> materialJdbc.update("UPDATE submissions SET user_id=user_id WHERE false"));
                assertThrows(org.springframework.dao.DataAccessException.class, () -> materialJdbc.update("DELETE FROM submissions WHERE false"));
            } finally {
                RUNTIME_ROLE.remove();
                materialJdbc.execute("DROP OWNED BY " + role); materialJdbc.execute("DROP ROLE " + role);
            }
        }
    }

    @Test void as09NotificationProducerDiscoveryAndSharedLockAdmission() throws Exception {
        try (var f = new AssignmentFixture(); var workers = Executors.newFixedThreadPool(2)) {
            var deadline = f.now.plusSeconds(3600);
            // Discovery has an exclusive lower bound, inclusive upper bound and strict keyset cursor.
            assertTrue(assignmentService.findReminderCandidates(deadline, deadline.plusSeconds(1), null, 20).stream().noneMatch(c -> c.assignmentId().equals(f.assignment)));
            var discovered = assignmentService.findReminderCandidates(f.now, deadline, null, 20).stream().filter(c -> c.assignmentId().equals(f.assignment)).findFirst().orElseThrow();
            assertEquals(f.base.lesson, discovered.lessonId()); assertTrue(discovered.submittedLearnerIds().isEmpty());
            var cursor = new com.codemastery.modules.assessment.application.service.AssignmentService.ReminderCursor(deadline, f.assignment);
            assertTrue(assignmentService.findReminderCandidates(f.now, deadline, cursor, 20).stream().noneMatch(c -> c.assignmentId().equals(f.assignment)));
            assertThrowsExactly(IllegalStateException.class, () -> assignmentService.lockNotificationState(f.assignment));
            var readOnly = new org.springframework.transaction.support.TransactionTemplate(materialTransactions); readOnly.setReadOnly(true);
            assertThrowsExactly(IllegalStateException.class, () -> readOnly.execute(tx -> assignmentService.lockNotificationState(f.assignment)));
            CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1), attempting = new CountDownLatch(1);
            var admitted = workers.submit(() -> new org.springframework.transaction.support.TransactionTemplate(materialTransactions).execute(tx -> {
                var state = assignmentService.lockNotificationState(f.assignment).orElseThrow(); locked.countDown();
                try { assertTrue(release.await(8, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new IllegalStateException(e); }
                return state;
            }));
            assertTrue(locked.await(5, TimeUnit.SECONDS));
            var edit = workers.submit(() -> {
                f.owner(); attempting.countDown();
                try { return assignmentService.setPublication(f.base.course, f.assignment, "hidden"); }
                finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
            });
            try { assertTrue(attempting.await(5, TimeUnit.SECONDS)); assertFalse(edit.isDone()); }
            finally { release.countDown(); }
            assertEquals("AS09", admitted.get(5, TimeUnit.SECONDS).assignmentTitle()); edit.get(5, TimeUnit.SECONDS);
            assertTrue(new org.springframework.transaction.support.TransactionTemplate(materialTransactions).execute(tx -> assignmentService.lockNotificationState(f.assignment)).isEmpty());
        }
    }


    @Test void as09DeadlineBoundaryCleanupAndGradingRace() throws Exception {
        try (var f = new AssignmentFixture(); var workers = Executors.newFixedThreadPool(2)) {
            var deadline = f.now.plusSeconds(3600);
            // now = deadline - 1 microsecond succeeds; equality fails before storage.
            var observed = new ObservedStorage();
            var edge = assignmentConsumer(observed, java.time.Clock.fixed(deadline.minusNanos(1000), java.time.ZoneOffset.UTC));
            f.learner(); edge.submitOrReplace(f.base.course, f.assignment, () -> assignmentPdf("edge.pdf", PDF_A));
            java.time.Instant reviewed = f.submittedAt(); int stored = observed.stored.size();
            var late = assignmentConsumer(observed, java.time.Clock.fixed(deadline, java.time.ZoneOffset.UTC));
            assertEquals(org.springframework.http.HttpStatus.CONFLICT, assertThrows(com.codemastery.common.exception.BusinessRuleException.class,
                    () -> late.submitOrReplace(f.base.course, f.assignment, () -> assignmentPdf("late.pdf", PDF_A))).getStatus());
            assertEquals(stored, observed.stored.size());
            // Widen the deadline, then hold a real replacement's Assignment lock while grading starts.
            f.owner(); assignmentService.replace(f.base.course, f.assignment,
                    new com.codemastery.modules.assessment.application.service.AssignmentService.AssignmentInput("AS09", "Instructions", deadline.plusSeconds(3600), f.base.lesson));
            CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1), grading = new CountDownLatch(1);
            var pausedRows = org.mockito.Mockito.mock(com.codemastery.modules.assessment.domain.repository.AssignmentRepository.class,
                    org.mockito.AdditionalAnswers.delegatesTo(assignmentRows));
            org.mockito.Mockito.doAnswer(call -> {
                var row = assignmentRows.findInCourseForUpdate(f.assignment, f.base.course); locked.countDown();
                assertTrue(release.await(8, TimeUnit.SECONDS)); return row;
            }).when(pausedRows).findInCourseForUpdate(f.assignment, f.base.course);
            var replacementService = new com.codemastery.modules.assessment.application.service.SubmissionService(pausedRows, materialAccess,
                    assignmentIdentities, assignmentEnrollments, observed, materialPersistence, applicationPublisher, materialTransactions,
                    1024, java.time.Clock.fixed(deadline, java.time.ZoneOffset.UTC));
            var replacement = workers.submit(() -> {
                f.learner(); try { return replacementService.submitOrReplace(f.base.course, f.assignment, () -> assignmentPdf("replacement.pdf", PDF_B)); }
                finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
            });
            assertTrue(locked.await(5, TimeUnit.SECONDS)); UUID id = f.submission();
            var save = workers.submit(() -> {
                f.owner(); grading.countDown();
                try { return edge.gradeSubmission(f.base.course, f.assignment, id, grade(reviewed)); }
                finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
            });
            try {
                assertTrue(grading.await(5, TimeUnit.SECONDS));
                boolean waiting = false;
                for (int i = 0; i < 100 && !waiting; i++) {
                    try (var connection = java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                         var query = connection.createStatement();
                         var result = query.executeQuery("SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE wait_event_type='Lock' AND query ILIKE '%assignments%')")) {
                        result.next(); waiting = result.getBoolean(1);
                    }
                    if (!waiting) Thread.sleep(10);
                }
                assertTrue(waiting, "Grade must wait for the replacement's Assignment lock"); assertFalse(save.isDone());
            } finally { release.countDown(); }
            assertFalse(replacement.get(5, TimeUnit.SECONDS).created());
            var failure = assertThrows(java.util.concurrent.ExecutionException.class, () -> save.get(5, TimeUnit.SECONDS));
            assertEquals(org.springframework.http.HttpStatus.CONFLICT, ((com.codemastery.common.exception.BusinessRuleException)failure.getCause()).getStatus());
            assertNull(materialJdbc.queryForObject("SELECT graded_at FROM submissions WHERE id=?", java.sql.Timestamp.class, id));
        }
    }

    @Test void as09DeadlineChangedDuringUploadAndDeleteFailureRemainSafe() throws Exception {
        try (var f = new AssignmentFixture()) {
            var observed = new ObservedStorage() {
                @Override public void store(String key, InputStream in, long size, String media) {
                    super.store(key, in, size, media);
                    f.owner(); assignmentService.replace(f.base.course, f.assignment,
                            new com.codemastery.modules.assessment.application.service.AssignmentService.AssignmentInput("AS09", "Instructions", f.now, f.base.lesson));
                    f.learner();
                }
            };
            var service = assignmentConsumer(observed, tickingClock(f.now)); f.learner();
            assertEquals(org.springframework.http.HttpStatus.CONFLICT, assertThrows(com.codemastery.common.exception.BusinessRuleException.class,
                    () -> service.submitOrReplace(f.base.course, f.assignment, () -> assignmentPdf("crossed.pdf", PDF_A))).getStatus());
            assertThrows(ObjectNotFoundException.class, () -> storage.open(observed.stored.getFirst()));
            assertEquals(0, materialJdbc.queryForObject("SELECT count(*) FROM submissions WHERE assignment_id=?", Integer.class, f.assignment));
            f.owner(); assignmentService.replace(f.base.course, f.assignment,
                    new com.codemastery.modules.assessment.application.service.AssignmentService.AssignmentInput("AS09", "Instructions", f.now.plusSeconds(3600), f.base.lesson));
            var failedDelete = new ObservedStorage(); failedDelete.failDelete = true;
            var replace = assignmentConsumer(failedDelete, tickingClock(f.now)); f.learner();
            replace.submitOrReplace(f.base.course, f.assignment, () -> assignmentPdf("a.pdf", PDF_A));
            String orphan = f.key(); replace.submitOrReplace(f.base.course, f.assignment, () -> assignmentPdf("b.pdf", PDF_B));
            assertTrue(failedDelete.deleted.contains(orphan));
            try (var kept = storage.open(orphan); var current = replace.downloadOwnSubmission(f.base.course, f.assignment)) {
                assertArrayEquals(PDF_A, kept.content().readAllBytes()); assertArrayEquals(PDF_B, current.content().readAllBytes());
            }
            storage.delete(orphan);
        }
    }


    @Test void as09HttpVisibilityAndResourceConcealmentMatrix() throws Exception {
        try (var f = new AssignmentFixture(); var other = new AssignmentFixture()) {
            String learner = f.base.token(f.base.learner), owner = f.base.token(f.base.owner), stranger = other.base.token(other.base.learner);
            String upload = f.path() + "/submission";
            byte[] multipart = materialMultipart(false), broken = "broken".getBytes(StandardCharsets.US_ASCII);
            assertEquals(201, materialHttp("PUT", upload, learner, "multipart/form-data; boundary=x", multipart).statusCode());
            UUID id = f.submission(); String grading = f.owned() + "/submissions/" + id + "/grade";
            for (String path : List.of(f.path(), f.path() + "/submission/file", f.owned() + "/submissions", f.owned() + "/submissions/" + id + "/file")) {
                assertEquals(401, materialHttp("GET", path, null, null, new byte[0]).statusCode(), path);
                assertEquals(403, materialHttp("GET", path, path.contains("owned-") ? learner : owner, null, new byte[0]).statusCode(), path);
            }
            assertEquals(401, materialHttp("PUT", grading, null, "application/json", broken).statusCode());
            assertEquals(403, materialHttp("PUT", grading, learner, "application/json", broken).statusCode());
            assertEquals(415, materialHttp("PUT", grading, learner, "multipart/form-data; boundary=x", broken).statusCode());
            assertEquals(400, materialHttp("GET", f.owned() + "/submissions?size=0", learner, null, new byte[0]).statusCode());
            assertEquals(403, materialHttp("PUT", upload, stranger, "multipart/form-data; boundary=x", broken).statusCode());
            materialJdbc.update("UPDATE enrollments SET status='left' WHERE course_id=?", f.base.course);
            assertEquals(403, materialHttp("PUT", upload, learner, "multipart/form-data; boundary=x", broken).statusCode());
            assertEquals(403, materialHttp("GET", f.path() + "/submission/file", learner, null, new byte[0]).statusCode());
            materialJdbc.update("UPDATE enrollments SET status='completed' WHERE course_id=?", f.base.course);
            assertEquals(200, materialHttp("GET", f.path(), learner, null, new byte[0]).statusCode());
            for (String table : List.of("assignments", "lessons", "courses")) {
                UUID target = table.equals("assignments") ? f.assignment : table.equals("lessons") ? f.base.lesson : f.base.course;
                materialJdbc.update("UPDATE " + table + " SET publication_status='hidden' WHERE id=?", target);
                assertEquals(404, materialHttp("PUT", upload, learner, "multipart/form-data; boundary=x", broken).statusCode(), table);
                assertEquals(404, materialHttp("GET", f.path() + "/submission/file", learner, null, new byte[0]).statusCode(), table);
                assertEquals(200, materialHttp("GET", f.owned() + "/submissions/" + id + "/file", owner, null, new byte[0]).statusCode(), table);
                materialJdbc.update("UPDATE " + table + " SET publication_status='published' WHERE id=?", target);
            }
            assertEquals(404, materialHttp("PUT", upload.replace(f.assignment.toString(), other.assignment.toString()), learner, "multipart/form-data; boundary=x", broken).statusCode());
            assertEquals(404, materialHttp("GET", f.owned() + "/submissions/" + UUID.randomUUID() + "/file", owner, null, new byte[0]).statusCode());
            materialJdbc.update("UPDATE users SET role='admin' WHERE id=?", other.base.learner);
            String admin = other.base.token(other.base.learner);
            assertEquals(403, materialHttp("GET", f.path(), admin, null, new byte[0]).statusCode());
            assertEquals(403, materialHttp("GET", f.owned() + "/submissions", admin, null, new byte[0]).statusCode());
            // No bytes follow the headers: parsing a part before admission would block this request.
            String encoded = upload.replace("/api/courses/", "/%61pi/%63ourses/").replace("/assignments/", "/%61ssignments/").replace("/submission", "/%73ubmission");
            try (var socket = new java.net.Socket("127.0.0.1", materialPort)) {
                socket.setSoTimeout(5000);
                String request = "PUT " + encoded + " HTTP/1.1\r\nHost: localhost\r\nAuthorization: Bearer " + owner
                        + "\r\nContent-Type: multipart/form-data; boundary=x\r\nContent-Length: 100000\r\n\r\n";
                socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII)); socket.getOutputStream().flush();
                var reader = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                assertTrue(reader.readLine().startsWith("HTTP/1.1 403"));
                var headers = new ArrayList<String>(); String line;
                while ((line = reader.readLine()) != null && !line.isEmpty()) headers.add(line.toLowerCase(java.util.Locale.ROOT));
                assertTrue(headers.contains("connection: close"));
            }
        }
    }

    @Test void as09PausedDownloadReleasesTheOnlyPoolConnection() throws Exception {
        try (var f = new AssignmentFixture(); var workers = Executors.newFixedThreadPool(2)) {
            f.learner(); submissionService.submitOrReplace(f.base.course, f.assignment, () -> assignmentPdf("a.pdf", PDF_A));
            var source = ((org.springframework.jdbc.datasource.DelegatingDataSource)materialJdbc.getDataSource()).getTargetDataSource();
            var pool = source.unwrap(com.zaxxer.hikari.HikariDataSource.class);
            int maximum = pool.getMaximumPoolSize(); long timeout = pool.getConnectionTimeout();
            CountDownLatch copying = new CountDownLatch(1), release = new CountDownLatch(1);
            pool.setMaximumPoolSize(1); pool.setConnectionTimeout(1000); pool.getHikariPoolMXBean().softEvictConnections();
            FileStorage paused = new FileStorage() {
                public void store(String key, InputStream in, long size, String media) { throw new AssertionError(); }
                public void delete(String key) { throw new AssertionError(); }
                public StoredObject open(String key) {
                    assertEquals(0, BORROWED.get()); var object = storage.open(key);
                    return new StoredObject(object.contentLength(), object.mediaType(), new java.io.FilterInputStream(object.content()) {
                        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                            copying.countDown();
                            try { assertTrue(release.await(5, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new IOException(e); }
                            return super.read(bytes, offset, length);
                        }
                    });
                }
            };
            try {
                var streaming = workers.submit(() -> {
                    f.learner();
                    try (var file = assignmentConsumer(paused, java.time.Clock.systemUTC()).downloadOwnSubmission(f.base.course, f.assignment)) {
                        var response = new ByteArrayOutputStream(); file.content().transferTo(response); return response.toByteArray();
                    } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
                });
                assertTrue(copying.await(5, TimeUnit.SECONDS));
                var another = workers.submit(() -> materialJdbc.queryForObject("SELECT 1", Integer.class));
                assertEquals(1, another.get(3, TimeUnit.SECONDS)); release.countDown();
                assertArrayEquals(PDF_A, streaming.get(5, TimeUnit.SECONDS));
            } finally { release.countDown(); pool.setMaximumPoolSize(maximum); pool.setConnectionTimeout(timeout); }
        }
    }

    @Test void as09NotificationStateRefreshesPreloadedEntityAndHoldsCallerSharedLock() throws Exception {
        try (var f = new AssignmentFixture(); var workers = Executors.newSingleThreadExecutor()) {
            var tx = new org.springframework.transaction.support.TransactionTemplate(materialTransactions);
            tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
            tx.executeWithoutResult(status -> {
                String callerTransaction = materialJdbc.queryForObject("SELECT pg_current_xact_id()::text", String.class);
                assertEquals("AS09", assignmentRows.findInCourse(f.assignment, f.base.course).orElseThrow().title());
                try {
                    workers.submit(() -> {
                        f.owner();
                        assignmentService.replace(f.base.course, f.assignment, new com.codemastery.modules.assessment.application.service.AssignmentService.AssignmentInput("Current", "Instructions", f.now.plusSeconds(7200), null));
                        f.learner();
                        assignmentConsumer(storage, tickingClock(f.now)).submitOrReplace(f.base.course, f.assignment, () -> assignmentPdf("current.pdf", PDF_A));
                        org.springframework.security.core.context.SecurityContextHolder.clearContext();
                    }).get(8, TimeUnit.SECONDS);
                } catch (Exception e) { throw new IllegalStateException(e); }
                var current = assignmentService.lockNotificationState(f.assignment).orElseThrow();
                assertEquals(callerTransaction, materialJdbc.queryForObject("SELECT pg_current_xact_id()::text", String.class));
                assertEquals("Current", current.assignmentTitle()); assertNull(current.lessonId());
                assertEquals(f.now.plusSeconds(7200), current.deadlineAt());
                assertEquals(java.util.Set.of(f.base.learner), current.submittedLearnerIds());
                assertThrows(UnsupportedOperationException.class, () -> current.submittedLearnerIds().clear());
                try {
                    String sqlState = workers.submit(() -> {
                        try (var c = java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
                            c.setAutoCommit(false);
                            try (var statement = c.createStatement()) {
                                statement.execute("SET LOCAL lock_timeout='300ms'");
                                statement.execute("SELECT id FROM assignments WHERE id='" + f.assignment + "' FOR UPDATE");
                                return "UNEXPECTED_LOCK_ACQUIRED";
                            } catch (java.sql.SQLException blocked) { return blocked.getSQLState(); }
                            finally { c.rollback(); }
                        }
                    }).get(5, TimeUnit.SECONDS);
                    assertEquals("55P03", sqlState);
                } catch (Exception e) { throw new IllegalStateException(e); }
            });
            // The caller completed: the former shared lock must no longer block a writer.
            tx.executeWithoutResult(status -> {
                materialJdbc.execute("SET LOCAL lock_timeout='300ms'");
                assertEquals(f.assignment, materialJdbc.queryForObject(
                        "SELECT id FROM assignments WHERE id=? FOR UPDATE", UUID.class, f.assignment));
            });
        }
    }

}
