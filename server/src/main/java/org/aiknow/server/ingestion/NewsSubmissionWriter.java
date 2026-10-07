package org.aiknow.server.ingestion;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service @RequiredArgsConstructor
public class NewsSubmissionWriter {
    private final NewsSubmissionRepository submissions;

    // Isolate a concurrent unique-key violation so the caller can read the winning row.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NewsSubmission insert(String sourceHash, String payloadHash, String sourceUrl, String payload) {
        return submissions.saveAndFlush(NewsSubmission.create(sourceHash, payloadHash, sourceUrl, payload));
    }
}
