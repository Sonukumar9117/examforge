package com.examforge.mapper;

import com.examforge.dto.response.AttemptResponse;
import com.examforge.dto.response.AttemptSummaryResponse;
import com.examforge.entity.*;
import com.examforge.service.AttemptService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class AttemptMapper {
    private AttemptMapper() {}

    public static AttemptSummaryResponse.ExamRef examRef(Exam e) {
        return new AttemptSummaryResponse.ExamRef(e.getId(), e.getTitle(), e.getSlug(), e.getCategory().getName(),
                e.getCategory().getIcon());
    }

    public static AttemptSummaryResponse toSummary(ExamAttempt a, Result r) {
        return new AttemptSummaryResponse(a.getId(), a.getAttemptNo(), a.getStatus().name(), examRef(a.getExam()),
                a.getStartedAt(), a.getDeadlineAt(), a.getSubmittedAt(),
                r == null ? null : r.getScore(), r == null ? null : r.getMaxScore(),
                r == null ? null : r.getPercentage(), r == null ? null : r.getPassed());
    }

    /** Builds the exam-hall view in the student's own question/option order. Never includes correct answers. */
    public static AttemptResponse toResponse(ExamAttempt a, AttemptService.Layout layout, Instant now) {
        Exam exam = a.getExam();
        Map<Long, ExamQuestion> byQuestion = exam.getQuestions().stream()
                .collect(Collectors.toMap(eq -> eq.getQuestion().getId(), Function.identity()));

        List<AttemptResponse.QuestionItem> questions = new ArrayList<>();
        int number = 1;
        for (Long qid : layout.questions()) {
            ExamQuestion eq = byQuestion.get(qid);
            if (eq == null) continue; // removed from the exam after this attempt started
            Question q = eq.getQuestion();
            Map<Long, QuestionOption> opts = q.getOptions().stream()
                    .collect(Collectors.toMap(QuestionOption::getId, Function.identity()));
            List<AttemptResponse.OptionItem> options = new ArrayList<>();
            int i = 0;
            for (Long oid : layout.options().getOrDefault(qid, List.of())) {
                QuestionOption o = opts.get(oid);
                if (o != null) options.add(new AttemptResponse.OptionItem(o.getId(), String.valueOf((char) ('A' + i++)), o.getOptionText()));
            }
            questions.add(new AttemptResponse.QuestionItem(q.getId(), number++, q.getType().name(), q.getQuestionText(),
                    q.getImageUrl(), q.getSubject().getName(), eq.getMarks(), eq.getNegativeMarks(), options));
        }

        List<AttemptResponse.AnswerState> answers = a.getAnswers().stream()
                .map(ans -> new AttemptResponse.AnswerState(ans.getQuestionId(), List.copyOf(ans.getSelectedOptionIds()),
                        ans.isMarkedForReview(), ans.isVisited(), ans.getClientSeq()))
                .toList();

        return new AttemptResponse(a.getId(), a.getAttemptNo(), a.getStatus().name(),
                new AttemptResponse.ExamInfo(exam.getId(), exam.getTitle(), exam.getSlug(), exam.getCategory().getName(),
                        exam.getDurationMinutes(), exam.isAllowNavigation(), exam.totalMarks()),
                a.getStartedAt(), a.getDeadlineAt(), now, a.getSubmittedAt(), questions, answers);
    }
}
