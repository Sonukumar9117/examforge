package com.examforge.service;

import com.examforge.dto.request.SavedQuestionRequest;
import com.examforge.dto.response.PageResponse;
import com.examforge.dto.response.ResultResponse;
import com.examforge.dto.response.SavedQuestionResponse;
import com.examforge.entity.Question;
import com.examforge.entity.QuestionOption;
import com.examforge.entity.SavedQuestion;
import com.examforge.exception.ApiException;
import com.examforge.repository.ExamAttemptRepository;
import com.examforge.repository.QuestionRepository;
import com.examforge.repository.SavedQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Bookmarked questions for revision. A student may only save a question they have already answered
 * in a finished test whose answers are released — so saving can never be used to peek at answers.
 */
@Service
@RequiredArgsConstructor
public class SavedQuestionService {
    private final SavedQuestionRepository saved;
    private final QuestionRepository questions;
    private final ExamAttemptRepository attempts;


    @Transactional
    public SavedQuestionResponse save(Long userId, SavedQuestionRequest req) {
        Question q = questions.findById(req.questionId()).orElseThrow(() -> ApiException.notFound("Question"));
        Instant now = Instant.now();
        boolean allowed = attempts.findFinishedExamsContainingQuestion(userId, q.getId()).stream()
                .anyMatch(exam -> ResultService.reviewAvailable(exam, now));
        if (!allowed) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CANNOT_SAVE_QUESTION",
                    "You can save questions from your reviewed test results only");
        }
        SavedQuestion s = saved.findByUserIdAndQuestionId(userId, q.getId())
                .orElseGet(() -> saved.save(new SavedQuestion(userId, q, null)));
        s.setNote(req.note() == null || req.note().isBlank() ? null : req.note().trim());
        return toResponse(s);
    }

    @Transactional
    public void remove(Long userId, Long questionId) {
        saved.findByUserIdAndQuestionId(userId, questionId).ifPresent(saved::delete);
    }

    @Transactional(readOnly = true)
    public PageResponse<SavedQuestionResponse> list(Long userId, int page, int size) {
        return PageResponse.of(saved.findByUserIdOrderByCreatedAtDesc(userId,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50))), SavedQuestionService::toResponse);
    }

    private static SavedQuestionResponse toResponse(SavedQuestion s) {
        Question q = s.getQuestion();
        List<ResultResponse.ReviewOption> options = new ArrayList<>();
        for (QuestionOption o : q.getOptions()) {
            options.add(new ResultResponse.ReviewOption(o.getId(), o.getLabel(), o.getOptionText(), o.isCorrect(), false));
        }
        return new SavedQuestionResponse(s.getId(), s.getNote(), s.getCreatedAt(), q.getId(), q.getType().name(),
                q.getQuestionText(), q.getSubject().getName(), q.getTopic(), q.getExplanation(), options);
    }
}
