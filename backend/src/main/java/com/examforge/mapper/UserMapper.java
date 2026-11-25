package com.examforge.mapper;

import com.examforge.dto.response.AdminUserResponse;
import com.examforge.dto.response.ProfileResponse;
import com.examforge.dto.response.UserResponse;
import com.examforge.entity.Role;
import com.examforge.entity.User;
import com.examforge.entity.UserProfile;

import java.util.List;

/** Entity → response DTO conversion. Controllers never return entities. */
public final class UserMapper {
    private UserMapper() {}

    public static UserResponse toResponse(User user, UserProfile p) {
        ProfileResponse profile = p == null ? null : new ProfileResponse(p.getThemePreference().name(), p.getBio(),
                p.getPhone(), p.getTargetCategoryId(), p.getCurrentStreak(), p.getLongestStreak(), p.isShowOnLeaderboard());
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getFullName(),
                user.getAvatarUrl(), user.isEmailVerified(), user.getPasswordHash() != null,
                user.getGoogleId() != null, roleNames(user), user.getCreatedAt(), profile);
    }

    public static AdminUserResponse toAdminResponse(User u) {
        return new AdminUserResponse(u.getId(), u.getUsername(), u.getEmail(), u.getFullName(), u.isEmailVerified(),
                u.isEnabled(), u.getGoogleId() != null, roleNames(u), u.getCreatedAt(), u.getLastLoginAt());
    }

    private static List<String> roleNames(User user) {
        return user.getRoles().stream().map(Role::getName).sorted().toList();
    }
}
