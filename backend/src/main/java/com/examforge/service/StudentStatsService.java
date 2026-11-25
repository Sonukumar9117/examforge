package com.examforge.service;

import com.examforge.dto.response.AttemptSummaryResponse;
import com.examforge.dto.response.StudentStatsResponse;
import com.examforge.entity.ExamAttempt;
import com.examforge.entity.Result;
import com.examforge.entity.UserProfile;
import com.examforge.entity.enums.AttemptStatus;
import com.examforge.mapper.AttemptMapper;
import com.examforge.repository.ExamAttemptRepository;
import com.examforge.repository.ResultRepository;
import com.examforge.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Dashboard numbers for one student. */
@Service
@RequiredArgsConstructor
public class StudentStatsService {
    private final ResultRepository results;
    private final ExamAttemptRepository attempts;
    private final UserProfileRepository profiles;


    @Transactional(readOnly = true)
    public StudentStatsResponse stats(Long userId) {
        long taken = results.countByUserId(userId);
        Object[] agg = results.aggregateForUser(userId).get(0);
        BigDecimal avg = agg[0] == null ? null : toDecimal(agg[0]).setScale(2, RoundingMode.HALF_UP);
        BigDecimal best = agg[1] == null ? null : toDecimal(agg[1]);
        long correct = ((Number) agg[2]).longValue();
        long incorrect = ((Number) agg[3]).longValue();
        long time = ((Number) agg[4]).longValue();

        UserProfile profile = profiles.findByUserId(userId).orElse(null);

        List<AttemptSummaryResponse> inProgress = attempts.findByUserIdAndStatusOrderByStartedAtDesc(userId, AttemptStatus.IN_PROGRESS)
                .stream().map(a -> AttemptMapper.toSummary(a, null)).toList();

        List<ExamAttempt> recentAttempts = attempts.findByUserIdOrderByStartedAtDesc(userId, PageRequest.of(0, 5)).getContent();
        Map<Long, Result> recentResults = results.findByAttemptIdIn(recentAttempts.stream().map(ExamAttempt::getId).toList())
                .stream().collect(Collectors.toMap(r -> r.getAttempt().getId(), Function.identity()));
        List<AttemptSummaryResponse> recent = recentAttempts.stream()
                .map(a -> AttemptMapper.toSummary(a, recentResults.get(a.getId()))).toList();

        List<StudentStatsResponse.TrendPoint> trend = results.findByUserIdOrderByComputedAtDesc(userId, PageRequest.of(0, 10))
                .stream()
                .sorted(Comparator.comparing(Result::getComputedAt))
                .map(r -> new StudentStatsResponse.TrendPoint(r.getComputedAt(), r.getAttempt().getExam().getTitle(), r.getPercentage()))
                .toList();

        List<StudentStatsResponse.SubjectAccuracy> subjects = results.subjectTotalsForUser(userId).stream()
                .map(row -> {
                    long c = ((Number) row[2]).longValue();
                    long w = ((Number) row[3]).longValue();
                    long u = ((Number) row[4]).longValue();
                    return new StudentStatsResponse.SubjectAccuracy((Long) row[0], (String) row[1], c, w, u,
                            ScoringService.percent(c, c + w));
                })
                .sorted(Comparator.comparing(StudentStatsResponse.SubjectAccuracy::accuracy))
                .toList();

        return new StudentStatsResponse(taken, avg, best, taken == 0 ? null : ScoringService.percent(correct, correct + incorrect),
                time, profile == null ? 0 : profile.getCurrentStreak(), profile == null ? 0 : profile.getLongestStreak(),
                inProgress, recent, trend, subjects);
    }

    private static BigDecimal toDecimal(Object o) {
        return o instanceof BigDecimal b ? b : new BigDecimal(o.toString());
    }
}
