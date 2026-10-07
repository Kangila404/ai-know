package org.aiknow.server.ingestion;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service @RequiredArgsConstructor
public class GenerationRecordWriter {
    private final GenerationRecordRepository records;
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GenerationRecord insert(String key, String url, String task, String hash, String request) {
        return records.saveAndFlush(GenerationRecord.create(key, url, task, hash, request));
    }
}
