package com.codemastery.modules.auth.infrastructure.security;

import com.codemastery.common.config.AppConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.AbstractDeserializer;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.SecretKey;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    static final String HS256_ID = Jwts.SIG.HS256.getId();
    // Local parser resource guards, not new token claims or runtime configuration.
    // The approved producer emits a small, uncompressed, shallow JSON object.
    private static final int MAX_TOKEN_LENGTH = 16_384;
    private static final BigDecimal LIFETIME_SECONDS = BigDecimal.valueOf(900);
    private static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder()
                            .maxDocumentLength(MAX_TOKEN_LENGTH)
                            .maxStringLength(MAX_TOKEN_LENGTH)
                            .maxNameLength(MAX_TOKEN_LENGTH)
                            .maxNestingDepth(16)
                            .maxNumberLength(64).build()).build())
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    private final AppConfig.JwtSettings settings;
    private final Clock clock;

    @Autowired
    public JwtTokenProvider(AppConfig.JwtSettings settings) {
        this(settings, Clock.systemUTC());
    }

    JwtTokenProvider(AppConfig.JwtSettings settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
    }

    public String createToken(UUID userId) {
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(settings.expiration());
        return Jwts.builder()
                .header().type("JWT").and()
                .subject(userId.toString())
                .issuer(settings.issuer())
                .audience().add(settings.audience()).and()
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey(), Jwts.SIG.HS256)
                .compact();
    }

    public UUID parseSubject(String compactJwt) {
        if (compactJwt == null || compactJwt.length() > MAX_TOKEN_LENGTH || compactJwt.isBlank()) {
            throw failedAuthentication();
        }
        try {
            Instant now = clock.instant();
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(signingKey())
                    .sig().clear().add(Jwts.SIG.HS256).and()
                    .zip().clear().and() // No compression is selected by the approved JWT profile.
                    .requireIssuer(settings.issuer())
                    .requireAudience(settings.audience())
                    .clockSkewSeconds(0)
                    .clock(() -> Date.from(now))
                    .json(new AbstractDeserializer<Map<String, ?>>() {
                        private boolean header = true;

                        @Override
                        protected Map<String, ?> doDeserialize(Reader reader) {
                            Map<String, Object> values = JSON.readValue(reader, new TypeReference<>() {});
                            // JJWT 0.13 reads the header first, then verifies the signature with
                            // verifyWith before deserializing claims. Never infer payload identity
                            // from attacker-controlled header keys such as iat/exp.
                            if (header) {
                                header = false;
                                return values;
                            }
                            return validateClaims(values, now);
                        }
                    })
                    .build()
                    .parseSignedClaims(compactJwt);
            if (!HS256_ID.equals(jws.getHeader().getAlgorithm()) || !"JWT".equals(jws.getHeader().getType())) {
                throw failedAuthentication();
            }
            Claims claims = jws.getPayload();
            Date issuedAt = claims.getIssuedAt();
            Date expiration = claims.getExpiration();
            if (issuedAt == null || expiration == null || claims.getSubject() == null) {
                throw failedAuthentication();
            }
            UUID subject = UUID.fromString(claims.getSubject());
            if (!subject.toString().equalsIgnoreCase(claims.getSubject())) {
                throw failedAuthentication();
            }
            return subject;
        } catch (BadCredentialsException exception) {
            throw exception;
        } catch (JwtException | IllegalArgumentException exception) {
            throw failedAuthentication();
        }
    }

    private static Map<String, ?> validateClaims(Map<String, Object> values, Instant now) {
        Object audience = values.get("aud");
        if (!(values.get("sub") instanceof String) || !(values.get("iss") instanceof String)
                || !(audience instanceof String || audience instanceof Collection<?> members
                && members.stream().allMatch(String.class::isInstance))) {
            throw failedAuthentication();
        }
        BigDecimal issuedAt = numericDate(values.get("iat"));
        BigDecimal expiration = numericDate(values.get("exp"));
        BigDecimal current = BigDecimal.valueOf(now.getEpochSecond())
                .add(BigDecimal.valueOf(now.getNano(), 9));
        // Compare magnitudes before subtraction or Date conversion: exponent notation
        // must not cause an enormous power-of-ten allocation. The 900-second live
        // window follows directly from the existing lifetime and zero-skew contract.
        if (issuedAt.compareTo(current) > 0 || expiration.compareTo(current) <= 0
                || issuedAt.compareTo(current.subtract(LIFETIME_SECONDS)) <= 0
                || expiration.compareTo(current.add(LIFETIME_SECONDS)) > 0
                // After removing trailing zeroes, numbers differing by an integer
                // have identical fractional scales. Reject unequal scales before
                // aligning them; this also covers extreme exponents near epoch zero.
                || Math.max(0, issuedAt.scale()) != Math.max(0, expiration.scale())
                || expiration.subtract(issuedAt).compareTo(LIFETIME_SECONDS) != 0) {
            throw failedAuthentication();
        }
        // Keep exact sub-millisecond checks above; JJWT still receives millisecond Dates.
        values.put("iat", date(issuedAt));
        values.put("exp", date(expiration));
        return values;
    }

    private static BigDecimal numericDate(Object value) {
        if (!(value instanceof Number number)) {
            throw failedAuthentication();
        }
        return new BigDecimal(number.toString()).stripTrailingZeros();
    }

    private static Date date(BigDecimal seconds) {
        return new Date(seconds.movePointRight(3).setScale(0, RoundingMode.FLOOR).longValueExact());
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(settings.decodedSecret());
    }

    private static BadCredentialsException failedAuthentication() {
        return new BadCredentialsException("Authentication failed.");
    }
}
