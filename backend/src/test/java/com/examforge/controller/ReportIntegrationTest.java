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

class ReportIntegrationTest extends IntegrationTestBase {

    @Test
    void examReportAttemptsAndCsv() throws Exception {
        String admin = "Bearer " + loginAndGetAccessToken("admin@examforge.test", "AdminPass123");
        JsonNode cat = json.readTree(mvc.perform(get("/api/v1/exam-categories/gate")).andReturn().getResponse().getContentAsString());
        long q = json.readTree(mvc.perform(post("/api/v1/admin/questions").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("subjectId", cat.at("/subjects/0/id").asLong(), "type", "MCQ", "questionText", "Report Q " + uniqueName(),
                        "difficulty", "HARD", "marks", 2, "negativeMarks", 0.5, "status", "APPROVED",
                        "options", List.of(Map.of("text", "right", "correct", true), Map.of("text", "wrong", "correct", false))))))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        JsonNode exam = json.readTree(mvc.perform(post("/api/v1/admin/exams").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("categoryId", cat.get("id").asLong(), "title", "GATE report " + uniqueName(), "type", "MOCK",
                        "difficulty", "HARD", "durationMinutes", 10, "passMarks", 2, "allowNavigation", true, "resultRelease", "IMMEDIATE"))))
                .andReturn().getResponse().getContentAsString());
        long examId = exam.at("/summary/id").asLong();
        String slug = exam.at("/summary/slug").asText();
        mvc.perform(put("/api/v1/admin/exams/" + examId + "/questions").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("questions", List.of(Map.of("questionId", q))))));
        mvc.perform(post("/api/v1/admin/exams/" + examId + "/publish").header("Authorization", admin)).andExpect(status().isOk());

        String studentToken = null;
        for (String answer : List.of("right", "wrong")) {
            String u = uniqueName();
            registerStudent(u, "Secret123");
            String token = "Bearer " + loginAndGetAccessToken(u, "Secret123");
            studentToken = token;
            JsonNode a = json.readTree(mvc.perform(post("/api/v1/exams/" + slug + "/attempts").header("Authorization", token))
                    .andReturn().getResponse().getContentAsString());
            long opt = 0;
            for (JsonNode o : a.at("/questions/0/options")) if (o.get("text").asText().equals(answer)) opt = o.get("id").asLong();
            mvc.perform(post("/api/v1/attempts/" + a.get("attemptId").asLong() + "/submit").header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(Map.of("answers", List.of(Map.of("questionId", q, "selectedOptionIds", List.of(opt),
                            "markedForReview", false, "visited", true, "seq", 1))))))
                    .andExpect(status().isOk());
        }

        mvc.perform(get("/api/v1/admin/reports/exams/" + examId).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finishedAttempts").value(2))
                .andExpect(jsonPath("$.uniqueStudents").value(2))
                .andExpect(jsonPath("$.highestScore").value(2.0))
                .andExpect(jsonPath("$.lowestScore").value(-0.5))
                .andExpect(jsonPath("$.averageScore").value(0.75))
                .andExpect(jsonPath("$.passRate").value(50.0))
                .andExpect(jsonPath("$.questions[0].correct").value(1))
                .andExpect(jsonPath("$.questions[0].incorrect").value(1))
                .andExpect(jsonPath("$.questions[0].accuracy").value(50.0))
                .andExpect(jsonPath("$.scoreDistribution.length()").value(10))
                .andExpect(jsonPath("$.scoreDistribution[0].count").value(1))
                .andExpect(jsonPath("$.scoreDistribution[9].count").value(1));

        mvc.perform(get("/api/v1/admin/reports/exams/" + examId + "/attempts").header("Authorization", admin))
                .andExpect(jsonPath("$.totalElements").value(2));

        String csv = mvc.perform(get("/api/v1/admin/reports/exams/" + examId + "/attempts.csv").header("Authorization", admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String[] lines = csv.replace("﻿", "").split("\r\n");
        assertThat(lines[0]).startsWith("attempt_id,username,full_name");
        assertThat(lines).hasSize(3);

        mvc.perform(get("/api/v1/admin/reports/exams/" + examId).header("Authorization", studentToken))
                .andExpect(status().isForbidden());
    }
}
