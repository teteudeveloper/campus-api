#!/usr/bin/env bash
# 1. Alinha a senha do administrador (POSTGRES_USER) ao valor de POSTGRES_PASSWORD (necessário em volumes antigos,
#    pois o PostgreSQL só aplica POSTGRES_PASSWORD na primeira inicialização).
# 2. Cria ou atualiza o usuário de runtime da aplicação (APP_DB_USER), com permissões apenas de DML.
# 3. Mantém somente leitura as tabelas de controle/auditoria (flyway_schema_history e *_legado).
# - Volume novo: executado automaticamente pelo entrypoint do PostgreSQL (docker-entrypoint-initdb.d).
# - Volume existente: executado por scripts/db-provisionar.sh (idempotente).
# O dono do schema (POSTGRES_USER) continua sendo o único usuário com DDL; o Flyway usa essa credencial.
# As senhas são lidas do ambiente pelo psql (\getenv), sem aparecer na linha de comando.
set -euo pipefail
: "${POSTGRES_USER:?}" "${POSTGRES_DB:?}" "${POSTGRES_PASSWORD:?}" "${APP_DB_USER:?APP_DB_USER não definido}" "${APP_DB_PASSWORD:?APP_DB_PASSWORD não definido}"

psql -v ON_ERROR_STOP=1 -q --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
\getenv owner POSTGRES_USER
\getenv owner_password POSTGRES_PASSWORD
\getenv db POSTGRES_DB
\getenv app_user APP_DB_USER
\getenv app_password APP_DB_PASSWORD
SELECT format('ALTER ROLE %I PASSWORD %L', :'owner', :'owner_password') \gexec
SELECT format('CREATE ROLE %I LOGIN', :'app_user')
 WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'app_user') \gexec
SELECT format('ALTER ROLE %I WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD %L',
              :'app_user', :'app_password') \gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO %I', :'db', :'app_user') \gexec
SELECT format('GRANT USAGE ON SCHEMA public TO %I', :'app_user') \gexec
SELECT format('GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO %I', :'app_user') \gexec
SELECT format('GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO %I', :'app_user') \gexec
SELECT format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO %I',
              :'owner', :'app_user') \gexec
SELECT format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO %I',
              :'owner', :'app_user') \gexec
SELECT format('REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON %I FROM %I', tablename, :'app_user')
  FROM pg_tables
 WHERE schemaname = 'public'
   AND tablename IN ('flyway_schema_history', 'alunos_disciplinas_legado', 'usuarios_legado') \gexec
SQL
echo "Senha do administrador ${POSTGRES_USER} alinhada ao ambiente; usuário de aplicação '${APP_DB_USER}' provisionado (somente DML)."
