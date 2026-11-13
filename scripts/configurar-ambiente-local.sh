#!/usr/bin/env bash
# Gera o arquivo .env local (ignorado pelo Git, permissão 600) com segredos aleatórios e fortes.
# Não sobrescreve um .env existente. Nenhum segredo é exibido no terminal.
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -e .env ]]; then
  echo ".env já existe; nada foi alterado. Consulte os valores com: less .env"
  exit 0
fi

segredo() { openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c "$1"; }
porta_livre() {
  for porta in "$@"; do
    if ! ss -ltnH "sport = :$porta" 2>/dev/null | grep -q .; then echo "$porta"; return; fi
  done
  echo "Nenhuma porta livre entre: $*" >&2; exit 1
}

POSTGRES_PORT="$(porta_livre 5433 5435 5436 5437 5438)"
APP_PORT="$(porta_livre 8080 8081 8085 8090)"

umask 177
cat > .env <<CONF
# Gerado por scripts/configurar-ambiente-local.sh em $(date -Iseconds). NÃO versionar.

# PostgreSQL. POSTGRES_USER é o administrador/dono do schema (usado pelo Flyway e para manutenção).
# Em um volume já inicializado, estes valores NÃO mudam as contas existentes: rode scripts/db-provisionar.sh.
POSTGRES_DB=campus_api
POSTGRES_USER=campus_user
POSTGRES_PASSWORD=$(segredo 32)
POSTGRES_PORT=${POSTGRES_PORT}

# Usuário de runtime da aplicação (somente SELECT/INSERT/UPDATE/DELETE)
APP_DB_USER=campus_app
APP_DB_PASSWORD=$(segredo 32)

APP_PORT=${APP_PORT}
JWT_SECRET=$(openssl rand -base64 32)

# Primeiro administrador da API (criado só se não existir nenhum ADMIN). Troque a senha após o primeiro login.
BOOTSTRAP_ADMIN_LOGIN=admin
BOOTSTRAP_ADMIN_PASSWORD=Adm$(segredo 20)$((RANDOM % 10))

CORS_ALLOWED_ORIGINS=
SWAGGER_ENABLED=true
FLYWAY_BASELINE_ON_MIGRATE=false
CONF
chmod 600 .env
echo "Criado .env (permissão 600) com PostgreSQL na porta ${POSTGRES_PORT} e API na porta ${APP_PORT}."
