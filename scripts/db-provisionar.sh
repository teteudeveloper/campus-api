#!/usr/bin/env bash
# Alinha um volume PostgreSQL JÁ INICIALIZADO com o .env (idempotente; executa o mesmo script usado na
# inicialização de volumes novos): senha do administrador e usuário de runtime com permissões apenas de DML.
# Usa o socket local dentro do contêiner; nenhuma senha é exibida.
set -euo pipefail
cd "$(dirname "$0")/.."
[[ -f .env ]] || { echo "Crie o .env com scripts/configurar-ambiente-local.sh" >&2; exit 1; }
docker compose exec -T postgres bash /docker-entrypoint-initdb.d/10-usuario-aplicacao.sh
