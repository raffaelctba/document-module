package com.documents;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Standalone document process. Public traffic enters only via api-gateway.
 * Domain logic lives in document-core.
 */
@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {
        "com.documents.controller",
        "com.documents.security"
})
public class DocumentsApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocumentsApplication.class, args);
    }
}
