package com.examforge.controller;

import com.examforge.IntegrationTestBase;
import com.examforge.service.AttemptExpiryJob;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The exam hall: server timing, autosave, scoring with negative marks, idempotent submit, expiry, review rules. */
class AttemptIntegrationTest extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AttemptExpiryJob expiryJob;

    private String admin;
    private String student;
    private long subjectId;
    private long categoryId;

    @BeforeEach
    void setUp() throws Exception {
        admin = "Bearer " + loginAndGetAccessToken("admin@examforge.test", "AdminPass123");
        String u = uniqueName();
        registerStudent(u, "Secret123");
        student = "Bearer " + loginAndGetAccessToken(u, "Secret123");
        JsonNode cat = json.readTree(mvc.perform(get("/api/v1/exam-categories/ssc-cgl-chsl")).andReturn().getResponse().getContentAsString());
        categoryId = cat.get("id").asLong();
        subjectId = cat.at("/subjects/0/id").asLong();
    }

    // ------------------------------------------------------------------ fixtures

    private long question(String type, List<Map<String, Object>> options) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/admin/questions").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("subjectId", subjectId, "type", type, "questionText", "Q " + uniqueName(),
                                "difficulty", "EASY", "marks", 4, "negativeMarks", 1, "status", "APPROVED",
                                "explanation", "Because.", "options", options))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private static Map<String, Object> opt(String text, boolean correct) {
        return Map.of("text", text, "correct", correct);
    }

    /** Publishes an exam with: MCQ (A correct), MULTI (A + C correct), TRUE_FALSE (True correct). */
    private String publishExam(Map<String, Object> extra) throws Exception {
        long mcq = question("MCQ", List.of(opt("right", true), opt("wrong", false), opt("other", false)));
        long multi = question("MULTI_SELECT", List.of(opt("m1", true), opt("m2", false), opt("m3", true)));
        long tf = question("TRUE_FALSE", List.of(opt("True", true), opt("False", false)));
        Map<String, Object> body = new HashMap<>(Map.of("categoryId", categoryId, "title", "Hall " + uniqueName(),
                "type", "MOCK", "difficulty", "EASY", "durationMinutes", 30, "passMarks", 4,
                "allowNavigation", true, "resultRelease", "IMMEDIATE"));
        body.putAll(extra);
        JsonNode exam = json.readTree(mvc.perform(post("/api/v1/admin/exams").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body))).andReturn().getResponse().getContentAsString());
        long examId = exam.at("/summary/id").asLong();
        mvc.perform(put("/api/v1/admin/exams/" + examId + "/questions").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("questions", List.of(Map.of("questionId", mcq), Map.of("questionId", multi), Map.of("questionId", tf))))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/exams/" + examId + "/publish").header("Authorization", admin)).andExpect(status().isOk());
        return exam.at("/summary/slug").asText();
    }

    private JsonNode start(String slug, String token) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/exams/" + slug + "/attempts").header("Authorization", token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    /** Option id by its text, for a question in the attempt payload. */
    private static long optionId(JsonNode attempt, String text) {
        for (JsonNode q : attempt.get("questions")) for (JsonNode o : q.get("options")) if (o.get("text").asText().equals(text)) return o.get("id").asLong();
        throw new AssertionError("no option " + text);
    }

    private static long questionIdOf(JsonNode attempt, String optionText) {
        for (JsonNode q : attempt.get("questions")) for (JsonNode o : q.get("options")) if (o.get("text").asText().equals(optionText)) return q.get("questionId").asLong();
        throw new AssertionError("no option " + optionText);
    }

    private static Map<String, Object> item(long qid, List<Long> selected, long seq) {
        return Map.of("questionId", qid, "selectedOptionIds", selected, "markedForReview", false, "visited", true, "seq", seq);
    }

    // ------------------------------------------------------------------ tests

    @Test
    void startResumeSaveSubmitAndScore() throws Exception {
        String slug = publishExam(Map.of());
        JsonNode attempt = start(slug, student);
        long attemptId = attempt.get("attemptId").asLong();

        // no answers leak to the client; the server sets the clock
        String raw = attempt.toString();
        assertThat(raw).doesNotContain("\"correct\"").doesNotContain("explanation");
        Instant started = Instant.parse(attempt.get("startedAt").asText());
        Instant deadline = Instant.parse(attempt.get("deadlineAt").asText());
        assertThat(Duration.between(started, deadline)).isEqualTo(Duration.ofMinutes(30));
        assertThat(attempt.get("serverNow").asText()).isNotBlank();
        assertThat(attempt.get("questions").size()).isEqualTo(3);

        // starting again resumes the same attempt
        assertThat(start(slug, student).get("attemptId").asLong()).isEqualTo(attemptId);

        long mcq = questionIdOf(attempt, "right");
        long multi = questionIdOf(attempt, "m1");
        long tf = questionIdOf(attempt, "True");

        // answer MCQ correctly (seq 2), then a stale save (seq 1) arrives late and must be ignored
        mvc.perform(put("/api/v1/attempts/" + attemptId + "/answers").header("Authorization", student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("answers", List.of(item(mcq, List.of(optionId(attempt, "right")), 2))))))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/attempts/" + attemptId + "/answers").header("Authorization", student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("answers", List.of(item(mcq, List.of(optionId(attempt, "wrong")), 1))))))
                .andExpect(status().isOk());

        // MULTI answered partially -> wrong; TF left unanswered
        mvc.perform(put("/api/v1/attempts/" + attemptId + "/answers").header("Authorization", student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("answers", List.of(item(multi, List.of(optionId(attempt, "m1")), 3))))))
                .andExpect(status().isOk());

        // invalid input is rejected
        mvc.perform(put("/api/v1/attempts/" + attemptId + "/answers").header("Authorization", student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("answers", List.of(item(mcq, List.of(optionId(attempt, "right"), optionId(attempt, "wrong")), 9))))))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/attempts/" + attemptId + "/answers").header("Authorization", student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("answers", List.of(item(tf, List.of(optionId(attempt, "m1")), 9))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OPTION"));

        // resume shows the saved state
        JsonNode resumed = json.readTree(mvc.perform(get("/api/v1/attempts/" + attemptId).header("Authorization", student))
                .andReturn().getResponse().getContentAsString());
        assertThat(resumed.get("answers").toString()).contains(String.valueOf(optionId(attempt, "right")));

        // another student cannot see or touch this attempt
        String other = uniqueName();
        registerStudent(other, "Secret123");
        String otherToken = "Bearer " + loginAndGetAccessToken(other, "Secret123");
        mvc.perform(get("/api/v1/attempts/" + attemptId).header("Authorization", otherToken)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/attempts/" + attemptId + "/submit").header("Authorization", otherToken)).andExpect(status().isNotFound());

        // submit: +4 (MCQ) −1 (MULTI wrong) +0 (TF) = 3 of 12
        String result = mvc.perform(post("/api/v1/attempts/" + attemptId + "/submit").header("Authorization", student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.score").value(3.0))
                .andExpect(jsonPath("$.maxScore").value(12.0))
                .andExpect(jsonPath("$.correct").value(1))
                .andExpect(jsonPath("$.incorrect").value(1))
                .andExpect(jsonPath("$.unanswered").value(1))
                .andExpect(jsonPath("$.passed").value(false))
                .andExpect(jsonPath("$.reviewAvailable").value(true))
                .andExpect(jsonPath("$.review.length()").value(3))
                .andExpect(jsonPath("$.rank").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(result).contains("\"outcome\":\"CORRECT\"", "\"outcome\":\"INCORRECT\"", "\"outcome\":\"UNANSWERED\"");

        // submitting again is safe and returns the same result
        mvc.perform(post("/api/v1/attempts/" + attemptId + "/submit").header("Authorization", student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(3.0));
        assertThat(jdbc.queryForObject("select count(*) from results where attempt_id = ?", Integer.class, attemptId)).isEqualTo(1);

        // no more saves after submission
        mvc.perform(put("/api/v1/attempts/" + attemptId + "/answers").header("Authorization", student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("answers", List.of(item(tf, List.of(optionId(attempt, "True")), 10))))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTEMPT_CLOSED"));

        // saved questions: allowed for a reviewed question
        mvc.perform(post("/api/v1/users/me/saved-questions").header("Authorization", student)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("questionId", multi, "note", "revise"))))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/users/me/saved-questions").header("Authorization", student))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].note").value("revise"));

        // dashboard stats and history
        mvc.perform(get("/api/v1/users/me/stats").header("Authorization", student))
                .andExpect(jsonPath("$.testsTaken").value(1))
                .andExpect(jsonPath("$.currentStreak").value(1))
                .andExpect(jsonPath("$.accuracy").value(50.0));
        mvc.perform(get("/api/v1/attempts/me").header("Authorization", student))
                .andExpect(jsonPath("$.content[0].attemptId").value(attemptId))
                .andExpect(jsonPath("$.content[0].score").value(3.0));
    }

    @Test
    void attemptLimitIsEnforced() throws Exception {
        String slug = publishExam(Map.of("maxAttempts", 1));
        long id = start(slug, student).get("attemptId").asLong();
        mvc.perform(post("/api/v1/attempts/" + id + "/submit").header("Authorization", student)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/exams/" + slug + "/attempts").header("Authorization", student))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTEMPT_LIMIT_REACHED"));
    }

    @Test
    void deadlineIsEnforcedByTheServer() throws Exception {
        String slug = publishExam(Map.of());
        JsonNode attempt = start(slug, student);
        long id = attempt.get("attemptId").asLong();
        long mcq = questionIdOf(attempt, "right");
        // answer correctly in time
        mvc.perform(put("/api/v1/attempts/" + id + "/answers").header("Authorization", student)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("answers", List.of(item(mcq, List.of(optionId(attempt, "right")), 1))))))
                .andExpect(status().isOk());

        // simulate the time running out (deadline + grace already passed)
        jdbc.update("update exam_attempts set deadline_at = ? where id = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(120)), id);

        // late saves are refused and the attempt is auto-submitted with what was saved in time
        mvc.perform(put("/api/v1/attempts/" + id + "/answers").header("Authorization", student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("answers", List.of(item(mcq, List.of(optionId(attempt, "wrong")), 2))))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTEMPT_EXPIRED"));
        mvc.perform(get("/api/v1/results/" + id).header("Authorization", student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTO_SUBMITTED"))
                .andExpect(jsonPath("$.score").value(4.0));
    }

    @Test
    void expiryJobSubmitsAbandonedAttempts() throws Exception {
        String slug = publishExam(Map.of());
        long id = start(slug, student).get("attemptId").asLong();
        jdbc.update("update exam_attempts set deadline_at = ? where id = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(120)), id);
        expiryJob.sweep();
        assertThat(jdbc.queryForObject("select status from exam_attempts where id = ?", String.class, id)).isEqualTo("AUTO_SUBMITTED");
        assertThat(jdbc.queryForObject("select count(*) from results where attempt_id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void manualReleaseHidesAnswersUntilReleased() throws Exception {
        String slug = publishExam(Map.of("resultRelease", "MANUAL"));
        JsonNode attempt = start(slug, student);
        long id = attempt.get("attemptId").asLong();
        long tf = questionIdOf(attempt, "True");
        mvc.perform(post("/api/v1/attempts/" + id + "/submit").header("Authorization", student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewAvailable").value(false))
                .andExpect(jsonPath("$.review").doesNotExist());
        mvc.perform(post("/api/v1/users/me/saved-questions").header("Authorization", student)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("questionId", tf))))
                .andExpect(status().isForbidden());

        long examId = json.readTree(mvc.perform(get("/api/v1/exams/" + slug).header("Authorization", student))
                .andReturn().getResponse().getContentAsString()).at("/summary/id").asLong();
        mvc.perform(post("/api/v1/admin/exams/" + examId + "/release-results").header("Authorization", admin))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/results/" + id).header("Authorization", student))
                .andExpect(jsonPath("$.reviewAvailable").value(true))
                .andExpect(jsonPath("$.review.length()").value(3));
    }

    @Test
    void cannotStartBeforeTheExamOpens() throws Exception {
        String slug = publishExam(Map.of("startsAt", Instant.now().plus(Duration.ofDays(1)).toString(),
                "endsAt", Instant.now().plus(Duration.ofDays(2)).toString()));
        mvc.perform(post("/api/v1/exams/" + slug + "/attempts").header("Authorization", student))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXAM_NOT_STARTED"));
    }
}
