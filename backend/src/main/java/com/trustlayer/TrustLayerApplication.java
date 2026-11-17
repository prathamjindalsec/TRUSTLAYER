package com.trustlayer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Application entry point. Spring Boot starts component scanning from this package,
 * so all application code must live in com.trustlayer or one of its subpackages.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class TrustLayerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrustLayerApplication.class, args);
    }
}
