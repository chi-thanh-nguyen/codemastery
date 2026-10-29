package com.codemastery.modules.auth;

import com.codemastery.common.config.AppConfig;
import com.codemastery.modules.auth.domain.model.AccountStatus;
import com.codemastery.modules.auth.domain.model.Role;
import com.codemastery.modules.auth.domain.model.User;
import com.codemastery.modules.auth.domain.repository.UserRepository;
import com.codemastery.modules.auth.infrastructure.security.AuthenticatedUser;
import com.codemastery.modules.auth.infrastructure.security.JwtAuthenticationFilter;
import com.codemastery.modules.auth.infrastructure.security.JwtTokenProvider;
import com.codemastery.modules.auth.infrastructure.security.RestSecurityErrorHandler;
import com.codemastery.modules.auth.infrastructure.security.SecurityConfig;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.filter.DelegatingFilterProxy;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class JwtTokenProviderTest {
    private static final UUID USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final Instant NOW = Instant.parse("2026-10-25T00:00:00Z");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private byte[] secret;
    private AppConfig.JwtSettings settings;
    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        settings = settings();
        secret = settings.decodedSecret();
        provider = providerAt(NOW);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    private static AppConfig.JwtSettings settings() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return new AppConfig.JwtSettings(HexFormat.of().formatHex(key), Duration.ofMinutes(15), "codemastery");
    }

    private JwtTokenProvider providerAt(Instant instant) throws Exception {
        // Exercise the existing internal clock seam without adding a public production test API.
        return BeanUtils.instantiateClass(JwtTokenProvider.class.getDeclaredConstructor(
                AppConfig.JwtSettings.class, Clock.class), settings, Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static Map<String, Object> claims() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("iss", "codemastery");
        values.put("aud", List.of("codemastery-api"));
        values.put("sub", USER_ID.toString());
        values.put("iat", NOW.getEpochSecond());
        values.put("exp", NOW.getEpochSecond() + 900);
        return values;
    }

    private String signed(Map<String, Object> claims) throws Exception {
        return signed(claims, Map.of("alg", "HS256", "typ", "JWT"), secret);
    }

    private static String signed(Map<String, Object> claims, Map<String, Object> header, byte[] key) throws Exception {
        // Raw JSON and JCA signing prevent a JWT builder from repairing malformed test claims.
        return signedJson(JSON.writeValueAsString(claims), JSON.writeValueAsString(header), key);
    }

    private static String signedJson(String claims, String header, byte[] key) throws Exception {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String input = encoder.encodeToString(header.getBytes(StandardCharsets.UTF_8)) + "."
                + encoder.encodeToString(claims.getBytes(StandardCharsets.UTF_8));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(Keys.hmacShaKeyFor(key));
        return input + "." + encoder.encodeToString(mac.doFinal(input.getBytes(StandardCharsets.US_ASCII)));
    }

    private static void rejected(JwtTokenProvider provider, String token) {
        var error = assertThrows(BadCredentialsException.class, () -> provider.parseSubject(token));
        assertEquals("Authentication failed.", error.getMessage());
        assertNull(error.getCause());
        if (token != null && !token.isBlank()) {
            assertFalse(error.getMessage().contains(token));
        }
    }

    @Nested
    class JwtContract {
        @Test
        void mintedTokenHasTheIndependentApprovedProfileAndRoundTrips() {
            String token = provider.createToken(USER_ID);
            var verified = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(secret))
                    .clock(() -> Date.from(NOW)).clockSkewSeconds(0).build().parseSignedClaims(token);
            assertEquals("HS256", verified.getHeader().getAlgorithm());
            assertEquals("JWT", verified.getHeader().getType());
            assertEquals("codemastery", verified.getPayload().getIssuer());
            assertEquals(Set.of("codemastery-api"), verified.getPayload().getAudience());
            assertEquals(USER_ID.toString(), verified.getPayload().getSubject());
            assertEquals(NOW, verified.getPayload().getIssuedAt().toInstant());
            assertEquals(NOW.plusSeconds(900), verified.getPayload().getExpiration().toInstant());
            JsonNode payload = JSON.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
            assertTrue(payload.get("iat").isNumber());
            assertTrue(payload.get("exp").isNumber());
            assertEquals(USER_ID, provider.parseSubject(token));
            assertFalse(token.contains(settings.secret()));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "not-a-jwt", "a.b.c"})
        void rejectsMissingOrMalformedTokens(String token) {
            rejected(provider, token);
        }

        @ParameterizedTest
        @ValueSource(strings = {"at+jwt", "jwt", "JWT ", ""})
        void rejectsOtherTokenTypes(String type) throws Exception {
            rejected(provider, signed(claims(), Map.of("alg", "HS256", "typ", type), secret));
        }

        @Test
        void rejectsMissingType() throws Exception {
            rejected(provider, signed(claims(), Map.of("alg", "HS256"), secret));
        }

        @ParameterizedTest
        @ValueSource(strings = {"none", "HS384", "HS512", "RS256"})
        void rejectsAlgorithmsOutsideTheSelectedProfile(String algorithm) throws Exception {
            rejected(provider, signed(claims(), Map.of("alg", algorithm, "typ", "JWT"), secret));
        }

        @Test
        void rejectsWrongKeyAndTampering() throws Exception {
            rejected(provider, signed(claims(), Map.of("alg", "HS256", "typ", "JWT"), settings().decodedSecret()));
            String[] parts = signed(claims()).split("\\.");
            byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
            signature[0] ^= 1;
            rejected(provider, parts[0] + "." + parts[1] + "."
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(signature));
        }

        @ParameterizedTest
        @ValueSource(strings = {"iss", "aud", "sub", "iat", "exp"})
        void rejectsEveryMissingRequiredClaim(String field) throws Exception {
            Map<String, Object> values = claims();
            values.remove(field);
            rejected(provider, signed(values));
        }

        static Stream<Arguments> invalidClaims() {
            return Stream.of(
                    Arguments.of("iss", "other-issuer"), Arguments.of("iss", 7),
                    Arguments.of("aud", "other-audience"), Arguments.of("aud", 7),
                    Arguments.of("aud", List.of("codemastery-api", 7)),
                    Arguments.of("sub", "not-a-uuid"), Arguments.of("sub", "1-1-1-1-1"),
                    Arguments.of("sub", 7), Arguments.of("sub", List.of(USER_ID.toString())),
                    Arguments.of("iat", Long.toString(NOW.getEpochSecond())), Arguments.of("iat", NOW.toString()),
                    Arguments.of("iat", true), Arguments.of("iat", List.of(NOW.getEpochSecond())), Arguments.of("iat", null),
                    Arguments.of("exp", Long.toString(NOW.getEpochSecond() + 900)),
                    Arguments.of("exp", NOW.plusSeconds(900).toString()), Arguments.of("exp", false),
                    Arguments.of("exp", List.of(NOW.getEpochSecond() + 900)), Arguments.of("exp", null));
        }

        @ParameterizedTest
        @MethodSource("invalidClaims")
        void rejectsIncorrectClaimValuesAndTypesWithoutCoercion(String field, Object value) throws Exception {
            Map<String, Object> values = claims();
            values.put(field, value);
            rejected(provider, signed(values));
        }

        @ParameterizedTest
        @ValueSource(longs = {0, 899, 901, 3600})
        void rejectsNon900SecondLifetimes(long seconds) throws Exception {
            Map<String, Object> values = claims();
            values.put("exp", NOW.getEpochSecond() + seconds);
            rejected(provider, signed(values));
        }

        @Test
        void appliesZeroSkewAtTheExactExpirationBoundary() throws Exception {
            String token = signed(claims());
            Instant expiry = NOW.plusSeconds(900);
            assertEquals(USER_ID, providerAt(expiry.minusNanos(1)).parseSubject(token));
            rejected(providerAt(expiry), token);
            rejected(providerAt(expiry.plusNanos(1)), token);
        }

        @Test
        void rejectsFutureIssuedAtWithoutRejectingItsExactBoundary() throws Exception {
            String token = signed(claims());
            assertEquals(USER_ID, providerAt(NOW).parseSubject(token));
            rejected(providerAt(NOW.minusNanos(1)), token);
        }

        @Test
        void preservesValidFractionalNumericDatesAndTheirBoundaries() throws Exception {
            Map<String, Object> values = claims();
            BigDecimal issued = BigDecimal.valueOf(NOW.getEpochSecond()).add(new BigDecimal("0.000000001"));
            values.put("iat", issued);
            values.put("exp", issued.add(BigDecimal.valueOf(900)));
            String token = signed(values);
            assertEquals(USER_ID, providerAt(NOW.plusNanos(1)).parseSubject(token));
            Instant expiry = NOW.plusSeconds(900).plusNanos(1);
            assertEquals(USER_ID, providerAt(expiry.minusNanos(1)).parseSubject(token));
            rejected(providerAt(expiry), token);
        }

        @Test
        void acceptsUuidLetterCaseAndBothStandardAudienceRepresentations() throws Exception {
            Map<String, Object> values = claims();
            UUID letters = UUID.fromString("abcdefab-cdef-4abc-8def-abcdefabcdef");
            values.put("sub", letters.toString().toUpperCase(Locale.ROOT));
            values.put("aud", "codemastery-api");
            assertEquals(letters, provider.parseSubject(signed(values)));
        }

        @Test
        void consumedSettingsRejectInvalidKeysAndLifetimeWithoutEchoingSecrets() {
            String shortKey = settings.secret().substring(2);
            var error = assertThrows(IllegalStateException.class,
                    () -> new AppConfig.JwtSettings(shortKey, Duration.ofMinutes(15), "codemastery"));
            assertFalse(error.getMessage().contains(shortKey));
            assertThrows(IllegalStateException.class,
                    () -> new AppConfig.JwtSettings(settings.secret(), Duration.ofHours(1), "codemastery"));
        }
    }

    @Nested
    @Timeout(5) // Local safety watchdog, not a latency benchmark or deployment limit.
    class ParsingResourceSafety {
        private Map<String, Object> header() {
            return new LinkedHashMap<>(Map.of("alg", "HS256", "typ", "JWT"));
        }

        @Test
        void headerClaimNamesAreMetadataAndNeverValidatedAsPayloadClaims() throws Exception {
            Map<String, Object> header = header();
            header.put("iat", "header metadata");
            header.put("exp", new BigDecimal("1e10000"));
            assertEquals(USER_ID, provider.parseSubject(signed(claims(), header, secret)));
            rejected(provider, signed(claims(), header, settings().decodedSecret()));
        }

        @Test
        void headerClaimsCannotSupplyMissingPayloadClaims() throws Exception {
            Map<String, Object> header = header();
            header.putAll(claims());
            rejected(provider, signed(Map.of(), header, secret));
        }

        @ParameterizedTest
        @ValueSource(strings = {"iat", "exp"})
        void rejectsExtremeNumericMagnitudesBeforeArithmetic(String field) throws Exception {
            for (String number : List.of("1e10000", "-1e10000", "1e-10000", "0e-10000")) {
                Map<String, Object> values = claims();
                values.put(field, new BigDecimal(number));
                rejected(provider, signed(values));
            }
        }

        @Test
        void unequalExtremeFractionalScalesNearEpochZeroCannotExpandDuringSubtraction() throws Exception {
            Map<String, Object> values = claims();
            values.put("iat", new BigDecimal("1e-10000"));
            values.put("exp", 900);
            rejected(providerAt(Instant.ofEpochSecond(1)), signed(values));
            values.put("iat", new BigDecimal("0e-10000"));
            assertEquals(USER_ID, providerAt(Instant.ofEpochSecond(1)).parseSubject(signed(values)));
        }

        @Test
        void preservesEqualFractionsAndEquivalentDecimalRepresentations() throws Exception {
            Map<String, Object> values = claims();
            values.put("iat", new BigDecimal(NOW.getEpochSecond() + ".123456789012345678901234567890"));
            values.put("exp", new BigDecimal((NOW.getEpochSecond() + 900) + ".12345678901234567890123456789"));
            assertEquals(USER_ID, providerAt(NOW.plusSeconds(1)).parseSubject(signed(values)));
        }

        @Test
        void compactInputAt16384CharactersIsAcceptedAndOneMoreIsRejected() throws Exception {
            String boundary = paddedToken(16_384);
            assertEquals(16_384, boundary.length());
            assertEquals(USER_ID, provider.parseSubject(boundary));
            String oversized = paddedToken(16_385);
            assertEquals(16_385, oversized.length());
            rejected(provider, oversized);
            rejected(provider, " ".repeat(16_385));
        }

        private String paddedToken(int length) throws Exception {
            // Base64url has a deterministic 4/3 expansion; only a few padding sizes
            // around this independent estimate are needed, with no parser-derived oracle.
            int fixedLength = signed(claims()).length();
            for (int padding = (length - fixedLength) * 3 / 4 - 20;
                 padding < (length - fixedLength) * 3 / 4 + 4; padding++) {
                Map<String, Object> values = claims();
                values.put("padding", "x".repeat(padding));
                String candidate = signed(values);
                if (candidate.length() == length) {
                    return candidate;
                }
            }
            throw new AssertionError("Test fixture could not reach the required compact length");
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void boundsNumericTokenLengthInHeadersAndClaims(boolean inHeader) throws Exception {
            Map<String, Object> header = header();
            Map<String, Object> values = claims();
            Map<String, Object> target = inHeader ? header : values;
            target.put("counter", new java.math.BigInteger("1".repeat(64)));
            assertEquals(USER_ID, provider.parseSubject(signed(values, header, secret)));
            target.put("counter", new java.math.BigInteger("1".repeat(65)));
            rejected(provider, signed(values, header, secret));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void boundsJsonNestingInHeadersAndClaims(boolean inHeader) throws Exception {
            Map<String, Object> header = header();
            Map<String, Object> values = claims();
            Map<String, Object> target = inHeader ? header : values;
            Object nested = "leaf";
            for (int depth = 0; depth < 15; depth++) {
                nested = List.of(nested);
            }
            target.put("nested", nested); // Root object plus 15 arrays = depth 16.
            assertEquals(USER_ID, provider.parseSubject(signed(values, header, secret)));
            target.put("nested", List.of(nested));
            rejected(provider, signed(values, header, secret));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void malformedJsonAndUnrepresentableExponentsRejectGenerically(boolean inHeader) throws Exception {
            String header = JSON.writeValueAsString(header());
            String payload = JSON.writeValueAsString(claims());
            for (String invalid : List.of("null", "[]", "{} {}", "{\"value\":1e2147483648}")) {
                rejected(provider, signedJson(inHeader ? payload : invalid, inHeader ? invalid : header, secret));
            }
        }

        @Test
        void compressionIsRejectedBeforePayloadExpansion() {
            String token = Jwts.builder().header().type("JWT").and()
                    .claims(claims()).compressWith(Jwts.ZIP.GZIP)
                    .signWith(Keys.hmacShaKeyFor(secret), Jwts.SIG.HS256).compact();
            rejected(provider, token);
        }
    }

    @Nested
    class ComponentWiring {
        @Test
        void productionComponentsInitializeWithBootManagedJackson3AndSettings() {
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                    .withBean(AppConfig.JwtSettings.class, () -> settings)
                    .withUserConfiguration(JwtTokenProvider.class, RestSecurityErrorHandler.class)
                    .run(context -> {
                        assertNull(context.getStartupFailure());
                        assertNotNull(context.getBean(JsonMapper.class));
                        JwtTokenProvider production = context.getBean(JwtTokenProvider.class);
                        assertEquals(USER_ID, production.parseSubject(production.createToken(USER_ID)));
                        var response = new MockHttpServletResponse();
                        context.getBean(RestSecurityErrorHandler.class).commence(
                                new MockHttpServletRequest("GET", "/api/test"), response,
                                new BadCredentialsException("Synthetic confidential input"));
                        assertProblem(response, 401, "Authentication failed.", "/api/test");
                    });
        }
    }

    @Nested
    class SecurityChain {
        private AnnotationConfigWebApplicationContext context;
        private MockMvc mvc;
        private JwtTokenProvider tokens;
        private UserRepository users;
        private FixtureEndpoints endpoints;
        private RestSecurityErrorHandler errors;

        @BeforeEach
        void startRealChain() {
            context = new AnnotationConfigWebApplicationContext();
            context.setServletContext(new MockServletContext());
            context.register(SecurityFixture.class);
            context.refresh();
            tokens = context.getBean(JwtTokenProvider.class);
            users = context.getBean(UserRepository.class);
            endpoints = context.getBean(FixtureEndpoints.class);
            errors = context.getBean(RestSecurityErrorHandler.class);
            mvc = MockMvcBuilders.webAppContextSetup(context)
                    .addFilters(new DelegatingFilterProxy("springSecurityFilterChain", context)).build();
        }

        @AfterEach
        void closeContext() {
            if (context != null) {
                context.close();
            }
        }

        private String token() {
            return tokens.createToken(USER_ID);
        }

        private User account(Role role, AccountStatus status) {
            return new User(USER_ID, "unit@example.test", "synthetic unused hash", "Unit Account", role, status);
        }

        static Stream<Arguments> publicOperations() {
            return Stream.of(Arguments.of(HttpMethod.POST, "/api/auth/register"),
                    Arguments.of(HttpMethod.POST, "/api/auth/login"), Arguments.of(HttpMethod.GET, "/api/courses"),
                    Arguments.of(HttpMethod.GET, "/api/courses/" + USER_ID));
        }

        @ParameterizedTest
        @MethodSource("publicOperations")
        void publicOperationsIgnoreBearerCandidatesAndIdentityStorage(HttpMethod method, String path) throws Exception {
            String valid = token();
            when(users.findById(any())).thenThrow(new DataAccessResourceFailureException("Synthetic storage outage"));
            for (String header : new String[]{null, "Bearer not-a-jwt", "Bearer " + valid}) {
                var request = request(method, path);
                if (header != null) {
                    request.header("Authorization", header);
                }
                mvc.perform(request).andExpect(status().isOk());
            }
            assertEquals(3, endpoints.effects.get());
            verify(tokens, never()).parseSubject(anyString());
            verifyNoInteractions(users);
        }

        static Stream<Arguments> unselectedOperations() {
            return Stream.of(Arguments.of(HttpMethod.GET, "/api/auth/register"),
                    Arguments.of(HttpMethod.GET, "/api/auth/login"), Arguments.of(HttpMethod.POST, "/api/courses"),
                    Arguments.of(HttpMethod.POST, "/api/courses/" + USER_ID),
                    Arguments.of(HttpMethod.POST, "/api/auth/register/extra"),
                    Arguments.of(HttpMethod.GET, "/api/courses/" + USER_ID + "/private"));
        }

        @ParameterizedTest
        @MethodSource("unselectedOperations")
        void publicMatchersDoNotExemptOtherMethodsOrDescendants(HttpMethod method, String path) throws Exception {
            when(users.findById(USER_ID)).thenReturn(Optional.of(account(Role.LEARNER, AccountStatus.ACTIVE)));
            String token = token();
            var response = mvc.perform(request(method, path).header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden()).andReturn().getResponse();
            assertProblem(response, 403, "Access is denied.", path);
            verify(tokens).parseSubject(token);
            verify(users).findById(USER_ID);
            assertEquals(0, endpoints.effects.get());
        }

        @ParameterizedTest
        @EnumSource(Role.class)
        void noRoleReceivesImplicitAccessToUnselectedCapabilities(Role role) throws Exception {
            when(users.findById(USER_ID)).thenReturn(Optional.of(account(role, AccountStatus.ACTIVE)));
            var response = mvc.perform(get("/api/review-only-unselected").header("Authorization", "Bearer " + token()))
                    .andExpect(status().isForbidden()).andReturn().getResponse();
            assertProblem(response, 403, "Access is denied.", "/api/review-only-unselected");
            assertEquals(0, endpoints.effects.get());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"Basic synthetic", "Bearer", "Bearer ", "bearer not-a-jwt"})
        void unauthenticatedRequestsHaveTheSameSafe401(String header) throws Exception {
            var request = get("/api/review-only-unselected");
            if (header != null) {
                request.header("Authorization", header);
            }
            var response = mvc.perform(request).andExpect(status().isUnauthorized()).andReturn().getResponse();
            assertProblem(response, 401, "Authentication failed.", "/api/review-only-unselected");
            verifyNoInteractions(users);
            assertEquals(0, endpoints.effects.get());
        }

        @Test
        void oversizedBearerFailsSafelyBeforeAccountLookupOrEndpointEffects() throws Exception {
            var response = mvc.perform(get("/api/review-only-unselected")
                            .header("Authorization", "Bearer " + "x".repeat(16_385)))
                    .andExpect(status().isUnauthorized()).andReturn().getResponse();
            assertProblem(response, 401, "Authentication failed.", "/api/review-only-unselected");
            verifyNoInteractions(users);
            assertNull(SecurityContextHolder.getContext().getAuthentication());
            assertEquals(0, endpoints.effects.get());
        }

        @ParameterizedTest
        @ValueSource(strings = {"missing", "locked"})
        void missingAndLockedAccountsCannotAuthenticate(String state) throws Exception {
            when(users.findById(USER_ID)).thenReturn("missing".equals(state) ? Optional.empty()
                    : Optional.of(account(Role.LEARNER, AccountStatus.LOCKED)));
            var response = mvc.perform(get("/api/review-only-unselected").header("Authorization", "Bearer " + token()))
                    .andExpect(status().isUnauthorized()).andReturn().getResponse();
            assertProblem(response, 401, "Authentication failed.", "/api/review-only-unselected");
            assertEquals(0, endpoints.effects.get());
        }

        @Test
        void accountStateIsReadAgainOnTheNextRequest() throws Exception {
            when(users.findById(USER_ID)).thenReturn(Optional.of(account(Role.INSTRUCTOR, AccountStatus.ACTIVE)))
                    .thenReturn(Optional.of(account(Role.INSTRUCTOR, AccountStatus.LOCKED)));
            String token = token();
            mvc.perform(get("/api/review-only-unselected").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/review-only-unselected").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
            verify(users, times(2)).findById(USER_ID);
            assertEquals(0, endpoints.effects.get());
        }

        static Stream<RuntimeException> accountReadFailures() {
            return Stream.of(new DataAccessResourceFailureException("Synthetic confidential SQL details"),
                    new IllegalArgumentException("Synthetic invalid stored role"));
        }

        @ParameterizedTest
        @MethodSource("accountReadFailures")
        void accountReadFailuresProduceSafe500AndStopTheActualChain(RuntimeException failure) throws Exception {
            when(users.findById(USER_ID)).thenThrow(failure);
            var response = mvc.perform(get("/api/review-only-unselected").queryParam("private", "confidential")
                            .header("Authorization", "Bearer " + token()))
                    .andExpect(status().isInternalServerError()).andReturn().getResponse();
            assertProblem(response, 500, "An unexpected error occurred.", "/api/review-only-unselected");
            assertFalse(response.getContentAsString().contains(failure.getMessage()));
            assertNull(SecurityContextHolder.getContext().getAuthentication());
            assertEquals(0, endpoints.effects.get());
        }

        @ParameterizedTest
        @EnumSource(Role.class)
        void filterUsesTheCurrentRoleAndKeepsThePrincipalInsideAuth(Role role) throws Exception {
            when(users.findById(USER_ID)).thenReturn(Optional.of(account(role, AccountStatus.ACTIVE)));
            var request = new MockHttpServletRequest("GET", "/api/review-only-unselected");
            request.addHeader("Authorization", "Bearer " + token());
            AtomicReference<Authentication> observed = new AtomicReference<>();
            new JwtAuthenticationFilter(tokens, users, errors).doFilter(request, new MockHttpServletResponse(),
                    (req, res) -> observed.set(SecurityContextHolder.getContext().getAuthentication()));
            AuthenticatedUser principal = assertInstanceOf(AuthenticatedUser.class, observed.get().getPrincipal());
            assertEquals(USER_ID, principal.userId());
            assertEquals(role, principal.role());
            assertEquals(List.of("ROLE_" + role.name()), principal.getAuthorities().stream()
                    .map(authority -> authority.getAuthority()).toList());
            assertEquals(USER_ID.toString(), principal.getUsername());
            assertEquals("", principal.getPassword());
            assertTrue(principal.isAccountNonExpired());
            assertTrue(principal.isAccountNonLocked());
            assertTrue(principal.isCredentialsNonExpired());
            assertTrue(principal.isEnabled());
        }

        @Test
        void browserCookieOrSessionCannotSupplyAuthentication() throws Exception {
            var session = new MockHttpSession();
            var identity = SecurityContextHolder.createEmptyContext();
            identity.setAuthentication(new UsernamePasswordAuthenticationToken("synthetic", null, List.of()));
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, identity);
            mvc.perform(get("/api/review-only-unselected").session(session).cookie(new Cookie("JSESSIONID", "synthetic")))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(users);
            assertEquals(0, endpoints.effects.get());
        }

        @Test
        void corsPreflightUsesOnlyApprovedOriginsMethodsAndHeaders() throws Exception {
            mvc.perform(options("/api/auth/register").header("Origin", "http://localhost:5173")
                            .header("Access-Control-Request-Method", "POST")
                            .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                    .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
            mvc.perform(options("/api/auth/register").header("Origin", "https://unapproved.example.test")
                            .header("Access-Control-Request-Method", "POST"))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(users);
            verify(tokens, never()).parseSubject(anyString());
            assertEquals(0, endpoints.effects.get());
        }

        @Test
        void committedResponsesArePreservedAndInvalidInstancePathsUseSafeFallback() throws Exception {
            var committed = new MockHttpServletResponse();
            committed.setStatus(202);
            committed.flushBuffer();
            errors.commence(new MockHttpServletRequest("GET", "/api/test"), committed,
                    new BadCredentialsException("Synthetic confidential"));
            assertEquals(202, committed.getStatus());
            assertEquals("", committed.getContentAsString());
            var response = new MockHttpServletResponse();
            errors.handle(new MockHttpServletRequest("GET", "/invalid path"), response,
                    new AccessDeniedException("Synthetic confidential"));
            assertProblem(response, 403, "Access is denied.", "/");
        }
    }

    private static void assertProblem(MockHttpServletResponse response, int status, String detail, String path)
            throws Exception {
        assertEquals(status, response.getStatus());
        assertTrue(MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(MediaType.parseMediaType(response.getContentType())));
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertEquals(status == 401 ? "Bearer" : null, response.getHeader("WWW-Authenticate"));
        JsonNode problem = JSON.readTree(response.getContentAsString());
        assertEquals("about:blank", problem.get("type").asString());
        assertEquals(status, problem.get("status").asInt());
        assertEquals(org.springframework.http.HttpStatus.valueOf(status).getReasonPhrase(), problem.get("title").asString());
        assertEquals(detail, problem.get("detail").asString());
        assertEquals(path, problem.get("instance").asString());
        assertEquals(5, problem.size());
        assertFalse(response.getContentAsString().contains("confidential"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({SecurityConfig.class, JwtTokenProvider.class, RestSecurityErrorHandler.class,
            JacksonAutoConfiguration.class, FixtureEndpoints.class})
    static class SecurityFixture {
        @Bean
        AppConfig.JwtSettings jwtSettings() {
            return settings();
        }

        @Bean
        AppConfig.CorsSettings corsSettings() {
            return new AppConfig.CorsSettings(List.of("http://localhost:5173"));
        }

        @Bean
        UserRepository users() {
            return mock(UserRepository.class);
        }

        @Bean
        static BeanPostProcessor observeRealTokenProvider() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String name) {
                    return bean instanceof JwtTokenProvider ? spy(bean) : bean;
                }
            };
        }
    }

    /** Test-only effect target; production adds no endpoint or protected operation. */
    @RestController
    static class FixtureEndpoints {
        private final AtomicInteger effects = new AtomicInteger();

        @RequestMapping("/api/**")
        Map<String, Object> effect() {
            effects.incrementAndGet();
            return Map.of("reached", true);
        }
    }
}
