package org.aiknow.server.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.aiknow.server.user.domain.User;
import org.aiknow.server.user.exception.UserErrorCode;
import org.aiknow.server.user.exception.UserException;
import org.aiknow.server.user.repository.UserRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SessionAuthenticationService {

    private final UserRepository userRepository;

    private final SecurityContextRepository contextRepository =
        new HttpSessionSecurityContextRepository();

    public void login(
        String userId,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        Authentication authentication = createAuthentication(userId);
        if (request.getSession(false) != null) request.changeSessionId();

        SecurityContext context =
            SecurityContextHolder.createEmptyContext();

        context.setAuthentication(authentication);

        SecurityContextHolder.setContext(context);

        contextRepository.saveContext(context, request, response);
    }

    public void logout(
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        Authentication authentication =
            SecurityContextHolder.getContext().getAuthentication();

        SecurityContextLogoutHandler contextLogoutHandler =
            new SecurityContextLogoutHandler();

        contextLogoutHandler.setSecurityContextRepository(
            contextRepository
        );


        contextLogoutHandler.logout(
            request,
            response,
            authentication
        );


        new CookieClearingLogoutHandler("JSESSIONID")
            .logout(request, response, authentication);
    }

    // ==== 메서드 ==== //

    private Authentication createAuthentication(String userId) {
        User user = userRepository.findByUserId(userId)
            .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));

        return UsernamePasswordAuthenticationToken.authenticated(
            userId,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_" + user.getUserRole().name()))
        );
    }
}
