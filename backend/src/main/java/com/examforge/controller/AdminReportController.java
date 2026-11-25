package com.examforge.controller;

import com.examforge.dto.response.AttemptReportRow;
import com.examforge.dto.response.ExamReportResponse;
import com.examforge.dto.response.PageResponse;
import com.examforge.security.CurrentUser;
import com.examforge.service.AuditService;
import com.examforge.service.ReportService;
import com.examforge.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin — reports")
public class AdminReportController {
    private final ReportService reports;
    private final UserService users;
    private final AuditService audit;

    public AdminReportController(ReportService reports, UserService users, AuditService audit) {
        this.reports = reports;
        this.users = users;
        this.audit = audit;
    }

    @GetMapping("/exams/{examId}")
    @Operation(summary = "Exam analytics: averages, pass rate, score distribution, per-question and per-subject accuracy")
    public ExamReportResponse exam(@PathVariable Long examId) {
        return reports.examReport(examId);
    }

    @GetMapping("/exams/{examId}/attempts")
    public PageResponse<AttemptReportRow> attempts(@PathVariable Long examId,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "25") int size) {
        return reports.attempts(examId, page, size);
    }

    @GetMapping(value = "/exams/{examId}/attempts.csv", produces = "text/csv")
    @Operation(summary = "Download every attempt of the exam as CSV")
    public void exportCsv(@AuthenticationPrincipal Jwt jwt, @PathVariable Long examId, HttpServletRequest http,
                          HttpServletResponse response) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"exam-" + examId + "-attempts.csv\"");
        reports.writeCsv(examId, new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8));
        audit.record(users.require(CurrentUser.id(jwt)), "REPORT_EXPORTED", "Exam", examId, "attempts.csv", http.getRemoteAddr());
    }
}
