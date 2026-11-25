package com.examforge.controller;

import com.examforge.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Students only see published exams, and never see questions or answers on these endpoints. */
class ExamBrowseIntegrationTest extends IntegrationTestBase {

    @Test
    void studentsSeePublishedExamsWithoutAnswers() throws Exception {
        String admin = "Bearer " + loginAndGetAccessToken("admin@examforge.test", "AdminPass123");
        JsonNode cat = json.readTree(mvc.perform(get("/api/v1/exam-categories/banking")).andReturn().getResponse().getContentAsString());
        long categoryId = cat.get("id").asLong();
        long subjectId = cat.at("/subjects/0/id").asLong();
        String secret = "SECRET-OPTION-" + uniqueName();

        long qid = json.readTree(mvc.perform(post("/api/v1/admin/questions").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("subjectId", subjectId, "type", "MCQ", "questionText", "Browse test Q",
                                "difficulty", "EASY", "marks", 1, "negativeMarks", 0.25, "status", "APPROVED",
                                "options", List.of(Map.of("text", secret, "correct", true), Map.of("text", "Other", "correct", false))))))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        String marker = uniqueName();
        Map<String, Object> exam = new HashMap<>(Map.of("categoryId", categoryId, "title", "Bank PO Mock " + marker,
                "type", "MOCK", "difficulty", "EASY", "durationMinutes", 20, "allowNavigation", true, "resultRelease", "IMMEDIATE"));
        JsonNode published = json.readTree(mvc.perform(post("/api/v1/admin/exams").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(exam))).andReturn().getResponse().getContentAsString());
        long examId = published.at("/summary/id").asLong();
        String slug = published.at("/summary/slug").asText();
        mvc.perform(put("/api/v1/admin/exams/" + examId + "/questions").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("questions", List.of(Map.of("questionId", qid))))));
        mvc.perform(post("/api/v1/admin/exams/" + examId + "/publish").header("Authorization", admin)).andExpect(status().isOk());

        exam.put("title", "Draft only " + marker);
        mvc.perform(post("/api/v1/admin/exams").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(exam))).andExpect(status().isCreated());

        String u = uniqueName();
        registerStudent(u, "Secret123");
        String student = "Bearer " + loginAndGetAccessToken(u, "Secret123");

        String list = mvc.perform(get("/api/v1/exams").header("Authorization", student)
                        .param("q", marker).param("category", "banking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat(list).contains("Bank PO Mock").doesNotContain("Draft only");

        String detail = mvc.perform(get("/api/v1/exams/" + slug).header("Authorization", student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.negativeMarking").value(true))
                .andExpect(jsonPath("$.summary.questionCount").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat(detail).doesNotContain(secret).doesNotContain("\"correct\"");

        mvc.perform(get("/api/v1/exams")).andExpect(status().isUnauthorized());
    }
}
