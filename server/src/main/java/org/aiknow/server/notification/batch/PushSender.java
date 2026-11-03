package org.aiknow.server.notification.batch;

import org.aiknow.server.notification.domain.DeviceType;

public interface PushSender {
    Result send(Message message);

    record Message(Long deliveryId, int attempt, String token, DeviceType platform,
                   Long cardNewsId, String title, String notificationTitle,
                   java.util.Map<String, String> data, String collapseKey, String channelId) {
        public Message(Long id, int attempt, String token, DeviceType platform, Long card, String title) {
            this(id, attempt, token, platform, card, title, "오늘의 AI 뉴스",
                java.util.Map.of("cardNewsId", card.toString(), "notificationId", id.toString()), "news-"+id, "daily_news");
        }
    }
    enum Status { SENT, INVALID_TOKEN, RETRY, PERMANENT_FAILURE }
    record Result(Status status, String errorCode) {
        public static Result sent() { return new Result(Status.SENT, null); }
    }
}
