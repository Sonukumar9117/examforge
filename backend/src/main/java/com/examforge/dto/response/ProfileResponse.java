package com.examforge.dto.response;

public record ProfileResponse(
        String themePreference,
        String bio,
        String phone,
        Long targetCategoryId,
        int currentStreak,
        int longestStreak,
        boolean showOnLeaderboard) {}
