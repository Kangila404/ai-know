package org.aiknow.server.auth.service;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.aiknow.server.user.repository.UserRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Re-check current account state before authorization, including existing sessions. */
public class CurrentAccountFilter extends OncePerRequestFilter {
    private final UserRepository users;
    private final SessionAuthenticationService sessions;
    public CurrentAccountFilter(UserRepository users, SessionAuthenticationService sessions) {
        this.users = users; this.sessions = sessions;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof UsernamePasswordAuthenticationToken && auth.isAuthenticated()
            && auth.getPrincipal() instanceof String userId) {
            var user = users.findByUserId(userId).orElse(null);
            if (user == null || user.isSuspended()) {
                sessions.logout(request, response);
                response.setStatus(401);
                return;
            }
            var updated = UsernamePasswordAuthenticationToken.authenticated(userId, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getUserRole().name())));
            updated.setDetails(auth.getDetails());
            // A new context avoids mutating the context object shared by concurrent session requests.
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(updated);
            SecurityContextHolder.setContext(context);
        }
        chain.doFilter(request, response);
    }
}
