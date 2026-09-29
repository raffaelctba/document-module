# document-module

Dual-mode, **domain-free** document/attachment platform module.

| Mode | Artifact | Use |
|------|----------|-----|
| Library | `document-core` | Embed via `DocumentModuleAutoConfiguration` + `DocumentsApi` |
| Service | `document-service` | Standalone process (`/documents/**`) |

## Robustness

- Multi-tenant: `tenantId` on documents; `findByIdAndTenantId`
- `DocumentActor` + configurable write/read roles
- Flyway V1 owns `documents` schema
- Opaque `ownerType` / `ownerId` + product allowlists
- `OwnerLookupPort` + `DocumentAccessPort` host SPIs
- Content type / max size policy
- Storage processes: `memory`, `filesystem`, `s3` (`documents.storage.provider`)
- Idempotency key on create
- Batch `POST /documents/batch`
- Soft-delete purge job
- Events: DocumentReady / DocumentDeleted

## Host integration

See [docs/HOST_INTEGRATION.md](docs/HOST_INTEGRATION.md).

```java
DocumentActor actor = DocumentActors.of(userId, companyId, tenantId, roles);
documentsApi.upload(request, bytes, actor);
```
