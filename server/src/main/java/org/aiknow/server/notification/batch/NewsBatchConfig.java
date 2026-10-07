package org.aiknow.server.notification.batch;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class NewsBatchConfig {
    @Bean
    Job dailyNewsJob(JobRepository repository, NewsDeliveryWorker worker) {
        var plan = new StepBuilder("planDailyNews", repository)
            .tasklet((contribution, context) -> { worker.plan(); return RepeatStatus.FINISHED; }).build();
        var send = new StepBuilder("sendDailyNews", repository)
            .tasklet((contribution, context) -> { worker.dispatch(); return RepeatStatus.FINISHED; }).build();
        return new JobBuilder("dailyNewsJob", repository).start(plan).next(send).build();
    }
}
