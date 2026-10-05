package org.aiknow.server.user.service;


import lombok.RequiredArgsConstructor;
import org.aiknow.server.auth.repository.AuthAccountRepository;
import org.aiknow.server.user.domain.User;
import org.aiknow.server.user.dto.req.UpdateNicknameRequest;
import org.aiknow.server.user.dto.res.UpdateNicknameResponse;
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
        authAccountRepository.deleteAllByUser(user);
        authAccountRepository.flush();
        userRepository.delete(user);
    }


    User findUserByUserIdOrThrow(String userId){
        return userRepository.findByUserId(userId)
            .orElseThrow(()-> new UserException(UserErrorCode.USER_NOT_FOUND));
    }

}
