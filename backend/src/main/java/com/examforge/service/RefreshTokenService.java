package com.examforge.service;

import com.examforge.config.AppProperties;
import com.examforge.entity.RefreshToken;
import com.examforge.entity.User;
import com.examforge.exception.ApiException;
import com.examforge.repository.RefreshTokenRepository;
import com.examforge.util.RequestInfo;
import com.examforge.util.Tokens;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Rotating refresh tokens. Each use revokes the presented token and issues a new one in the same
 * "family". Presenting an already-revoked token means it was stolen or replayed, so the whole family
 * is revoked and the user must sign in again.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository repository;
    private final AppProperties props;


    /** A newly issued token: the raw value goes to the client exactly once. */
    public record Issued(String rawToken, RefreshToken entity, Duration ttl) {}

    public Duration ttl(boolean rememberMe) {
        return rememberMe ? props.jwt().refreshTokenRememberTtl() : props.jwt().refreshTokenTtl();
    }

    @Transactional
    public Issued issue(User user, boolean rememberMe, RequestInfo info) {
        return issueInFamily(user, UUID.randomUUID().toString(), rememberMe, info);
    }

    private Issued issueInFamily(User user, String familyId, boolean rememberMe, RequestInfo info) {
        String raw = Tokens.newOpaqueToken();
        Duration ttl = ttl(rememberMe);
        RefreshToken entity = repository.save(new RefreshToken(user, Tokens.sha256(raw), familyId, rememberMe,
                Instant.now().plus(ttl), info.userAgent(), info.ip()));
        return new Issued(raw, entity, ttl);
    }

    /** Validates and rotates. noRollbackFor keeps the family revocation when reuse is detected. */
    @Transactional(noRollbackFor = ApiException.class)
    public Issued rotate(String rawToken, RequestInfo info) {
        RefreshToken current = find(rawToken);
        Instant now = Instant.now();
        if (current.getRevokedAt() != null) {
            // Two tabs (or a retry) refreshing at the same moment: the token was rotated a few seconds ago
            // and its session is still alive, so this is not theft. Issue another token in the same session.
            Duration grace = props.jwt().refreshReuseGrace();
            boolean recentlyRotated = grace != null && current.getRevokedAt().isAfter(now.minus(grace))
                    && repository.existsByFamilyIdAndRevokedAtIsNullAndExpiresAtAfter(current.getFamilyId(), now);
            if (recentlyRotated && current.getUser().isEnabled()) {
                return issueInFamily(current.getUser(), current.getFamilyId(), current.isRememberMe(), info);
            }
            repository.revokeFamily(current.getFamilyId(), now);
            log.warn("Refresh token reuse detected for user {} — session family revoked", current.getUser().getId());
            throw ApiException.unauthorized("REFRESH_TOKEN_REUSED", "Session expired. Please sign in again.");
        }
        if (!current.getExpiresAt().isAfter(now)) {
            throw ApiException.unauthorized("REFRESH_TOKEN_EXPIRED", "Session expired. Please sign in again.");
        }
        User user = current.getUser();
        if (!user.isEnabled()) {
            repository.revokeFamily(current.getFamilyId(), now);
            throw ApiException.forbidden("ACCOUNT_DISABLED", "This account has been disabled");
        }
        current.setRevokedAt(now);
        return issueInFamily(user, current.getFamilyId(), current.isRememberMe(), info);
    }

    /** Revoke the whole login session the token belongs to (logout). Unknown tokens are ignored. */
    @Transactional
    public void revokeSession(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        repository.findByTokenHash(Tokens.sha256(rawToken))
                .ifPresent(t -> repository.revokeFamily(t.getFamilyId(), Instant.now()));
    }

    @Transactional
    public void revokeAll(Long userId) {
        repository.revokeAllForUser(userId, Instant.now());
    }

    private RefreshToken find(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw ApiException.unauthorized("REFRESH_TOKEN_MISSING", "Not signed in");
        }
        return repository.findByTokenHash(Tokens.sha256(rawToken))
                .orElseThrow(() -> ApiException.unauthorized("REFRESH_TOKEN_INVALID", "Session expired. Please sign in again."));
    }

    /** Housekeeping: delete tokens that expired more than a week ago. Runs daily at 03:30. */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpired() {
        int deleted = repository.deleteExpiredBefore(Instant.now().minus(7, ChronoUnit.DAYS));
        if (deleted > 0) log.info("Purged {} expired refresh tokens", deleted);
    }
}
