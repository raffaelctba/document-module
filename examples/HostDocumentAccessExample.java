package com.example.host.documents;

import com.documents.api.DocumentAccessPort;
import com.documents.api.dto.DocumentActor;
import com.documents.domain.model.OwnerRef;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** EXAMPLE host SPI — place in host app, not document-module. */
@Primary
@Component
public class HostDocumentAccessExample implements DocumentAccessPort {

    @Override
    public Boolean canWrite(DocumentActor actor, OwnerRef owner) {
        // e.g. workspace membership for ownerType WORKSPACE
        return null; // fall through to role defaults
    }

    @Override
    public Boolean canRead(DocumentActor actor, OwnerRef owner) {
        return null;
    }
}
