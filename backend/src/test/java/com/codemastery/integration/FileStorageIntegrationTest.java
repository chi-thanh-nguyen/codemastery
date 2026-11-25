package com.codemastery.integration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.codemastery.infrastructure.storage.FileStorage;
import com.codemastery.infrastructure.storage.FileStorage.FileStorageException;
import com.codemastery.infrastructure.storage.FileStorage.ObjectNotFoundException;
import com.codemastery.infrastructure.storage.FileStorage.StoredObject;
import com.codemastery.infrastructure.storage.s3.S3FileStorage;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
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
