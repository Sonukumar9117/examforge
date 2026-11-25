package com.examforge.service;

import com.examforge.dto.response.AttemptReportRow;
import com.examforge.dto.response.ExamReportResponse;
import com.examforge.dto.response.PageResponse;
import com.examforge.entity.*;
import com.examforge.entity.enums.AttemptStatus;
import com.examforge.exception.ApiException;
import com.examforge.mapper.ExamMapper;
import com.examforge.repository.ExamAttemptRepository;
import com.examforge.repository.ExamRepository;
import com.examforge.repository.ResultRepository;
import com.examforge.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Admin analytics and CSV export for one exam. */
@Service
@RequiredArgsConstructor
public class ReportService {
    private final ExamRepository exams;
    private final ExamAttemptRepository attempts;
    private final ResultRepository results;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public ExamReportResponse examReport(Long examId) {
        Exam exam = exams.findById(examId).orElseThrow(() -> ApiException.notFound("Exam"));
        List<Result> rs = results.findByExamIdOrderByComputedAtAsc(examId);
        long total = attempts.countByExamId(examId);
        long inProgress = attempts.countByExamIdAndStatus(examId, AttemptStatus.IN_PROGRESS);
        int n = rs.size();

        BigDecimal avgScore = null, avgPct = null, high = null, low = null, passRate = null;
        Integer avgTime = null;
        if (n > 0) {
            avgScore = sum(rs.stream().map(Result::getScore)).divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
            avgPct = sum(rs.stream().map(Result::getPercentage)).divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
            high = rs.stream().map(Result::getScore).max(Comparator.naturalOrder()).orElse(null);
            low = rs.stream().map(Result::getScore).min(Comparator.naturalOrder()).orElse(null);
            avgTime = (int) Math.round(rs.stream().mapToInt(Result::getTimeTakenSeconds).average().orElse(0));
            if (exam.getPassMarks() != null) {
                passRate = ScoringService.percent(rs.stream().filter(r -> Boolean.TRUE.equals(r.getPassed())).count(), n);
            }
        }

        long[] buckets = new long[10];
        for (Result r : rs) {
            int b = r.getPercentage().max(BigDecimal.ZERO).divide(BigDecimal.TEN, 0, RoundingMode.FLOOR).intValue();
            buckets[Math.min(Math.max(b, 0), 9)]++;
        }
        List<ExamReportResponse.Bucket> distribution = new ArrayList<>();
        for (int i = 0; i < 10; i++) distribution.add(new ExamReportResponse.Bucket(i * 10, i * 10 + 10, buckets[i]));

        // Per-question outcomes. Rows only exist for questions a student touched; the rest count as unanswered.
        Map<Long, long[]> outcome = new HashMap<>(); // questionId -> [correct, incorrect]
        for (Object[] row : attempts.answerOutcomes(examId)) {
            Boolean correct = (Boolean) row[1];
            if (correct == null) continue;
            outcome.computeIfAbsent((Long) row[0], k -> new long[2])[correct ? 0 : 1] += ((Number) row[2]).longValue();
        }
        long finished = n;
        List<ExamReportResponse.QuestionStat> questionStats = new ArrayList<>();
        Map<String, long[]> bySubject = new TreeMap<>();
        for (ExamQuestion eq : exam.getQuestions()) {
            Question q = eq.getQuestion();
            long[] o = outcome.getOrDefault(q.getId(), new long[2]);
            long unanswered = Math.max(0, finished - o[0] - o[1]);
            questionStats.add(new ExamReportResponse.QuestionStat(eq.getDisplayOrder(), q.getId(), shorten(q.getQuestionText()),
                    q.getSubject().getName(), q.getDifficulty().name(), o[0], o[1], unanswered, ScoringService.percent(o[0], o[0] + o[1])));
            long[] s = bySubject.computeIfAbsent(q.getSubject().getName(), k -> new long[3]);
            s[0] += o[0];
            s[1] += o[1];
            s[2] += unanswered;
        }
        List<ExamReportResponse.SubjectStat> subjectStats = bySubject.entrySet().stream()
                .map(e -> new ExamReportResponse.SubjectStat(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2],
                        ScoringService.percent(e.getValue()[0], e.getValue()[0] + e.getValue()[1])))
                .toList();

        return new ExamReportResponse(ExamMapper.toSummary(exam), total, finished, inProgress,
                rs.stream().map(Result::getUserId).distinct().count(), avgScore, avgPct, high, low, passRate, avgTime,
                distribution, questionStats, subjectStats);
    }

    @Transactional(readOnly = true)
    public PageResponse<AttemptReportRow> attempts(Long examId, int page, int size) {
        exams.findById(examId).orElseThrow(() -> ApiException.notFound("Exam"));
        Page<ExamAttempt> p = attempts.findByExamIdOrderByStartedAtDesc(examId, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        List<AttemptReportRow> rows = rows(p.getContent());
        return new PageResponse<>(rows, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }

    /** Writes every attempt of the exam as CSV (UTF-8, Excel-friendly). */
    @Transactional(readOnly = true)
    public void writeCsv(Long examId, Writer out) throws IOException {
        exams.findById(examId).orElseThrow(() -> ApiException.notFound("Exam"));
        out.write('﻿'); // BOM so Excel opens UTF-8 correctly
        out.write("attempt_id,username,full_name,email,attempt_no,status,started_at,submitted_at,score,max_score,percentage,correct,incorrect,unanswered,time_taken_seconds,passed\r\n");
        DateTimeFormatter iso = DateTimeFormatter.ISO_INSTANT;
        for (AttemptReportRow r : rows(attempts.findByExamIdOrderByStartedAtAsc(examId))) {
            out.write(String.join(",",
                    String.valueOf(r.attemptId()), csv(r.username()), csv(r.fullName()), csv(r.email()), String.valueOf(r.attemptNo()),
                    r.status(), r.startedAt() == null ? "" : iso.format(r.startedAt()), r.submittedAt() == null ? "" : iso.format(r.submittedAt()),
                    str(r.score()), str(r.maxScore()), str(r.percentage()), str(r.correct()), str(r.incorrect()), str(r.unanswered()),
                    str(r.timeTakenSeconds()), r.passed() == null ? "" : r.passed().toString()));
            out.write("\r\n");
        }
        out.flush();
    }

    private List<AttemptReportRow> rows(List<ExamAttempt> list) {
        Map<Long, User> userById = users.findAllById(list.stream().map(ExamAttempt::getUserId).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Result> resultByAttempt = results.findByAttemptIdIn(list.stream().map(ExamAttempt::getId).toList()).stream()
                .collect(Collectors.toMap(r -> r.getAttempt().getId(), Function.identity()));
        return list.stream().map(a -> {
            User u = userById.get(a.getUserId());
            Result r = resultByAttempt.get(a.getId());
            return new AttemptReportRow(a.getId(), a.getUserId(), u == null ? null : u.getUsername(), u == null ? null : u.getFullName(),
                    u == null ? null : u.getEmail(), a.getAttemptNo(), a.getStatus().name(), a.getStartedAt(), a.getSubmittedAt(),
                    r == null ? null : r.getScore(), r == null ? null : r.getMaxScore(), r == null ? null : r.getPercentage(),
                    r == null ? null : r.getCorrectCount(), r == null ? null : r.getIncorrectCount(),
                    r == null ? null : r.getUnansweredCount(), r == null ? null : r.getTimeTakenSeconds(), r == null ? null : r.getPassed());
        }).toList();
    }

    private static BigDecimal sum(java.util.stream.Stream<BigDecimal> s) {
        return s.reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String shorten(String s) {
        return s.length() <= 140 ? s : s.substring(0, 137) + "…";
    }

    /** CSV-escape; also neutralises formulas (=, +, -, @) so the file is safe to open in spreadsheets. */
    static String csv(String v) {
        if (v == null) return "";
        String s = v;
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) s = "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }
}
