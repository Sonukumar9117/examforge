package com.examforge.controller;

import com.examforge.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatalogIntegrationTest extends IntegrationTestBase {

    @Test
    void publicCanListSeededCategoriesAndSubjects() throws Exception {
        String body = mvc.perform(get("/api/v1/exam-categories"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode list = json.readTree(body);
        assertThat(list.size()).isGreaterThanOrEqualTo(9);
        assertThat(list.findValuesAsText("slug")).contains("upsc-civil-services", "jee-neet");

        mvc.perform(get("/api/v1/exam-categories/jee-neet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjects.length()").value(4));
        mvc.perform(get("/api/v1/subjects")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/exam-categories/does-not-exist")).andExpect(status().isNotFound());
    }

    @Test
    void onlyAdminsCanManageCategories() throws Exception {
        String student = uniqueName();
        registerStudent(student, "Secret123");
        String studentToken = loginAndGetAccessToken(student, "Secret123");
        mvc.perform(post("/api/v1/admin/categories").header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "Hack", "displayOrder", 1, "active", true))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCreatesUpdatesAndDeletesCategoryAndSubject() throws Exception {
        String admin = "Bearer " + loginAndGetAccessToken("admin@examforge.test", "AdminPass123");
        String subjectName = "Ethics " + uniqueName();

        long subjectId = json.readTree(mvc.perform(post("/api/v1/admin/subjects").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", subjectName))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        String catName = "CAPF " + uniqueName();
        JsonNode created = json.readTree(mvc.perform(post("/api/v1/admin/categories").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", catName, "displayOrder", 50, "active", true,
                                "icon", "Shield", "subjectIds", List.of(subjectId)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        long categoryId = created.get("id").asLong();
        assertThat(created.get("slug").asText()).startsWith("capf-");
        assertThat(created.at("/subjects/0/id").asLong()).isEqualTo(subjectId);

        // duplicate name (case-insensitive)
        mvc.perform(post("/api/v1/admin/categories").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", catName.toUpperCase(), "displayOrder", 1, "active", true))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_EXISTS"));

        // deactivate -> hidden from the public list
        mvc.perform(put("/api/v1/admin/categories/" + categoryId).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", catName, "displayOrder", 50, "active", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        String publicList = mvc.perform(get("/api/v1/exam-categories")).andReturn().getResponse().getContentAsString();
        assertThat(publicList).doesNotContain(catName);

        mvc.perform(delete("/api/v1/admin/categories/" + categoryId).header("Authorization", admin))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/admin/subjects/" + subjectId).header("Authorization", admin))
                .andExpect(status().isNoContent());
    }
}
