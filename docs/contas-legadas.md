# Transição das contas legadas (`usuarios_legado`)

## O que existe

Bancos criados antes desta versão podem conter uma tabela `usuarios` criada fora do código atual (não consta no
histórico do repositório). A migração V3 **preserva** essa tabela como `usuarios_legado`; nada é apagado.

Estrutura real encontrada: `id uuid`, `email varchar(150)`, `role varchar(20)` (check `USER`/`ADMIN`),
`senha varchar(255)`. Relatório agregado do banco local (sem dados pessoais):

| Indicador | Resultado |
|---|---|
| contas | 7 |
| `role` | `USER` = 7 |
| formato do hash | BCrypt sem prefixo (custo 10) = 7 |
| e-mails duplicados | 0 |
| e-mail igual ao de um aluno | 1 |
| e-mail igual ao de um professor | 0 |

Reproduza com `docker compose exec -T postgres psql -U campus_user -d campus_api -tA -F ' | ' < scripts/sql/contas-legadas-relatorio.sql`.

## Por que não há migração automática

- `USER` não corresponde a nenhum perfil atual (ADMIN, PROFESSOR, ALUNO). Atribuir ALUNO/PROFESSOR seria suposição;
  atribuir ADMIN seria escalada de privilégio.
- Perfis ALUNO e PROFESSOR exigem vínculo com um aluno/professor. Um e-mail igual é apenas indício, não prova de
  titularidade; 6 das 7 contas não têm nem esse indício.
- Os hashes têm formato BCrypt, mas não há como comprovar que foram gerados apenas com BCrypt sobre a senha (sem
  pepper ou pré-processamento) sem conhecer uma senha. Copiá-los não é seguro.

## Impacto

As contas antigas **não autenticam** na API atual (a tabela antiga não é usada pelo código). Os titulares só terão
acesso depois que o administrador recriar as contas.

## Procedimento (administrador)

1. Gere a lista nominal **somente para uso próprio** (contém e-mails):
   `docker compose exec -T postgres psql -U campus_user -d campus_api < scripts/sql/contas-legadas-candidatos.sql`
   ou abra o mesmo SQL no DBeaver.
2. Para cada conta, confirme por um canal confiável quem é o titular e qual papel deve ter. Não use apenas a
   coincidência de e-mail.
3. Se o titular for aluno ou professor ainda não cadastrado, cadastre-o (`POST /alunos` ou `POST /professores`).
4. Gere uma senha temporária **individual** para cada conta (nunca reutilize):
   `openssl rand -base64 18`
5. Crie a conta (`POST /usuarios`) com login, perfil e vínculo confirmados, por exemplo:
   ```json
   {"login": "<email-confirmado>", "senha": "<senha-temporaria-individual>", "perfil": "ALUNO", "alunoId": "<id-do-aluno>"}
   ```
6. Entregue a senha temporária por canal seguro e peça ao titular que a troque no primeiro acesso
   (`PUT /auth/senha`). **A API não obriga essa troca** (não há marcação de senha temporária); acompanhe a
   orientação manualmente. Se a senha temporária vazar, use `PUT /usuarios/{id}/senha` para gerar outra.
7. Contas sem titular confirmado não devem ser recriadas.
8. Acompanhe o progresso com o relatório (`ja_recriadas_com_mesmo_login`).

`usuarios_legado` deve ser mantida até a conclusão e a conferência; a remoção futura deve ser feita por uma nova
migração, após backup.
