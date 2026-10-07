package org.aiknow.server.editorial;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.aiknow.server.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Service @RequiredArgsConstructor
public class EditorialAuditService {
    private final EditorialAuditRepository audits;
    private final UserRepository users;
    private final ObjectMapper mapper;
    private final Clock clock;
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String type, Long id, String action, String userId, String reason, Object before, Object after) {
        var actor = users.findByUserId(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        audits.save(EditorialAudit.of(type, id, action, actor.getId(), clock.instant(), reason, json(before), json(after)));
    }
    public String json(Object value) {
        try { return value == null ? null : mapper.writeValueAsString(value); }
        catch (JsonProcessingException failure) { throw new IllegalStateException("Cannot preserve editorial revision", failure); }
    }
}
