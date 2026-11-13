#!/usr/bin/env bash
# Backup verificável do PostgreSQL do docker-compose:
#   backups/<data>-<rotulo>/campus_api.dump (pg_dump -Fc), roles.sql (sem senhas), SHA256SUMS e contagens.
# A verificação restaura o dump em um contêiner temporário e compara contagens de linhas por tabela.
# Uso: ./scripts/db-backup.sh [rotulo]
set -euo pipefail
cd "$(dirname "$0")/.."
[[ -f .env ]] || { echo "Arquivo .env não encontrado" >&2; exit 1; }
set -a; . ./.env; set +a

destino="backups/$(date +%Y%m%d-%H%M%S)-${1:-manual}"
umask 077
mkdir -p "$destino"
docker compose exec -T postgres pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc > "$destino/campus_api.dump"
docker compose exec -T postgres pg_dumpall -U "$POSTGRES_USER" --roles-only --no-role-passwords > "$destino/roles.sql"
(cd "$destino" && sha256sum campus_api.dump roles.sql > SHA256SUMS)

contagem="SELECT string_agg(format('%s=%s', c.relname,
            (xpath('/row/n/text()', query_to_xml(format('select count(*) as n from %I.%I', n.nspname, c.relname), false, true, '')))[1]::text), ' ' ORDER BY c.relname)
             FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE c.relkind = 'r' AND n.nspname = 'public'"
docker compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc "$contagem" > "$destino/contagens-origem.txt"

temporario="campus-backup-verificacao-$$"
trap 'docker rm -f -v "$temporario" >/dev/null 2>&1 || true' EXIT
docker run -d --name "$temporario" -e POSTGRES_PASSWORD=verificacao -e POSTGRES_USER="$POSTGRES_USER" \
       -e POSTGRES_DB="$POSTGRES_DB" postgres:16 >/dev/null
until docker exec "$temporario" pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/dev/null 2>&1; do sleep 1; done
sleep 2
# roles.sql recria as roles (sem senhas) para que os GRANTs do dump possam ser restaurados; a role do
# superusuário já existe no contêiner temporário e esse erro é esperado.
docker exec -i "$temporario" psql -q -U "$POSTGRES_USER" -d postgres < "$destino/roles.sql" >/dev/null 2>&1 || true
docker exec -i "$temporario" pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --exit-on-error < "$destino/campus_api.dump"
docker exec "$temporario" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc "$contagem" > "$destino/contagens-restaurado.txt"

if diff -q "$destino/contagens-origem.txt" "$destino/contagens-restaurado.txt" >/dev/null; then
  echo "Backup verificado em $destino (restauração com contagens idênticas)."
else
  echo "FALHA: contagens divergentes entre origem e restauração em $destino" >&2; exit 1
fi
