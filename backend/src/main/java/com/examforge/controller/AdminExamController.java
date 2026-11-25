package com.examforge.controller;

import com.examforge.dto.request.ExamQuestionsRequest;
import com.examforge.dto.request.ExamRequest;
import com.examforge.dto.response.ExamDetailResponse;
import com.examforge.dto.response.ExamSummaryResponse;
import com.examforge.dto.response.PageResponse;
import com.examforge.entity.enums.ExamStatus;
import com.examforge.security.CurrentUser;
import com.examforge.service.ExamService;
import com.examforge.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/exams")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin — exams")
public class AdminExamController {
    private final ExamService exams;
    private final UserService users;

    public AdminExamController(ExamService exams, UserService users) {
        this.exams = exams;
        this.users = users;
    }

    @GetMapping
    public PageResponse<ExamSummaryResponse> search(@RequestParam(required = false) String q,
                                                    @RequestParam(required = false) Long categoryId,
                                                    @RequestParam(required = false) ExamStatus status,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return exams.search(q, categoryId, status, page, size);
    }

    @GetMapping("/{id}")
    public ExamDetailResponse get(@PathVariable Long id) {
        return exams.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExamDetailResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ExamRequest req,
                                     HttpServletRequest http) {
        return exams.create(req, users.require(CurrentUser.id(jwt)), http.getRemoteAddr());
    }

    @PutMapping("/{id}")
    public ExamDetailResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                     @Valid @RequestBody ExamRequest req, HttpServletRequest http) {
        return exams.update(id, req, users.require(CurrentUser.id(jwt)), http.getRemoteAddr());
    }

    @PutMapping("/{id}/questions")
    public ExamDetailResponse setQuestions(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                           @Valid @RequestBody ExamQuestionsRequest req, HttpServletRequest http) {
        return exams.setQuestions(id, req, users.require(CurrentUser.id(jwt)), http.getRemoteAddr());
    }

    @PostMapping("/{id}/publish")
    public ExamDetailResponse publish(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, HttpServletRequest http) {
        return exams.publish(id, users.require(CurrentUser.id(jwt)), http.getRemoteAddr());
    }

    @PostMapping("/{id}/unpublish")
    public ExamDetailResponse unpublish(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, HttpServletRequest http) {
        return exams.unpublish(id, users.require(CurrentUser.id(jwt)), http.getRemoteAddr());
    }

    @PostMapping("/{id}/release-results")
    public ExamDetailResponse releaseResults(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, HttpServletRequest http) {
        return exams.releaseResults(id, users.require(CurrentUser.id(jwt)), http.getRemoteAddr());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, HttpServletRequest http) {
        exams.delete(id, users.require(CurrentUser.id(jwt)), http.getRemoteAddr());
    }
}
