package com.documents.infrastructure.access;

import com.documents.api.DocumentAccessPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(DocumentAccessPort.class)
public class DefaultDocumentAccess implements DocumentAccessPort {
}
