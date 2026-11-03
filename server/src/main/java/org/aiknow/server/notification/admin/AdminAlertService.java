package org.aiknow.server.notification.admin;

import java.time.*;
import lombok.RequiredArgsConstructor;
import org.aiknow.server.cardNews.domain.*;
import org.aiknow.server.notification.batch.NewsDeliveryProperties;
import org.aiknow.server.notification.repository.DeviceTokenRepository;
import org.aiknow.server.user.domain.UserRole;
import org.aiknow.server.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class AdminAlertService {
    private final AdminAlertRepository alerts;
    private final AdminAlertDeliveryRepository deliveries;
    private final UserRepository users;
    private final DeviceTokenRepository devices;
    private final NewsDeliveryProperties properties;
    private final Clock clock;

    @Transactional
    public AdminAlert draftReceived(Long id) {
        return record("submission:"+id, "DRAFT_READY", "새 콘텐츠 검수가 필요합니다",
            "n8n이 생성한 초안 #"+id+"이 저장되었습니다. 내용을 확인한 뒤 승인해 주세요.", id, null, null);
    }

    @Transactional
    public AdminAlert approved(CardNews article) {
        boolean news = article.getContentType() == ContentType.NEWS;
        var date = clock.instant().atZone(properties.zone()).toLocalDate().plusDays(1);
        String body = news ? (properties.enabled()
            ? date+" 발송 후보에 등록되었습니다. 자정 기준으로 한 건을 선정하므로 추가 승인에 따라 변경될 수 있습니다."
            : "발송 대기로 등록했지만 자동 발송이 꺼져 있습니다. 발송 일정은 아직 확정되지 않았습니다.")
            : "AI 이론이 아카이브에 공개되었습니다. 승인 뉴스가 없는 날 대체 발송될 수 있습니다.";
        return record("approved:"+article.getId(), news ? "DELIVERY_CANDIDATE" : "THEORY_PUBLISHED",
            news ? "뉴스 승인 · 다음 발송 준비" : "AI 이론 승인 완료", body, null, article.getId(), news && properties.enabled() ? date : null);
    }

    @Transactional
    public AdminAlert editionSelected(LocalDate date, Long articleId) {
        return record("edition:"+date, "DELIVERY_SELECTED", articleId == null ? "발송할 콘텐츠가 없습니다" : "오늘 발송 콘텐츠가 확정되었습니다",
            articleId == null ? date+" 기준 승인된 뉴스·대체 이론이 없습니다. 관리자 검수가 필요합니다."
                : date+" 발송할 카드 #"+articleId+"이 선정되었습니다. 알림을 허용한 회원의 설정 시간에 발송합니다.", null, articleId, date);
    }

    @Transactional
    public AdminAlert automation(String workflow, String execution, String status, String stage) {
        boolean failed = status.equals("FAILED");
        return record("automation:"+workflow+":"+execution, failed ? "AUTOMATION_FAILED" : "AUTOMATION_NO_CONTENT",
            failed ? "콘텐츠 자동화 실행에 실패했습니다" : "콘텐츠 자동화가 새 초안 없이 종료되었습니다",
            "n8n 실행 #"+execution+" · "+(stage == null || stage.isBlank() ? "실행 내역을 확인해 주세요." : "확인할 단계: "+stage), null, null, null);
    }

    private AdminAlert record(String key, String type, String title, String body, Long submission, Long card, LocalDate date) {
        var existing = alerts.findByEventKey(key);
        if (existing.isPresent()) return existing.get();
        var now = clock.instant();
        var alert = alerts.saveAndFlush(AdminAlert.create(key, type, title, body, submission, card, date, now));
        // The durable outbox is committed with the event, never send FCM inside the editorial transaction.
        for (var user : users.findByUserRoleAndSuspendedFalse(UserRole.ADMIN))
            for (var device : devices.findByUserIdAndActiveTrue(user.getId()))
                deliveries.save(AdminAlertDelivery.pending(alert.getId(), user.getId(), device.getId(), now));
        return alert;
    }
}
