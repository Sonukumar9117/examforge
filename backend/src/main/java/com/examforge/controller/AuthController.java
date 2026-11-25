package com.examforge.controller;

import com.examforge.config.AppProperties;
import com.examforge.dto.request.ForgotPasswordRequest;
import com.examforge.dto.request.GoogleLoginRequest;
import com.examforge.dto.request.LoginRequest;
import com.examforge.dto.request.RefreshRequest;
import com.examforge.dto.request.RegisterRequest;
import com.examforge.dto.request.ResetPasswordRequest;
import com.examforge.dto.request.TokenRequest;
import com.examforge.dto.response.AuthConfigResponse;
import com.examforge.dto.response.AuthResponse;
import com.examforge.dto.response.MessageResponse;
import com.examforge.dto.response.UsernameAvailabilityResponse;
import com.examforge.service.AuthService;
import com.examforge.service.UserService;
import com.examforge.util.RequestInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Arrays;

/**
 * Authentication endpoints for web and mobile.
 * Web: refresh token travels in an HttpOnly cookie. Mobile: send {@code X-Client-Type: mobile} and the
 * refresh token is returned in the body instead (store it in Keychain/Keystore).
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
public class AuthController {
    static final String REFRESH_COOKIE = "ef_refresh";
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;
    private final UserService userService;
    private final AppProperties props;

    public AuthController(AuthService authService, UserService userService, AppProperties props) {
        this.authService = authService;
        this.userService = userService;
        this.props = props;
    }

    @GetMapping("/config")
    @Operation(summary = "Public client configuration (Google client id, feature flags)")
    public AuthConfigResponse config() {
        return new AuthConfigResponse(props.google().enabled(), blankToNull(props.google().webClientId()),
                props.auth().requireVerifiedEmail());
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req, HttpServletRequest http) {
        RequestInfo info = RequestInfo.from(http);
        return respond(HttpStatus.CREATED, authService.register(req, info), info);
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in with email or username + password")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        RequestInfo info = RequestInfo.from(http);
        return respond(HttpStatus.OK, authService.login(req, info), info);
    }

    @PostMapping("/google")
    @Operation(summary = "Sign in with a Google ID token (from the web button or native mobile sign-in)")
    public ResponseEntity<AuthResponse> google(@Valid @RequestBody GoogleLoginRequest req, HttpServletRequest http) {
        RequestInfo info = RequestInfo.from(http);
        return respond(HttpStatus.OK, authService.loginWithGoogle(req, info), info);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token (cookie or body) for a new access token; rotates the refresh token")
    public ResponseEntity<AuthResponse> refresh(@RequestBody(required = false) RefreshRequest body,
                                                HttpServletRequest http) {
        RequestInfo info = RequestInfo.from(http);
        return respond(HttpStatus.OK, authService.refresh(refreshTokenFrom(body, http), info), info);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequest body, HttpServletRequest http) {
        authService.logout(refreshTokenFrom(body, http));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                .build();
    }

    @GetMapping("/username-available")
    public UsernameAvailabilityResponse usernameAvailable(@RequestParam String username) {
        return new UsernameAvailabilityResponse(username, authService.isUsernameAvailable(username));
    }

    @PostMapping("/verify-email")
    public MessageResponse verifyEmail(@Valid @RequestBody TokenRequest req) {
        authService.verifyEmail(req.token());
        return new MessageResponse("Email verified");
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest req, HttpServletRequest http) {
        authService.forgotPassword(req.email(), RequestInfo.from(http));
        return new MessageResponse("If an account exists for this email, a reset link has been sent.");
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest req, HttpServletRequest http) {
        authService.resetPassword(req.token(), req.newPassword(), RequestInfo.from(http));
        return new MessageResponse("Password updated. You can now sign in.");
    }

    // ------------------------------------------------------------------ helpers

    private ResponseEntity<AuthResponse> respond(HttpStatus status, AuthService.AuthResult result, RequestInfo info) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        String bodyRefreshToken = null;
        if (result.refresh() != null) {
            if (info.mobileClient()) {
                bodyRefreshToken = result.refresh().rawToken();
            } else {
                // Session cookie unless "remember me", in which case it survives browser restarts.
                Duration maxAge = result.refresh().entity().isRememberMe() ? result.refresh().ttl() : null;
                builder.header(HttpHeaders.SET_COOKIE, refreshCookie(result.refresh().rawToken(), maxAge).toString());
            }
        }
        AuthResponse body = new AuthResponse(
                result.accessToken(),
                result.accessToken() == null ? null : "Bearer",
                result.accessToken() == null ? null : authServiceTtl(),
                bodyRefreshToken,
                userService.toResponse(result.user()),
                result.emailVerificationRequired());
        return builder.body(body);
    }

    private Long authServiceTtl() {
        return props.jwt().accessTokenTtl().toSeconds();
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder cookie = ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(props.auth().cookieSecure())
                .sameSite(props.auth().cookieSameSite())
                .path(COOKIE_PATH);
        if (maxAge != null) cookie.maxAge(maxAge);
        return cookie.build();
    }

    private static String refreshTokenFrom(RefreshRequest body, HttpServletRequest http) {
        if (body != null && body.refreshToken() != null && !body.refreshToken().isBlank()) {
            return body.refreshToken();
        }
        Cookie[] cookies = http.getCookies();
        if (cookies == null) return null;
        return Arrays.stream(cookies).filter(c -> REFRESH_COOKIE.equals(c.getName()))
                .map(Cookie::getValue).findFirst().orElse(null);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
