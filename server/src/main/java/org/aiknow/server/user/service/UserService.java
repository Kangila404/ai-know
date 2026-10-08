package org.aiknow.server.user.service;


import lombok.RequiredArgsConstructor;
import org.aiknow.server.auth.repository.AuthAccountRepository;
import org.aiknow.server.profile.domain.ProfileImg;
import org.aiknow.server.profile.exception.ProfileErrorCode;
import org.aiknow.server.profile.exception.ProfileException;
import org.aiknow.server.profile.repository.ProfileImgRepository;
import org.aiknow.server.user.domain.User;
import org.aiknow.server.user.dto.req.UpdateNicknameRequest;
import org.aiknow.server.user.dto.res.UpdateNicknameResponse;
import org.aiknow.server.user.dto.res.UpdateProfileResponse;
import org.aiknow.server.user.dto.res.UserMeResponse;
import org.aiknow.server.user.exception.UserErrorCode;
import org.aiknow.server.user.exception.UserException;
import org.aiknow.server.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AuthAccountRepository authAccountRepository;
    private final ProfileImgRepository profileImgRepository;

    private final org.aiknow.server.cardNews.repository.LikesRepository likes;
    private final org.aiknow.server.cardNews.repository.CardReadRepository reads;
    private final org.aiknow.server.notification.repository.DeviceTokenRepository devices;
    private final org.aiknow.server.notification.repository.NotificationSettingRepository settings;
    private final org.aiknow.server.notification.batch.NewsDeliveryRepository deliveries;

    @Transactional
    public UserMeResponse completeOnboarding(String userId) {
        User user = findUserByUserIdOrThrow(userId);
        user.completeOnboarding();
        return UserMeResponse.from(user);
    }

    @Transactional(readOnly = true)
    public UserMeResponse getUser(String userId){
        User user = findUserByUserIdOrThrow(userId);
        return UserMeResponse.from(user);
    }

    @Transactional
    public UpdateNicknameResponse updateNickname(String userId, UpdateNicknameRequest request){
        User user = findUserByUserIdOrThrow(userId);
        user.updateNickname(request.nickname());
        return UpdateNicknameResponse.from(user.getNickname());
    }

    @Transactional
    public void withdraw(String userId){
        User user = findUserByUserIdOrThrow(userId);
        likes.deleteAllByUser(user);
        reads.deleteAllByUserId(user.getId());
        deliveries.deleteAllByUserId(user.getId());
        devices.deleteAllByUserId(user.getId());
        settings.deleteAllByUserId(user.getId());
        authAccountRepository.deleteAllByUser(user);
        authAccountRepository.flush();
        userRepository.delete(user);
    }

    @Transactional
    public UpdateProfileResponse updateProfile(String userId, Long profileId){
        User user = findUserByUserIdOrThrow(userId);
        ProfileImg profileImg = profileImgRepository.findById(profileId)
            .orElseThrow(()->new ProfileException(ProfileErrorCode.PROFILE_NOT_FOUND));
        user.updateProfileImg(profileImg);
        return UpdateProfileResponse.from(user.getProfileImg());
    }


    User findUserByUserIdOrThrow(String userId){
        return userRepository.findByUserId(userId)
            .orElseThrow(()-> new UserException(UserErrorCode.USER_NOT_FOUND));
    }


}
