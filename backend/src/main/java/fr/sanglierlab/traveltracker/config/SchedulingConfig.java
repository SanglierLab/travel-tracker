package fr.sanglierlab.traveltracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SchedulingConfig {

    /** Un seul fil d'exécution suffit : un seul vol est suivi à la fois (voir AdsbBatch). */
    @Bean
    ThreadPoolTaskScheduler adsbScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("adsb-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
