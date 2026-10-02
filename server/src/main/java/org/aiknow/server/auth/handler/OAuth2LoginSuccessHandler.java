package org.aiknow.server.auth.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aiknow.server.auth.domain.SocialProvider;
import org.aiknow.server.auth.dto.req.LoginRequest;
import org.aiknow.server.auth.dto.res.LoginResponse;
import org.aiknow.server.auth.service.AuthAccountService;
import org.aiknow.server.auth.service.SessionAuthenticationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler
    implements AuthenticationSuccessHandler {

    private final AuthAccountService authAccountService;
    private final SessionAuthenticationService sessionAuthenticationService;

    @Value("${app.login-success-url}")
    private String loginSuccessUrl;

    @Override
    public void onAuthenticationSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    ) throws IOException {

        try {
            LoginRequest loginRequest = createLoginRequest(authentication);

            LoginResponse loginResponse = findOrCreateAccount(loginRequest);

            sessionAuthenticationService.login(
                loginResponse.userId(),
                request,
                response
            );
        } catch (RuntimeException exception) {
            sessionAuthenticationService.logout(request, response);

            log.error("소셜 로그인 후 계정 연결에 실패했습니다.", exception);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        response.sendRedirect(loginSuccessUrl);
    }

    // ==== 메서드 ==== //

    private LoginRequest createLoginRequest(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken token)
            || !"google".equals(token.getAuthorizedClientRegistrationId())
            || !(token.getPrincipal() instanceof OidcUser oidcUser)) {
            throw new IllegalStateException("지원하지 않는 소셜 인증입니다.");
        }

        return new LoginRequest(
            SocialProvider.GOOGLE,
            oidcUser.getIdToken().getSubject(),
            resolveNickname(oidcUser.getFullName())
        );
    }

    private String resolveNickname(String name) {
        if (name == null || name.isBlank()) {
            return "새 사용자";
        }

        String nickname = name.strip();

        return nickname.length() <= 20 ? nickname : "새 사용자";
    }

    private LoginResponse findOrCreateAccount(LoginRequest request) {
        try {
            return authAccountService.findOrCreateUserId(request);
        } catch (DataIntegrityViolationException exception) {

            return authAccountService.findOrCreateUserId(request);
        }
    }
}