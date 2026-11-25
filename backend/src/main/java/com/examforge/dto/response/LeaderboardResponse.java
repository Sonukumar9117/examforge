package com.examforge.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Ranking of one exam. For live tests it is only {@code available} after the test window closes.
 * Ranked by score (high first), then time taken (fast first), then submission time (early first).
 * Students who turned off "show on leaderboard" appear as "Anonymous student".
 */
public record LeaderboardResponse(
        ExamSummaryResponse exam,
        boolean available,
        String message,
        Instant opensAt,
        int totalParticipants,
        List<Entry> entries,
        Entry me) {

    public record Entry(int rank, String name, boolean me, BigDecimal score, BigDecimal maxScore, BigDecimal percentage,
                        BigDecimal accuracy, int timeTakenSeconds, BigDecimal betterThanPercent) {}
}
