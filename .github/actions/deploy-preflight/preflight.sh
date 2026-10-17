#!/usr/bin/env bash
# deploy-preflight v1 (see action.yml). Never prints a value: only names, shapes and results.
set -uo pipefail
set +x
: "${PF_SSH_KEY_VAR:=SSH_KEY}" "${PF_HOST_VAR:=EC2_HOST}" "${PF_USER_VAR:=EC2_USER}"
: "${PF_EXPECT_RUNNING:=}" "${PF_DB_USER_VAR:=DATABASE_USERNAME}" "${PF_DB_PASSWORD_VAR:=DATABASE_PASSWORD}"
fail=0
err() { echo "::error title=preflight::$*"; fail=1; }
ok() { echo "preflight: ok   $*"; }
val() { printf '%s' "${!1:-}"; }

remote="${PF_REMOTE:-true}"
[ "$remote" = true ] || remote=false
echo "preflight: deploy checks: $remote (false = build-only run)"

# 1. Required names are set and non-empty.
names="${PF_REQUIRED_BUILD:-}"
[ "$remote" = true ] && names="$names ${PF_REQUIRED:-}"
for name in $names; do
  if [ -z "$(val "$name")" ]; then err "missing or empty: $name"; else ok "set: $name"; fi
done

# 2. Cheap well-formedness checks.
for pair in ${PF_MIN_LENGTH:-}; do
  name="${pair%%=*}"; min="${pair#*=}"
  case " $names " in *" $name "*) ;; *) continue ;; esac
  len=$(val "$name" | wc -c | tr -d ' ')
  [ "$len" -ge "$min" ] && ok "$name length >= $min" || err "$name is shorter than $min characters"
done
if [ -n "${PF_DB_URL_VAR:-}" ] && [ "$remote" = true ]; then
  url=$(val "$PF_DB_URL_VAR")
  if [ -n "$url" ]; then
    if [[ "$url" =~ ^jdbc:postgresql://[^/?]+/[^/?]+ ]]; then ok "$PF_DB_URL_VAR looks like jdbc:postgresql://host[:port]/db"
    else err "$PF_DB_URL_VAR is not jdbc:postgresql://host[:port]/db"; fi
  fi
fi

key_file=""
cleanup() { [ -n "$key_file" ] && rm -f "$key_file"; }
trap cleanup EXIT
if [ "$remote" = true ] && [ -n "$(val "$PF_SSH_KEY_VAR")" ]; then
  key_file=$(mktemp); chmod 600 "$key_file"
  printf '%s\n' "$(val "$PF_SSH_KEY_VAR")" > "$key_file"
  if grep -q $'\r' "$key_file"; then err "$PF_SSH_KEY_VAR contains CR characters (pasted with Windows line endings)"; fi
  if out=$(ssh-keygen -y -f "$key_file" 2>&1 >/dev/null); then ok "$PF_SSH_KEY_VAR parses as a private key"
  else err "$PF_SSH_KEY_VAR does not parse as a private key (ssh-keygen: ${out//$key_file/<key>})"; fi
fi

if [ "$fail" -ne 0 ]; then echo "preflight: FAILED before contacting the host"; exit 1; fi
[ "$remote" = true ] || { echo "preflight: passed (build-only)"; exit 0; }

# 3. SSH to the EC2 host.
host=$(val "$PF_HOST_VAR"); user=$(val "$PF_USER_VAR")
SSH=(ssh -i "$key_file" -o BatchMode=yes -o ConnectTimeout=20 -o StrictHostKeyChecking=no -o LogLevel=ERROR "$user@$host")
if out=$("${SSH[@]}" 'echo preflight-ssh-ok' 2>&1) && [ "$out" = preflight-ssh-ok ]; then ok "SSH to the EC2 host"
else out=$(printf '%s' "$out" | tail -1); out=${out//"$host"/<host>}; out=${out//"$user"@/<user>@}
  err "SSH to the EC2 host failed: $(printf '%s' "$out" | sed -E 's/[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+/<ip>/g' | cut -c1-200)"; exit 1; fi

# Host facts (informational).
"${SSH[@]}" "free -m | awk '/^Mem:/{print \"preflight: info memory MB total/available \" \$2 \"/\" \$7} /^Swap:/{print \"preflight: info swap MB total/used \" \$2 \"/\" \$3}';
  echo 'preflight: info containers:'; docker ps --format '  {{.Names}} | {{.Status}}' 2>/dev/null | head -40" 2>/dev/null || true
if [ -n "${PF_NETWORK:-}" ]; then
  if "${SSH[@]}" "docker network inspect '$PF_NETWORK' >/dev/null 2>&1"; then ok "docker network $PF_NETWORK exists"
  else err "docker network $PF_NETWORK is missing on the host"; fi
fi

for pattern in ${PF_EXPECT_RUNNING:-}; do
  if "${SSH[@]}" "docker ps --format '{{.Names}}' | grep -qiF -- '$pattern'" 2>/dev/null; then ok "container running: *$pattern*"
  else echo "::warning title=preflight::no running container matches '$pattern' (dependency of this service)"; fi
done

# 4. The database accepts a connection from that host (creds go over SSH stdin, never on a command line).
if [ -n "${PF_DB_URL_VAR:-}" ]; then
  schema="${PF_DB_CREATE_SCHEMA:-}"
  if [ -n "$schema" ] && ! [[ "$schema" =~ ^[a-z_][a-z0-9_]*$ ]]; then err "db-create-schema is not a plain schema name"; schema=""; fi
  pgurl=$(val "$PF_DB_URL_VAR"); pgurl="${pgurl#jdbc:}"; pgurl="${pgurl%%\?*}"
  query="select 1"
  # Flyway-owner report for one schema: exists, CREATE on it, CREATE on the db, tables, tables owned
  # by another role, flyway history present.
  [ -n "$schema" ] && query="select (select count(*) from pg_namespace where nspname = '$schema') > 0,
      coalesce((select has_schema_privilege(current_user, n.oid, 'CREATE') from pg_namespace n where nspname = '$schema'), false),
      has_database_privilege(current_user, current_database(), 'CREATE'),
      (select count(*) from pg_tables where schemaname = '$schema'),
      (select count(*) from pg_tables where schemaname = '$schema' and tableowner <> current_user),
      to_regclass('$schema.flyway_schema_history') is not null"
  net_arg=""; [ -n "${PF_NETWORK:-}" ] && net_arg="--network $PF_NETWORK"
  remote_script=$(mktemp); chmod 600 "$remote_script"
  {
    echo 'set -u; f=$(mktemp); chmod 600 "$f"'
    echo "cat > \"\$f\" <<'PREFLIGHT_CREDS'"
    printf 'PGUSER=%s\nPGPASSWORD=%s\nPGURL=%s\nPGCONNECT_TIMEOUT=10\n' "$(val "$PF_DB_USER_VAR")" "$(val "$PF_DB_PASSWORD_VAR")" "$pgurl"
    echo 'PREFLIGHT_CREDS'
    echo 'img=$(docker ps --format "{{.Image}}" | grep -m1 -i postgres || true); img=${img:-postgres:16-alpine}'
    printf 'docker run --rm %s --env-file "$f" "$img" sh -c %q %q 2>&1\n' "$net_arg" 'pg_isready -q -d "$PGURL" -t 10 || { echo pg_isready-failed; exit 2; }; psql "$PGURL" -v ON_ERROR_STOP=1 -tAX -F " " -c "$0"' "$query"
    echo 'rc=$?; rm -f "$f"; exit $rc'
  } > "$remote_script"
  out=$("${SSH[@]}" 'bash -s' < "$remote_script" 2>&1); rc=$?
  rm -f "$remote_script"
  clean=$(printf '%s' "$out" | tail -2 | tr '\n' ' ' | sed -E 's/"[^"]*"/"<host>"/g; s/[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+/<ip>/g' | cut -c1-240)
  if [ $rc -ne 0 ]; then err "database connection from the host failed: $clean"
  else
    ok "database accepts a connection from the host (select 1 / pg_isready)"
    if [ -n "$schema" ]; then
      read -r exists create_schema create_db tables foreign history <<<"$(printf '%s' "$out" | tail -1)"
      echo "preflight: info schema $schema exists=$exists create-on-schema=$create_schema create-on-db=$create_db tables=$tables not-owned-by-db-user=$foreign flyway-history=$history"
      if [ "$exists" = t ] && [ "$create_schema" != t ]; then err "DB user has no CREATE on schema $schema; the service cannot own its Flyway history"
      elif [ "$exists" != t ] && [ "$create_db" != t ]; then err "DB user cannot create schema $schema"
      else ok "DB user can own schema $schema (Flyway)"; fi
      [ "${foreign:-0}" = 0 ] || echo "::warning title=preflight::$foreign table(s) in schema $schema are owned by another role; later ALTER migrations would fail"
    fi
  fi
fi

[ "$fail" -eq 0 ] && echo "preflight: passed" || { echo "preflight: FAILED"; exit 1; }
