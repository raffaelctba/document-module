# Host integration — document-module

## Dual mode

| Mode | Dependency | Entry |
|------|------------|--------|
| Library | `document-core` | `DocumentModuleAutoConfiguration` → `DocumentsApi` |
| Service | `document-service` | `/documents/**` via gateway |

## Checklist

1. Flyway: module owns schema `documents` (V1 migration). One writer.
2. Actor:
   ```java
   DocumentActor actor = DocumentActors.of(userId, companyId, tenantId, roles);
   documentsApi.create(request, actor);
   ```
3. Optional SPIs:
   - `OwnerLookupPort` — verify owner exists / same tenant
   - `DocumentAccessPort` — product membership (return true/false/null)
4. Opaque owners: map host concepts to `ownerType`/`ownerId` only (no lease/property vocabulary in module).
5. Content policy: `documents.allowed-content-types`, `documents.max-size-bytes`
6. Storage process (`documents.storage.provider`):
   - `memory` — in-process bytes (tests and local default)
   - `filesystem` — directory in `documents.storage.directory`
   - `s3` — Amazon S3 using `documents.storage.bucket` and `documents.storage.region`

   MyBuilding maps its AWS properties onto that namespace:

   ```yaml
   aws.s3.bucket: mypropertyappbucket
   aws.s3.region: us-east-1
   documents.storage.provider: s3
   documents.storage.bucket: ${aws.s3.bucket}
   documents.storage.region: ${aws.s3.region}
   ```
7. Purge: `POST /documents/jobs/purge-deleted` per tenant (needs `HOST_DOCUMENT_WRITE`) or `purgeDeleted(systemForTenant, limit)`

## Access

`DocumentAccessRules` decides who may read or write the documents of an owner. There are two
kinds of caller:

| Caller | Identity | May |
|--------|----------|-----|
| Trusted application (host) | `HOST_DOCUMENT_READ` / `HOST_DOCUMENT_WRITE` | read (and write) any document of its tenant, and of its company when both sides have one. The host has already decided this user may see this record. |
| End user | anything else | only their own profile documents: owner `USER_PROFILE`, owner id = their user id |

- Through the gateway, `HOST_*` capabilities exist **only** on a service call: the host signs its
  own identity headers with `GATEWAY_INTERNAL_SECRET` from the internal docker network. The gateway
  strips `HOST_*` from every end-user token.
- Roles from an end-user token (ADMIN, MANAGER, OWNER, ...) grant nothing in this module. They
  are the application's vocabulary. Map them to records in the host, or through
  `DocumentAccessPort` when the module runs in-process.
- A record the caller may not see answers **404** (same as a missing one); an owner the caller may
  not list or attach to answers **403**.
- `POST /documents/jobs/purge-deleted` needs `HOST_DOCUMENT_WRITE`.

## Events

Spring application events (in-process):
- `DocumentReadyEvent`
- `DocumentDeletedEvent`
