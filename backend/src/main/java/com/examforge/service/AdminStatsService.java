package com.examforge.service;

import com.examforge.dto.response.AdminStatsResponse;
import com.examforge.entity.Role;
import com.examforge.entity.enums.AttemptStatus;
import com.examforge.entity.enums.ExamStatus;
import com.examforge.entity.enums.QuestionStatus;
import com.examforge.repository.ExamAttemptRepository;
import com.examforge.repository.ExamCategoryRepository;
import com.examforge.repository.ExamRepository;
import com.examforge.repository.QuestionRepository;
import com.examforge.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Numbers for the admin dashboard. */
@RequiredArgsConstructor
@Service
public class AdminStatsService {
    private final UserRepository users;
    private final ExamCategoryRepository categories;
    private final QuestionRepository questions;
    private final ExamRepository exams;
    private final ExamAttemptRepository attempts;

    @Transactional(readOnly = true)
    public AdminStatsResponse stats() {
        return new AdminStatsResponse(
                users.count(),
                users.countByRole(Role.STUDENT),
                users.countByRole(Role.ADMIN),
                users.countByEmailVerifiedTrue(),
                users.countByCreatedAtAfter(Instant.now().minus(7, ChronoUnit.DAYS)),
                categories.count(),
                questions.count(),
                questions.countByStatus(QuestionStatus.APPROVED),
                exams.countByStatus(ExamStatus.PUBLISHED),
                exams.countByStatus(ExamStatus.DRAFT),
                attempts.count(),
                attempts.countByStatusNot(AttemptStatus.IN_PROGRESS));
    }
}
