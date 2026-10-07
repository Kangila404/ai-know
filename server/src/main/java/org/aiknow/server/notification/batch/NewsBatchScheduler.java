package org.aiknow.server.notification.batch;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.news-delivery.enabled", havingValue = "true")
public class NewsBatchScheduler {
    private final JobOperator jobOperator;
    private final Job dailyNewsJob;
    private final Clock clock;

    @Scheduled(cron = "${app.news-delivery.cron}", zone = "${app.news-delivery.zone}")
    public void run() {
        try {
            jobOperator.start(dailyNewsJob, new JobParametersBuilder()
                .addString("scheduledMinute", clock.instant().truncatedTo(ChronoUnit.MINUTES).toString())
                .toJobParameters());
        } catch (JobExecutionAlreadyRunningException | JobInstanceAlreadyCompleteException exception) {
            log.debug("Daily news job was already claimed for this minute");
        } catch (Exception exception) {
            log.error("Daily news batch failed", exception);
        }
    }
}
