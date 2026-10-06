package org.aiknow.server.profile.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.aiknow.server.profile.dto.res.ProfileResponse;
import org.aiknow.server.profile.service.ProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "프로필 이미지 API")
@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @Operation(description = "프로필 리스트 조회")
    @GetMapping
    public ResponseEntity<List<ProfileResponse>> getProfiles(){
        List<ProfileResponse> response = profileService.getProfiles();
        return ResponseEntity.ok(response);
    }
}
