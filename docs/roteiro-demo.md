# Roteiro de demonstração ao vivo (Swagger UI)

A demonstração usa um banco **separado** (`campus_api_demo`), que começa vazio, apenas com o administrador. O banco
principal (`campus_api`) e seus dados não são alterados.

## Preparar (antes da apresentação)

```bash
./scripts/demo-iniciar.sh            # recria o banco de demonstração vazio e sobe a API apontando para ele
grep '^BOOTSTRAP_ADMIN_PASSWORD=' .env | cut -d= -f2-   # senha do admin (não exiba na tela)
```

Abra <http://localhost:8080/swagger-ui.html>. Nesse modo os campos já vêm editáveis (sem clicar em *Try it out*),
cada operação de criação tem exemplos prontos na lista **Examples** e o token autorizado continua salvo ao
recarregar a página. As seções aparecem na ordem deste roteiro.

Para repetir a apresentação do zero, rode `./scripts/demo-iniciar.sh` de novo. Ao terminar:
`./scripts/demo-encerrar.sh` (volta a API para o banco principal; `--remover-banco` apaga o banco de demonstração).

## Passo a passo

Em cada passo: escolha o exemplo indicado, clique em **Execute** e mostre a resposta. Onde houver
`COLE-AQUI-...`, substitua pelo `id` retornado no passo anterior.

| # | Seção / operação | Exemplo | O que mostrar |
|---|---|---|---|
| 1 | Matrículas → `GET /matriculas` | — | sem token: **401** com `codigo: NAO_AUTENTICADO` |
| 2 | Autenticação → `POST /auth/login` | *1. Administrador* (digite a senha do admin) | 200 com `accessToken` e `refreshToken` |
| 3 | botão **Authorize** | cole só o `accessToken` | cadeados fechados |
| 4 | Professores → `POST /professores` | *Carlos Mendes* | 201; copie o `id` (professor) |
| 5 | Alunos → `POST /alunos` | *Lucas Oliveira* e depois *Mariana Costa* | 201; copie os dois `id` |
| 6 | Disciplinas → `POST /disciplinas` | *Com professor responsável* | execute uma vez **sem** trocar o marcador → 400 apontando `professorId`; troque pelo id do professor → 201; copie o `id` |
| 7 | Usuários → `POST /usuarios` | *Conta de professor*, *Conta de aluno*, *Conta de outra aluna* | 201 com perfil e vínculo; a senha nunca aparece na resposta |
| 8 | Matrículas → `POST /matriculas` | *Matricular aluno na disciplina* (ids do Lucas e da disciplina) | 201 com status `MATRICULADO`; execute de novo → **409** `MATRICULA_DUPLICADA` |
| 9 | Autenticação → `POST /auth/login` | *2. Professor* | copie o token e troque em **Authorize** (Logout → cole o novo) |
| 10 | Matrículas → `PATCH /matriculas/atualizar-notas/{id}` | *Só a nota 1* | 200, continua `MATRICULADO`, `media` nula |
| 11 | mesma operação | *Inválida (nota 11 → 400)* | 400 com o campo `nota1` |
| 12 | mesma operação | *Reprovado*, *Média exatamente 7,0*, *Aprovado* | status recalculado: REPROVADO (5,5) → APROVADO (7,0) → APROVADO (7,75) |
| 13 | Autenticação → `POST /auth/login` | *3. Aluno* | troque o token em **Authorize** |
| 14 | Matrículas → `GET /matriculas/emitir-historico/{alunoId}` | id do Lucas | 200 com a disciplina, notas, média e APROVADO |
| 15 | mesma operação | id da Mariana | **403** `ACESSO_NEGADO`: o aluno só vê o próprio histórico |
| 16 | `PATCH /matriculas/atualizar-notas/{id}` | *Aprovado* | **403**: aluno não altera notas |

Opcional: `POST /auth/refresh` com o `refreshToken` (renova) e repetir o mesmo token (401, sessão encerrada por
reutilização); `POST /auth/logout`.

As senhas dos exemplos (`Professor2026`, `Aluno2026x`, `Aluna2026x`) existem apenas no banco de demonstração,
porque as contas são criadas durante o roteiro.
