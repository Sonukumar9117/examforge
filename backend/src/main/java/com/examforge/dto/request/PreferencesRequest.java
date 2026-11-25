package com.examforge.dto.request;

import com.examforge.entity.UserProfile;

/** Each field is optional; only the ones sent are changed. */
public record PreferencesRequest(UserProfile.ThemePreference themePreference, Boolean showOnLeaderboard) {}
