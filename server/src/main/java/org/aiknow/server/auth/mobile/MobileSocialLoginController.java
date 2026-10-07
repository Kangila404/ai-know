package org.aiknow.server.auth.mobile;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.aiknow.server.auth.domain.SocialProvider;
import org.aiknow.server.auth.dto.req.LoginRequest;
import org.aiknow.server.auth.dto.res.LoginResponse;
import org.aiknow.server.auth.service.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Native SDK ID tokens are verified server-side, then exchanged for the existing session cookie. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/social")
public class MobileSocialLoginController {
    private static final String CHALLENGE = MobileSocialLoginController.class.getName() + ".challenge";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SocialTokenVerifier verifier;
    private final AuthAccountService accounts;
    private final SessionAuthenticationService sessions;
    private final Clock clock;

    public record TokenRequest(@NotBlank @Size(max = 16000) String idToken, @Size(max = 20) String nickname) {}
    public record Challenge(SocialProvider provider, String nonce, Instant expiresAt) implements java.io.Serializable {}
    public record SessionResult(String userId, String csrfHeaderName, String csrfToken) {}

    @GetMapping("/providers")
    public Set<SocialProvider> providers() { return verifier.enabledProviders(); }

    @PostMapping("/{provider}/challenge")
    public ResponseEntity<Challenge> challenge(@PathVariable SocialProvider provider, HttpServletRequest request) {
        verifier.requireEnabled(provider);
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        var challenge = new Challenge(provider, HexFormat.of().formatHex(bytes), clock.instant().plusSeconds(300));
        request.getSession(true).setAttribute(CHALLENGE, challenge);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(challenge);
    }

    @PostMapping("/{provider}")
    public ResponseEntity<SessionResult> login(@PathVariable SocialProvider provider, @Valid @RequestBody TokenRequest body,
        HttpServletRequest request, HttpServletResponse response) {
        verifier.requireEnabled(provider);
        var session = request.getSession(false);
        Challenge challenge;
        if (session == null) throw unauthorized();
        synchronized (session) {
            Object saved = session.getAttribute(CHALLENGE);
            session.removeAttribute(CHALLENGE);
            if (!(saved instanceof Challenge value)) throw unauthorized();
            challenge = value;
        }
        if (challenge.provider() != provider || !clock.instant().isBefore(challenge.expiresAt())) throw unauthorized();
        var jwt = verifier.verify(provider, body.idToken());
        String nonce = jwt.getClaimAsString("nonce");
        if (nonce == null || !MessageDigest.isEqual(nonce.getBytes(StandardCharsets.UTF_8), challenge.nonce().getBytes(StandardCharsets.UTF_8))
            || jwt.getIssuedAt().isAfter(clock.instant().plusSeconds(60))) throw unauthorized();
        String nickname = body.nickname() == null || body.nickname().isBlank() ? "새 사용자" : body.nickname().strip();
        var accountRequest = new LoginRequest(provider, jwt.getSubject(), nickname);
        LoginResponse account;
        try { account = accounts.findOrCreateUserId(accountRequest); }
        catch (DataIntegrityViolationException concurrentSignup) { account = accounts.findOrCreateUserId(accountRequest); }
        sessions.login(account.userId(), request, response);
        // Rotate CSRF as well as the session ID when anonymous state becomes authenticated.
        var repository = new HttpSessionCsrfTokenRepository();
        CsrfToken csrf = repository.generateToken(request);
        repository.saveToken(csrf, request, response);
        new org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler().handle(request, response, () -> csrf);
        CsrfToken exposed = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new SessionResult(account.userId(), exposed.getHeaderName(), exposed.getToken()));
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인 요청이 만료되었거나 유효하지 않습니다. 새 인증 요청을 시작하세요.");
    }
}
