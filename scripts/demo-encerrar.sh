#!/usr/bin/env bash
# Encerra a demonstração: recria a API apontando para o banco principal do .env, com o Swagger nas opções padrão.
# Uso: ./scripts/demo-encerrar.sh                    mantém o banco campus_api_demo (pode ser reaproveitado)
#      ./scripts/demo-encerrar.sh --remover-banco    também apaga o banco campus_api_demo (nunca o principal)
set -euo pipefail
cd "$(dirname "$0")/.."
DEMO_DB=campus_api_demo
[[ -f .env ]] || { echo "Arquivo .env não encontrado" >&2; exit 1; }
set -a; . ./.env; set +a
[[ "${POSTGRES_DB:-campus_api}" != "$DEMO_DB" ]] || { echo "POSTGRES_DB do .env não pode ser $DEMO_DB" >&2; exit 1; }

docker compose --profile app up -d --no-deps --force-recreate app >/dev/null 2>&1
for _ in $(seq 1 60); do
  [[ "$(docker inspect -f '{{.State.Health.Status}}' campus-api-app 2>/dev/null)" == healthy ]] && break
  sleep 2
done
echo "API de volta ao banco principal (${POSTGRES_DB:-campus_api}): http://localhost:${APP_PORT:-8080}/swagger-ui.html"

if [[ "${1:-}" == "--remover-banco" ]]; then
  docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -q -U "$POSTGRES_USER" -d postgres \
    -c "DROP DATABASE IF EXISTS $DEMO_DB WITH (FORCE)"
  echo "Banco $DEMO_DB removido."
fi
echo "Dica: o token da demonstração fica salvo no navegador; use Logout no botão Authorize do Swagger."
