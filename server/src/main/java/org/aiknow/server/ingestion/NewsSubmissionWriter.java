package org.aiknow.server.ingestion;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service @RequiredArgsConstructor
public class NewsSubmissionWriter {
    private final NewsSubmissionRepository submissions;
    private final org.aiknow.server.notification.admin.AdminAlertService alerts;

    // Isolate a concurrent unique-key violation so the caller can read the winning row.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NewsSubmission insert(String sourceHash, String payloadHash, String sourceUrl, String payload) {
        var submission = submissions.saveAndFlush(NewsSubmission.create(sourceHash, payloadHash, sourceUrl, payload));
        alerts.draftReceived(submission.getId());
        return submission;
    }
}
