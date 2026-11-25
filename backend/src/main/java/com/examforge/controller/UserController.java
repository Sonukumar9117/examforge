package com.examforge.controller;

import com.examforge.dto.request.ChangePasswordRequest;
import com.examforge.dto.request.PreferencesRequest;
import com.examforge.dto.request.UpdateProfileRequest;
import com.examforge.dto.response.MessageResponse;
import com.examforge.dto.response.UserResponse;
import com.examforge.security.CurrentUser;
import com.examforge.service.AuthService;
import com.examforge.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** The signed-in user's own account. The user id always comes from the access token. */
@RestController
@RequestMapping("/api/v1/users/me")
@Tag(name = "Current user")
public class UserController {
    private final UserService userService;
    private final AuthService authService;

    public UserController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    @GetMapping
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return userService.toResponse(userService.require(CurrentUser.id(jwt)));
    }

    @PutMapping
    public UserResponse updateProfile(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest req) {
        return userService.updateProfile(CurrentUser.id(jwt), req);
    }

    @PutMapping("/preferences")
    public UserResponse updatePreferences(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PreferencesRequest req) {
        return userService.updatePreferences(CurrentUser.id(jwt), req);
    }

    @PutMapping("/password")
    public MessageResponse changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest req) {
        userService.changePassword(CurrentUser.id(jwt), req);
        return new MessageResponse("Password updated");
    }

    @PostMapping("/verification-email")
    public MessageResponse resendVerification(@AuthenticationPrincipal Jwt jwt) {
        authService.sendVerification(userService.require(CurrentUser.id(jwt)));
        return new MessageResponse("If your email is not verified yet, a new link has been sent.");
    }
}
