package com.examforge.service;

import com.examforge.dto.request.SaveAnswersRequest;
import com.examforge.dto.response.AttemptResponse;
import com.examforge.dto.response.AttemptSummaryResponse;
import com.examforge.dto.response.PageResponse;
import com.examforge.dto.response.SaveAnswersResponse;
import com.examforge.entity.*;
import com.examforge.entity.enums.AttemptStatus;
import com.examforge.entity.enums.ExamStatus;
import com.examforge.entity.enums.ExamType;
import com.examforge.entity.enums.QuestionType;
import com.examforge.exception.ApiException;
import com.examforge.mapper.AttemptMapper;
import com.examforge.repository.ExamAttemptRepository;
import com.examforge.repository.ExamRepository;
import com.examforge.repository.ResultRepository;
import com.examforge.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The exam hall on the server: start/resume, autosave, submit, and expiry.
 * The server owns all timing: start time, deadline and the "too late" check use server time only.
 */
@Service
public class AttemptService {
    private final ExamRepository exams;
    private final ExamAttemptRepository attempts;
    private final UserRepository users;
    private final ResultRepository results;
    private final ScoringService scoring;
    private final ObjectMapper json;
    /** Small allowance for network delay: saves up to this long after the deadline are still accepted. */
    private final Duration grace;

    public AttemptService(ExamRepository exams, ExamAttemptRepository attempts, UserRepository users,
                          ResultRepository results, ScoringService scoring, ObjectMapper json,
                          @Value("${app.exam.submit-grace:30s}") Duration grace) {
        this.exams = exams;
        this.attempts = attempts;
        this.users = users;
        this.results = results;
        this.scoring = scoring;
        this.json = json;
        this.grace = grace;
    }

    /** Question order and option order shown to one student (shuffled when the exam asks for it). */
    public record Layout(List<Long> questions, Map<Long, List<Long>> options) {}

    // ------------------------------------------------------------------ start / resume

    @Transactional
    public AttemptResponse start(String slug, Long userId) {
        users.findByIdForUpdate(userId).orElseThrow(() -> ApiException.notFound("User")); // one start at a time per user
        Exam exam = exams.findBySlugAndStatus(slug, ExamStatus.PUBLISHED).orElseThrow(() -> ApiException.notFound("Exam"));
        Instant now = Instant.now();

        Optional<ExamAttempt> open = attempts.findFirstByExamIdAndUserIdAndStatus(exam.getId(), userId, AttemptStatus.IN_PROGRESS);
        if (open.isPresent()) {
            ExamAttempt a = open.get();
            if (!isPastGrace(a, now)) return toResponse(a, now); // resume
            scoring.finalizeAttempt(a, AttemptStatus.AUTO_SUBMITTED, now);
        }

        DateTimeFormatter fmt = DateTimeFormatter.ISO_INSTANT;
        if (exam.getStartsAt() != null && now.isBefore(exam.getStartsAt())) {
            throw new ApiException(HttpStatus.CONFLICT, "EXAM_NOT_STARTED", "This test opens at " + fmt.format(exam.getStartsAt()));
        }
        if (exam.getEndsAt() != null && !now.isBefore(exam.getEndsAt())) {
            throw new ApiException(HttpStatus.CONFLICT, "EXAM_CLOSED", "This test has closed");
        }
        // A live test is a fair competition: one sitting per student inside the window.
        if (exam.getType() == ExamType.LIVE && attempts.countByExamIdAndUserId(exam.getId(), userId) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "LIVE_TEST_TAKEN",
                    "You have already taken this live test. The leaderboard opens when the test closes.");
        }
        if (exam.getMaxAttempts() != null && attempts.countByExamIdAndUserId(exam.getId(), userId) >= exam.getMaxAttempts()) {
            throw new ApiException(HttpStatus.CONFLICT, "ATTEMPT_LIMIT_REACHED",
                    "You have used all " + exam.getMaxAttempts() + " attempt(s) for this test");
        }
        if (exam.getQuestions().isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "EXAM_EMPTY", "This test has no questions");
        }

        Instant deadline = now.plus(Duration.ofMinutes(exam.getDurationMinutes()));
        if (exam.getEndsAt() != null && exam.getEndsAt().isBefore(deadline)) deadline = exam.getEndsAt();

        ExamAttempt attempt = new ExamAttempt(exam, userId, attempts.maxAttemptNo(exam.getId(), userId) + 1, now, deadline,
                writeLayout(buildLayout(exam)));
        attempts.save(attempt);
        return toResponse(attempt, now);
    }

    @Transactional
    public AttemptResponse get(Long attemptId, Long userId) {
        ExamAttempt a = finalizeIfExpired(attemptId, userId);
        return toResponse(a, Instant.now());
    }

    /** Loads (and locks) the attempt; if its time is up it is scored right away. */
    @Transactional
    public ExamAttempt finalizeIfExpired(Long attemptId, Long userId) {
        ExamAttempt a = requireOwnedForUpdate(attemptId, userId);
        Instant now = Instant.now();
        if (a.getStatus() == AttemptStatus.IN_PROGRESS && isPastGrace(a, now)) {
            scoring.finalizeAttempt(a, AttemptStatus.AUTO_SUBMITTED, now);
        }
        return a;
    }

    // ------------------------------------------------------------------ autosave / submit

    /** noRollbackFor: when a late save finds the time is up, the auto-submission must still be saved. */
    @Transactional(noRollbackFor = ApiException.class)
    public SaveAnswersResponse save(Long attemptId, Long userId, SaveAnswersRequest req) {
        ExamAttempt a = requireOwnedForUpdate(attemptId, userId);
        Instant now = Instant.now();
        if (a.getStatus().isFinished()) {
            throw new ApiException(HttpStatus.CONFLICT, "ATTEMPT_CLOSED", "This test has already been submitted");
        }
        if (isPastGrace(a, now)) {
            scoring.finalizeAttempt(a, AttemptStatus.AUTO_SUBMITTED, now);
            throw new ApiException(HttpStatus.CONFLICT, "ATTEMPT_EXPIRED", "Time is up. Your test was submitted automatically.");
        }
        applyAnswers(a, req.answers(), now);
        a.setLastSavedAt(now);
        return new SaveAnswersResponse(now, now, a.getDeadlineAt(), a.getStatus().name());
    }

    /** Idempotent: submitting an already-finished attempt simply returns. Final answers may be included. */
    @Transactional
    public void submit(Long attemptId, Long userId, SaveAnswersRequest finalAnswers) {
        ExamAttempt a = requireOwnedForUpdate(attemptId, userId);
        if (a.getStatus().isFinished()) return;
        Instant now = Instant.now();
        boolean late = isPastGrace(a, now);
        if (!late && finalAnswers != null && finalAnswers.answers() != null) applyAnswers(a, finalAnswers.answers(), now);
        scoring.finalizeAttempt(a, late ? AttemptStatus.AUTO_SUBMITTED : AttemptStatus.SUBMITTED, now);
    }

    /** Called by the expiry job, one attempt per transaction. */
    @Transactional
    public void expire(Long attemptId) {
        attempts.findByIdForUpdate(attemptId).ifPresent(a -> {
            Instant now = Instant.now();
            if (a.getStatus() == AttemptStatus.IN_PROGRESS && isPastGrace(a, now)) {
                scoring.finalizeAttempt(a, AttemptStatus.AUTO_SUBMITTED, now);
            }
        });
    }

    @Transactional(readOnly = true)
    public List<Long> expiredAttemptIds() {
        return attempts.findIdsByStatusAndDeadlineBefore(AttemptStatus.IN_PROGRESS, Instant.now().minus(grace));
    }

    // ------------------------------------------------------------------ history

    @Transactional(readOnly = true)
    public PageResponse<AttemptSummaryResponse> mine(Long userId, int page, int size) {
        var p = attempts.findByUserIdOrderByStartedAtDesc(userId, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50)));
        Map<Long, Result> byAttempt = results.findByAttemptIdIn(p.getContent().stream().map(ExamAttempt::getId).toList())
                .stream().collect(Collectors.toMap(r -> r.getAttempt().getId(), Function.identity()));
        return PageResponse.of(p, a -> AttemptMapper.toSummary(a, byAttempt.get(a.getId())));
    }

    // ------------------------------------------------------------------ helpers

    private void applyAnswers(ExamAttempt a, List<SaveAnswersRequest.Item> items, Instant now) {
        Layout layout = readLayout(a.getLayout());
        Map<Long, Question> questions = a.getExam().getQuestions().stream().map(ExamQuestion::getQuestion)
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        Map<Long, AttemptAnswer> existing = a.getAnswers().stream()
                .collect(Collectors.toMap(AttemptAnswer::getQuestionId, Function.identity()));

        for (SaveAnswersRequest.Item item : items) {
            Question q = questions.get(item.questionId());
            List<Long> allowedOptions = layout.options().get(item.questionId());
            if (q == null || allowedOptions == null) {
                throw ApiException.badRequest("INVALID_QUESTION", "Question " + item.questionId() + " is not part of this test");
            }
            Set<Long> selected = new LinkedHashSet<>(item.selectedOptionIds() == null ? List.of() : item.selectedOptionIds());
            if (!allowedOptions.containsAll(selected)) {
                throw ApiException.badRequest("INVALID_OPTION", "An option does not belong to question " + item.questionId());
            }
            if (q.getType() != QuestionType.MULTI_SELECT && selected.size() > 1) {
                throw ApiException.badRequest("INVALID_OPTION", "Question " + item.questionId() + " allows only one answer");
            }
            AttemptAnswer answer = existing.get(item.questionId());
            if (answer == null) {
                answer = new AttemptAnswer(a, item.questionId());
                a.getAnswers().add(answer);
                existing.put(item.questionId(), answer);
            } else if (item.seq() <= answer.getClientSeq()) {
                continue; // an older or repeated save arriving late — keep the newer state
            }
            boolean changed = !answer.getSelectedOptionIds().equals(selected);
            answer.getSelectedOptionIds().clear();
            answer.getSelectedOptionIds().addAll(selected);
            answer.setMarkedForReview(item.markedForReview());
            answer.setVisited(item.visited() || !selected.isEmpty());
            answer.setClientSeq(item.seq());
            if (changed) answer.setAnsweredAt(selected.isEmpty() ? null : now);
        }
    }

    private Layout buildLayout(Exam exam) {
        List<Question> qs = exam.getQuestions().stream().map(ExamQuestion::getQuestion).collect(Collectors.toCollection(ArrayList::new));
        if (exam.isShuffleQuestions()) Collections.shuffle(qs);
        Map<Long, List<Long>> options = new LinkedHashMap<>();
        for (Question q : qs) {
            List<Long> ids = q.getOptions().stream().map(QuestionOption::getId).collect(Collectors.toCollection(ArrayList::new));
            if (exam.isShuffleOptions() && q.getType() != QuestionType.TRUE_FALSE) Collections.shuffle(ids);
            options.put(q.getId(), ids);
        }
        return new Layout(qs.stream().map(Question::getId).toList(), options);
    }

    private AttemptResponse toResponse(ExamAttempt a, Instant now) {
        return AttemptMapper.toResponse(a, readLayout(a.getLayout()), now);
    }

    private boolean isPastGrace(ExamAttempt a, Instant now) {
        return now.isAfter(a.getDeadlineAt().plus(grace));
    }

    private ExamAttempt requireOwnedForUpdate(Long attemptId, Long userId) {
        return attempts.findByIdForUpdate(attemptId)
                .filter(a -> a.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Attempt")); // 404 also for other users' attempts
    }

    private String writeLayout(Layout layout) {
        try {
            return json.writeValueAsString(layout);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    Layout readLayout(String raw) {
        try {
            return json.readValue(raw, Layout.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt attempt layout", e);
        }
    }
}
