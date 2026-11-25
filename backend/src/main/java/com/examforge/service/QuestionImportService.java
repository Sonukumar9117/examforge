package com.examforge.service;

import com.examforge.dto.request.QuestionImportRequest;
import com.examforge.dto.request.QuestionRequest;
import com.examforge.dto.request.QuestionRequest.OptionRequest;
import com.examforge.dto.response.ImportReportResponse;
import com.examforge.dto.response.ImportReportResponse.RowError;
import com.examforge.entity.Question;
import com.examforge.entity.Subject;
import com.examforge.entity.User;
import com.examforge.entity.enums.Difficulty;
import com.examforge.entity.enums.QuestionStatus;
import com.examforge.entity.enums.QuestionType;
import com.examforge.exception.ApiException;
import com.examforge.repository.QuestionRepository;
import com.examforge.repository.SubjectRepository;
import com.examforge.util.CsvParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Bulk question import from CSV or JSON. Every row is validated with the same rules as the editor;
 * valid rows are saved as DRAFT (unless dryRun), invalid rows are reported with their row number.
 *
 * CSV header (order free, case-insensitive):
 *   subject, topic, type, difficulty, question, optionA … optionH, correct, explanation, marks,
 *   negativeMarks, tags, language, source
 *   - correct: "B" for single answer, "A;C" for multi-select
 *   - tags: separated by ";"
 * JSON: an array of objects with the same names, except "options": [{"text": "...", "correct": true}]
 *   and "tags": ["..."].
 */
@Service
@RequiredArgsConstructor
public class QuestionImportService {
    public static final int MAX_ROWS = 1000;
    private static final String[] OPTION_COLUMNS = {"optiona", "optionb", "optionc", "optiond", "optione", "optionf", "optiong", "optionh"};

    private final QuestionRepository questions;
    private final SubjectRepository subjects;
    private final QuestionService questionService;
    private final AuditService audit;
    private final Validator validator;
    private final ObjectMapper objectMapper;


    private record Parsed(int row, QuestionRequest request, String error) {}

    @Transactional
    public ImportReportResponse importQuestions(QuestionImportRequest req, User actor, String ip) {
        Map<String, Subject> subjectLookup = new HashMap<>();
        for (Subject s : subjects.findAll()) {
            subjectLookup.put(s.getName().toLowerCase(Locale.ROOT), s);
            subjectLookup.put(s.getSlug().toLowerCase(Locale.ROOT), s);
            subjectLookup.put(String.valueOf(s.getId()), s);
        }

        List<Parsed> parsed = req.format() == QuestionImportRequest.Format.CSV
                ? parseCsv(req.content(), subjectLookup)
                : parseJson(req.content(), subjectLookup);
        if (parsed.size() > MAX_ROWS) {
            throw ApiException.badRequest("TOO_MANY_ROWS", "Import at most " + MAX_ROWS + " questions at a time");
        }

        List<RowError> errors = new ArrayList<>();
        List<QuestionRequest> valid = new ArrayList<>();
        for (Parsed p : parsed) {
            String problem = p.error() != null ? p.error() : validate(p.request());
            if (problem != null) errors.add(new RowError(p.row(), problem));
            else valid.add(p.request());
        }

        int imported = 0;
        if (!req.dryRun()) {
            for (QuestionRequest r : valid) {
                Question q = new Question();
                questionService.apply(q, r);
                q.setStatus(QuestionStatus.DRAFT); // always reviewed by an admin before use
                q.setCreatedBy(actor.getId());
                questions.save(q);
                imported++;
            }
            audit.record(actor, "QUESTIONS_IMPORTED", "Question", null,
                    imported + " imported, " + errors.size() + " rejected (" + req.format() + ")", ip);
        }
        return new ImportReportResponse(parsed.size(), valid.size(), imported, req.dryRun(), errors);
    }

    private String validate(QuestionRequest r) {
        Set<ConstraintViolation<QuestionRequest>> violations = validator.validate(r);
        if (!violations.isEmpty()) {
            ConstraintViolation<QuestionRequest> v = violations.iterator().next();
            return fieldLabel(v.getPropertyPath().toString()) + " " + v.getMessage();
        }
        String optionProblem = QuestionService.validateOptions(r.type(), r.options());
        if (optionProblem != null) return optionProblem;
        if (r.negativeMarks().compareTo(r.marks()) > 0) return "negativeMarks cannot be more than marks";
        return null;
    }

    private static String fieldLabel(String path) {
        return path.equals("questionText") ? "question" : path;
    }

    // ------------------------------------------------------------------ CSV

    private List<Parsed> parseCsv(String content, Map<String, Subject> subjectLookup) {
        List<CsvParser.Row> rows = CsvParser.parse(content);
        if (rows.isEmpty()) throw ApiException.badRequest("EMPTY_FILE", "The file has no rows");
        Map<String, Integer> col = new HashMap<>();
        List<String> header = rows.get(0).cells();
        for (int i = 0; i < header.size(); i++) col.put(header.get(i).trim().toLowerCase(Locale.ROOT), i);
        for (String required : List.of("subject", "question", "correct", "optiona", "optionb")) {
            if (!col.containsKey(required)) {
                throw ApiException.badRequest("MISSING_COLUMN", "CSV header must include a '" + required + "' column");
            }
        }

        List<Parsed> out = new ArrayList<>();
        for (CsvParser.Row row : rows.subList(1, rows.size())) {
            try {
                Fields f = name -> {
                    Integer i = col.get(name);
                    return i == null || i >= row.cells().size() ? null : blankToNull(row.cells().get(i));
                };
                QuestionType type = parseEnum(QuestionType.class, f.get("type"), QuestionType.MCQ, "type");
                Set<String> correctLabels = new HashSet<>();
                String correct = f.get("correct");
                if (correct == null) throw new IllegalArgumentException("correct is empty (use a letter like B, or A;C)");
                for (String part : correct.split("[;,\\s]+")) {
                    if (!part.isBlank()) correctLabels.add(part.trim().toUpperCase(Locale.ROOT));
                }
                List<OptionRequest> options = new ArrayList<>();
                for (int i = 0; i < OPTION_COLUMNS.length; i++) {
                    String text = f.get(OPTION_COLUMNS[i]);
                    if (text == null) continue;
                    String label = String.valueOf((char) ('A' + i));
                    options.add(new OptionRequest(text, correctLabels.remove(label)));
                }
                if (!correctLabels.isEmpty()) {
                    throw new IllegalArgumentException("correct refers to an empty option: " + String.join(", ", correctLabels));
                }
                out.add(new Parsed(row.line(), new QuestionRequest(
                        subjectId(f.get("subject"), subjectLookup),
                        f.get("topic"),
                        type,
                        f.get("question"),
                        null,
                        f.get("explanation"),
                        parseEnum(Difficulty.class, f.get("difficulty"), Difficulty.MEDIUM, "difficulty"),
                        f.get("language"),
                        parseDecimal(f.get("marks"), BigDecimal.ONE, "marks"),
                        parseDecimal(f.get("negativemarks"), BigDecimal.ZERO, "negativeMarks"),
                        f.get("source"),
                        QuestionStatus.DRAFT,
                        splitTags(f.get("tags")),
                        options), null));
            } catch (IllegalArgumentException e) {
                out.add(new Parsed(row.line(), null, e.getMessage()));
            }
        }
        return out;
    }

    @FunctionalInterface
    private interface Fields {
        String get(String name);
    }

    // ------------------------------------------------------------------ JSON

    private List<Parsed> parseJson(String content, Map<String, Subject> subjectLookup) {
        JsonNode root;
        try {
            root = objectMapper.readTree(content);
        } catch (Exception e) {
            throw ApiException.badRequest("INVALID_JSON", "The file is not valid JSON");
        }
        // Accept [ {...}, ... ], { "questions": [ ... ] } or a single question object.
        if (root != null && root.isObject()) root = root.has("questions") ? root.get("questions") : objectMapper.createArrayNode().add(root);
        if (root == null || !root.isArray()) throw ApiException.badRequest("INVALID_JSON", "The JSON must be a question, an array of questions, or {\"questions\": [...]}");

        List<Parsed> out = new ArrayList<>();
        for (int i = 0; i < root.size(); i++) {
            JsonNode n = root.get(i);
            int rowNumber = i + 1;
            try {
                if (!n.isObject()) throw new IllegalArgumentException("each item must be an object");
                List<OptionRequest> options = parseJsonOptions(n);
                Set<String> tags = new LinkedHashSet<>();
                n.path("tags").forEach(t -> tags.add(t.asText()));
                String externalId = text(n, "id");
                if (externalId != null) tags.add(externalId); // e.g. "NEET-MOCK-001" — searchable as a tag
                String subjectRef = n.hasNonNull("subjectId") ? n.get("subjectId").asText() : text(n, "subject");
                long correctCount = options.stream().filter(OptionRequest::correct).count();
                QuestionType defaultType = correctCount > 1 ? QuestionType.MULTI_SELECT : QuestionType.MCQ;
                BigDecimal[] marks = parseJsonMarks(n);
                out.add(new Parsed(rowNumber, new QuestionRequest(
                        subjectId(subjectRef, subjectLookup),
                        text(n, "topic"),
                        parseEnum(QuestionType.class, text(n, "type"), defaultType, "type"),
                        n.hasNonNull("question") ? text(n, "question") : text(n, "questionText"),
                        text(n, "imageUrl"),
                        text(n, "explanation"),
                        parseEnum(Difficulty.class, text(n, "difficulty"), Difficulty.MEDIUM, "difficulty"),
                        text(n, "language"),
                        marks[0],
                        marks[1],
                        text(n, "source"),
                        QuestionStatus.DRAFT,
                        tags,
                        options), null));
            } catch (IllegalArgumentException e) {
                out.add(new Parsed(rowNumber, null, e.getMessage()));
            }
        }
        return out;
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() || v.isContainerNode() ? null : blankToNull(v.asText());
    }

    /**
     * Options either carry "correct": true themselves, or the question has "correctAnswer" ("B", "A,C" or
     * ["A","C"]) matched against each option's "id" — or its letter by position when options have no id.
     */
    private static List<OptionRequest> parseJsonOptions(JsonNode n) {
        Set<String> answerKeys = new LinkedHashSet<>();
        JsonNode answer = n.get("correctAnswer");
        if (answer == null) answer = n.get("correctAnswers");
        if (answer != null && !answer.isNull()) {
            if (answer.isArray()) answer.forEach(a -> answerKeys.add(a.asText().trim().toUpperCase(Locale.ROOT)));
            else for (String part : answer.asText().split("[;,\\s]+")) if (!part.isBlank()) answerKeys.add(part.trim().toUpperCase(Locale.ROOT));
        }

        List<OptionRequest> options = new ArrayList<>();
        Set<String> matched = new HashSet<>();
        int index = 0;
        for (JsonNode o : n.path("options")) {
            String letter = String.valueOf((char) ('A' + index++));
            String key = o.hasNonNull("id") ? o.get("id").asText().trim().toUpperCase(Locale.ROOT) : letter;
            boolean correct;
            if (answerKeys.isEmpty()) {
                correct = o.path("correct").asBoolean(false);
            } else {
                correct = answerKeys.contains(key);
                if (correct) matched.add(key);
            }
            options.add(new OptionRequest(text(o, "text"), correct));
        }
        answerKeys.removeAll(matched);
        if (!answerKeys.isEmpty()) {
            throw new IllegalArgumentException("correctAnswer " + answerKeys + " does not match any option id");
        }
        return options;
    }

    /**
     * Returns [marks, negativeMarks]. Accepts "marks": 4 with "negativeMarks": 1, or
     * "marks": {"correct": 4, "incorrect": -1, "unattempted": 0}.
     */
    private static BigDecimal[] parseJsonMarks(JsonNode n) {
        JsonNode m = n.get("marks");
        if (m != null && m.isObject()) {
            BigDecimal correct = parseDecimal(text(m, "correct"), BigDecimal.ONE, "marks.correct");
            BigDecimal incorrect = parseDecimal(text(m, "incorrect"), BigDecimal.ZERO, "marks.incorrect").abs();
            BigDecimal unattempted = parseDecimal(text(m, "unattempted"), BigDecimal.ZERO, "marks.unattempted");
            if (unattempted.signum() != 0) throw new IllegalArgumentException("marks.unattempted must be 0");
            return new BigDecimal[]{correct, incorrect};
        }
        return new BigDecimal[]{
                parseDecimal(text(n, "marks"), BigDecimal.ONE, "marks"),
                parseDecimal(text(n, "negativeMarks"), BigDecimal.ZERO, "negativeMarks")};
    }

    // ------------------------------------------------------------------ helpers

    private static Long subjectId(String ref, Map<String, Subject> lookup) {
        if (ref == null) throw new IllegalArgumentException("subject is empty");
        Subject s = lookup.get(ref.trim().toLowerCase(Locale.ROOT));
        if (s == null) throw new IllegalArgumentException("unknown subject '" + ref + "' (create it first under Categories & subjects)");
        return s.getId();
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, E fallback, String field) {
        if (value == null) return fallback;
        String v = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (type == QuestionType.class && (v.equals("SINGLE") || v.equals("SINGLE_CHOICE"))) v = "MCQ";
        if (type == QuestionType.class && (v.equals("MULTIPLE") || v.equals("MULTI"))) v = "MULTI_SELECT";
        try {
            return Enum.valueOf(type, v);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(field + " must be one of " + Arrays.toString(type.getEnumConstants()));
        }
    }

    private static BigDecimal parseDecimal(String value, BigDecimal fallback, String field) {
        if (value == null) return fallback;
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be a number");
        }
    }

    private static Set<String> splitTags(String value) {
        if (value == null) return Set.of();
        Set<String> tags = new LinkedHashSet<>();
        for (String t : value.split(";")) if (!t.isBlank()) tags.add(t.trim());
        return tags;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
