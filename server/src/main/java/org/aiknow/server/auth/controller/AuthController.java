package org.aiknow.server.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.aiknow.server.auth.service.SessionAuthenticationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SessionAuthenticationService sessionAuthenticationService;

    @GetMapping("/csrf")
    public ResponseEntity<CsrfToken> getCsrfToken(CsrfToken csrfToken) {
        return ResponseEntity.ok(csrfToken);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        sessionAuthenticationService.logout(request, response);
        return ResponseEntity.noContent().build();
    }
}