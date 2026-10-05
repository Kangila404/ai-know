package org.aiknow.server.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
public class SessionAuthenticationService {

    private final SecurityContextRepository contextRepository =
        new HttpSessionSecurityContextRepository();

    public void login(
        String userId,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        Authentication authentication = createAuthentication(userId);

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
        return UsernamePasswordAuthenticationToken.authenticated(
            userId,
            null,
            List.of()
        );
    }
}