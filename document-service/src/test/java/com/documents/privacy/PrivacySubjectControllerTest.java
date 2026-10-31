package com.documents.privacy;

import com.myproperty.platform.security.GatewayIdentityFilter;
import com.myproperty.platform.security.IdentityProperties;
import com.myproperty.platform.testing.IdentityRequests;
import com.myproperty.platform.web.PlatformExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PrivacySubjectControllerTest {

    private MockMvc mockMvc;
    private PrivacySubjectStore store;

    @BeforeEach
    void setUp() {
        store = mock(PrivacySubjectStore.class);
        IdentityProperties properties = new IdentityProperties(
                IdentityRequests.SECRET, 300_000L, IdentityProperties.Headers.defaults());
        mockMvc = MockMvcBuilders.standaloneSetup(new PrivacySubjectController(store, new PrivacySignature(PrivacySigning.PROPERTIES)))
                .addFilters(new GatewayIdentityFilter(properties))
                .setControllerAdvice(new PlatformExceptionHandler())
                .defaultRequest(get("/").with(PrivacySigning.signed()))
                .build();
    }

    @Test
    void onlyTheHostPrivacyCapabilityMayCallIt() {
        assertThat(PrivacySubjectController.class.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('HOST_PRIVACY')");
    }

    @Test
    void exportAndErasureAreScopedToTheCallersTenant() throws Exception {
        when(store.export("ten-1", "kc-9")).thenReturn(Map.of("service", "document"));
        when(store.erase("ten-1", "kc-9")).thenReturn(Map.of("service", "document"));

        mockMvc.perform(IdentityRequests.withIdentity(get("/documents/privacy/subjects/kc-9"), "mybuilding-api", "", "ten-1",
                        "HOST_PRIVACY"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.service").value("document"));
        mockMvc.perform(IdentityRequests.withIdentity(post("/documents/privacy/subjects/kc-9/erasure"), "mybuilding-api", "",
                        "ten-1", "HOST_PRIVACY"))
                .andExpect(status().isOk());
        verify(store).export("ten-1", "kc-9");
        verify(store).erase("ten-1", "kc-9");
    }

    @Test
    void aCallWithoutATenantOrWithABadSubjectIsRefused() throws Exception {
        mockMvc.perform(IdentityRequests.withIdentity(get("/documents/privacy/subjects/kc-9"), "mybuilding-api", "", "",
                        "HOST_PRIVACY"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(IdentityRequests.withIdentity(get("/documents/privacy/subjects/a b"), "mybuilding-api", "", "ten-1",
                        "HOST_PRIVACY"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(store);
    }

    @Test
    void aCallWhoseSignedPartsWereChangedOrThatTargetsAScopedIdIsRefused() throws Exception {
        // Signed for one address, then sent with another: refused before any lookup.
        mockMvc.perform(IdentityRequests.withIdentity(post("/documents/privacy/subjects/kc-9/erasure"), "mybuilding-api", "", "ten-1",
                        "HOST_PRIVACY")
                        .with(request -> {
                            request.addHeader("X-Subject-Email", "someone-else@example.com");
                            return request;
                        }))
                .andExpect(status().isUnauthorized());
        // A shared-mailbox style id such as PROPERTY:5 is not a subject.
        mockMvc.perform(IdentityRequests.withIdentity(post("/documents/privacy/subjects/PROPERTY:5/erasure"), "mybuilding-api", "",
                        "ten-1", "HOST_PRIVACY"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(store);
    }
}
