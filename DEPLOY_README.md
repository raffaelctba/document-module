# document-service — EC2 deploy

Same pattern as `otp-module`: one shared EC2 host, one Docker Compose project per service,
image in GHCR, loopback-only host port, health gate in CI.

| Env | Branch | Dir on EC2 | Compose project | Host port → container | Network |
| --- | --- | --- | --- | --- | --- |
| TEST | `test` | `/opt/dev/document` | `document-test` | `127.0.0.1:9185` → `8085` | `mybuilding-test` |
| PRD | `main` (only if repo var `EC2_PRD_ENABLED=true`) | `/opt/prd/document` | `document-prd` | `127.0.0.1:8185` → `8085` | `mybuilding-prd` |

- Image: `ghcr.io/<owner>/document-service:{latest|test-latest}` + `sha-<sha>` / `test-sha-<sha>`.
- Docker DNS name on the shared network: **`document-module`** — api-gateway upstream `http://document-module:8085`.
- Health: `curl -fsS http://127.0.0.1:9185/actuator/health` (TEST) → `{"status":"UP"}`.
- `Dockerfile` is multi-stage and self-contained (build context = this repo). The private
  platform-sdk / BOM come from `raffaelctba/PlatformModules` GitHub Packages via a BuildKit
  secret (`--secret id=maven_settings,src=~/.m2/settings.xml`), never baked into a layer.

## Database / Flyway (hybrid, host-owned for now)

- Schema: **`documents`** in the shared Postgres.
- pm-backend applies the `documents` migrations (V21, V35, V61, …). `document-service` still ships `db/migration` and defaults to `spring.flyway.enabled: true` for local dev; the **prd/test profiles now force it off** and compose pins it off.
- The container is pinned to `SPRING_FLYWAY_ENABLED=false`. **Exactly one Flyway writer** —
  do not enable it here until a dedicated ownership-transfer PR also removes the schema from
  pm-backend's `spring.flyway.schemas` / migration set (one-way door).
- pm-backend still embeds `document-core` in library mode. This deploy does **not** cut the host
  over to HTTP; that is a later, per-module PR.

## Pinned behaviour (compose base)

| Env | Value | Why |
| --- | --- | --- |
| `SPRING_FLYWAY_ENABLED` | `false` | host owns schema `documents` |


### Storage must match pm-backend

`DOCUMENTS_STORAGE_PROVIDER` is required (the module default is `memory`). Point it at the
**same** bucket/prefix pm-backend uses, otherwise documents written by one process are
invisible to the other.

### Fixes included

- document-core `NoOpOwnerLookup` was `@Component @ConditionalOnMissingBean`. It matched
  itself on the second condition pass and removed itself, so the standalone service failed
  with "required a bean of type OwnerLookupPort". It is now a `@Bean @ConditionalOnMissingBean`
  fallback in `DocumentModuleConfig`. pm-backend supplies its own adapter, so library mode
  behaves the same.
- The parent POM declares the `github` (PlatformModules) repository so the module builds
  outside the monorepo.

## Secrets / variables

Repository-level: `PLATFORM_MODULES_PACKAGES_TOKEN` (secret, PAT `read:packages`),
optional `PLATFORM_MODULES_PACKAGES_USERNAME`, and `EC2_PRD_ENABLED` (variable, set `true`
only after TEST is proven).

Per GitHub Environment `TEST` and `PRD`: `EC2_SSH_KEY` (secret), `EC2_HOST`, `EC2_USER`
(variables — same values as otp-module / pm-backend), plus:

| Name | Kind | Notes |
| --- | --- | --- |
| `DATABASE_URL` | secret **required** | JDBC URL of the shared Postgres (same DB as pm-backend), e.g. jdbc:postgresql://<host>:5432/<db> |
| `DATABASE_USERNAME` | secret **required** | Recommended: a dedicated role with DML-only grants on the module schema (DDL stays with the host Flyway) |
| `DATABASE_PASSWORD` | secret **required** |  |
| `GATEWAY_INTERNAL_SECRET` | secret **required** | HMAC shared with api-gateway (same per-env value) |
| `REDIS_HOST` | var **required** | Shared Redis (same host as otp-service). platform-messaging + health need it |
| `REDIS_PORT` | var |  |
| `REDIS_PASSWORD` | secret **required** | Same as otp-service REDIS_PASSWORD |
| `REDIS_DATABASE` | var | Dedicated Redis logical DB. platform-messaging uses Redis Streams consumer groups; sharing pm-backend's DB would make this idle service COMPETE for host messages. Change only at HTTP cutover |
| `DOCUMENTS_STORAGE_PROVIDER` | var **required** | s3 | filesystem | memory — must match pm-backend's store |
| `DOCUMENTS_BUCKET` | var |  |
| `DOCUMENTS_KEY_PREFIX` | var |  |
| `DOCUMENTS_S3_REGION` | var |  |
| `DOCUMENTS_S3_ENDPOINT` | var | optional (non-AWS S3) |
| `DOCUMENTS_S3_ACCESS_KEY` | secret |  |
| `DOCUMENTS_S3_SECRET_KEY` | secret |  |

Templates: `.env.test.template`, `.env.prod.template`. The deploy fails closed if a
required value is missing.

## Manual operations

```bash
cd /opt/dev/document
docker compose --env-file .env -f docker-compose.yml -f docker-compose.test.yml ps
docker compose --env-file .env -f docker-compose.yml -f docker-compose.test.yml logs --tail 200 document-module
curl -fsS http://127.0.0.1:9185/actuator/health
```

Rollback: re-run the workflow with `image_tag=test-sha-<previous sha>` / `sha-<previous sha>`.

Local build:

```bash
docker build --secret id=maven_settings,src=$HOME/.m2/settings.xml -t document-service .
```
