package com.examforge.controller;

import com.examforge.dto.response.ResultResponse;
import com.examforge.entity.Role;
import com.examforge.security.CurrentUser;
import com.examforge.service.ResultService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/results")
@Tag(name = "Results")
public class ResultController {
    private final ResultService results;

    public ResultController(ResultService results) {
        this.results = results;
    }

    /** Students see their own results; admins can open any result. */
    @GetMapping("/{attemptId}")
    public ResultResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long attemptId) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        boolean admin = roles != null && roles.contains(Role.ADMIN);
        return results.get(attemptId, CurrentUser.id(jwt), admin);
    }
}
