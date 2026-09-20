package com.documents;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.documents", importOptions = ImportOption.DoNotIncludeTests.class)
class DocumentModuleDependencyTest {

    @ArchTest
    static final ArchRule documentsDoesNotDependOnHostOrSiblingModules =
            noClasses()
                    .that().resideInAPackage("com.documents..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.billing..",
                            "com.payments..",
                            "com.notifications..",
                            "com.booking..",
                            "com.chat..",
                            "com.ai..");
}
