package com.examforge.controller;

import com.examforge.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Server-side role checks: students can never reach admin endpoints. */
class AuthorizationIntegrationTest extends IntegrationTestBase {

    private static final String ADMIN_EMAIL = "admin@examforge.test";
    private static final String ADMIN_PASSWORD = "AdminPass123";

    @Test
    void protectedEndpointsRequireAValidToken() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void studentCannotUseAdminEndpoints() throws Exception {
        String username = uniqueName();
        registerStudent(username, "Secret123");
        String token = loginAndGetAccessToken(username, "Secret123");

        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));
        mvc.perform(get("/api/v1/admin/stats").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void bootstrapAdminCanManageUsersButNotDemoteSelf() throws Exception {
        String adminToken = loginAndGetAccessToken(ADMIN_EMAIL, ADMIN_PASSWORD);
        mvc.perform(get("/api/v1/admin/stats").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAdmins").value(1));

        String username = uniqueName();
        long studentId = registerStudent(username, "Secret123").at("/user/id").asLong();
        mvc.perform(put("/api/v1/admin/users/" + studentId + "/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("roles", new String[]{"ROLE_STUDENT", "ROLE_ADMIN"}))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(2));

        long adminId = mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString().transform(s -> {
                    try { return json.readTree(s).get("id").asLong(); } catch (Exception e) { throw new RuntimeException(e); }
                });
        mvc.perform(put("/api/v1/admin/users/" + adminId + "/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("roles", new String[]{"ROLE_STUDENT"}))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_DEMOTE_SELF"));
    }

    @Test
    void studentCannotPromoteThemselves() throws Exception {
        String username = uniqueName();
        long id = registerStudent(username, "Secret123").at("/user/id").asLong();
        String token = loginAndGetAccessToken(username, "Secret123");
        mvc.perform(put("/api/v1/admin/users/" + id + "/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("roles", new String[]{"ROLE_ADMIN"}))))
                .andExpect(status().isForbidden());
    }
}
