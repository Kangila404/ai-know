package org.aiknow.server.user.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.aiknow.server.user.dto.req.UpdateNicknameRequest;
import org.aiknow.server.user.dto.res.UpdateNicknameResponse;
import org.aiknow.server.user.dto.res.UpdateProfileResponse;
import org.aiknow.server.user.dto.res.UserMeResponse;
import org.aiknow.server.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;


@Tag(name = "유저 API")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final org.aiknow.server.auth.service.SessionAuthenticationService sessions;

    @PatchMapping("/onboarding")
    public UserMeResponse completeOnboarding(@AuthenticationPrincipal String userId) {
        return userService.completeOnboarding(userId);
    }

    @GetMapping
    public ResponseEntity<UserMeResponse> getUser(
        @AuthenticationPrincipal String userId
    ){
        UserMeResponse response = userService.getUser(userId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/nickname")
    public ResponseEntity<UpdateNicknameResponse> updateNickname(
        @AuthenticationPrincipal String userId,
        @RequestHeader("X-CSRF-TOKEN") String csrfToken,
        @Valid @RequestBody UpdateNicknameRequest request
    ){
        UpdateNicknameResponse response = userService.updateNickname(userId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping({"/me", "/users/me"})
    public ResponseEntity<Void> withdraw(
        @AuthenticationPrincipal String userId,
        jakarta.servlet.http.HttpServletRequest request,
        jakarta.servlet.http.HttpServletResponse response
    ) {
        userService.withdraw(userId);
        sessions.logout(request, response);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/profile/{profile_id}")
    public ResponseEntity<UpdateProfileResponse> updateProfile(
        @AuthenticationPrincipal String userId,
        @PathVariable("profile_id") Long profileId
    ){
        UpdateProfileResponse response = userService.updateProfile(userId, profileId);
        return ResponseEntity.ok(response);
    }


}
