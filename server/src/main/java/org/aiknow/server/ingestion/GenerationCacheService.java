package org.aiknow.server.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service @RequiredArgsConstructor
public class GenerationCacheService {
    private final GenerationRecordRepository records;
    private final GenerationRecordWriter writer;
    private final NewsSubmissionRepository submissions;
    private final ObjectMapper mapper;
    public record Claim(String decision, Long id, String responseJson, String reason) {}
    public record Existing(String sourceUrl, Long submissionId, String status) {}

    @Transactional(readOnly = true)
    public List<Existing> existing(List<String> urls) {
        return urls.stream().distinct().map(url -> submissions.findBySourceHash(hash(url))
            .map(s -> new Existing(url, s.getId(), s.getStatus().name())).orElse(null)).filter(Objects::nonNull).toList();
    }

    // One paid reservation per article/task, durable across manual runs and process restarts.
    // An uncertain/unfinished attempt is never automatically released for another paid call.
    public Claim claim(String url, String task, String requestJson) {
        requireJson(requestJson);
        if (submissions.findBySourceHash(hash(url)).isPresent())
            return new Claim("BLOCKED", null, null, "이미 검수 DB에 저장된 기사입니다.");
        String key = hash(url + "\n" + task), requestHash = hash(requestJson);
        var existing = records.findByAttemptKey(key);
        if (existing.isPresent()) return reuse(existing.get(), requestHash);
        try {
            var created = writer.insert(key, url, task, requestHash, requestJson);
            return new Claim("CALL", created.getId(), null, null);
        } catch (DataIntegrityViolationException ex) {
            return records.findByAttemptKey(key).map(r -> reuse(r, requestHash)).orElseThrow(() -> ex);
        }
    }

    private Claim reuse(GenerationRecord record, String hash) {
        if (!record.getRequestHash().equals(hash))
            return new Claim("BLOCKED", record.getId(), null, "같은 기사의 생성 요청이 이미 기록되어 있습니다. 저장된 실행을 검토하세요.");
        String response = record.getRecoveredResponseJson() == null ? record.getResponseJson() : record.getRecoveredResponseJson();
        if (response == null)
            return new Claim("BLOCKED", record.getId(), null, "기존 호출이 진행 중이거나 결과가 불확실합니다. 중복 과금을 방지하기 위해 재호출하지 않습니다.");
        return new Claim("REPLAY", record.getId(), response, null);
    }

    @Transactional
    public void complete(Long id, String responseJson) {
        requireJson(responseJson);
        var record = records.findForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (record.getResponseJson() != null && !record.getResponseJson().equals(responseJson))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 저장된 응답을 덮어쓸 수 없습니다.");
        record.complete(responseJson);
    }
    @Transactional(readOnly = true)
    public void requireImageReservation(Long id) {
        var record = records.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!record.getTask().startsWith("image_generation_"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지 생성 예약이 아닙니다.");
    }
    private void requireJson(String value) {
        try { if (!mapper.readTree(value).isObject()) throw new IllegalArgumentException(); }
        catch (Exception ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JSON 객체가 필요합니다."); }
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
