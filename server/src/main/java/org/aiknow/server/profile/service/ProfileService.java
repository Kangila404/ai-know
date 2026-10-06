package org.aiknow.server.profile.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.aiknow.server.admin.exception.AdminErrorCode;
import org.aiknow.server.admin.exception.AdminException;
import org.aiknow.server.profile.domain.ProfileImg;
import org.aiknow.server.profile.dto.req.RevertProfileRequest;
import org.aiknow.server.profile.dto.res.ProfileResponse;
import org.aiknow.server.profile.dto.res.RevertProfileResponse;
import org.aiknow.server.profile.exception.ProfileErrorCode;
import org.aiknow.server.profile.exception.ProfileException;
import org.aiknow.server.profile.repository.ProfileImgRepository;
import org.aiknow.server.user.domain.User;
import org.aiknow.server.user.domain.UserRole;
import org.aiknow.server.user.exception.UserErrorCode;
import org.aiknow.server.user.exception.UserException;
import org.aiknow.server.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileImgRepository profileImgRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<ProfileResponse> getProfiles(){

        List<ProfileResponse> profiles = profileImgRepository.findAll()
            .stream()
            .map(ProfileResponse::from)
            .toList();
        return profiles;
    }

    // ========= admin 메서드 ========== //
    @Transactional
    public RevertProfileResponse registerProfile(
        String adminId,
        RevertProfileRequest request
        ){
            findAdminAndValidateOrThrow(adminId);
        if(request.name() == null || request.imgUrl() == null){
            throw new ProfileException(ProfileErrorCode.INVALID_PROFILE_IMAGE_REQUEST);
        }
        ProfileImg profileImg = ProfileImg.create(request.name(), request.imgUrl());
        profileImgRepository.save(profileImg);
        return RevertProfileResponse.from(profileImg);
    }

    @Transactional
    public RevertProfileResponse updateProfile(
        String adminId,
        RevertProfileRequest request,
        Long profileId
    ){
        findAdminAndValidateOrThrow(adminId);
        if(request.name() == null || request.imgUrl() == null){
            throw new ProfileException(ProfileErrorCode.INVALID_PROFILE_IMAGE_REQUEST);
        }

        ProfileImg profileImg = findProfileImgByIdOrThrow(profileId);
        profileImg.update(request.name(), request.imgUrl());

        return RevertProfileResponse.from(profileImg);
    }

    @Transactional
    public void deleteProfile(
        String adminId,
        Long profileId
    ){
        findAdminAndValidateOrThrow(adminId);
        ProfileImg profileImg = findProfileImgByIdOrThrow(profileId);
        profileImgRepository.delete(profileImg);
    }

    // ============ 조회 및 검증 메서드 ============ //
    // 1. admin 조회
    private void findAdminAndValidateOrThrow(String adminId){
        User user =  userRepository.findByUserId(adminId)
            .orElseThrow(()-> new UserException(UserErrorCode.USER_NOT_FOUND));

        if(user.getUserRole() != UserRole.ADMIN){
            throw new AdminException(AdminErrorCode.ADMIN_FORBIDDEN);
        }

    }

    // 2. ProfileImg 조회
    private ProfileImg findProfileImgByIdOrThrow(Long profileId){
        return profileImgRepository.findById(profileId)
            .orElseThrow(()-> new ProfileException(ProfileErrorCode.PROFILE_NOT_FOUND));
    }







}
