package org.aiknow.server.auth.repository;

import java.util.Optional;
import org.aiknow.server.auth.domain.AuthAccount;
import org.aiknow.server.auth.domain.SocialProvider;
import org.aiknow.server.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthAccountRepository extends JpaRepository<AuthAccount, Long> {
    Optional<AuthAccount> findByProviderAndProviderId(SocialProvider provider, String providerId);

    void deleteAllByUser(User user);
}
