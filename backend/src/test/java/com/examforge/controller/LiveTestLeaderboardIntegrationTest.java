package com.examforge.controller;

import com.examforge.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Live test = a published exam open for a time window. Each student takes it once inside the window;
 * when the window closes, everyone is ranked on the leaderboard.
 */
class LiveTestLeaderboardIntegrationTest extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    record Student(String token, String name) {}

    Student student(String fullName) throws Exception {
        String u = uniqueName();
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("fullName", fullName, "username", u, "email", u + "@example.com",
                        "password", "Secret123", "acceptTerms", true)))).andExpect(status().isCreated());
        return new Student("Bearer " + loginAndGetAccessToken(u, "Secret123"), fullName);
    }

    /** Takes the test answering `correctCount` of the 2 questions correctly; returns the attempt id. */
    long take(String slug, Student s, int correctCount) throws Exception {
        JsonNode a = json.readTree(mvc.perform(post("/api/v1/exams/" + slug + "/attempts").header("Authorization", s.token()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long id = a.get("attemptId").asLong();
        int i = 0;
        for (JsonNode q : a.get("questions")) {
            String wanted = i++ < correctCount ? "right" : "wrong";
            long opt = 0;
            for (JsonNode o : q.get("options")) if (o.get("text").asText().equals(wanted)) opt = o.get("id").asLong();
            mvc.perform(put("/api/v1/attempts/" + id + "/answers").header("Authorization", s.token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(Map.of("answers", List.of(Map.of("questionId", q.get("questionId").asLong(),
                            "selectedOptionIds", List.of(opt), "markedForReview", false, "visited", true, "seq", 1))))))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/v1/attempts/" + id + "/submit").header("Authorization", s.token())).andExpect(status().isOk());
        return id;
    }

    @Test
    void oneAttemptInWindowThenLeaderboardAfterClose() throws Exception {
        String admin = "Bearer " + loginAndGetAccessToken("admin@examforge.test", "AdminPass123");
        JsonNode cat = json.readTree(mvc.perform(get("/api/v1/exam-categories/state-pcs")).andReturn().getResponse().getContentAsString());
        long subjectId = cat.at("/subjects/0/id").asLong();
        List<Map<String, Object>> qs = List.of();
        long[] qids = new long[2];
        for (int i = 0; i < 2; i++) {
            qids[i] = json.readTree(mvc.perform(post("/api/v1/admin/questions").header("Authorization", admin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(Map.of("subjectId", subjectId, "type", "MCQ", "questionText", "Live Q" + i + " " + uniqueName(),
                            "difficulty", "EASY", "marks", 2, "negativeMarks", 0.5, "status", "APPROVED",
                            "options", List.of(Map.of("text", "right", "correct", true), Map.of("text", "wrong", "correct", false))))))
                    .andReturn().getResponse().getContentAsString()).get("id").asLong();
        }
        JsonNode exam = json.readTree(mvc.perform(post("/api/v1/admin/exams").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("categoryId", cat.get("id").asLong(), "title", "PCS Live " + uniqueName(), "type", "LIVE",
                        "difficulty", "MEDIUM", "durationMinutes", 20, "allowNavigation", true, "resultRelease", "AFTER_END",
                        "startsAt", Instant.now().minusSeconds(60).toString(),
                        "endsAt", Instant.now().plus(Duration.ofHours(2)).toString()))))
                .andReturn().getResponse().getContentAsString());
        long examId = exam.at("/summary/id").asLong();
        String slug = exam.at("/summary/slug").asText();
        mvc.perform(put("/api/v1/admin/exams/" + examId + "/questions").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("questions", List.of(Map.of("questionId", qids[0]), Map.of("questionId", qids[1]))))));
        mvc.perform(post("/api/v1/admin/exams/" + examId + "/publish").header("Authorization", admin)).andExpect(status().isOk());

        Student asha = student("Asha Verma");
        Student ravi = student("Ravi Kumar");
        Student meera = student("Meera Iyer");
        mvc.perform(put("/api/v1/users/me/preferences").header("Authorization", meera.token())
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("showOnLeaderboard", false))))
                .andExpect(jsonPath("$.profile.showOnLeaderboard").value(false));

        // The test is listed as open now
        mvc.perform(get("/api/v1/exams/live").header("Authorization", asha.token()))
                .andExpect(jsonPath("$.open[?(@.exam.slug == '" + slug + "')]").exists());

        long ashaAttempt = take(slug, asha, 2); // 4.0
        take(slug, ravi, 1);                     // 1.5
        take(slug, meera, 0);                    // -1.0

        // only one sitting per student
        mvc.perform(post("/api/v1/exams/" + slug + "/attempts").header("Authorization", asha.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LIVE_TEST_TAKEN"));

        // while the window is open: no leaderboard, no rank, answers hidden (AFTER_END)
        mvc.perform(get("/api/v1/leaderboards/exams/" + slug).header("Authorization", asha.token()))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.entries.length()").value(0));
        mvc.perform(get("/api/v1/results/" + ashaAttempt).header("Authorization", asha.token()))
                .andExpect(jsonPath("$.rank").doesNotExist())
                .andExpect(jsonPath("$.rankNote").isNotEmpty())
                .andExpect(jsonPath("$.reviewAvailable").value(false));

        // the window closes
        jdbc.update("update exams set ends_at = ? where id = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(1)), examId);

        String board = mvc.perform(get("/api/v1/leaderboards/exams/" + slug).header("Authorization", ravi.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.totalParticipants").value(3))
                .andExpect(jsonPath("$.entries[0].name").value("Asha Verma"))
                .andExpect(jsonPath("$.entries[0].score").value(4.0))
                .andExpect(jsonPath("$.entries[1].name").value("You"))
                .andExpect(jsonPath("$.entries[1].me").value(true))
                .andExpect(jsonPath("$.entries[2].name").value("Anonymous student"))
                .andExpect(jsonPath("$.me.rank").value(2))
                .andReturn().getResponse().getContentAsString();
        assertThat(board).doesNotContain("Meera");

        mvc.perform(get("/api/v1/results/" + ashaAttempt).header("Authorization", asha.token()))
                .andExpect(jsonPath("$.rank").value(1))
                .andExpect(jsonPath("$.percentile").value(100.0))
                .andExpect(jsonPath("$.leaderboardAvailable").value(true))
                .andExpect(jsonPath("$.reviewAvailable").value(true));
        mvc.perform(get("/api/v1/exams/live").header("Authorization", asha.token()))
                .andExpect(jsonPath("$.ended[?(@.exam.slug == '" + slug + "')].myStatus").value("SUBMITTED"));
        // closed: nobody can start it any more
        mvc.perform(post("/api/v1/exams/" + slug + "/attempts").header("Authorization", student("Late Comer").token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXAM_CLOSED"));
        assertThat(qs).isEmpty();
    }
}
