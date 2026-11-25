package com.examforge.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "user_profiles")
@Getter
@Setter
public class UserProfile {

    public enum ThemePreference { LIGHT, DARK, SYSTEM }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "target_category_id")
    private Long targetCategoryId;

    @Column(length = 500)
    private String bio;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "theme_preference", nullable = false, columnDefinition = "VARCHAR(10)")
    private ThemePreference themePreference = ThemePreference.SYSTEM;

    @Column(name = "current_streak", nullable = false)
    private int currentStreak;

    @Column(name = "longest_streak", nullable = false)
    private int longestStreak;

    @Column(name = "last_active_date")
    private LocalDate lastActiveDate;

    /** false = shown as "Anonymous student" on leaderboards. */
    @Column(name = "show_on_leaderboard", nullable = false)
    private boolean showOnLeaderboard = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserProfile() {}

    public UserProfile(User user) {
        this.user = user;
    }

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    }
