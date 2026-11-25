package com.examforge.controller;

import com.examforge.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuestionBankIntegrationTest extends IntegrationTestBase {

    private String admin;
    private long physicsId;

    @BeforeEach
    void setUp() throws Exception {
        admin = "Bearer " + loginAndGetAccessToken("admin@examforge.test", "AdminPass123");
        JsonNode subjects = json.readTree(mvc.perform(get("/api/v1/subjects")).andReturn().getResponse().getContentAsString());
        for (JsonNode s : subjects) if (s.get("slug").asText().equals("physics")) physicsId = s.get("id").asLong();
    }

    private Map<String, Object> mcq(String text, boolean twoCorrect) {
        return Map.of(
                "subjectId", physicsId, "type", "MCQ", "questionText", text, "difficulty", "EASY",
                "marks", 2, "negativeMarks", 0.5, "tags", List.of("Units", "basics"),
                "options", List.of(
                        Map.of("text", "Newton", "correct", true),
                        Map.of("text", "Joule", "correct", twoCorrect),
                        Map.of("text", "Watt", "correct", false)));
    }

    @Test
    void createSearchUpdateAndDeleteQuestion() throws Exception {
        String text = "SI unit of force " + uniqueName() + "?";
        JsonNode created = json.readTree(mvc.perform(post("/api/v1/admin/questions").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(mcq(text, false))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        long id = created.get("id").asLong();
        assertThat(created.at("/options/0/label").asText()).isEqualTo("A");
        assertThat(created.at("/options/0/correct").asBoolean()).isTrue();
        assertThat(created.get("status").asText()).isEqualTo("DRAFT");
        assertThat(created.get("tags").toString()).contains("units"); // tags are normalised to lower case

        mvc.perform(get("/api/v1/admin/questions").header("Authorization", admin).param("q", text.substring(0, 25)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/admin/questions").header("Authorization", admin).param("tag", "units"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/admin/questions/bulk-status").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("ids", List.of(id), "status", "APPROVED"))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/questions/" + id).header("Authorization", admin))
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mvc.perform(delete("/api/v1/admin/questions/" + id).header("Authorization", admin))
                .andExpect(status().isNoContent());
    }

    @Test
    void rejectsInvalidOptionsForType() throws Exception {
        mvc.perform(post("/api/v1/admin/questions").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(mcq("Two answers?", true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OPTIONS"));
    }

    @Test
    void csvImportReportsBadRowsAndOnlySavesValidOnes() throws Exception {
        String marker = uniqueName();
        String csv = """
                subject,topic,type,difficulty,question,optionA,optionB,optionC,optionD,correct,explanation,marks,negativeMarks,tags
                Physics,Motion,MCQ,EASY,"Speed is distance per ___ (%1$s)",time,mass,force,area,A,"v = d/t",1,0.25,kinematics
                physics,Units,MULTI_SELECT,MEDIUM,"Which are SI base units? (%1$s)",metre,kilogram,litre,,A;B,,2,0,units;si
                Astrology,,MCQ,EASY,"Unknown subject row (%1$s)",a,b,,,A,,1,0,
                Physics,,MCQ,EASY,"Two correct for MCQ (%1$s)",a,b,,,A;B,,1,0,
                """.formatted(marker);

        mvc.perform(post("/api/v1/admin/questions/import").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("format", "CSV", "content", csv, "dryRun", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(4))
                .andExpect(jsonPath("$.validRows").value(2))
                .andExpect(jsonPath("$.imported").value(0))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].row").value(4));

        mvc.perform(post("/api/v1/admin/questions/import").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("format", "CSV", "content", csv, "dryRun", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(2));

        mvc.perform(get("/api/v1/admin/questions").header("Authorization", admin).param("q", marker))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].status").value("DRAFT"));
    }

    @Test
    void jsonImportWorks() throws Exception {
        String marker = uniqueName();
        String body = toJson(List.of(Map.of(
                "subject", "physics", "type", "TRUE_FALSE", "question", "Light is a wave (" + marker + ")",
                "options", List.of(Map.of("text", "True", "correct", true), Map.of("text", "False", "correct", false)))));
        mvc.perform(post("/api/v1/admin/questions/import").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("format", "JSON", "content", body, "dryRun", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.errors.length()").value(0));
    }

    @Test
    void jsonImportAcceptsCorrectAnswerKeysAndMarksObject() throws Exception {
        String id = "NEET-MOCK-" + uniqueName();
        // exactly the shape many question banks export: one object, correctAnswer letter, marks object
        String single = """
                {
                  "id": "%s",
                  "subject": "Physics",
                  "topic": "Electrostatics",
                  "difficulty": "Easy",
                  "question": "The SI unit of electric charge is: (%s)",
                  "options": [
                    {"id": "A", "text": "Ampere"},
                    {"id": "B", "text": "Coulomb"},
                    {"id": "C", "text": "Volt"},
                    {"id": "D", "text": "Ohm"}
                  ],
                  "correctAnswer": "B",
                  "explanation": "Electric charge is measured in coulombs.",
                  "marks": {"correct": 4, "incorrect": -1, "unattempted": 0}
                }""".formatted(id, id);
        mvc.perform(post("/api/v1/admin/questions/import").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("format", "JSON", "content", single, "dryRun", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.errors.length()").value(0));

        JsonNode q = json.readTree(mvc.perform(get("/api/v1/admin/questions").header("Authorization", admin).param("tag", id.toLowerCase()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andReturn().getResponse().getContentAsString()).at("/content/0");
        assertThat(q.get("marks").asDouble()).isEqualTo(4.0);
        assertThat(q.get("negativeMarks").asDouble()).isEqualTo(1.0);
        assertThat(q.get("difficulty").asText()).isEqualTo("EASY");
        assertThat(q.at("/options/1/text").asText()).isEqualTo("Coulomb");
        assertThat(q.at("/options/1/correct").asBoolean()).isTrue();
        assertThat(q.at("/options/0/correct").asBoolean()).isFalse();

        // array form: two correct answers -> multiple-answer question; unknown key -> row error
        String many = """
                [
                  {"subject": "Physics", "question": "Vector quantities? (%1$s)",
                   "options": [{"id": "A", "text": "Velocity"}, {"id": "B", "text": "Mass"}, {"id": "C", "text": "Force"}],
                   "correctAnswer": ["A", "C"], "marks": {"correct": 4, "incorrect": -2}},
                  {"subject": "Physics", "question": "Bad key (%1$s)",
                   "options": [{"id": "A", "text": "x"}, {"id": "B", "text": "y"}], "correctAnswer": "E"}
                ]""".formatted(uniqueName());
        mvc.perform(post("/api/v1/admin/questions/import").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("format", "JSON", "content", many, "dryRun", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validRows").value(1))
                .andExpect(jsonPath("$.errors[0].row").value(2))
                .andExpect(jsonPath("$.errors[0].message").value(org.hamcrest.Matchers.containsString("does not match any option")));
    }

    @Test
    void studentsCannotSeeTheQuestionBank() throws Exception {
        String u = uniqueName();
        registerStudent(u, "Secret123");
        String token = loginAndGetAccessToken(u, "Secret123");
        mvc.perform(get("/api/v1/admin/questions").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
