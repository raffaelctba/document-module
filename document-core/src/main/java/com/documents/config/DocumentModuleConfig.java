package com.documents.config;

import com.documents.api.DocumentsApi;
import com.documents.application.DocumentsApiAdapter;
import com.myproperty.platform.common.time.ClockPort;
import com.documents.application.port.DocumentRepositoryPort;
import com.documents.application.port.ObjectStoragePort;
import com.documents.api.OwnerLookupPort;
import com.documents.application.service.DocumentAppService;
import com.documents.domain.service.DocumentDomainService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DocumentsProperties.class)
@ComponentScan(basePackages = {
        "com.documents.infrastructure.persistence.repository",
        "com.documents.infrastructure.storage",
        "com.documents.infrastructure.owner",
        "com.documents.infrastructure.clock"
})
public class DocumentModuleConfig {

    @Bean
    public DocumentDomainService documentDomainService(DocumentsProperties properties) {
        return new DocumentDomainService(properties);
    }

    @Bean
    public DocumentAppService documentAppService(DocumentRepositoryPort documents,
                                                 ObjectStoragePort objectStorage,
                                                 OwnerLookupPort ownerLookup,
                                                 ClockPort clock,
                                                 DocumentDomainService domain,
                                                 DocumentsProperties properties) {
        return new DocumentAppService(documents, objectStorage, ownerLookup, clock, domain, properties);
    }

    @Bean
    public DocumentsApi documentsApi(DocumentAppService documentAppService) {
        return new DocumentsApiAdapter(documentAppService);
    }
}
