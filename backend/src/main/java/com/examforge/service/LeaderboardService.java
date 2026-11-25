package com.examforge.service;

import com.examforge.dto.response.LeaderboardResponse;
import com.examforge.entity.Exam;
import com.examforge.entity.Result;
import com.examforge.entity.User;
import com.examforge.entity.UserProfile;
import com.examforge.entity.enums.ExamStatus;
import com.examforge.entity.enums.ExamType;
import com.examforge.exception.ApiException;
import com.examforge.mapper.ExamMapper;
import com.examforge.repository.ExamRepository;
import com.examforge.repository.ResultRepository;
import com.examforge.repository.UserProfileRepository;
import com.examforge.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Leaderboards. Each student counts once — with their FIRST finished attempt, so retakes can't climb the
 * board (live tests allow only one attempt anyway). For live tests the board opens when the window closes.
 */
@Service
@RequiredArgsConstructor
public class LeaderboardService {
    static final String ANONYMOUS = "Anonymous student";
    private static final Comparator<Result> ORDER = Comparator
            .comparing(Result::getScore, Comparator.reverseOrder())
            .thenComparingInt(Result::getTimeTakenSeconds)
            .thenComparing(Result::getComputedAt);

    private final ExamRepository exams;
    private final ResultRepository results;
    private final UserRepository users;
    private final UserProfileRepository profiles;


    /** A live test's board is hidden until its window has closed. Other exams are always open. */
    public static boolean isOpen(Exam exam, Instant now) {
        return exam.getType() != ExamType.LIVE || (exam.getEndsAt() != null && !now.isBefore(exam.getEndsAt()));
    }

    /** Ranked results: one per student (their first finished attempt). */
    @Transactional(readOnly = true)
    public List<Result> ranked(Exam exam) {
        Map<Long, Result> firstPerUser = new LinkedHashMap<>();
        for (Result r : results.findByExamIdOrderByComputedAtAsc(exam.getId())) firstPerUser.putIfAbsent(r.getUserId(), r);
        return firstPerUser.values().stream().sorted(ORDER).toList();
    }

    @Transactional(readOnly = true)
    public LeaderboardResponse get(String slug, Long viewerId, int limit) {
        Exam exam = exams.findBySlugAndStatus(slug, ExamStatus.PUBLISHED).orElseThrow(() -> ApiException.notFound("Exam"));
        Instant now = Instant.now();
        if (!isOpen(exam, now)) {
            return new LeaderboardResponse(ExamMapper.toSummary(exam), false,
                    "The leaderboard opens when this live test closes.", exam.getEndsAt(), 0, List.of(), null);
        }

        List<Result> ranked = ranked(exam);
        int n = ranked.size();
        int top = Math.min(Math.max(limit, 1), 500);
        Set<Long> needed = new HashSet<>();
        for (int i = 0; i < Math.min(top, n); i++) needed.add(ranked.get(i).getUserId());
        needed.add(viewerId);
        Map<Long, User> userById = users.findAllById(needed).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Boolean> visible = profiles.findByUserIdIn(needed).stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(), UserProfile::isShowOnLeaderboard));

        List<LeaderboardResponse.Entry> entries = new ArrayList<>();
        LeaderboardResponse.Entry me = null;
        for (int i = 0; i < n; i++) {
            Result r = ranked.get(i);
            boolean isMe = r.getUserId().equals(viewerId);
            if (i >= top && !isMe) continue;
            User u = userById.get(r.getUserId());
            String name = isMe ? "You" : (u == null || !visible.getOrDefault(r.getUserId(), true) ? ANONYMOUS : u.getFullName());
            LeaderboardResponse.Entry e = new LeaderboardResponse.Entry(i + 1, name, isMe, r.getScore(), r.getMaxScore(),
                    r.getPercentage(), r.getAccuracy(), r.getTimeTakenSeconds(),
                    n <= 1 ? BigDecimal.ZERO.setScale(2) : ScoringService.percent(n - 1 - i, n - 1));
            if (i < top) entries.add(e);
            if (isMe) me = e;
        }
        return new LeaderboardResponse(ExamMapper.toSummary(exam), true, n == 0 ? "No one has finished this test yet." : null,
                null, n, entries, me);
    }

    /** Rank (1-based) and "better than %" of one student, or null if they are not on the board. */
    @Transactional(readOnly = true)
    public LeaderboardResponse.Entry positionOf(Exam exam, Long userId) {
        List<Result> ranked = ranked(exam);
        int n = ranked.size();
        for (int i = 0; i < n; i++) {
            Result r = ranked.get(i);
            if (r.getUserId().equals(userId)) {
                return new LeaderboardResponse.Entry(i + 1, "You", true, r.getScore(), r.getMaxScore(), r.getPercentage(),
                        r.getAccuracy(), r.getTimeTakenSeconds(),
                        n <= 1 ? BigDecimal.ZERO.setScale(2) : ScoringService.percent(n - 1 - i, n - 1));
            }
        }
        return null;
    }
}
