package com.examforge.service;

import com.examforge.dto.request.ExamQuestionsRequest;
import com.examforge.dto.request.ExamRequest;
import com.examforge.dto.response.ExamDetailResponse;
import com.examforge.dto.response.ExamSummaryResponse;
import com.examforge.dto.response.PageResponse;
import com.examforge.entity.Exam;
import com.examforge.entity.ExamQuestion;
import com.examforge.entity.Question;
import com.examforge.entity.User;
import com.examforge.entity.enums.ExamStatus;
import com.examforge.entity.enums.ExamType;
import com.examforge.entity.enums.QuestionStatus;
import com.examforge.exception.ApiException;
import com.examforge.mapper.ExamMapper;
import com.examforge.repository.ExamAttemptRepository;
import com.examforge.repository.ExamRepository;
import com.examforge.repository.ExamSpecs;
import com.examforge.repository.QuestionRepository;
import com.examforge.util.Slugs;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Admin exam builder: details, question list, scheduling and publishing. */
@Service
@RequiredArgsConstructor
public class ExamService {
    private final ExamRepository exams;
    private final QuestionRepository questions;
    private final ExamAttemptRepository attempts;
    private final CatalogService catalog;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PageResponse<ExamSummaryResponse> search(String q, Long categoryId, ExamStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
        return PageResponse.of(exams.findAll(ExamSpecs.filter(q, categoryId, null, status, null, null, null), pageable),
                ExamMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public ExamDetailResponse get(Long id) {
        return ExamMapper.toDetail(require(id));
    }

    @Transactional
    public ExamDetailResponse create(ExamRequest req, User actor, String ip) {
        Exam e = new Exam();
        apply(e, req);
        e.setSlug(Slugs.unique(req.title(), exams::existsBySlug));
        e.setCreatedBy(actor.getId());
        exams.save(e);
        audit.record(actor, "EXAM_CREATED", "Exam", e.getId(), e.getTitle(), ip);
        return ExamMapper.toDetail(e);
    }

    @Transactional
    public ExamDetailResponse update(Long id, ExamRequest req, User actor, String ip) {
        Exam e = require(id);
        requireEditable(e);
        apply(e, req);
        audit.record(actor, "EXAM_UPDATED", "Exam", id, e.getTitle(), ip);
        return ExamMapper.toDetail(e);
    }

    @Transactional
    public ExamDetailResponse setQuestions(Long id, ExamQuestionsRequest req, User actor, String ip) {
        Exam e = require(id);
        requireEditable(e);
        List<Long> ids = req.questions().stream().map(ExamQuestionsRequest.Item::questionId).toList();
        if (new HashSet<>(ids).size() != ids.size()) {
            throw ApiException.badRequest("DUPLICATE_QUESTION", "A question can only appear once in an exam");
        }
        Map<Long, Question> found = questions.findAllById(ids).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        if (found.size() != ids.size()) throw ApiException.badRequest("QUESTION_NOT_FOUND", "One or more questions do not exist");

        e.getQuestions().clear();
        exams.flush(); // delete old rows before inserting, so the (exam, question) unique key is not hit
        int order = 1;
        for (ExamQuestionsRequest.Item item : req.questions()) {
            Question q = found.get(item.questionId());
            BigDecimal marks = scale(item.marks() != null ? item.marks() : q.getMarks());
            BigDecimal negative = scale(item.negativeMarks() != null ? item.negativeMarks() : q.getNegativeMarks());
            if (negative.compareTo(marks) > 0) {
                throw ApiException.badRequest("INVALID_MARKS", "Question #" + q.getId() + ": negative marks cannot exceed marks");
            }
            e.getQuestions().add(new ExamQuestion(e, q, order++, marks, negative));
        }
        audit.record(actor, "EXAM_QUESTIONS_SET", "Exam", id, ids.size() + " questions", ip);
        return ExamMapper.toDetail(e);
    }

    @Transactional
    public ExamDetailResponse publish(Long id, User actor, String ip) {
        Exam e = require(id);
        if (e.getStatus() == ExamStatus.PUBLISHED) return ExamMapper.toDetail(e);
        List<String> problems = publishProblems(e);
        if (!problems.isEmpty()) {
            throw ApiException.badRequest("EXAM_NOT_READY", "Can't publish yet: " + String.join("; ", problems));
        }
        e.setStatus(ExamStatus.PUBLISHED);
        e.setPublishedAt(Instant.now());
        audit.record(actor, "EXAM_PUBLISHED", "Exam", id, e.getTitle(), ip);
        return ExamMapper.toDetail(e);
    }

    @Transactional
    public ExamDetailResponse unpublish(Long id, User actor, String ip) {
        Exam e = require(id);
        e.setStatus(ExamStatus.DRAFT);
        audit.record(actor, "EXAM_UNPUBLISHED", "Exam", id, e.getTitle(), ip);
        return ExamMapper.toDetail(e);
    }

    /** For exams whose answers are released manually: students can now see answers and explanations. */
    @Transactional
    public ExamDetailResponse releaseResults(Long id, User actor, String ip) {
        Exam e = require(id);
        if (e.getResultsReleasedAt() == null) e.setResultsReleasedAt(Instant.now());
        audit.record(actor, "EXAM_RESULTS_RELEASED", "Exam", id, e.getTitle(), ip);
        return ExamMapper.toDetail(e);
    }

    @Transactional
    public void delete(Long id, User actor, String ip) {
        Exam e = require(id);
        if (e.getStatus() == ExamStatus.PUBLISHED) {
            throw ApiException.conflict("EXAM_PUBLISHED", "Unpublish the exam before deleting it");
        }
        if (attempts.existsByExamId(id)) {
            throw ApiException.conflict("EXAM_HAS_ATTEMPTS",
                    "Students have already taken this exam, so it cannot be deleted. Unpublish it instead.");
        }
        exams.delete(e);
        audit.record(actor, "EXAM_DELETED", "Exam", id, e.getTitle(), ip);
    }

    // ------------------------------------------------------------------ rules

    /** Everything that blocks publishing, in plain words. */
    static List<String> publishProblems(Exam e) {
        List<String> problems = new ArrayList<>();
        if (e.getQuestions().isEmpty()) problems.add("add at least one question");
        long drafts = e.getQuestions().stream().filter(q -> q.getQuestion().getStatus() != QuestionStatus.APPROVED).count();
        if (drafts > 0) problems.add(drafts + " question(s) are still drafts — approve them in the question bank");
        if (e.getPassMarks() != null && e.getPassMarks().compareTo(e.totalMarks()) > 0) {
            problems.add("pass marks (" + e.getPassMarks() + ") are higher than total marks (" + e.totalMarks() + ")");
        }
        if (e.getType() == ExamType.LIVE && (e.getStartsAt() == null || e.getEndsAt() == null)) {
            problems.add("a live exam needs a start and end time");
        }
        if (e.getEndsAt() != null && e.getEndsAt().isBefore(Instant.now())) problems.add("the end time is in the past");
        return problems;
    }

    private void apply(Exam e, ExamRequest req) {
        if (req.startsAt() != null && req.endsAt() != null) {
            if (!req.endsAt().isAfter(req.startsAt())) {
                throw ApiException.badRequest("INVALID_SCHEDULE", "The end time must be after the start time");
            }
            long windowMinutes = java.time.Duration.between(req.startsAt(), req.endsAt()).toMinutes();
            if (windowMinutes < req.durationMinutes()) {
                throw ApiException.badRequest("INVALID_SCHEDULE", "The exam window is shorter than the exam duration");
            }
        }
        e.setCategory(catalog.requireCategory(req.categoryId()));
        e.setTitle(req.title().trim());
        e.setDescription(blankToNull(req.description()));
        e.setInstructions(blankToNull(req.instructions()));
        e.setType(req.type());
        e.setDifficulty(req.difficulty());
        e.setDurationMinutes(req.durationMinutes());
        e.setPassMarks(req.passMarks() == null ? null : scale(req.passMarks()));
        e.setStartsAt(req.startsAt());
        e.setEndsAt(req.endsAt());
        e.setMaxAttempts(req.maxAttempts());
        e.setShuffleQuestions(req.shuffleQuestions());
        e.setShuffleOptions(req.shuffleOptions());
        e.setAllowNavigation(req.allowNavigation());
        e.setResultRelease(req.resultRelease());
    }

    private static void requireEditable(Exam e) {
        if (e.getStatus() == ExamStatus.PUBLISHED) {
            throw ApiException.conflict("EXAM_PUBLISHED", "Unpublish the exam before changing it");
        }
    }

    Exam require(Long id) {
        return exams.findById(id).orElseThrow(() -> ApiException.notFound("Exam"));
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
