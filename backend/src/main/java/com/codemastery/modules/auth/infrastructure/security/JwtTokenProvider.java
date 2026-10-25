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
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

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
        if (compactJwt == null || compactJwt.isBlank()) {
            throw failedAuthentication();
        }
        try {
            Instant now = clock.instant();
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(signingKey())
                    .sig().clear().add(Jwts.SIG.HS256).and()
                    .requireIssuer(settings.issuer())
                    .requireAudience(settings.audience())
                    .clockSkewSeconds(0)
                    .clock(() -> Date.from(now))
                    .json(new AbstractDeserializer<Map<String, ?>>() {
                        @Override
                        protected Map<String, ?> doDeserialize(Reader reader) {
                            return readJson(reader, now);
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

    private static Map<String, ?> readJson(Reader reader, Instant now) {
        Map<String, Object> values = JSON.readValue(reader, new TypeReference<>() {});
        // JJWT also calls this reader for the JOSE header. Claims require both timestamps;
        // the post-parse checks reject a payload with neither timestamp.
        if (values.containsKey("iat") || values.containsKey("exp")) {
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
            if (expiration.subtract(issuedAt).compareTo(BigDecimal.valueOf(900)) != 0
                    || issuedAt.compareTo(current) > 0 || expiration.compareTo(current) <= 0) {
                throw failedAuthentication();
            }
            // Preserve fractional NumericDates for JJWT's millisecond Date checks. The exact
            // raw numeric checks above remain authoritative at sub-millisecond boundaries.
            values.put("iat", date(issuedAt));
            values.put("exp", date(expiration));
        }
        return values;
    }

    private static BigDecimal numericDate(Object value) {
        if (!(value instanceof Number number)) {
            throw failedAuthentication();
        }
        return new BigDecimal(number.toString());
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
