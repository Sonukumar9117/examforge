package com.examforge.service;

import com.examforge.dto.request.ChangePasswordRequest;
import com.examforge.dto.request.PreferencesRequest;
import com.examforge.dto.request.UpdateProfileRequest;
import com.examforge.dto.response.UserResponse;
import com.examforge.entity.User;
import com.examforge.entity.UserProfile;
import com.examforge.exception.ApiException;
import com.examforge.mapper.UserMapper;
import com.examforge.repository.ExamCategoryRepository;
import com.examforge.repository.UserProfileRepository;
import com.examforge.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final PasswordEncoder passwordEncoder;
    private final ExamCategoryRepository categories;

    @Transactional(readOnly = true)
    public User require(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
    }

    /** Returns the profile, creating an empty one for older accounts that don't have it yet. */
    @Transactional
    public UserProfile profileOf(User user) {
        return profiles.findByUserId(user.getId()).orElseGet(() -> profiles.save(new UserProfile(user)));
    }

    @Transactional
    public UserResponse toResponse(User user) {
        return UserMapper.toResponse(user, profileOf(user));
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest req) {
        User user = require(userId);
        user.setFullName(req.fullName().trim());
        user.setAvatarUrl(blankToNull(req.avatarUrl()));
        UserProfile profile = profileOf(user);
        profile.setBio(blankToNull(req.bio()));
        profile.setPhone(blankToNull(req.phone()));
        Long target = req.targetCategoryId();
        if (target != null && categories.findById(target).filter(c -> c.isActive()).isEmpty()) {
            throw ApiException.badRequest("CATEGORY_NOT_FOUND", "Choose a valid target exam");
        }
        profile.setTargetCategoryId(target);
        return UserMapper.toResponse(user, profile);
    }

    @Transactional
    public UserResponse updatePreferences(Long userId, PreferencesRequest req) {
        User user = require(userId);
        UserProfile profile = profileOf(user);
        if (req.themePreference() != null) profile.setThemePreference(req.themePreference());
        if (req.showOnLeaderboard() != null) profile.setShowOnLeaderboard(req.showOnLeaderboard());
        return UserMapper.toResponse(user, profile);
    }

    /** Changes the password. Google-only accounts may set a first password without a current one. */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest req) {
        User user = require(userId);
        if (user.getPasswordHash() != null) {
            if (req.currentPassword() == null || !passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "WRONG_PASSWORD", "Current password is incorrect");
            }
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
