package com.examforge.service;

import com.examforge.entity.*;
import com.examforge.entity.enums.AttemptStatus;
import com.examforge.repository.ResultRepository;
import com.examforge.repository.UserProfileRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns a finished attempt into a {@link Result}. Runs inside the caller's transaction, which must already
 * hold a row lock on the attempt (see ExamAttemptRepository.findByIdForUpdate).
 *
 * Marking: correct = +marks; wrong = −negativeMarks; unanswered = 0. A multiple-answer question counts as
 * correct only if exactly the correct set of options is chosen.
 */
@Service
@RequiredArgsConstructor
public class ScoringService {
    private final ResultRepository results;
    private final UserProfileRepository profiles;
    private ZoneId zone;

    @PostConstruct
    public void setZoneId(){
        this.zone= ZonedDateTime.now().getZone();
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public Result finalizeAttempt(ExamAttempt attempt, AttemptStatus finalStatus, Instant now) {
        Exam exam = attempt.getExam();
        Map<Long, AttemptAnswer> answers = attempt.getAnswers().stream()
                .collect(Collectors.toMap(AttemptAnswer::getQuestionId, Function.identity()));

        Result result = new Result(attempt);
        Map<Long, ResultSubjectScore> bySubject = new LinkedHashMap<>();
        BigDecimal score = BigDecimal.ZERO;
        BigDecimal max = BigDecimal.ZERO;
        int correct = 0, incorrect = 0, unanswered = 0;

        for (ExamQuestion eq : exam.getQuestions()) {
            Question q = eq.getQuestion();
            AttemptAnswer answer = answers.get(q.getId());
            Boolean outcome;
            BigDecimal awarded;
            if (answer == null || !answer.isAnswered()) {
                outcome = null;
                awarded = BigDecimal.ZERO;
                unanswered++;
            } else {
                Set<Long> correctIds = q.getOptions().stream().filter(QuestionOption::isCorrect)
                        .map(QuestionOption::getId).collect(Collectors.toSet());
                if (answer.getSelectedOptionIds().equals(correctIds)) {
                    outcome = true;
                    awarded = eq.getMarks();
                    correct++;
                } else {
                    outcome = false;
                    awarded = eq.getNegativeMarks().negate();
                    incorrect++;
                }
            }
            if (answer != null) {
                answer.setCorrect(outcome);
                answer.setMarksAwarded(awarded);
            }
            score = score.add(awarded);
            max = max.add(eq.getMarks());
            bySubject.computeIfAbsent(q.getSubject().getId(), id -> new ResultSubjectScore(result, q.getSubject()))
                    .add(awarded, eq.getMarks(), outcome);
        }

        int attempted = correct + incorrect;
        result.setScore(score.setScale(2, RoundingMode.HALF_UP));
        result.setMaxScore(max.setScale(2, RoundingMode.HALF_UP));
        result.setCorrectCount(correct);
        result.setIncorrectCount(incorrect);
        result.setUnansweredCount(unanswered);
        result.setAccuracy(percent(correct, attempted));
        result.setPercentage(max.signum() == 0 ? BigDecimal.ZERO.setScale(2)
                : score.multiply(BigDecimal.valueOf(100)).divide(max, 2, RoundingMode.HALF_UP));
        Instant end = now.isBefore(attempt.getDeadlineAt()) ? now : attempt.getDeadlineAt();
        result.setTimeTakenSeconds((int) Math.max(0, Duration.between(attempt.getStartedAt(), end).toSeconds()));
        result.setPassed(exam.getPassMarks() == null ? null : score.compareTo(exam.getPassMarks()) >= 0);
        result.setComputedAt(now);
        result.getSubjectScores().addAll(bySubject.values());

        attempt.setStatus(finalStatus);
        attempt.setSubmittedAt(now);
        updateStreak(attempt.getUserId(), now);
        return results.save(result);
    }

    /** Daily practice streak: +1 on the first finished test of a new consecutive day, reset after a gap. */
    private void updateStreak(Long userId, Instant now) {
        profiles.findByUserId(userId).ifPresent(p -> {
            LocalDate today = now.atZone(zone).toLocalDate();
            LocalDate last = p.getLastActiveDate();
            if (today.equals(last)) return;
            int streak = last != null && last.plusDays(1).equals(today) ? p.getCurrentStreak() + 1 : 1;
            p.setCurrentStreak(streak);
            p.setLongestStreak(Math.max(p.getLongestStreak(), streak));
            p.setLastActiveDate(today);
        });
    }

    static BigDecimal percent(long part, long whole) {
        return whole == 0 ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(part * 100L).divide(BigDecimal.valueOf(whole), 2, RoundingMode.HALF_UP);
    }
}
