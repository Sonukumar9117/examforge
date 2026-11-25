package com.examforge.service;

import com.examforge.dto.response.ExamSummaryResponse;
import com.examforge.dto.response.LeaderboardResponse;
import com.examforge.dto.response.ResultResponse;
import com.examforge.entity.*;
import com.examforge.entity.enums.AttemptStatus;
import com.examforge.entity.enums.ExamType;
import com.examforge.exception.ApiException;
import com.examforge.mapper.AttemptMapper;
import com.examforge.repository.ExamAttemptRepository;
import com.examforge.repository.ResultRepository;
import com.examforge.repository.SavedQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Result pages: score summary, subject breakdown, rank, question review and practice suggestions. */
@Service
@RequiredArgsConstructor
public class ResultService {
    /** Rank and percentile are only meaningful with enough comparison data. */
    static final int MIN_CANDIDATES_FOR_RANK = 10;
    private static final BigDecimal WEAK_SUBJECT_ACCURACY = BigDecimal.valueOf(60);

    private final ExamAttemptRepository attempts;
    private final ResultRepository results;
    private final SavedQuestionRepository saved;
    private final AttemptService attemptService;
    private final ExamBrowseService browse;
    private final LeaderboardService leaderboards;


    /** Whether students may see correct answers and explanations for this exam yet. */
    public static boolean reviewAvailable(Exam exam, Instant now) {
        return switch (exam.getResultRelease()) {
            case IMMEDIATE -> true;
            case AFTER_END -> exam.getEndsAt() == null || now.isAfter(exam.getEndsAt());
            case MANUAL -> exam.getResultsReleasedAt() != null;
        };
    }

    @Transactional
    public ResultResponse get(Long attemptId, Long viewerId, boolean viewerIsAdmin) {
        ExamAttempt a;
        if (viewerIsAdmin) {
            a = attempts.findById(attemptId).orElseThrow(() -> ApiException.notFound("Attempt"));
        } else {
            a = attemptService.finalizeIfExpired(attemptId, viewerId); // 404 for other users' attempts
        }
        if (a.getStatus() == AttemptStatus.IN_PROGRESS) {
            throw new ApiException(HttpStatus.CONFLICT, "ATTEMPT_IN_PROGRESS", "This test is still in progress");
        }
        Result r = results.findByAttemptId(attemptId).orElseThrow(() -> ApiException.notFound("Result"));
        Exam exam = a.getExam();
        Instant now = Instant.now();

        long candidates = results.countByExamId(exam.getId());
        Long rank = null;
        BigDecimal percentile = null;
        String rankNote = null;
        boolean leaderboardOpen = LeaderboardService.isOpen(exam, now);
        if (exam.getType() == ExamType.LIVE) {
            if (!leaderboardOpen) {
                rankNote = "Ranks are published when this live test closes on "
                        + DateTimeFormatter.ISO_INSTANT.format(exam.getEndsAt().atOffset(ZoneOffset.UTC)) + ".";
            } else {
                LeaderboardResponse.Entry pos = leaderboards.positionOf(exam, a.getUserId());
                if (pos != null && pos.score().compareTo(r.getScore()) == 0) { // ranked by first attempt
                    rank = (long) pos.rank();
                    percentile = pos.betterThanPercent();
                }
                candidates = leaderboards.ranked(exam).size();
            }
        } else if (candidates >= MIN_CANDIDATES_FOR_RANK) {
            rank = results.countByExamIdAndScoreGreaterThan(exam.getId(), r.getScore()) + 1;
            percentile = ScoringService.percent(results.countByExamIdAndScoreLessThan(exam.getId(), r.getScore()), candidates);
        } else {
            rankNote = "Rank appears once at least " + MIN_CANDIDATES_FOR_RANK + " students have taken this test.";
        }

        List<ResultResponse.SubjectScore> subjects = r.getSubjectScores().stream()
                .map(s -> new ResultResponse.SubjectScore(s.getSubject().getId(), s.getSubject().getName(), s.getScore(),
                        s.getMaxScore(), s.getCorrectCount(), s.getIncorrectCount(), s.getUnansweredCount(),
                        ScoringService.percent(s.getCorrectCount(), s.getCorrectCount() + s.getIncorrectCount())))
                .sorted(Comparator.comparing(ResultResponse.SubjectScore::subject))
                .toList();

        boolean reviewOpen = viewerIsAdmin || reviewAvailable(exam, now);
        String reviewMessage = reviewOpen ? null : switch (exam.getResultRelease()) {
            case AFTER_END -> "Answers and explanations will be available after the test closes on "
                    + DateTimeFormatter.ISO_INSTANT.format(exam.getEndsAt().atOffset(ZoneOffset.UTC)) + ".";
            case MANUAL -> "Answers and explanations will be available when the examiner releases them.";
            default -> null;
        };

        return new ResultResponse(a.getId(), a.getAttemptNo(), a.getStatus().name(), AttemptMapper.examRef(exam),
                r.getScore(), r.getMaxScore(), r.getPercentage(), r.getAccuracy(), exam.getPassMarks(), r.getPassed(),
                r.getCorrectCount() + r.getIncorrectCount() + r.getUnansweredCount(),
                r.getCorrectCount(), r.getIncorrectCount(), r.getUnansweredCount(), r.getTimeTakenSeconds(),
                a.getSubmittedAt(), rank, percentile, candidates, rankNote, leaderboardOpen, subjects, reviewOpen, reviewMessage,
                reviewOpen ? review(a, viewerId) : null,
                recommendations(exam, subjects));
    }

    private List<ResultResponse.ReviewItem> review(ExamAttempt a, Long viewerId) {
        AttemptService.Layout layout = attemptService.readLayout(a.getLayout());
        Map<Long, ExamQuestion> byQuestion = a.getExam().getQuestions().stream()
                .collect(Collectors.toMap(eq -> eq.getQuestion().getId(), Function.identity()));
        Map<Long, AttemptAnswer> answers = a.getAnswers().stream()
                .collect(Collectors.toMap(AttemptAnswer::getQuestionId, Function.identity()));
        Set<Long> savedIds = byQuestion.isEmpty() ? Set.of() : saved.savedAmong(viewerId, byQuestion.keySet());

        List<ResultResponse.ReviewItem> items = new ArrayList<>();
        int number = 1;
        for (Long qid : layout.questions()) {
            ExamQuestion eq = byQuestion.get(qid);
            if (eq == null) continue;
            Question q = eq.getQuestion();
            AttemptAnswer ans = answers.get(qid);
            Set<Long> chosen = ans == null ? Set.of() : ans.getSelectedOptionIds();
            Map<Long, QuestionOption> opts = q.getOptions().stream().collect(Collectors.toMap(QuestionOption::getId, Function.identity()));
            List<ResultResponse.ReviewOption> options = new ArrayList<>();
            int i = 0;
            for (Long oid : layout.options().getOrDefault(qid, List.of())) {
                QuestionOption o = opts.get(oid);
                if (o == null) continue;
                options.add(new ResultResponse.ReviewOption(o.getId(), String.valueOf((char) ('A' + i++)), o.getOptionText(),
                        o.isCorrect(), chosen.contains(o.getId())));
            }
            String outcome = ans == null || ans.getCorrect() == null ? "UNANSWERED" : ans.getCorrect() ? "CORRECT" : "INCORRECT";
            BigDecimal awarded = ans == null || ans.getMarksAwarded() == null ? BigDecimal.ZERO.setScale(2) : ans.getMarksAwarded();
            items.add(new ResultResponse.ReviewItem(number++, qid, q.getType().name(), q.getQuestionText(), q.getImageUrl(),
                    q.getSubject().getName(), q.getTopic(), eq.getMarks(), eq.getNegativeMarks(), awarded, outcome,
                    q.getExplanation(), savedIds.contains(qid), options));
        }
        return items;
    }

    /** Up to three other published tests that cover the subjects the student found hardest. */
    private List<ExamSummaryResponse> recommendations(Exam exam, List<ResultResponse.SubjectScore> subjects) {
        List<ResultResponse.SubjectScore> weak = subjects.stream()
                .filter(s -> s.correct() + s.incorrect() + s.unanswered() > 0)
                .filter(s -> s.accuracy().compareTo(WEAK_SUBJECT_ACCURACY) < 0 || s.unanswered() > s.correct())
                .sorted(Comparator.comparing(ResultResponse.SubjectScore::accuracy))
                .toList();
        Map<Long, ExamSummaryResponse> picked = new LinkedHashMap<>();
        for (ResultResponse.SubjectScore s : weak) {
            for (ExamSummaryResponse e : browse.browse(null, null, s.subjectId(), null, null, 0, 6).content()) {
                if (!e.id().equals(exam.getId())) picked.putIfAbsent(e.id(), e);
                if (picked.size() >= 3) return List.copyOf(picked.values());
            }
        }
        return List.copyOf(picked.values());
    }
}
