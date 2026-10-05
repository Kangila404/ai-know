package org.aiknow.server.user.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.aiknow.server.user.dto.req.UpdateNicknameRequest;
import org.aiknow.server.user.dto.res.UpdateNicknameResponse;
import org.aiknow.server.user.dto.res.UserMeResponse;
import org.aiknow.server.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

    @DeleteMapping("/users/me")
    public ResponseEntity<Void> withdraw(
        @AuthenticationPrincipal String userId
    ) {
        userService.withdraw(userId);
        return ResponseEntity.noContent().build();
    }



}
