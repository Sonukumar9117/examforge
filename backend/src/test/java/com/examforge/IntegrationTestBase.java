package com.examforge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"mysql", "test"})
@Import(IntegrationTestBase.CleanDatabase.class)
public abstract class IntegrationTestBase {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    /** Start every test run from an empty, freshly migrated schema. */
    @TestConfiguration
    static class CleanDatabase {
        private static boolean cleaned;

        @Bean
        FlywayMigrationStrategy cleanMigrate() {
            return (Flyway flyway) -> {
                synchronized (CleanDatabase.class) {
                    if (!cleaned) {
                        flyway.clean();
                        cleaned = true;
                    }
                }
                flyway.migrate();
            };
        }
    }

    protected static String uniqueName() {
        return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    protected String toJson(Object body) throws Exception {
        return json.writeValueAsString(body);
    }

    /** Registers a new student and returns the parsed response body. */
    protected JsonNode registerStudent(String username, String password) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "fullName", "Test Student",
                                "username", username,
                                "email", username + "@example.com",
                                "password", password,
                                "acceptTerms", true))))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(res.getResponse().getContentAsString());
    }

    protected String loginAndGetAccessToken(String identifier, String password) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("identifier", identifier, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }
}
