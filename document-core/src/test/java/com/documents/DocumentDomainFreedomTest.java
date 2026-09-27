package com.documents;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Guards the module core against residential/PM product-domain vocabulary.
 * Multi-tenant isolation fields (tenantId) are allowed.
 */
class DocumentDomainFreedomTest {

    @Test
    void publicApiDoesNotExposeProductDomainFieldNames() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.documents.api", "com.documents.domain");

        classes.forEach(javaClass -> {
            javaClass.getAllFields().forEach(field -> {
                String name = field.getName().toLowerCase();
                assertFalse(name.contains("lease"), () -> javaClass.getName() + "#" + field.getName());
                assertFalse(name.equals("propertyid") || name.equals("property_id") || name.equals("unitid") || name.equals("unit_id"),
                        () -> javaClass.getName() + "#" + field.getName());
                assertFalse(name.contains("landlord") || name.contains("condo"),
                        () -> javaClass.getName() + "#" + field.getName());
            });
            javaClass.getMethods().forEach(method -> {
                String name = method.getName().toLowerCase();
                assertFalse(name.contains("lease") || name.contains("landlord"),
                        () -> javaClass.getName() + "#" + method.getName());
            });
        });
    }
}
