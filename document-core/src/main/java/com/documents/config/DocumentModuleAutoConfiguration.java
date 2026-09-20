package com.documents.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Entry point for the document module. The module's own configuration namespace
 * ({@code documents.*}) is defined in classpath:documents.yml and imported via
 * spring.config.import in the host application.yml, so it is bound onto
 * {@link DocumentsProperties} without any custom property-loading code here.
 */
@AutoConfiguration
@Import(DocumentModuleConfig.class)
@EntityScan(basePackages = "com.documents.infrastructure.persistence.entity")
@EnableJpaRepositories(basePackages = "com.documents.infrastructure.persistence.repository")
public class DocumentModuleAutoConfiguration {
}
