package com.examforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

/** Typed view of all {@code app.*} settings from application.properties. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Auth auth,
        Google google,
        Admin admin,
        Mail mail,
        Cors cors,
        String frontendUrl) {

    public record Jwt(String secret, String issuer, Duration accessTokenTtl,
                      Duration refreshTokenTtl, Duration refreshTokenRememberTtl,
                      /* concurrent refreshes within this window are not treated as token theft */
                      Duration refreshReuseGrace) {}

    public record Auth(boolean requireVerifiedEmail, boolean cookieSecure, String cookieSameSite,
                       int maxFailedLogins, Duration lockDuration, int rateLimitPerMinute) {}

    public record Google(String webClientId, List<String> extraClientIds) {
        /** Every OAuth client id whose ID tokens we accept (web + mobile apps). */
        public List<String> allClientIds() {
            Stream<String> extra = extraClientIds == null ? Stream.empty() : extraClientIds.stream();
            return Stream.concat(Stream.ofNullable(webClientId), extra)
                    .map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
        }

        public boolean enabled() {
            return !allClientIds().isEmpty();
        }
    }

    public record Admin(String email, String username, String password, String fullName) {}

    public record Mail(boolean enabled, String from) {}

    public record Cors(List<String> allowedOrigins) {}
}
