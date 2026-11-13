package com.example.decision;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class DecisionPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(DecisionPlatformApplication.class, args);
    }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Configurable Workflow Decision Platform")
                        .version("1.0.0")
                        .description("Generic platform for rule evaluation, workflow orchestration, audit trails, and explainable decisions."));
    }
}

