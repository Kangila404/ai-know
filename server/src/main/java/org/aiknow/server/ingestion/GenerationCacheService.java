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
    public record Claim(String decision, Long id, String responseJson, String reason, String contextJson) {
        public Claim(String decision, Long id, String responseJson, String reason) { this(decision,id,responseJson,reason,null); }
    }
    public record Existing(String sourceUrl, Long submissionId, String status) {}
    public record SelectionContext(String decision, Long id, String requestJson, String contextJson, String reason) {}

    @Transactional(readOnly = true)
    public List<Existing> existing(List<String> urls) {
        return urls.stream().distinct().map(url -> submissions.findBySourceHash(hash(url))
            .map(s -> new Existing(url, s.getId(), s.getStatus().name())).orElse(null)).filter(Objects::nonNull).toList();
    }

    // One paid reservation per article/task, durable across manual runs and process restarts.
    // An uncertain/unfinished attempt is never automatically released for another paid call.
    public Claim claim(String url, String task, String requestJson) {
        return claim(url, task, requestJson, null);
    }
    public Claim claim(String url, String task, String requestJson, String contextJson) {
        requireJson(requestJson);
        if (contextJson != null) {
            if (!task.equals("image_selection")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Context is only supported for image selection");
            requireContext(contextJson);
        }
        if (submissions.findBySourceHash(hash(url)).isPresent())
            return new Claim("BLOCKED", null, null, "이미 검수 DB에 저장된 기사입니다.");
        String key = hash(url + "\n" + task), requestHash = hash(requestJson);
        var existing = records.findByAttemptKey(key);
        if (existing.isPresent()) return reuse(existing.get(), requestHash);
        try {
            var created = writer.insert(key, url, task, requestHash, requestJson, contextJson);
            return new Claim("CALL", created.getId(), null, null);
        } catch (DataIntegrityViolationException ex) {
            return records.findByAttemptKey(key).map(r -> reuse(r, requestHash)).orElseThrow(() -> ex);
        }
    }

    @Transactional(readOnly = true)
    public SelectionContext selectionContext(String url, String draftJson) {
        requireJson(draftJson);
        var record = records.findByAttemptKey(hash(url + "\nimage_selection")).orElse(null);
        if (record == null) return new SelectionContext("NONE", null, null, null, null);
        if (record.getContextJson() == null)
            return new SelectionContext("BLOCKED", record.getId(), null, null, "기존 이미지 선택 후보가 없습니다. 원래 n8n 실행에서 후보 목록을 복구하세요.");
        if (!json(record.getContextJson()).path("draft").equals(json(draftJson)))
            return new SelectionContext("BLOCKED", record.getId(), null, null, "저장된 이미지 선택과 현재 초안이 다릅니다. 원래 초안을 복구하세요.");
        if (record.getResponseJson() == null && record.getRecoveredResponseJson() == null)
            return new SelectionContext("BLOCKED", record.getId(), null, null, "기존 이미지 선택 결과가 불확실합니다. 원래 실행 결과를 복구하세요. 유료 재호출하지 않습니다.");
        return new SelectionContext("RESTORE", record.getId(), record.getRequestJson(), record.getContextJson(), null);
    }

    // Attach only the original execution's context. Never replace requests/responses or unlock paid calls.
    @Transactional
    public void recoverContext(Long id, String requestJson, String contextJson) {
        requireJson(requestJson); requireContext(contextJson);
        var record = records.findForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!record.getTask().equals("image_selection") || !record.getRequestHash().equals(hash(requestJson)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "원래 이미지 선택 요청과 일치하지 않습니다.");
        if (record.getContextJson() != null && !json(record.getContextJson()).equals(json(contextJson)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "기존 후보 목록을 덮어쓸 수 없습니다.");
        record.attachContext(contextJson);
    }

    private com.fasterxml.jackson.databind.JsonNode json(String value) {
        try { return mapper.readTree(value); }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON"); }
    }
    private void requireContext(String value) {
        if (value.length() > 1000000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Context too large");
        var node = json(value);
        if (!node.isObject() || node.path("version").asInt() != 1 || !node.path("draft").isObject()
            || !node.path("assets").isArray() || node.path("assets").size() > 5 || node.size() != 3)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected version, draft and assets context");
    }

    private Claim reuse(GenerationRecord record, String hash) {
        if (!record.getRequestHash().equals(hash))
            return new Claim("BLOCKED", record.getId(), null, "같은 기사의 생성 요청이 이미 기록되어 있습니다. 저장된 실행을 검토하세요.");
        String response = record.getRecoveredResponseJson() == null ? record.getResponseJson() : record.getRecoveredResponseJson();
        if (response == null)
            return new Claim("BLOCKED", record.getId(), null, "기존 호출이 진행 중이거나 결과가 불확실합니다. 중복 과금을 방지하기 위해 재호출하지 않습니다.");
        return new Claim("REPLAY", record.getId(), response, null, record.getContextJson());
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
