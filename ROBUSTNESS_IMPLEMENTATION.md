# document-module robustness plan

## Analysis summary (baseline)

| Area | Status |
|------|--------|
| Domain-free opaque owners | Good (`ownerType`/`ownerId`, config allowlists) |
| Dual-mode core/service | Good |
| DocumentActor + roles | Good (configurable write/read roles) |
| Tenant on entity/list | Partial — list scoped; `findById` not tenant-scoped at repo |
| Flyway | **Missing** (`enabled: false`, host-only schema comment) |
| Host SPI | `OwnerLookupPort` only; no general access SPI |
| Content policy | No MIME/size allowlist beyond request fields |
| Events / jobs | None |
| Host docs | Missing |

## P0
- [x] Flyway V1 owns `documents` schema
- [x] `findByIdAndTenantId` + use in requireVisible
- [x] Module `AccessDeniedException` (not spring-security only)
- [x] Content type + max size policy on create/complete

## P1
- [x] `DocumentAccessPort` host SPI
- [x] `DocumentActors` factory
- [x] Batch `listByIds`
- [x] Optional idempotency key on create
- [x] README dual-mode

## P2
- [x] Soft-delete purge (`purgeDeleted`)
- [x] Host integration guide
- [x] ApplicationEvent hooks DocumentReady / DocumentDeleted
