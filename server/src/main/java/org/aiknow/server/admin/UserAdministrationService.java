package org.aiknow.server.admin;

import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.aiknow.server.user.domain.*;
import org.aiknow.server.user.repository.UserRepository;
import org.aiknow.server.editorial.EditorialAuditService;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service @RequiredArgsConstructor
public class UserAdministrationService {
    private final UserRepository users;
    private final EditorialAuditService audits;
    public record Account(Long id, long version, String userId, String nickname, UserRole role, boolean suspended,
        java.time.LocalDateTime createdAt, java.time.LocalDateTime lastLoginAt) {
        static Account from(User u) { return new Account(u.getId(), u.getVersion(), u.getUserId(), u.getNickname(),
            u.getUserRole(), u.isSuspended(), u.getCreatedAt(), u.getLastLogined_at()); }
    }
    public record Update(@NotNull @Min(0) Long version, @NotNull UserRole role, @NotNull Boolean suspended,
        @NotBlank @Size(max=2000) String reason) {}
    @Transactional(readOnly=true)
    public Page<Account> list(String query, UserRole role, Boolean suspended, int page, int size) {
        return users.search(query.strip().toLowerCase(java.util.Locale.ROOT), role, suspended,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))).map(Account::from);
    }
    @Transactional(readOnly=true)
    public Account get(Long id) { return Account.from(users.findById(id).orElseThrow(this::notFound)); }
    @Transactional
    public Account update(Long id, Update request, String actorId) {
        // Serializes admin changes and prevents two administrators from removing each other's last access.
        var admins = users.lockAdministrators();
        var actor = admins.stream().filter(u -> u.getUserId().equals(actorId) && !u.isSuspended()).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        var user = users.findForUpdate(id).orElseThrow(this::notFound);
        if (user.getVersion() != request.version())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "다른 관리자가 수정했습니다. 새로고침 후 다시 시도하세요.");
        if (user.getId().equals(actor.getId()) && (request.role() != UserRole.ADMIN || request.suspended()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "자신의 관리자 권한을 해제하거나 본인 계정을 정지할 수 없습니다.");
        var before = Account.from(user);
        user.updateAdministration(request.role(), request.suspended());
        users.flush();
        var after = Account.from(user);
        audits.record("USER", id, "ACCOUNT_UPDATE", actorId, request.reason().strip(), before, after);
        return after;
    }
    private ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "계정을 찾을 수 없습니다."); }
}
