package com.examforge.controller;

import com.examforge.dto.response.LeaderboardResponse;
import com.examforge.dto.response.LiveTestsResponse;
import com.examforge.security.CurrentUser;
import com.examforge.service.ExamBrowseService;
import com.examforge.service.LeaderboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Live tests & leaderboards")
public class LeaderboardController {
    private final LeaderboardService leaderboards;
    private final ExamBrowseService browse;

    public LeaderboardController(LeaderboardService leaderboards, ExamBrowseService browse) {
        this.leaderboards = leaderboards;
        this.browse = browse;
    }

    @GetMapping("/exams/live")
    @Operation(summary = "Live tests: open now, upcoming, and recently closed, with your own status")
    public LiveTestsResponse live(@AuthenticationPrincipal Jwt jwt) {
        return browse.live(CurrentUser.id(jwt));
    }

    @GetMapping("/leaderboards/exams/{slug}")
    @Operation(summary = "Leaderboard of an exam (live tests: only after the test window closes)")
    public LeaderboardResponse exam(@AuthenticationPrincipal Jwt jwt, @PathVariable String slug,
                                    @RequestParam(defaultValue = "100") int limit) {
        return leaderboards.get(slug, CurrentUser.id(jwt), limit);
    }
}
