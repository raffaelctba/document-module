package com.documents.controller;

import com.documents.api.DocumentsApi;
import com.documents.api.dto.CreateDocumentRequestDto;
import com.documents.api.dto.DocumentActor;
import com.documents.api.dto.DocumentResponseDto;
import com.documents.domain.service.DocumentDomainServiceTest;
import com.myproperty.platform.security.GatewayIdentityFilter;
import com.myproperty.platform.security.IdentityProperties;
import com.myproperty.platform.testing.IdentityRequests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentControllerAuthTest {

    private MockMvc mockMvc;
    private DocumentsApi documentsApi;

    @BeforeEach
    void setUp() {
        documentsApi = mock(DocumentsApi.class);
        IdentityProperties properties = new IdentityProperties(
                IdentityRequests.SECRET, 300_000L, IdentityProperties.Headers.defaults());
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new DocumentController(documentsApi, DocumentDomainServiceTest.properties()))
                .addFilters(new GatewayIdentityFilter(properties))
                .setControllerAdvice(new PlatformExceptionHandler())
                .setCustomArgumentResolvers()
                .build();
    }

    @Test
    void missingIdentityReturns401() throws Exception {
        mockMvc.perform(get("/documents/d1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void invalidSignatureReturns401() throws Exception {
        mockMvc.perform(get("/documents/d1")
                        .header("X-User-Id", "user-1")
                        .header("X-Roles", "ADMIN")
                        .header("X-Gateway-Timestamp", Long.toString(System.currentTimeMillis()))
                        .header("X-Internal-Signature", "deadbeef"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createRequiresDocumentAccess() throws Exception {
        PreAuthorize authorization = DocumentController.class
                .getMethod("create", CreateDocumentRequestDto.class)
                .getAnnotation(PreAuthorize.class);
        assertTrue(authorization.value().contains("documentAccess"));
    }

    @Test
    void gatewayBasePathHappyPathReturns200() throws Exception {
        when(documentsApi.get(eq("d1"), any())).thenReturn(response("d1"));
        mockMvc.perform(IdentityRequests.withIdentity(get("/documents/d1"), "user-1", "co-1", "ten-1", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("d1"));
    }

    @Test
    void apiPrefixedPathIsNotTheGatewayRoute() throws Exception {
        mockMvc.perform(IdentityRequests.withIdentity(get("/api/documents/d1"), "user-1", "co-1", "ten-1", "ADMIN"))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingTenantFallsBackToProductDefault() throws Exception {
        var actor = forClass(DocumentActor.class);
        when(documentsApi.get(eq("d1"), actor.capture())).thenReturn(response("d1"));
        mockMvc.perform(IdentityRequests.withIdentity(get("/documents/d1"), "user-1", "co-1", null, "ADMIN"))
                .andExpect(status().isOk());
        assertEquals("myproperty", actor.getValue().tenantId());
    }

    @Test
    void contentRedirectsToSignedUrl() throws Exception {
        when(documentsApi.contentUrl(eq("d1"), any())).thenReturn("https://bucket.example/docs/d1?sig=1");
        mockMvc.perform(IdentityRequests.withIdentity(
                        get("/documents/d1/content"), "user-1", "co-1", "ten-1", "ADMIN"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://bucket.example/docs/d1?sig=1"));
    }

    @Test
    void createReturns201() throws Exception {
        when(documentsApi.create(any(), any())).thenReturn(response("d1"));
        mockMvc.perform(IdentityRequests.withIdentity(
                                post("/documents").contentType(MediaType.APPLICATION_JSON).content("""
                                        {
                                          "ownerType": "PROPERTY",
                                          "ownerId": "prop-123",
                                          "purpose": "GALLERY",
                                          "filename": "pool.jpg"
                                        }
                                        """),
                        "user-1", "co-1", "ten-1", "ADMIN"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("d1"));
    }

    private static DocumentResponseDto response(String id) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new DocumentResponseDto(
                id, "myproperty", "ten-1", "co-1", "PROPERTY", "prop-123", "GALLERY",
                1, null,
                "docs/myproperty/ten-1/PROPERTY/prop-123/" + id, "pool.jpg", "image/jpeg",
                12L, null, "PENDING_UPLOAD", Set.of(), "user-1", now, null,
                "https://upload.example/" + id, now, null, null);
    }
}
