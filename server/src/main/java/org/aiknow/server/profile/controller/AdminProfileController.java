package org.aiknow.server.profile.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Path;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.aiknow.server.profile.dto.req.RevertProfileRequest;
import org.aiknow.server.profile.dto.res.RevertProfileResponse;
import org.aiknow.server.profile.service.ProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 프로필 관리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/profile")
public class AdminProfileController {

    private final ProfileService profileService;

    @PostMapping
    public ResponseEntity<RevertProfileResponse> registerProfile(
        @AuthenticationPrincipal String adminId,
        @RequestBody @Valid RevertProfileRequest request
    ){
        RevertProfileResponse response = profileService.registerProfile(adminId, request);
        return ResponseEntity.ok(response);
    };

    @PatchMapping("/{profile_id}")
    public ResponseEntity<RevertProfileResponse> updateProfile(
        @AuthenticationPrincipal String adminId,
        @RequestBody @Valid RevertProfileRequest request,
        @PathVariable("profile_id") Long profileId
    ){
        RevertProfileResponse response = profileService.updateProfile(adminId, request, profileId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{profile_id}")
    public ResponseEntity<Void> deleteProfile(
        @AuthenticationPrincipal String adminId,
        @PathVariable("profile_id") Long profileId
    ){
        profileService.deleteProfile(adminId, profileId);
        return ResponseEntity.ok().build();
    }
}
