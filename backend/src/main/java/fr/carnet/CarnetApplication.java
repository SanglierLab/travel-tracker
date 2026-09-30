package fr.carnet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CarnetApplication {
    public static void main(String[] args) {
        SpringApplication.run(CarnetApplication.class, args);
    }
}
