package com.documents.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ComponentScan;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentHostScanTest {

    private static final List<String> INFRASTRUCTURE_PACKAGES = List.of(
            "com.documents.infrastructure.persistence.repository",
            "com.documents.infrastructure.storage",
            "com.documents.infrastructure.clock");

    @Test
    void documentModuleDoesNotScanHostPackages() {
        ComponentScan scan = DocumentModuleConfig.class.getAnnotation(ComponentScan.class);
        assertNotNull(scan);
        List<String> packages = Arrays.asList(scan.basePackages());
        assertFalse(packages.stream().anyMatch(pkg -> pkg.startsWith("com.myproperty")),
                "Document module must not scan host packages");
        assertFalse(packages.contains("com.documents"),
                "Document module must not scan the whole com.documents tree");
        assertFalse(packages.contains("com.documents.domain"),
                "Document module must not scan domain packages");
        assertFalse(packages.contains("com.documents.application"),
                "Document module must not scan application packages");
        assertFalse(packages.contains("com.documents.controller"),
                "Library auto-config must not scan standalone HTTP controllers");
        assertFalse(packages.contains("com.documents.security"),
                "Library auto-config must not scan standalone authorization filters");
    }

    @Test
    void documentModuleScansOnlyInfrastructurePackages() {
        ComponentScan scan = DocumentModuleConfig.class.getAnnotation(ComponentScan.class);
        assertNotNull(scan);
        List<String> packages = Arrays.asList(scan.basePackages());
        assertEquals(INFRASTRUCTURE_PACKAGES.size(), packages.size());
        assertTrue(packages.containsAll(INFRASTRUCTURE_PACKAGES),
                "Document module must scan only document infrastructure packages");
    }

    @Test
    void documentCoreDoesNotContainHttpControllers() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.documents.controller.DocumentController"));
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.documents.api.DocumentController"));
    }
}
