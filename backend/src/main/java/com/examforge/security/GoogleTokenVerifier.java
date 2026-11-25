package com.examforge.security;

import com.examforge.config.AppProperties;
import com.examforge.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Verifies Google Sign-In ID tokens on the server: signature (Google's public keys), expiry, issuer,
 * audience (must be one of our OAuth client ids) and that Google has verified the email address.
 * Works for tokens from the web button and from native Android/iOS sign-in.
 */
@Component
public class GoogleTokenVerifier {
    private static final String GOOGLE_JWKS = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> GOOGLE_ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private final AppProperties props;
    private volatile JwtDecoder decoder;

    public GoogleTokenVerifier(AppProperties props) {
        this.props = props;
    }

    public record GoogleIdentity(String subject, String email, String name, String pictureUrl) {}

    public GoogleIdentity verify(String idToken) {
        if (!props.google().enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_NOT_CONFIGURED",
                    "Google sign-in is not configured on this server");
        }
        Jwt jwt;
        try {
            jwt = decoder().decode(idToken);
        } catch (JwtException e) {
            throw ApiException.unauthorized("GOOGLE_TOKEN_INVALID", "Google sign-in failed. Please try again.");
        }
        Object verified = jwt.getClaim("email_verified");
        boolean emailVerified = Boolean.TRUE.equals(verified) || "true".equals(String.valueOf(verified));
        String email = jwt.getClaimAsString("email");
        if (email == null || !emailVerified) {
            throw ApiException.unauthorized("GOOGLE_EMAIL_NOT_VERIFIED", "Your Google email address is not verified");
        }
        return new GoogleIdentity(jwt.getSubject(), email.toLowerCase(Locale.ROOT),
                jwt.getClaimAsString("name"), jwt.getClaimAsString("picture"));
    }

    private JwtDecoder decoder() {
        if (decoder == null) {
            synchronized (this) {
                if (decoder == null) {
                    List<String> audiences = props.google().allClientIds();
                    NimbusJwtDecoder d = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWKS).build();
                    d.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                            new JwtTimestampValidator(),
                            new JwtClaimValidator<Object>(JwtClaimNames.ISS,
                                    iss -> iss != null && GOOGLE_ISSUERS.contains(String.valueOf(iss))),
                            new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                                    aud -> aud != null && aud.stream().anyMatch(audiences::contains))));
                    decoder = d;
                }
            }
        }
        return decoder;
    }
}
