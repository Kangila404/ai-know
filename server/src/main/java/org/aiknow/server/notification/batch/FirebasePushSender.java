package org.aiknow.server.notification.batch;

import com.google.firebase.messaging.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class FirebasePushSender implements PushSender {
    private final FirebaseMessaging messaging;
    public FirebasePushSender(FirebaseMessaging messaging) { this.messaging = messaging; }

    @Override
    public Result send(PushSender.Message request) {
        var message = com.google.firebase.messaging.Message.builder()
            .setToken(request.token())
            .setNotification(Notification.builder().setTitle(request.notificationTitle()).setBody(request.title()).build())
            .putAllData(request.data())
            .setAndroidConfig(AndroidConfig.builder().setTtl(3600000)
                .setNotification(AndroidNotification.builder().setChannelId(request.channelId())
                    .setTag(request.collapseKey()).build()).build())
            .setApnsConfig(ApnsConfig.builder().putHeader("apns-push-type", "alert")
                .putHeader("apns-priority", "10")
                .putHeader("apns-collapse-id", request.collapseKey())
                .setAps(Aps.builder().setSound("default").build()).build())
            .build();
        var future = messaging.sendAsync(message);
        try {
            future.get(30, TimeUnit.SECONDS);
            return Result.sent();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return new Result(Status.RETRY, "INTERRUPTED");
        } catch (java.util.concurrent.TimeoutException exception) {
            future.cancel(true);
            return new Result(Status.RETRY, "TIMEOUT");
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof FirebaseMessagingException failure) {
                MessagingErrorCode code = failure.getMessagingErrorCode();
                if (code == MessagingErrorCode.UNREGISTERED) return new Result(Status.INVALID_TOKEN, code.name());
                if (code == MessagingErrorCode.INVALID_ARGUMENT || code == MessagingErrorCode.SENDER_ID_MISMATCH)
                    return new Result(Status.PERMANENT_FAILURE, code.name());
                return new Result(Status.RETRY, code == null ? "FCM_ERROR" : code.name());
            }
            return new Result(Status.RETRY, "FCM_ERROR");
        }
    }
}
