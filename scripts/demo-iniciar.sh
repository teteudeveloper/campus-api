#!/usr/bin/env bash
# Prepara uma demonstração ao vivo pelo Swagger UI:
#  - banco SEPARADO "campus_api_demo", vazio (apenas o administrador do bootstrap), com as migrações aplicadas;
#  - API reiniciada apontando para ele, em modo demonstração: a página /demo/index.html encadeia ids e token
#    automaticamente e o exemplo de login já traz o administrador de demonstração.
# O banco principal (POSTGRES_DB, normalmente campus_api) e o administrador dele NÃO são alterados.
# A senha do administrador de demonstração é aleatória, fica em .env.demo (permissão 600, ignorado pelo Git) e só
# existe no banco campus_api_demo.
#
# Uso: ./scripts/demo-iniciar.sh               recria o banco de demonstração do zero
#      ./scripts/demo-iniciar.sh --manter      reaproveita o banco de demonstração existente
#      ./scripts/demo-iniciar.sh --build       também reconstrói a imagem da API
# Para voltar ao banco principal: ./scripts/demo-encerrar.sh
set -euo pipefail
cd "$(dirname "$0")/.."

DEMO_DB=campus_api_demo
MANTER=false
CONSTRUIR=false
for arg in "$@"; do
  case "$arg" in
    --manter) MANTER=true ;;
    --build) CONSTRUIR=true ;;
    *) echo "Opção desconhecida: $arg" >&2; exit 2 ;;
  esac
done

[[ -f .env ]] || { echo "Crie o .env com ./scripts/configurar-ambiente-local.sh" >&2; exit 1; }
set -a; . ./.env; set +a
PRINCIPAL="${POSTGRES_DB:-campus_api}"
[[ "$PRINCIPAL" != "$DEMO_DB" ]] || { echo "POSTGRES_DB do .env não pode ser $DEMO_DB" >&2; exit 1; }

# Administrador exclusivo do banco de demonstração (reaproveitado com --manter, pois a conta já existe nele)
DEMO_ADMIN_LOGIN=admin
if [[ "$MANTER" == true && -f .env.demo ]]; then
  DEMO_ADMIN_SENHA="$(sed -n 's/^DEMO_ADMIN_SENHA=//p' .env.demo)"
else
  DEMO_ADMIN_SENHA="Demo$(openssl rand -base64 24 | tr -dc 'A-Za-z0-9' | head -c 16)$((RANDOM % 10))"
  ( umask 177; printf '# Gerado por scripts/demo-iniciar.sh; vale apenas para o banco campus_api_demo.\nDEMO_ADMIN_SENHA=%s\n' \
      "$DEMO_ADMIN_SENHA" > .env.demo )
fi

aguardar_saudavel() {
  local container="$1" estado=""
  for _ in $(seq 1 60); do
    estado="$(docker inspect -f '{{.State.Health.Status}}' "$container" 2>/dev/null || true)"
    [[ "$estado" == healthy ]] && return 0
    sleep 2
  done
  echo "$container não ficou saudável (estado: ${estado:-desconhecido}). Veja: docker compose logs" >&2
  exit 1
}
sql() { docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -q -U "$POSTGRES_USER" -d postgres "$@"; }

echo "==> PostgreSQL"
docker compose up -d postgres >/dev/null 2>&1
aguardar_saudavel campus-api-db

echo "==> Banco de demonstração ($DEMO_DB)"
docker compose --profile app stop app >/dev/null 2>&1 || true
if [[ "$MANTER" == false ]]; then
  sql -c "SET client_min_messages TO warning" -c "DROP DATABASE IF EXISTS $DEMO_DB WITH (FORCE)" \
      -c "CREATE DATABASE $DEMO_DB OWNER \"$POSTGRES_USER\""
  echo "    recriado vazio"
else
  sql -tAc "SELECT 1 FROM pg_database WHERE datname = '$DEMO_DB'" | grep -q 1 \
    || sql -c "CREATE DATABASE $DEMO_DB OWNER \"$POSTGRES_USER\""
  echo "    mantido"
fi
docker compose exec -T -e POSTGRES_DB="$DEMO_DB" postgres bash /docker-entrypoint-initdb.d/10-usuario-aplicacao.sh >/dev/null
echo "    permissões do usuário de aplicação aplicadas"

echo "==> Migrações"
POSTGRES_DB="$DEMO_DB" docker compose --profile app run --rm --no-deps migracao 2>&1 \
  | grep -E "Successfully|up to date|ERROR" | sed 's/^/    /'

if [[ "$CONSTRUIR" == true ]] || ! docker image inspect campus-api:local >/dev/null 2>&1; then
  echo "==> Construindo a imagem da API"
  if docker buildx version >/dev/null 2>&1; then docker build -q -t campus-api:local . >/dev/null
  else DOCKER_BUILDKIT=0 docker build -q -t campus-api:local . >/dev/null 2>&1; fi
fi

echo "==> API apontando para $DEMO_DB"
POSTGRES_DB="$DEMO_DB" SWAGGER_ENABLED=true SWAGGER_TRY_IT_OUT=true SWAGGER_PERSISTIR_AUTORIZACAO=true \
  DEMO_HABILITADA=true BOOTSTRAP_ADMIN_LOGIN="$DEMO_ADMIN_LOGIN" BOOTSTRAP_ADMIN_PASSWORD="$DEMO_ADMIN_SENHA" \
  docker compose --profile app up -d --no-deps --force-recreate app >/dev/null 2>&1
aguardar_saudavel campus-api-app

contagem="$(docker compose exec -T postgres psql -U "$POSTGRES_USER" -d "$DEMO_DB" -tA -F ' ' -c \
  "SELECT (SELECT count(*) FROM alunos), (SELECT count(*) FROM professores), (SELECT count(*) FROM disciplinas),
          (SELECT count(*) FROM matriculas), (SELECT count(*) FROM usuarios)")"
read -r alunos professores disciplinas matriculas usuarios <<<"$contagem"
porta="${APP_PORT:-8080}"
cat <<MSG

Demonstração pronta.
  Banco:      $DEMO_DB  (alunos=$alunos professores=$professores disciplinas=$disciplinas matrículas=$matriculas usuários=$usuarios)
  Demonstração: http://localhost:$porta/demo/index.html   (ids e token preenchidos automaticamente)
  Swagger UI:   http://localhost:$porta/swagger-ui.html
  Login:        exemplo "1. Administrador" já preenchido (admin de demonstração, senha em .env.demo)
  Roteiro:      docs/roteiro-demo.md
Para voltar ao banco principal ($PRINCIPAL): ./scripts/demo-encerrar.sh
MSG
