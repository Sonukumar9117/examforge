-- ExamForge V4: leaderboard privacy.
-- Students who switch this off appear on leaderboards as "Anonymous student".
ALTER TABLE user_profiles
    ADD COLUMN show_on_leaderboard BOOLEAN NOT NULL DEFAULT TRUE;
