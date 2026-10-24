package com.codemastery.modules.auth;

import com.codemastery.common.config.AppConfig;
import com.codemastery.modules.auth.infrastructure.security.JwtTokenProvider;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenProviderTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private String secretHex;
    private AppConfig.JwtSettings settings;
    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        secretHex = HexFormat.of().formatHex(keyBytes);
        settings = new AppConfig.JwtSettings(secretHex, Duration.ofMinutes(15), "codemastery");
        provider = new JwtTokenProvider(settings);
    }

    @Test
    void createsAndVerifiesHs256TokenWithRequiredClaims() {
        Instant beforeIssue = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        String token = provider.createToken(USER_ID);
        Instant afterIssue = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        assertEquals(USER_ID, provider.parseSubject(token));

        var jws = Jwts.parser()
                .verifyWith(signingKey())
                .clockSkewSeconds(0)
                .build()
                .parseSignedClaims(token);
        assertEquals("HS256", jws.getHeader().getAlgorithm());
        assertEquals("codemastery", jws.getPayload().getIssuer());
        assertTrue(jws.getPayload().getAudience().contains("codemastery-api"));
        assertEquals(USER_ID.toString(), jws.getPayload().getSubject());
        Instant issuedAt = jws.getPayload().getIssuedAt().toInstant();
        Instant expiration = jws.getPayload().getExpiration().toInstant();
        assertFalse(issuedAt.isBefore(beforeIssue));
        assertFalse(issuedAt.isAfter(afterIssue));
        assertEquals(issuedAt.plusSeconds(900), expiration);
        assertFalse(token.contains(secretHex));
    }

    @Test
    void rejectsMalformedTokenAndWrongSignature() {
        assertAuthenticationFailure("not-a-jwt");
        String token = provider.createToken(USER_ID);
        byte[] other = new byte[32];
        new SecureRandom().nextBytes(other);
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        String forged = Jwts.builder()
                .subject(USER_ID.toString())
                .issuer("codemastery")
                .audience().add("codemastery-api").and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .signWith(Keys.hmacShaKeyFor(other), Jwts.SIG.HS256)
                .compact();
        assertAuthenticationFailure(forged);
        assertAuthenticationFailure(token.substring(0, token.length() - 4) + "abcd");
    }

    @Test
    void rejectsWrongAlgorithmIssuerAndAudience() {
        SecretKey hs384Key = Jwts.SIG.HS384.key().build();
        String hs384 = Jwts.builder()
                .subject(USER_ID.toString())
                .issuer("codemastery")
                .audience().add("codemastery-api").and()
                .issuedAt(Date.from(Instant.now().truncatedTo(ChronoUnit.SECONDS)))
                .expiration(Date.from(Instant.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(900)))
                .signWith(hs384Key, Jwts.SIG.HS384)
                .compact();
        assertAuthenticationFailure(hs384);
        assertAuthenticationFailure(tokenWith("other-issuer", "codemastery-api"));
        assertAuthenticationFailure(tokenWith("codemastery", "other-audience"));
    }

    @Test
    void rejectsMissingRequiredClaimsNonUuidSubjectAndFutureIssuedAt() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        assertAuthenticationFailure(Jwts.builder()
                .issuer("codemastery")
                .audience().add("codemastery-api").and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .signWith(signingKey(), Jwts.SIG.HS256)
                .compact());
        assertAuthenticationFailure(Jwts.builder()
                .subject(USER_ID.toString())
                .issuer("codemastery")
                .audience().add("codemastery-api").and()
                .expiration(Date.from(now.plusSeconds(900)))
                .signWith(signingKey(), Jwts.SIG.HS256)
                .compact());
        assertAuthenticationFailure(Jwts.builder()
                .subject(USER_ID.toString())
                .issuer("codemastery")
                .audience().add("codemastery-api").and()
                .issuedAt(Date.from(now))
                .signWith(signingKey(), Jwts.SIG.HS256)
                .compact());
        assertAuthenticationFailure(Jwts.builder()
                .subject("not-a-uuid")
                .issuer("codemastery")
                .audience().add("codemastery-api").and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .signWith(signingKey(), Jwts.SIG.HS256)
                .compact());
        String futureIat = Jwts.builder()
                .subject(USER_ID.toString())
                .issuer("codemastery")
                .audience().add("codemastery-api").and()
                .issuedAt(Date.from(now.plusSeconds(60)))
                .expiration(Date.from(now.plusSeconds(960)))
                .signWith(signingKey(), Jwts.SIG.HS256)
                .compact();
        assertAuthenticationFailure(futureIat);
    }

    @Test
    void rejectsExpiredTokenWithZeroClockSkewAndDoesNotMintIdentity() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        String expired = compactToken(now.minusSeconds(900), now);
        AuthenticationException failure = assertThrows(BadCredentialsException.class,
                () -> provider.parseSubject(expired));
        assertEquals("Authentication failed.", failure.getMessage());
        assertFalse(failure.getMessage().contains(expired));
        String stillValid = compactToken(now, now.plusSeconds(900));
        assertEquals(USER_ID, provider.parseSubject(stillValid));
    }

    @Test
    void consumedJwtSettingsRejectInvalidKeysWithoutExposingThem() {
        String shortKey = secretHex.substring(2);
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new AppConfig.JwtSettings(shortKey, Duration.ofMinutes(15), "codemastery"));
        assertFalse(failure.getMessage().contains(shortKey));
        assertThrows(IllegalStateException.class,
                () -> new AppConfig.JwtSettings(secretHex, Duration.ofHours(1), "codemastery"));
    }

    private String tokenWith(String issuer, String audience) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return Jwts.builder()
                .subject(USER_ID.toString())
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .signWith(signingKey(), Jwts.SIG.HS256)
                .compact();
    }

    private String compactToken(Instant issuedAt, Instant expiration) {
        return Jwts.builder()
                .subject(USER_ID.toString())
                .issuer("codemastery")
                .audience().add("codemastery-api").and()
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiration))
                .signWith(signingKey(), Jwts.SIG.HS256)
                .compact();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(settings.decodedSecret());
    }

    private void assertAuthenticationFailure(String token) {
        BadCredentialsException exception = assertThrows(BadCredentialsException.class,
                () -> provider.parseSubject(token));
        assertEquals("Authentication failed.", exception.getMessage());
        if (token != null) {
            assertFalse(exception.getMessage().contains(token));
        }
    }
}
