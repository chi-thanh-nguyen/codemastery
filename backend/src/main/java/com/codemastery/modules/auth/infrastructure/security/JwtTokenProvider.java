package com.codemastery.modules.auth.infrastructure.security;

import com.codemastery.common.config.AppConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    static final String HS256_ID = Jwts.SIG.HS256.getId();

    private final AppConfig.JwtSettings settings;
    private final Clock clock;

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
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(signingKey())
                    .requireIssuer(settings.issuer())
                    .requireAudience(settings.audience())
                    .clockSkewSeconds(0)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(compactJwt);
            if (!HS256_ID.equals(jws.getHeader().getAlgorithm())) {
                throw failedAuthentication();
            }
            Claims claims = jws.getPayload();
            Date issuedAt = claims.getIssuedAt();
            Date expiration = claims.getExpiration();
            if (issuedAt == null || expiration == null || claims.getSubject() == null) {
                throw failedAuthentication();
            }
            Instant now = clock.instant();
            if (issuedAt.toInstant().isAfter(now) || !now.isBefore(expiration.toInstant())) {
                throw failedAuthentication();
            }
            return UUID.fromString(claims.getSubject());
        } catch (BadCredentialsException exception) {
            throw exception;
        } catch (JwtException | IllegalArgumentException exception) {
            throw failedAuthentication();
        }
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(settings.decodedSecret());
    }

    private static BadCredentialsException failedAuthentication() {
        return new BadCredentialsException("Authentication failed.");
    }
}
