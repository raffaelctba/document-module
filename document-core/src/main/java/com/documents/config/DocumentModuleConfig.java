package com.documents.config;

import com.documents.api.DocumentAccessPort;
import com.documents.api.DocumentsApi;
import com.documents.application.DocumentsApiAdapter;
import com.myproperty.platform.common.time.ClockPort;
import com.documents.application.port.DocumentRepositoryPort;
import com.documents.application.port.ObjectStoragePort;
import com.documents.api.OwnerLookupPort;
import com.documents.application.service.DocumentAppService;
import com.documents.domain.service.DocumentDomainService;
import com.documents.infrastructure.access.DefaultDocumentAccess;
import com.documents.infrastructure.owner.NoOpOwnerLookup;
import com.documents.infrastructure.storage.ObjectStorageProcesses;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DocumentsProperties.class)
@ComponentScan(basePackages = {
        "com.documents.infrastructure.persistence.repository",
        "com.documents.infrastructure.storage",
        "com.documents.infrastructure.clock"
})
public class DocumentModuleConfig {

    /** Fallback when the host (or service) supplies no owner lookup adapter. */
    @Bean
    @ConditionalOnMissingBean(OwnerLookupPort.class)
    public OwnerLookupPort noOpOwnerLookup() {
        return new NoOpOwnerLookup();
    }

    @Bean
    @ConditionalOnMissingBean(DocumentAccessPort.class)
    public DocumentAccessPort documentAccessPort() {
        return new DefaultDocumentAccess();
    }

    @Bean
    public ObjectStoragePort objectStoragePort(DocumentsProperties properties) {
        return ObjectStorageProcesses.open(properties);
    }

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
                                                 DocumentsProperties properties,
                                                 DocumentAccessPort access,
                                                 org.springframework.context.ApplicationEventPublisher events) {
        return new DocumentAppService(documents, objectStorage, ownerLookup, clock, domain, properties, access, events);
    }

    @Bean
    public DocumentsApi documentsApi(DocumentAppService documentAppService) {
        return new DocumentsApiAdapter(documentAppService);
    }
}
