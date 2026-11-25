package com.examforge.controller;

import com.examforge.IntegrationTestBase;
import com.examforge.service.MailService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthIntegrationTest extends IntegrationTestBase {

    private static final String PASSWORD = "Secret123";

    @MockBean
    MailService mailService;

    @Test
    void registerCreatesStudentEvenIfAdminRoleIsRequested() throws Exception {
        String username = uniqueName();
        Map<String, Object> body = new HashMap<>(Map.of(
                "fullName", "Sneaky User", "username", username, "email", username + "@example.com",
                "password", PASSWORD, "acceptTerms", true));
        body.put("roles", new String[]{"ROLE_ADMIN"}); // must be ignored

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.user.roles[0]").value("ROLE_STUDENT"))
                .andExpect(jsonPath("$.user.roles.length()").value(1))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")));
    }

    @Test
    void registerRejectsDuplicatesAndInvalidInput() throws Exception {
        String username = uniqueName();
        registerStudent(username, PASSWORD);

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of(
                        "fullName", "Other", "username", username.toUpperCase(), "email", uniqueName() + "@example.com",
                        "password", PASSWORD, "acceptTerms", true))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of(
                        "fullName", "X", "username", "a b", "email", "not-an-email",
                        "password", "short", "acceptTerms", false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.username").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.acceptTerms").exists());
    }

    @Test
    void loginWorksWithEmailOrUsernameAndRejectsWrongPassword() throws Exception {
        String username = uniqueName();
        registerStudent(username, PASSWORD);

        assertThat(loginAndGetAccessToken(username, PASSWORD)).isNotBlank();
        assertThat(loginAndGetAccessToken(username + "@EXAMPLE.com", PASSWORD)).isNotBlank();

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("identifier", username, "password", "Wrong123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("identifier", "nobody_" + username, "password", PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    @Test
    void accountLocksAfterRepeatedFailures() throws Exception {
        String username = uniqueName();
        registerStudent(username, PASSWORD);
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("identifier", username, "password", "Wrong123"))))
                    .andExpect(status().isUnauthorized());
        }
        // Even the correct password is refused while locked.
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("identifier", username, "password", PASSWORD))))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void refreshRotatesTokenAndDetectsReuse() throws Exception {
        String username = uniqueName();
        registerStudent(username, PASSWORD);
        Cookie first = loginCookie(username);

        MvcResult rotated = mvc.perform(post("/api/v1/auth/refresh").cookie(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        Cookie second = rotated.getResponse().getCookie(AuthController.REFRESH_COOKIE);
        assertThat(second).isNotNull();
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        // Replaying the old token revokes the whole session...
        mvc.perform(post("/api/v1/auth/refresh").cookie(first))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"));
        // ...so the newest token stops working too.
        mvc.perform(post("/api/v1/auth/refresh").cookie(second))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mobileClientsReceiveRefreshTokenInBodyAndCanLogout() throws Exception {
        String username = uniqueName();
        registerStudent(username, PASSWORD);

        MvcResult login = mvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("identifier", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn();
        String refresh = json.readTree(login.getResponse().getContentAsString()).get("refreshToken").asText();
        assertThat(refresh).isNotBlank();

        MvcResult refreshed = mvc.perform(post("/api/v1/auth/refresh").header("X-Client-Type", "mobile")
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("refreshToken", refresh))))
                .andExpect(status().isOk())
                .andReturn();
        String newRefresh = json.readTree(refreshed.getResponse().getContentAsString()).get("refreshToken").asText();

        mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("refreshToken", newRefresh))))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("refreshToken", newRefresh))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void emailVerificationAndPasswordResetFlow() throws Exception {
        String username = uniqueName();
        String email = username + "@example.com";
        JsonNode reg = registerStudent(username, PASSWORD);
        assertThat(reg.at("/user/emailVerified").asBoolean()).isFalse();

        ArgumentCaptor<String> verifyToken = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendVerificationEmail(eq(email), anyString(), verifyToken.capture());
        mvc.perform(post("/api/v1/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("token", verifyToken.getValue()))))
                .andExpect(status().isOk());
        // Tokens are single-use.
        mvc.perform(post("/api/v1/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("token", verifyToken.getValue()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));

        mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("email", email))))
                .andExpect(status().isAccepted());
        ArgumentCaptor<String> resetToken = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendPasswordResetEmail(eq(email), anyString(), resetToken.capture());

        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("token", resetToken.getValue(), "newPassword", "BrandNew456"))))
                .andExpect(status().isOk());

        assertThat(loginAndGetAccessToken(username, "BrandNew456")).isNotBlank();
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("identifier", username, "password", PASSWORD))))
                .andExpect(status().isUnauthorized());

        // Unknown emails get the same answer (no account discovery).
        mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("email", "nobody-" + email))))
                .andExpect(status().isAccepted());
    }

    @Test
    void usernameAvailabilityAndConfig() throws Exception {
        String username = uniqueName();
        registerStudent(username, PASSWORD);
        mvc.perform(get("/api/v1/auth/username-available").param("username", username))
                .andExpect(jsonPath("$.available").value(false));
        mvc.perform(get("/api/v1/auth/username-available").param("username", uniqueName()))
                .andExpect(jsonPath("$.available").value(true));
        mvc.perform(get("/api/v1/auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.googleEnabled").value(false));
    }

    @Test
    void googleLoginReportsWhenNotConfigured() throws Exception {
        mvc.perform(post("/api/v1/auth/google").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("idToken", "x.y.z"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GOOGLE_NOT_CONFIGURED"));
    }

    private Cookie loginCookie(String username) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("identifier", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = res.getResponse().getCookie(AuthController.REFRESH_COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        return cookie;
    }
}
