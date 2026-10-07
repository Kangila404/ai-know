package org.aiknow.server.notification.batch;

import org.aiknow.server.notification.domain.DeviceType;

public interface PushSender {
    Result send(Message message);

    record Message(Long deliveryId, int attempt, String token, DeviceType platform,
                   Long cardNewsId, String title) {}
    enum Status { SENT, INVALID_TOKEN, RETRY, PERMANENT_FAILURE }
    record Result(Status status, String errorCode) {
        public static Result sent() { return new Result(Status.SENT, null); }
    }
}
