package fr.sanglierlab.traveltracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SchedulingConfig {

    /** Trois fils : un vol (AdsbBatch), une croisière (AisBatch) et une session LiveTrack (LiveTrackBatch) peuvent être suivis en même temps, sans s'attendre. */
    @Bean
    ThreadPoolTaskScheduler trackingScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3);
        scheduler.setThreadNamePrefix("tracking-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
