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
7. Purge: `POST /documents/jobs/purge-deleted` per tenant or `purgeDeleted(systemForTenant, limit)`

## Events

Spring application events (in-process):
- `DocumentReadyEvent`
- `DocumentDeletedEvent`
