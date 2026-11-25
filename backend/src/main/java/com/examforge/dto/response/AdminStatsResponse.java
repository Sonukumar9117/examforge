package com.examforge.dto.response;

public record AdminStatsResponse(
        long totalUsers,
        long totalStudents,
        long totalAdmins,
        long verifiedUsers,
        long newUsersLast7Days,
        long totalCategories,
        long totalQuestions,
        long approvedQuestions,
        long publishedExams,
        long draftExams,
        long totalAttempts,
        long finishedAttempts) {}
