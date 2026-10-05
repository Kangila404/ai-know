package org.aiknow.server.auth.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.aiknow.server.auth.domain.AuthAccount;
import org.aiknow.server.auth.domain.SocialProvider;
import org.aiknow.server.auth.dto.req.LoginRequest;
import org.aiknow.server.auth.dto.res.LoginResponse;
import org.aiknow.server.auth.repository.AuthAccountRepository;
import org.aiknow.server.user.domain.User;
import org.aiknow.server.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class AuthAccountService {

    private final AuthAccountRepository authAccountRepository;
    private final UserRepository userRepository;

    @Transactional
    public LoginResponse findOrCreateUserId(
        @NotNull @Valid LoginRequest request
    ) {
        User user = findOrRegisterUser(
            request.provider(),
            request.providerId(),
            request.nickname()
        );

        user.recordLogin();

        return new LoginResponse(user.getUserId());
    }

    // ==== 메서드 ==== //

    private User findOrRegisterUser(
        SocialProvider provider,
        String providerId,
        String nickname
    ) {
        return authAccountRepository
            .findByProviderAndProviderId(provider, providerId)
            .map(AuthAccount::getUser)
            .orElseGet(() -> registerUser(
                provider,
                providerId,
                nickname
            ));
    }

    private User registerUser(
        SocialProvider provider,
        String providerId,
        String nickname
    ) {
        User user = User.createSocialUser(nickname);
        User savedUser = userRepository.save(user);

        AuthAccount authAccount = new AuthAccount(
            provider,
            providerId,
            savedUser
        );

        authAccountRepository.save(authAccount);

        return savedUser;
    }
}