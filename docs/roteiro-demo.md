# Roteiro de demonstração ao vivo (Swagger UI)

A demonstração usa um banco **separado** (`campus_api_demo`), que começa vazio, apenas com um administrador próprio.
O banco principal (`campus_api`), seus dados e o administrador principal não são alterados.

## Preparar (antes da apresentação)

```bash
./scripts/demo-iniciar.sh     # recria o banco de demonstração vazio e sobe a API em modo demonstração
```

Abra **<http://localhost:8080/demo/index.html>**. É o mesmo Swagger UI da API, com três automatismos:

- os campos já vêm preenchidos com exemplos prontos e editáveis (basta clicar em **Execute**);
- os ids criados (professor, aluno, aluna, disciplina, matrícula) são guardados e entram sozinhos nas requisições
  seguintes, inclusive nos campos `{id}` e `{alunoId}`; a faixa no topo mostra os ids capturados;
- após cada login, o token é aplicado automaticamente no **Authorize**.

O exemplo *1. Administrador* já contém o login e a senha do administrador **de demonstração** (senha aleatória,
gerada a cada execução do script e salva em `.env.demo`; vale só para `campus_api_demo`).

Para repetir do zero: rode `./scripts/demo-iniciar.sh` de novo e clique em **Limpar ids e token** na página.
Ao terminar: `./scripts/demo-encerrar.sh` (volta a API para o banco principal; `--remover-banco` apaga o banco de
demonstração).

## Passo a passo (só escolher o exemplo e clicar em Execute)

| # | Seção → operação | Exemplo | Resultado esperado |
|---|---|---|---|
| 1 | Matrículas → `GET /matriculas` | — (antes do login) | **401** `NAO_AUTENTICADO` |
| 2 | Autenticação → `POST /auth/login` | *1. Administrador* | 200; token aplicado no Authorize |
| 3 | Professores → `POST /professores` | *Carlos Mendes* | 201 |
| 4 | Alunos → `POST /alunos` | *Lucas Oliveira*, depois *Mariana Costa* | 201, 201 |
| 5 | Disciplinas → `POST /disciplinas` | *Com professor responsável* | 201, já vinculada ao professor criado |
| 6 | Usuários → `POST /usuarios` | *Conta de professor*, *Conta de aluno*, *Conta de outra aluna* | 201 ×3; senha nunca aparece na resposta |
| 7 | Matrículas → `POST /matriculas` | *Matricular aluno na disciplina* | 201 `MATRICULADO`; executar de novo → **409** `MATRICULA_DUPLICADA` |
| 8 | Autenticação → `POST /auth/login` | *2. Professor* | 200; agora autenticado como professor |
| 9 | Matrículas → `PATCH /matriculas/atualizar-notas/{id}` | *Só a nota 1* | 200, continua `MATRICULADO`, média nula |
| 10 | mesma operação | *Inválida (nota 11 → 400)* | **400** apontando `nota1` |
| 11 | mesma operação | *Reprovado*, *Média exatamente 7,0*, *Aprovado* | REPROVADO 5,5 → APROVADO 7,0 → APROVADO 7,75 |
| 12 | Autenticação → `POST /auth/login` | *3. Aluno* | 200; autenticado como aluno |
| 13 | Matrículas → `GET /matriculas/emitir-historico/{alunoId}` | *Aluno Lucas (o próprio)* | 200 com notas, média e APROVADO |
| 14 | mesma operação | *Aluna Mariana (outra aluna)* | **403** `ACESSO_NEGADO` |
| 15 | `PATCH /matriculas/atualizar-notas/{id}` | *Aprovado* | **403**: aluno não altera notas |

Opcional: `POST /auth/refresh` e `POST /auth/logout`.

Ensaio automatizado em 17/11/2026: os 21 passos acima executados apenas com os exemplos, sem edição, produziram
exatamente os resultados esperados. A página `/demo` só é liberada com o modo demonstração ligado
(`DEMO_HABILITADA=true`, definido pelo script); fora dele, responde 401.

As senhas dos exemplos de professor e alunos (`Professor2026`, `Aluno2026x`, `Aluna2026x`) só existem no banco de
demonstração, porque as contas são criadas durante o roteiro.

## Se algo falhar

| Sintoma | Ação |
|---|---|
| `10-usuario-aplicacao.sh: No such file or directory` | a pasta `docker/` foi recriada por um checkout com o contêiner ligado: `docker compose up -d --force-recreate postgres` (dados preservados) e rode o script de novo |
| 401 no login do administrador | rode `./scripts/demo-iniciar.sh` de novo e recarregue a página (a senha muda a cada execução) |
| ids antigos na faixa do topo | clique em **Limpar ids e token** |
