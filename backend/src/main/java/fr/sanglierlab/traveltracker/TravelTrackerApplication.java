package fr.sanglierlab.traveltracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TravelTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TravelTrackerApplication.class, args);
    }
}
