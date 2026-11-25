package com.examforge.controller;

import com.examforge.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExamBuilderIntegrationTest extends IntegrationTestBase {

    private String admin;
    private long subjectId;
    private long categoryId;

    @BeforeEach
    void setUp() throws Exception {
        admin = "Bearer " + loginAndGetAccessToken("admin@examforge.test", "AdminPass123");
        JsonNode cat = json.readTree(mvc.perform(get("/api/v1/exam-categories/jee-neet")).andReturn().getResponse().getContentAsString());
        categoryId = cat.get("id").asLong();
        subjectId = cat.at("/subjects/0/id").asLong();
    }

    private long createQuestion(String text) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/admin/questions").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("subjectId", subjectId, "type", "MCQ", "questionText", text,
                                "difficulty", "MEDIUM", "marks", 4, "negativeMarks", 1,
                                "options", List.of(Map.of("text", "A1", "correct", true), Map.of("text", "B1", "correct", false))))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private Map<String, Object> examBody(String title) {
        Map<String, Object> m = new HashMap<>();
        m.put("categoryId", categoryId);
        m.put("title", title);
        m.put("type", "MOCK");
        m.put("difficulty", "MEDIUM");
        m.put("durationMinutes", 60);
        m.put("passMarks", 4);
        m.put("allowNavigation", true);
        m.put("resultRelease", "IMMEDIATE");
        return m;
    }

    @Test
    void fullExamBuilderLifecycle() throws Exception {
        long q1 = createQuestion("Exam builder Q1 " + uniqueName());
        long q2 = createQuestion("Exam builder Q2 " + uniqueName());

        JsonNode exam = json.readTree(mvc.perform(post("/api/v1/admin/exams").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(examBody("JEE Mock " + uniqueName()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        long examId = exam.at("/summary/id").asLong();
        assertThat(exam.at("/summary/status").asText()).isEqualTo("DRAFT");

        // q2 gets a per-exam marking override
        mvc.perform(put("/api/v1/admin/exams/" + examId + "/questions").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("questions", List.of(
                                Map.of("questionId", q1),
                                Map.of("questionId", q2, "marks", 2, "negativeMarks", 0.5))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.questionCount").value(2))
                .andExpect(jsonPath("$.summary.totalMarks").value(6.0))
                .andExpect(jsonPath("$.questions[1].marks").value(2.0));

        // questions are still drafts -> cannot publish
        mvc.perform(post("/api/v1/admin/exams/" + examId + "/publish").header("Authorization", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EXAM_NOT_READY"));

        mvc.perform(post("/api/v1/admin/questions/bulk-status").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("ids", List.of(q1, q2), "status", "APPROVED"))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/exams/" + examId + "/publish").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("PUBLISHED"));

        // published exams and their questions are locked
        mvc.perform(put("/api/v1/admin/exams/" + examId).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(examBody("Renamed"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXAM_PUBLISHED"));
        mvc.perform(delete("/api/v1/admin/questions/" + q1).header("Authorization", admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUESTION_IN_USE"));

        // the category now shows a published exam
        mvc.perform(get("/api/v1/exam-categories/jee-neet"))
                .andExpect(jsonPath("$.publishedExamCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        mvc.perform(post("/api/v1/admin/exams/" + examId + "/unpublish").header("Authorization", admin))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/admin/exams/" + examId).header("Authorization", admin))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/admin/questions/" + q1).header("Authorization", admin))
                .andExpect(status().isNoContent());
    }

    @Test
    void rejectsBadScheduleAndDuplicateQuestions() throws Exception {
        Map<String, Object> body = examBody("Live " + uniqueName());
        body.put("type", "LIVE");
        body.put("startsAt", "2030-01-01T10:00:00Z");
        body.put("endsAt", "2030-01-01T10:30:00Z"); // shorter than the 60 minute duration
        mvc.perform(post("/api/v1/admin/exams").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SCHEDULE"));

        long examId = json.readTree(mvc.perform(post("/api/v1/admin/exams").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(examBody("Dup " + uniqueName()))))
                .andReturn().getResponse().getContentAsString()).at("/summary/id").asLong();
        long q = createQuestion("Dup check " + uniqueName());
        mvc.perform(put("/api/v1/admin/exams/" + examId + "/questions").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("questions", List.of(Map.of("questionId", q), Map.of("questionId", q))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_QUESTION"));
    }
}
