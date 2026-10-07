package org.aiknow.server.notification.batch;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(NewsDeliveryProperties.class)
public class PushConfig {
    @Bean(destroyMethod = "delete")
    @ConditionalOnProperty(name = "app.news-delivery.fcm-enabled", havingValue = "true")
    FirebaseApp newsFirebaseApp(NewsDeliveryProperties properties) throws IOException {
        GoogleCredentials credentials;
        if (properties.credentialsPath() == null || properties.credentialsPath().isBlank()) {
            credentials = GoogleCredentials.getApplicationDefault();
        } else {
            try (var input = Files.newInputStream(Path.of(properties.credentialsPath()))) {
                credentials = GoogleCredentials.fromStream(input);
            }
        }
        return FirebaseApp.initializeApp(FirebaseOptions.builder().setCredentials(credentials)
            .setConnectTimeout(10000).setReadTimeout(20000).build(), "aiknow-news");
    }

    @Bean
    @ConditionalOnProperty(name = "app.news-delivery.fcm-enabled", havingValue = "true")
    PushSender firebaseSender(FirebaseApp newsFirebaseApp) {
        return new FirebasePushSender(FirebaseMessaging.getInstance(newsFirebaseApp));
    }

    @Bean
    @ConditionalOnProperty(name = "app.news-delivery.fcm-enabled", havingValue = "false", matchIfMissing = true)
    PushSender disabledSender() {
        return message -> new PushSender.Result(PushSender.Status.PERMANENT_FAILURE, "FCM_DISABLED");
    }
}
