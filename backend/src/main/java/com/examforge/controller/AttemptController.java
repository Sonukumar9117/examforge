package com.examforge.controller;

import com.examforge.dto.request.SaveAnswersRequest;
import com.examforge.dto.response.AttemptResponse;
import com.examforge.dto.response.AttemptSummaryResponse;
import com.examforge.dto.response.PageResponse;
import com.examforge.dto.response.ResultResponse;
import com.examforge.dto.response.SaveAnswersResponse;
import com.examforge.security.CurrentUser;
import com.examforge.service.AttemptService;
import com.examforge.service.ResultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** The exam hall API. Every call is limited to the signed-in student's own attempts. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Exam attempts")
public class AttemptController {
    private final AttemptService attempts;
    private final ResultService results;

    public AttemptController(AttemptService attempts, ResultService results) {
        this.attempts = attempts;
        this.results = results;
    }

    @PostMapping("/exams/{slug}/attempts")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start a test, or resume the attempt already in progress")
    public AttemptResponse start(@AuthenticationPrincipal Jwt jwt, @PathVariable String slug) {
        return attempts.start(slug, CurrentUser.id(jwt));
    }

    @GetMapping("/attempts/me")
    public PageResponse<AttemptSummaryResponse> mine(@AuthenticationPrincipal Jwt jwt,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return attempts.mine(CurrentUser.id(jwt), page, size);
    }

    @GetMapping("/attempts/{id}")
    @Operation(summary = "Questions (without answers), saved state, serverNow and deadlineAt")
    public AttemptResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return attempts.get(id, CurrentUser.id(jwt));
    }

    @PutMapping("/attempts/{id}/answers")
    @Operation(summary = "Autosave answer states (batch). Late or repeated items are ignored by seq.")
    public SaveAnswersResponse save(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                    @Valid @RequestBody SaveAnswersRequest req) {
        return attempts.save(id, CurrentUser.id(jwt), req);
    }

    @PostMapping("/attempts/{id}/submit")
    @Operation(summary = "Submit the test (safe to call more than once) and get the result")
    public ResultResponse submit(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                 @Valid @RequestBody(required = false) SaveAnswersRequest finalAnswers) {
        Long userId = CurrentUser.id(jwt);
        attempts.submit(id, userId, finalAnswers);
        return results.get(id, userId, false);
    }
}
