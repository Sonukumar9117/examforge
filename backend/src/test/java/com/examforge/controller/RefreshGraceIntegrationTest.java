package com.examforge.controller;

import com.examforge.IntegrationTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Two browser tabs refreshing at the same time must not log the user out. */
@TestPropertySource(properties = "app.jwt.refresh-reuse-grace=30s")
class RefreshGraceIntegrationTest extends IntegrationTestBase {

    @Test
    void concurrentRefreshWithinGraceWindowIsAllowedButLogoutStillWins() throws Exception {
        String username = uniqueName();
        registerStudent(username, "Secret123");
        Cookie original = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("identifier", username, "password", "Secret123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(AuthController.REFRESH_COOKIE);

        // "Tab A" rotates, then "tab B" presents the same (now rotated) token a moment later.
        mvc.perform(post("/api/v1/auth/refresh").cookie(original)).andExpect(status().isOk());
        Cookie tabB = mvc.perform(post("/api/v1/auth/refresh").cookie(original))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(AuthController.REFRESH_COOKIE);

        // After logout the whole session is dead, even inside the grace window.
        mvc.perform(post("/api/v1/auth/logout").cookie(tabB)).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh").cookie(original)).andExpect(status().isUnauthorized());
    }
}
