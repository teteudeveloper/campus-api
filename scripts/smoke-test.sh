#!/usr/bin/env bash
# Fluxo integrado contra uma instância em execução (requer curl e jq).
# Uso: BASE_URL=http://localhost:8080 ADMIN_LOGIN=admin ADMIN_PASSWORD='...' ./scripts/smoke-test.sh
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
: "${ADMIN_LOGIN:?defina ADMIN_LOGIN}"
: "${ADMIN_PASSWORD:?defina ADMIN_PASSWORD}"
SUFIXO="$(date +%s)"

passo() { printf '\n==> %s\n' "$*"; }
falha() { printf 'FALHOU: %s\n' "$*" >&2; exit 1; }

ARQ="$(mktemp)"; trap 'rm -f "$ARQ"' EXIT
# chamar METODO CAMINHO [TOKEN] [CORPO] -> define $STATUS e $CORPO
chamar() {
  local metodo="$1" caminho="$2" token="${3:-}" corpo="${4:-}"
  local args=(-sS -o "$ARQ" -w '%{http_code}' -X "$metodo" "$BASE_URL$caminho" -H 'Content-Type: application/json')
  [[ -n "$token" ]] && args+=(-H "Authorization: Bearer $token")
  [[ -n "$corpo" ]] && args+=(-d "$corpo")
  STATUS="$(curl "${args[@]}")"
  CORPO="$(cat "$ARQ")"
}
campo() { jq -r "$1" <<<"$CORPO"; }
esperar() { [[ "$STATUS" == "$1" ]] || falha "$2 (esperado $1, obtido $STATUS)"; echo "ok ($STATUS) $2"; }

passo "1. Aplicação saudável"
chamar GET /actuator/health; esperar 200 "health"

passo "2-3. Autenticar administrador"
chamar POST /auth/login "" "{\"login\":\"$ADMIN_LOGIN\",\"senha\":\"$ADMIN_PASSWORD\"}"; esperar 200 "login admin"
LOGIN="$CORPO"
ADMIN="$(jq -r .accessToken <<<"$LOGIN")"

passo "4. Cadastrar professor e alunos"
chamar POST /professores "$ADMIN" "{\"nome\":\"Prof Smoke\",\"email\":\"prof$SUFIXO@smoke.test\",\"registro\":\"PS-$SUFIXO\"}"; esperar 201 "professor"
PROF_ID="$(campo .id)"
chamar POST /alunos "$ADMIN" "{\"nome\":\"Aluno Smoke\",\"email\":\"aluno$SUFIXO@smoke.test\",\"matricula\":\"AS-$SUFIXO\"}"; esperar 201 "aluno"
ALUNO_ID="$(campo .id)"
chamar POST /alunos "$ADMIN" "{\"nome\":\"Outro Smoke\",\"email\":\"outro$SUFIXO@smoke.test\",\"matricula\":\"OS-$SUFIXO\"}"; esperar 201 "outro aluno"
OUTRO_ID="$(campo .id)"

passo "5. Criar e vincular contas"
chamar POST /usuarios "$ADMIN" "{\"login\":\"prof$SUFIXO\",\"senha\":\"Professor2026\",\"perfil\":\"PROFESSOR\",\"professorId\":\"$PROF_ID\"}"; esperar 201 "conta professor"
chamar POST /usuarios "$ADMIN" "{\"login\":\"aluno$SUFIXO\",\"senha\":\"Aluno2026xx\",\"perfil\":\"ALUNO\",\"alunoId\":\"$ALUNO_ID\"}"; esperar 201 "conta aluno"

passo "6. Criar disciplina e associar professor"
chamar POST /disciplinas "$ADMIN" "{\"nome\":\"Smoke I\",\"codigo\":\"SMK-$SUFIXO\",\"cargaHoraria\":60}"; esperar 201 "disciplina"
DISC_ID="$(campo .id)"
chamar PUT "/disciplinas/$DISC_ID/professor" "$ADMIN" "{\"professorId\":\"$PROF_ID\"}"; esperar 200 "associar professor"

passo "7. Matricular aluno"
chamar POST /matriculas "$ADMIN" "{\"alunoId\":\"$ALUNO_ID\",\"disciplinaId\":\"$DISC_ID\"}"; esperar 201 "matrícula"
MAT_ID="$(campo .id)"
chamar POST /matriculas "$ADMIN" "{\"alunoId\":\"$ALUNO_ID\",\"disciplinaId\":\"$DISC_ID\"}"; esperar 409 "matrícula duplicada bloqueada"

passo "8. Professor lança notas"
chamar POST /auth/login "" "{\"login\":\"prof$SUFIXO\",\"senha\":\"Professor2026\"}"; esperar 200 "login professor"
PROF="$(campo .accessToken)"
chamar PATCH "/matriculas/atualizar-notas/$MAT_ID" "$PROF" '{"nota1":8.0,"nota2":7.5}'; esperar 200 "lançar notas"
NOTAS="$CORPO"
[[ "$(jq -r .status <<<"$NOTAS")" == "APROVADO" ]] || falha "status esperado APROVADO"
echo "média: $(jq -r .media <<<"$NOTAS")"

passo "9. Aluno consulta o próprio histórico"
chamar POST /auth/login "" "{\"login\":\"aluno$SUFIXO\",\"senha\":\"Aluno2026xx\"}"; esperar 200 "login aluno"
LOGIN_ALUNO="$CORPO"
ALUNO="$(jq -r .accessToken <<<"$LOGIN_ALUNO")"
chamar GET "/matriculas/emitir-historico/$ALUNO_ID" "$ALUNO"; esperar 200 "histórico próprio"
jq -c '.disciplinas[] | {nomeDisciplina, media, status}' <<<"$CORPO"

passo "10. Bloqueios de acesso indevido"
chamar GET "/matriculas/emitir-historico/$OUTRO_ID" "$ALUNO"; esperar 403 "aluno lendo histórico alheio"
chamar PATCH "/matriculas/atualizar-notas/$MAT_ID" "$ALUNO" '{"nota1":10}'; esperar 403 "aluno alterando nota"
chamar POST /usuarios "$PROF" '{"login":"intruso","senha":"Intruso20266","perfil":"ADMIN"}'; esperar 403 "professor criando admin"
chamar GET /alunos ""; esperar 401 "sem token"

passo "11. Renovar e encerrar sessão"
chamar POST /auth/refresh "" "{\"refreshToken\":\"$(jq -r .refreshToken <<<"$LOGIN_ALUNO")\"}"; esperar 200 "refresh"
RENOVADO="$CORPO"
chamar POST /auth/refresh "" "{\"refreshToken\":\"$(jq -r .refreshToken <<<"$LOGIN_ALUNO")\"}"; esperar 401 "reutilização do refresh antigo"
chamar POST /auth/logout "" "{\"refreshToken\":\"$(jq -r .refreshToken <<<"$RENOVADO")\"}"; esperar 204 "logout"

printf '\nFluxo integrado concluído com sucesso.\n'
