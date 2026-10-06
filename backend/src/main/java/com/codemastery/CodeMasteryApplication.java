package com.codemastery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of the CodeMastery modular monolith.
 *
 * <p>All modules (auth, course, assessment, adaptive, interaction, admin) live under
 * {@code com.codemastery.modules} and are deployed as a single Spring Boot application.
 * Component, entity and repository scanning starts from this package.
 *
 * <p>{@code @ConfigurationPropertiesScan} registers the typed configuration classes bound to the
 * {@code codemastery.*} properties defined in {@code application.yml}.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CodeMasteryApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeMasteryApplication.class, args);
    }
}