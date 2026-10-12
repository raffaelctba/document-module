package com.documents.controller;

import com.documents.application.DocumentsApiAdapter;
import com.documents.application.port.DocumentRepositoryPort;
import com.documents.application.service.DocumentAppService;
import com.documents.config.DocumentsProperties;
import com.documents.domain.model.Document;
import com.documents.domain.service.DocumentDomainService;
import com.documents.infrastructure.owner.NoOpOwnerLookup;
import com.documents.infrastructure.storage.InMemoryObjectStorageAdapter;
import com.myproperty.platform.security.GatewayIdentityFilter;
import com.myproperty.platform.security.IdentityProperties;
import com.myproperty.platform.testing.IdentityRequests;
import com.myproperty.platform.web.PlatformExceptionHandler;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP behaviour of document-service with the real document-core rules and gateway identity
 * headers, as the gateway forwards them: an end-user identity (no HOST_* role, which the gateway
 * strips from tokens) and a host service identity (HOST_DOCUMENT_* signed by pm-backend).
 */
class DocumentAccessHttpTest {

    private static final String TENANT = "myproperty";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DocumentsProperties properties = new DocumentsProperties(
                "myproperty",
                Map.of("myproperty", List.of("PROPERTY", "USER_PROFILE", "CONVERSATION")),
                Map.of("myproperty", List.of("AVATAR", "LEASE", "CHAT_ATTACHMENT")),
                List.of(), null, null,
                new DocumentsProperties.Storage("memory", "b", "us-east-1", null, null, null, true, "docs",
                        Duration.ofMinutes(15), null),
                new DocumentsProperties.OwnerLookup(false),
                new DocumentsProperties.Http(100));
        DocumentAppService service = new DocumentAppService(
                new InMemoryDocuments(), new InMemoryObjectStorageAdapter(), new NoOpOwnerLookup(),
                Instant::now, new DocumentDomainService(properties), properties);
        IdentityProperties identity = new IdentityProperties(
                IdentityRequests.SECRET, 300_000L, IdentityProperties.Headers.defaults());
        mockMvc = MockMvcBuilders.standaloneSetup(new DocumentController(new DocumentsApiAdapter(service)))
                .addFilters(new GatewayIdentityFilter(identity))
                .setControllerAdvice(new DocumentExceptionHandler(), new PlatformExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void userACannotReadUserBsDocumentContentOrList() throws Exception {
        String avatarOfB = create("USER_PROFILE", "user-b", "AVATAR", endUser("user-b"));
        String leaseByHost = create("PROPERTY", "prop-1", "LEASE", host("user-b", "HOST_DOCUMENT_WRITE"));

        for (String id : List.of(avatarOfB, leaseByHost)) {
            mockMvc.perform(as(get("/documents/" + id), endUser("user-a"))).andExpect(status().isNotFound());
            mockMvc.perform(as(get("/documents/" + id + "/content"), endUser("user-a"))).andExpect(status().isNotFound());
            mockMvc.perform(as(get("/documents/" + id + "/content/bytes"), endUser("user-a"))).andExpect(status().isNotFound());
        }
        mockMvc.perform(as(get("/documents").param("ownerType", "USER_PROFILE").param("ownerId", "user-b"),
                        endUser("user-a")))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(get("/documents").param("ownerType", "PROPERTY").param("ownerId", "prop-1"),
                        endUser("user-a")))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(post("/documents/batch").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"ids\":[\"" + avatarOfB + "\",\"" + leaseByHost + "\"]}"),
                        endUser("user-a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void applicationRolesInAnEndUserTokenGrantNothing() throws Exception {
        String lease = create("PROPERTY", "prop-1", "LEASE", host("user-b", "HOST_DOCUMENT_WRITE"));

        mockMvc.perform(as(get("/documents/" + lease + "/content"), new Caller("user-a", "ADMIN,MANAGER,OWNER,MEMBER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void userReadsOwnProfileDocument() throws Exception {
        String avatar = create("USER_PROFILE", "user-a", "AVATAR", endUser("user-a"));

        mockMvc.perform(as(get("/documents/" + avatar), endUser("user-a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value("user-a"));
        mockMvc.perform(as(get("/documents").param("ownerType", "USER_PROFILE").param("ownerId", "user-a"),
                        endUser("user-a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void hostServiceIdentityReadsOnBehalfOfTheUserItAuthorized() throws Exception {
        String lease = create("PROPERTY", "prop-1", "LEASE", host("user-b", "HOST_DOCUMENT_WRITE"));

        mockMvc.perform(as(get("/documents/" + lease + "/content/bytes"), host("user-a", "HOST_DOCUMENT_READ")))
                .andExpect(status().isOk());
        mockMvc.perform(as(get("/documents").param("ownerType", "PROPERTY").param("ownerId", "prop-1"),
                        host("user-a", "HOST_DOCUMENT_READ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    private String create(String ownerType, String ownerId, String purpose, Caller caller) throws Exception {
        String body = """
                {"ownerType":"%s","ownerId":"%s","purpose":"%s","filename":"f.pdf","contentType":"application/pdf","sizeBytes":1}
                """.formatted(ownerType, ownerId, purpose);
        String json = mockMvc.perform(as(post("/documents").contentType(MediaType.APPLICATION_JSON).content(body), caller))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        // In-memory storage: the presigned PUT already created an (empty) object, so the first
        // content read marks the document ready.
        return JsonPath.read(json, "$.id");
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder as(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, Caller caller) {
        return IdentityRequests.withIdentity(request, caller.userId(), "co-1", TENANT, caller.roles());
    }

    private static Caller endUser(String userId) {
        return new Caller(userId, "");
    }

    private static Caller host(String userId, String roles) {
        return new Caller(userId, roles);
    }

    private record Caller(String userId, String roles) {
    }

    private static final class InMemoryDocuments implements DocumentRepositoryPort {
        private final Map<String, Document> byId = new ConcurrentHashMap<>();

        @Override
        public Document save(Document document) {
            byId.put(document.id(), document);
            return document;
        }

        @Override
        public Optional<Document> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<Document> findByIdAndTenantId(String id, String tenantId) {
            return findById(id).filter(d -> tenantId.equals(d.tenantId()));
        }

        @Override
        public Optional<Document> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey) {
            return Optional.empty();
        }

        @Override
        public boolean existsActiveSuccessor(String documentId) {
            return false;
        }

        @Override
        public Page<Document> listActive(String product, String tenantId, String ownerType, String ownerId,
                                         String purpose, boolean includeVersions, Pageable pageable) {
            List<Document> matches = byId.values().stream()
                    .filter(d -> !d.deleted() && d.tenantId().equals(tenantId))
                    .filter(d -> d.owner().type().equals(ownerType) && d.owner().id().equals(ownerId))
                    .toList();
            return new PageImpl<>(new ArrayList<>(matches), pageable, matches.size());
        }

        @Override
        public List<Document> findByIdsAndTenantId(Collection<String> ids, String tenantId) {
            return ids.stream().map(byId::get).filter(d -> d != null && tenantId.equals(d.tenantId())).toList();
        }

        @Override
        public List<Document> findDeletedBefore(String tenantId, Instant deletedBefore, int limit) {
            return List.of();
        }

        @Override
        public void hardDelete(String id) {
            byId.remove(id);
        }
    }
}
