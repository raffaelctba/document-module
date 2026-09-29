package com.documents.infrastructure.access;

import com.documents.api.DocumentAccessPort;

/**
 * Fallback authorization port. Registered from {@code DocumentModuleConfig}
 * only when the host does not provide its own {@link DocumentAccessPort}.
 * A {@code @Component} with {@code @ConditionalOnMissingBean} matches this
 * class itself and then removes the only bean.
 */
public class DefaultDocumentAccess implements DocumentAccessPort {
}
