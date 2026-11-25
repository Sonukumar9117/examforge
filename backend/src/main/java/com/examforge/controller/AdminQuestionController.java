package com.examforge.controller;

import com.examforge.dto.request.BulkQuestionStatusRequest;
import com.examforge.dto.request.QuestionImportRequest;
import com.examforge.dto.request.QuestionRequest;
import com.examforge.dto.response.ImportReportResponse;
import com.examforge.dto.response.MessageResponse;
import com.examforge.dto.response.PageResponse;
import com.examforge.dto.response.QuestionResponse;
import com.examforge.entity.enums.Difficulty;
import com.examforge.entity.enums.QuestionStatus;
import com.examforge.entity.enums.QuestionType;
import com.examforge.security.CurrentUser;
import com.examforge.service.QuestionImportService;
import com.examforge.service.QuestionService;
import com.examforge.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/questions")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin — question bank")
public class AdminQuestionController {
    private final QuestionService questions;
    private final QuestionImportService importer;
    private final UserService users;

    public AdminQuestionController(QuestionService questions, QuestionImportService importer, UserService users) {
        this.questions = questions;
        this.importer = importer;
        this.users = users;
    }

    @GetMapping
    public PageResponse<QuestionResponse> search(@RequestParam(required = false) String q,
                                                 @RequestParam(required = false) Long subjectId,
                                                 @RequestParam(required = false) Difficulty difficulty,
                                                 @RequestParam(required = false) QuestionType type,
                                                 @RequestParam(required = false) QuestionStatus status,
                                                 @RequestParam(required = false) String tag,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return questions.search(q, subjectId, difficulty, type, status, tag, page, size);
    }

    @GetMapping("/{id}")
    public QuestionResponse get(@PathVariable Long id) {
        return questions.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody QuestionRequest req,
                                   HttpServletRequest http) {
        return questions.create(req, users.require(CurrentUser.id(jwt)), http.getRemoteAddr());
    }

    @PutMapping("/{id}")
    public QuestionResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                   @Valid @RequestBody QuestionRequest req, HttpServletRequest http) {
        return questions.update(id, req, users.require(CurrentUser.id(jwt)), http.getRemoteAddr());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, HttpServletRequest http) {
        questions.delete(id, users.require(CurrentUser.id(jwt)), http.getRemoteAddr());
    }

    @PostMapping("/bulk-status")
    @Operation(summary = "Approve (or move back to draft) many questions at once")
    public MessageResponse bulkStatus(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BulkQuestionStatusRequest req,
                                      HttpServletRequest http) {
        int n = questions.bulkStatus(req, users.require(CurrentUser.id(jwt)), http.getRemoteAddr());
        return new MessageResponse(n + " question(s) set to " + req.status());
    }

    @PostMapping("/import")
    @Operation(summary = "Import questions from CSV or JSON text. Use dryRun=true to validate without saving.")
    public ImportReportResponse importQuestions(@AuthenticationPrincipal Jwt jwt,
                                                @Valid @RequestBody QuestionImportRequest req, HttpServletRequest http) {
        return importer.importQuestions(req, users.require(CurrentUser.id(jwt)), http.getRemoteAddr());
    }
}
