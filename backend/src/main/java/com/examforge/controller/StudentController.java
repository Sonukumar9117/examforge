package com.examforge.controller;

import com.examforge.dto.request.SavedQuestionRequest;
import com.examforge.dto.response.PageResponse;
import com.examforge.dto.response.SavedQuestionResponse;
import com.examforge.dto.response.StudentStatsResponse;
import com.examforge.security.CurrentUser;
import com.examforge.service.SavedQuestionService;
import com.examforge.service.StudentStatsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** The signed-in student's dashboard numbers and saved questions. */
@RestController
@RequestMapping("/api/v1/users/me")
@Tag(name = "Current user")
public class StudentController {
    private final StudentStatsService stats;
    private final SavedQuestionService saved;

    public StudentController(StudentStatsService stats, SavedQuestionService saved) {
        this.stats = stats;
        this.saved = saved;
    }

    @GetMapping("/stats")
    public StudentStatsResponse stats(@AuthenticationPrincipal Jwt jwt) {
        return stats.stats(CurrentUser.id(jwt));
    }

    @GetMapping("/saved-questions")
    public PageResponse<SavedQuestionResponse> savedQuestions(@AuthenticationPrincipal Jwt jwt,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        return saved.list(CurrentUser.id(jwt), page, size);
    }

    @PostMapping("/saved-questions")
    @ResponseStatus(HttpStatus.CREATED)
    public SavedQuestionResponse saveQuestion(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SavedQuestionRequest req) {
        return saved.save(CurrentUser.id(jwt), req);
    }

    @DeleteMapping("/saved-questions/{questionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeSavedQuestion(@AuthenticationPrincipal Jwt jwt, @PathVariable Long questionId) {
        saved.remove(CurrentUser.id(jwt), questionId);
    }
}
