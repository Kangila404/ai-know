package org.aiknow.server.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.aiknow.server.editorial.EditorialAuditService;
import org.aiknow.server.storage.ImageStorage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service @RequiredArgsConstructor
public class GenerationRecoveryService {
    private final GenerationRecordRepository records;
    private final GenerationCacheService cache;
    private final EditorialAuditService audit;
    private final ImageStorage images;
    private final ObjectMapper mapper;

    @Transactional
    public GenerationRecord recover(Long id, long version, String reason, String response, String actor) {
        var record = editable(id, version);
        if (record.getTask().startsWith("image_generation_")) throw bad("이미지는 이미지 복구 업로드 API를 사용하세요.");
        try { if (!mapper.readTree(response).isObject()) throw bad("JSON 객체가 필요합니다."); }
        catch (java.io.IOException failure) { throw bad("유효한 JSON 객체가 필요합니다."); }
        String before = record.getRecoveredResponseJson(); record.recover(response); records.saveAndFlush(record);
        audit.record("GENERATION", id, "RECOVER_RESPONSE", actor, reason, before, response); return record;
    }

    @Transactional
    public GenerationRecord recoverImage(Long id, long version, String reason, MultipartFile file, String actor) {
        var record = editable(id, version); cache.requireImageReservation(id);
        var receipt = images.store(file);
        String before = record.getRecoveredResponseJson(); record.recover(audit.json(receipt)); records.saveAndFlush(record);
        audit.record("GENERATION", id, "RECOVER_IMAGE", actor, reason, before, receipt); return record;
    }

    private GenerationRecord editable(Long id, long version) {
        var record = records.findForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (record.getVersion() != version) throw new ResponseStatusException(HttpStatus.CONFLICT, "생성 기록을 다시 조회하세요.");
        if (!cache.existing(java.util.List.of(record.getSourceUrl())).isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 저장된 기사는 검수 편집 API를 사용하세요.");
        return record;
    }
    private ResponseStatusException bad(String reason) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason); }
}
