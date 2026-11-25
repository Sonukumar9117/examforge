package com.examforge.service;

import com.examforge.config.AppProperties;
import com.examforge.dto.request.GoogleLoginRequest;
import com.examforge.dto.request.LoginRequest;
import com.examforge.dto.request.RegisterRequest;
import com.examforge.entity.AuthToken;
import com.examforge.entity.Role;
import com.examforge.entity.User;
import com.examforge.entity.UserProfile;
import com.examforge.exception.ApiException;
import com.examforge.repository.AuthTokenRepository;
import com.examforge.repository.RoleRepository;
import com.examforge.repository.UserProfileRepository;
import com.examforge.repository.UserRepository;
import com.examforge.security.GoogleTokenVerifier.GoogleIdentity;
import com.examforge.security.GoogleTokenVerifier;
import com.examforge.security.JwtService;
import com.examforge.util.RequestInfo;
import com.examforge.util.Tokens;
import com.examforge.util.ValidationPatterns;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final Duration VERIFY_TTL = Duration.ofHours(24);
    private static final Duration RESET_TTL = Duration.ofMinutes(30);
    private static final String BAD_CREDENTIALS = "Invalid email/username or password";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final RoleRepository roles;
    private final UserProfileRepository profiles;
    private final AuthTokenRepository authTokens;
    private final RefreshTokenService refreshTokens;
    private final JwtService jwtService;
    private final GoogleTokenVerifier googleVerifier;
    private final PasswordEncoder passwordEncoder;
    private final MailService mail;
    private final AuditService audit;
    private final AppProperties props;
    /** Pre-computed hash so unknown users take as long to reject as wrong passwords (no user enumeration by timing). */
    private  String dummyHash;


    @PostConstruct
    public void init() {
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    /** Result of a successful sign-in. {@code refresh} is null when no session was started. */
    public record AuthResult(User user, String accessToken, RefreshTokenService.Issued refresh,
                             boolean emailVerificationRequired) {}

    // ---------------------------------------------------------------- register / login

    @Transactional
    public AuthResult register(RegisterRequest req, RequestInfo info) {
        String email = normalizeEmail(req.email());
        String username = req.username().trim();
        if (users.existsByUsernameIgnoreCase(username)) {
            throw ApiException.conflict("USERNAME_TAKEN", "This username is already taken");
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists");
        }
        User user = new User(username, email, req.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        // Every self-registered account is a student. Roles are never taken from the request.
        user.getRoles().add(roles.require(Role.STUDENT));
        users.save(user);
        profiles.save(new UserProfile(user));
        sendVerification(user);
        audit.record(user, "USER_REGISTERED", "User", user.getId(), null, info.ip());

        if (props.auth().requireVerifiedEmail()) {
            return new AuthResult(user, null, null, true);
        }
        return startSession(user, false, info);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult login(LoginRequest req, RequestInfo info) {
        String identifier = req.identifier().trim();
        User user = (identifier.contains("@")
                ? users.findByEmailIgnoreCase(normalizeEmail(identifier))
                : users.findByUsernameIgnoreCase(identifier)).orElse(null);

        Instant now = Instant.now();
        if (user == null || user.getPasswordHash() == null) {
            passwordEncoder.matches(req.password(), dummyHash);
            throw ApiException.unauthorized("BAD_CREDENTIALS", BAD_CREDENTIALS);
        }
        if (user.isLocked(now)) {
            throw new ApiException(HttpStatus.LOCKED, "ACCOUNT_LOCKED",
                    "Too many failed attempts. Try again in a few minutes or reset your password.");
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            registerFailedLogin(user, now, info);
            throw ApiException.unauthorized("BAD_CREDENTIALS", BAD_CREDENTIALS);
        }
        ensureCanSignIn(user);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        return startSession(user, req.rememberMe(), info);
    }

    private void registerFailedLogin(User user, Instant now, RequestInfo info) {
        int attempts = user.getFailedLoginAttempts() + 1;
        if (attempts >= props.auth().maxFailedLogins()) {
            user.setLockedUntil(now.plus(props.auth().lockDuration()));
            user.setFailedLoginAttempts(0);
            audit.record(user, "ACCOUNT_LOCKED", "User", user.getId(), "too many failed logins", info.ip());
        } else {
            user.setFailedLoginAttempts(attempts);
        }
    }

    // ---------------------------------------------------------------- Google

    @Transactional
    public AuthResult loginWithGoogle(GoogleLoginRequest req, RequestInfo info) {
        GoogleIdentity google = googleVerifier.verify(req.idToken());

        User user = users.findByGoogleId(google.subject()).orElse(null);
        if (user == null) {
            user = users.findByEmailIgnoreCase(google.email()).orElse(null);
            if (user != null) {
                // Google has verified ownership of this email, so it is safe to link the accounts.
                user.setGoogleId(google.subject());
                user.setEmailVerified(true);
                if (user.getAvatarUrl() == null) user.setAvatarUrl(google.pictureUrl());
                audit.record(user, "GOOGLE_LINKED", "User", user.getId(), null, info.ip());
            }
        }
        if (user == null) {
            String name = google.name() == null || google.name().isBlank()
                    ? google.email().substring(0, google.email().indexOf('@')) : google.name();
            user = new User(generateUsername(google.email()), google.email(), truncate(name, 100));
            user.setGoogleId(google.subject());
            user.setEmailVerified(true);
            user.setAvatarUrl(google.pictureUrl() != null && google.pictureUrl().length() <= 500 ? google.pictureUrl() : null);
            user.getRoles().add(roles.require(Role.STUDENT));
            users.save(user);
            profiles.save(new UserProfile(user));
            audit.record(user, "USER_REGISTERED", "User", user.getId(), "via Google", info.ip());
        }
        ensureCanSignIn(user);
        return startSession(user, req.rememberMe(), info);
    }

    // ---------------------------------------------------------------- sessions

    /** noRollbackFor: a detected token reuse must stay revoked even though we throw. */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult refresh(String rawRefreshToken, RequestInfo info) {
        RefreshTokenService.Issued issued = refreshTokens.rotate(rawRefreshToken, info);
        User user = issued.entity().getUser();
        return new AuthResult(user, jwtService.createAccessToken(user), issued, false);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokens.revokeSession(rawRefreshToken);
    }

    private AuthResult startSession(User user, boolean rememberMe, RequestInfo info) {
        user.setLastLoginAt(Instant.now());
        RefreshTokenService.Issued refresh = refreshTokens.issue(user, rememberMe, info);
        return new AuthResult(user, jwtService.createAccessToken(user), refresh, false);
    }

    private void ensureCanSignIn(User user) {
        if (!user.isEnabled()) {
            throw ApiException.forbidden("ACCOUNT_DISABLED", "This account has been disabled. Contact support.");
        }
        if (props.auth().requireVerifiedEmail() && !user.isEmailVerified()) {
            throw ApiException.forbidden("EMAIL_NOT_VERIFIED", "Please verify your email address before signing in");
        }
    }

    // ---------------------------------------------------------------- email verification

    @Transactional
    public void sendVerification(User user) {
        if (user.isEmailVerified()) return;
        String raw = createAuthToken(user, AuthToken.Type.EMAIL_VERIFICATION, VERIFY_TTL);
        mail.sendVerificationEmail(user.getEmail(), user.getFullName(), raw);
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        AuthToken token = consume(rawToken, AuthToken.Type.EMAIL_VERIFICATION);
        token.getUser().setEmailVerified(true);
    }

    // ---------------------------------------------------------------- password reset

    /** Always succeeds from the caller's point of view, so it can't be used to discover accounts. */
    @Transactional
    public void forgotPassword(String email, RequestInfo info) {
        users.findByEmailIgnoreCase(normalizeEmail(email)).filter(User::isEnabled).ifPresent(user -> {
            String raw = createAuthToken(user, AuthToken.Type.PASSWORD_RESET, RESET_TTL);
            mail.sendPasswordResetEmail(user.getEmail(), user.getFullName(), raw);
            audit.record(user, "PASSWORD_RESET_REQUESTED", "User", user.getId(), null, info.ip());
        });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword, RequestInfo info) {
        AuthToken token = consume(rawToken, AuthToken.Type.PASSWORD_RESET);
        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setEmailVerified(true); // they proved access to the inbox
        refreshTokens.revokeAll(user.getId()); // sign out every device
        audit.record(user, "PASSWORD_RESET", "User", user.getId(), null, info.ip());
    }

    // ---------------------------------------------------------------- helpers

    public boolean isUsernameAvailable(String username) {
        return username != null && username.matches(ValidationPatterns.USERNAME_REGEX)
                && !users.existsByUsernameIgnoreCase(username.trim());
    }

    private String createAuthToken(User user, AuthToken.Type type, Duration ttl) {
        authTokens.invalidateAll(user.getId(), type, Instant.now());
        String raw = Tokens.newOpaqueToken();
        authTokens.save(new AuthToken(user, Tokens.sha256(raw), type, Instant.now().plus(ttl)));
        return raw;
    }

    private AuthToken consume(String rawToken, AuthToken.Type type) {
        AuthToken token = authTokens.findByTokenHashAndType(Tokens.sha256(rawToken), type)
                .filter(t -> t.isUsable(Instant.now()))
                .orElseThrow(() -> ApiException.badRequest("TOKEN_INVALID", "This link is invalid or has expired"));
        token.setUsedAt(Instant.now());
        return token;
    }

    private String generateUsername(String email) {
        String base = email.substring(0, email.indexOf('@')).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        if (base.length() < 3) base = "user" + base;
        base = truncate(base, 20);
        String candidate = base;
        while (users.existsByUsernameIgnoreCase(candidate)) {
            candidate = base + (1000 + RANDOM.nextInt(9000));
        }
        return candidate;
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
