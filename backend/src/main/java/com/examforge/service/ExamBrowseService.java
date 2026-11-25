package com.examforge.service;

import com.examforge.dto.response.ExamPublicResponse;
import com.examforge.dto.response.LiveTestsResponse;
import com.examforge.dto.response.ExamSummaryResponse;
import com.examforge.dto.response.PageResponse;
import com.examforge.entity.Exam;
import com.examforge.entity.ExamAttempt;
import com.examforge.entity.ExamQuestion;
import com.examforge.entity.Result;
import com.examforge.entity.Subject;
import com.examforge.entity.enums.Difficulty;
import com.examforge.entity.enums.ExamStatus;
import com.examforge.entity.enums.ExamType;
import com.examforge.exception.ApiException;
import com.examforge.mapper.CatalogMapper;
import com.examforge.mapper.ExamMapper;
import com.examforge.repository.ExamAttemptRepository;
import com.examforge.repository.ExamRepository;
import com.examforge.repository.ExamSpecs;
import com.examforge.repository.ResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Student-facing exam catalogue: only published exams that are still available. */
@Service
@RequiredArgsConstructor
public class ExamBrowseService {
    /** Closed live tests stay listed (with their leaderboard) for this long. */
    private static final Duration RECENTLY_ENDED = Duration.ofDays(30);

    private final ExamRepository exams;
    private final ExamAttemptRepository attempts;
    private final ResultRepository results;



    /** Live tests (published, type LIVE): open now, upcoming, and recently closed — with this student's status. */
    @Transactional(readOnly = true)
    public LiveTestsResponse live(Long userId) {
        Instant now = Instant.now();
        List<Exam> list = exams.findByStatusAndTypeAndEndsAtAfterOrderByStartsAtAsc(ExamStatus.PUBLISHED, ExamType.LIVE,
                now.minus(RECENTLY_ENDED));
        Map<Long, ExamAttempt> mine = attempts.findByUserIdAndExamIdIn(userId, list.stream().map(Exam::getId).toList()).stream()
                .collect(Collectors.toMap(a -> a.getExam().getId(), Function.identity(), (x, y) -> x.getAttemptNo() <= y.getAttemptNo() ? x : y));
        Map<Long, Result> scores = results.findByAttemptIdIn(mine.values().stream().map(ExamAttempt::getId).toList()).stream()
                .collect(Collectors.toMap(r -> r.getAttempt().getId(), Function.identity()));

        List<LiveTestsResponse.Item> open = new ArrayList<>(), upcoming = new ArrayList<>(), ended = new ArrayList<>();
        for (Exam e : list) {
            ExamAttempt a = mine.get(e.getId());
            Result r = a == null ? null : scores.get(a.getId());
            LiveTestsResponse.Item item = new LiveTestsResponse.Item(ExamMapper.toSummary(e), a == null ? null : a.getId(),
                    a == null ? null : a.getStatus().name(), r == null ? null : r.getScore());
            if (!e.getEndsAt().isAfter(now)) ended.add(item);
            else if (e.getStartsAt() != null && e.getStartsAt().isAfter(now)) upcoming.add(item);
            else open.add(item);
        }
        Collections.reverse(ended); // most recently closed first
        return new LiveTestsResponse(open, upcoming, ended, now);
    }

    @Transactional(readOnly = true)
    public PageResponse<ExamSummaryResponse> browse(String q, String categorySlug, Long subjectId, Difficulty difficulty,
                                                    ExamType type, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Direction.DESC, "publishedAt").and(Sort.by("id")));
        var spec = ExamSpecs.visibleToStudents(Instant.now())
                .and(ExamSpecs.filter(q, null, categorySlug, null, type, difficulty, subjectId));
        return PageResponse.of(exams.findAll(spec, pageable), ExamMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public ExamPublicResponse get(String slug) {
        Exam e = exams.findBySlugAndStatus(slug, ExamStatus.PUBLISHED)
                .orElseThrow(() -> ApiException.notFound("Exam"));
        BigDecimal maxNegative = e.getQuestions().stream().map(ExamQuestion::getNegativeMarks)
                .max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        Map<Long, Subject> subjects = new LinkedHashMap<>();
        e.getQuestions().forEach(eq -> subjects.putIfAbsent(eq.getQuestion().getSubject().getId(), eq.getQuestion().getSubject()));
        return new ExamPublicResponse(ExamMapper.toSummary(e), e.getDescription(), e.getInstructions(),
                e.isAllowNavigation(), maxNegative.signum() > 0, maxNegative,
                subjects.values().stream().map(CatalogMapper::toResponse).toList());
    }
}
