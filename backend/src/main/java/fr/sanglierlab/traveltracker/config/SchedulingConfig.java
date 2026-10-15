package fr.sanglierlab.traveltracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SchedulingConfig {

    /** Deux fils : un vol (AdsbBatch) et une croisière (AisBatch) peuvent être suivis en même temps, sans s'attendre l'un l'autre. */
    @Bean
    ThreadPoolTaskScheduler trackingScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("tracking-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
