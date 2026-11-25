package com.examforge.service;

import com.examforge.dto.request.BulkQuestionStatusRequest;
import com.examforge.dto.request.QuestionRequest;
import com.examforge.dto.response.PageResponse;
import com.examforge.dto.response.QuestionResponse;
import com.examforge.entity.Question;
import com.examforge.entity.QuestionOption;
import com.examforge.entity.User;
import com.examforge.entity.enums.Difficulty;
import com.examforge.entity.enums.ExamStatus;
import com.examforge.entity.enums.QuestionStatus;
import com.examforge.entity.enums.QuestionType;
import com.examforge.exception.ApiException;
import com.examforge.mapper.QuestionMapper;
import com.examforge.repository.ExamQuestionRepository;
import com.examforge.repository.QuestionRepository;
import com.examforge.repository.QuestionSpecs;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** The question bank: create, edit, search, approve and delete questions. */
@Service
@RequiredArgsConstructor
public class QuestionService {
    private final QuestionRepository questions;
    private final ExamQuestionRepository examQuestions;
    private final CatalogService catalog;
    private final AuditService audit;


    @Transactional(readOnly = true)
    public PageResponse<QuestionResponse> search(String q, Long subjectId, Difficulty difficulty, QuestionType type,
                                                 QuestionStatus status, String tag, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.of(questions.findAll(QuestionSpecs.filter(q, subjectId, difficulty, type, status, tag), pageable),
                QuestionMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public QuestionResponse get(Long id) {
        return QuestionMapper.toResponse(require(id));
    }

    @Transactional(readOnly = true)
    public Question require(Long id) {
        return questions.findById(id).orElseThrow(() -> ApiException.notFound("Question"));
    }

    @Transactional
    public QuestionResponse create(QuestionRequest req, User actor, String ip) {
        Question q = new Question();
        apply(q, req);
        q.setCreatedBy(actor.getId());
        questions.save(q);
        audit.record(actor, "QUESTION_CREATED", "Question", q.getId(), null, ip);
        return QuestionMapper.toResponse(q);
    }

    @Transactional
    public QuestionResponse update(Long id, QuestionRequest req, User actor, String ip) {
        Question q = require(id);
        if (examQuestions.existsByQuestionIdAndExamStatus(id, ExamStatus.PUBLISHED)) {
            throw ApiException.conflict("QUESTION_IN_PUBLISHED_EXAM",
                    "This question is part of a published exam. Unpublish the exam before editing it.");
        }
        apply(q, req);
        audit.record(actor, "QUESTION_UPDATED", "Question", id, null, ip);
        return QuestionMapper.toResponse(q);
    }

    @Transactional
    public void delete(Long id, User actor, String ip) {
        Question q = require(id);
        if (examQuestions.existsByQuestionId(id)) {
            throw ApiException.conflict("QUESTION_IN_USE", "This question is used in an exam. Remove it from the exam first.");
        }
        questions.delete(q);
        audit.record(actor, "QUESTION_DELETED", "Question", id, null, ip);
    }

    @Transactional
    public int bulkStatus(BulkQuestionStatusRequest req, User actor, String ip) {
        List<Question> found = questions.findAllById(req.ids());
        if (req.status() == QuestionStatus.DRAFT) {
            for (Question q : found) {
                if (examQuestions.existsByQuestionIdAndExamStatus(q.getId(), ExamStatus.PUBLISHED)) {
                    throw ApiException.conflict("QUESTION_IN_PUBLISHED_EXAM",
                            "Question #" + q.getId() + " is in a published exam and must stay approved.");
                }
            }
        }
        found.forEach(q -> q.setStatus(req.status()));
        audit.record(actor, "QUESTIONS_STATUS_CHANGED", "Question", null,
                found.size() + " -> " + req.status(), ip);
        return found.size();
    }

    // ------------------------------------------------------------------ shared rules (also used by import)

    void apply(Question q, QuestionRequest req) {
        String problem = validateOptions(req.type(), req.options());
        if (problem != null) throw ApiException.badRequest("INVALID_OPTIONS", problem);
        if (req.negativeMarks().compareTo(req.marks()) > 0) {
            throw ApiException.badRequest("INVALID_MARKS", "Negative marks cannot be more than the marks for a correct answer");
        }
        q.setSubject(catalog.requireSubject(req.subjectId()));
        q.setTopic(blankToNull(req.topic()));
        q.setType(req.type());
        q.setQuestionText(req.questionText().trim());
        q.setImageUrl(blankToNull(req.imageUrl()));
        q.setExplanation(blankToNull(req.explanation()));
        q.setDifficulty(req.difficulty());
        q.setLanguage(req.language() == null || req.language().isBlank() ? "EN" : req.language().trim().toUpperCase(Locale.ROOT));
        q.setMarks(req.marks().setScale(2, java.math.RoundingMode.HALF_UP));
        q.setNegativeMarks(req.negativeMarks().setScale(2, java.math.RoundingMode.HALF_UP));
        q.setSource(blankToNull(req.source()));
        q.setStatus(req.status() == null ? QuestionStatus.DRAFT : req.status());
        q.getTags().clear();
        q.getTags().addAll(normalizeTags(req.tags()));
        q.replaceOptions(req.options().stream()
                .map(o -> new QuestionOption(o.text().trim(), o.correct()))
                .collect(Collectors.toList()));
    }

    /** Returns a human-readable problem, or null when the options fit the question type. */
    static String validateOptions(QuestionType type, List<QuestionRequest.OptionRequest> options) {
        if (options == null || options.size() < 2) return "Add at least two options";
        if (options.size() > 8) return "A question can have at most 8 options";
        long correct = options.stream().filter(QuestionRequest.OptionRequest::correct).count();
        Set<String> distinct = options.stream().map(o -> o.text().trim().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        if (distinct.size() != options.size()) return "Options must be different from each other";
        return switch (type) {
            case MCQ -> correct == 1 ? null : "A single-answer question needs exactly one correct option";
            case MULTI_SELECT -> correct >= 1 ? null : "Mark at least one correct option";
            case TRUE_FALSE -> options.size() == 2 && correct == 1 ? null
                    : "A true/false question needs exactly two options with one correct";
        };
    }

    static Set<String> normalizeTags(Set<String> tags) {
        if (tags == null) return Set.of();
        return tags.stream().map(t -> t.trim().toLowerCase(Locale.ROOT)).filter(t -> !t.isEmpty())
                .map(t -> t.length() > 50 ? t.substring(0, 50) : t)
                .collect(Collectors.toCollection(java.util.TreeSet::new));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
