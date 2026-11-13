-- LISTA NOMINAL para uso exclusivo do administrador (contém e-mails; não compartilhe a saída).
-- Somente leitura. A coincidência de e-mail é apenas um indício: confirme a identidade antes de criar o vínculo.
-- Uso no DBeaver (conectado como administrador) ou:
--   docker compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" < scripts/sql/contas-legadas-candidatos.sql
SELECT u.id                         AS id_legado,
       lower(btrim(u.email))        AS email_legado,
       u.role                       AS role_legado,
       a.id                         AS aluno_candidato,
       a.matricula                  AS matricula_candidata,
       p.id                         AS professor_candidato,
       p.registro                   AS registro_candidato,
       n.id IS NOT NULL             AS ja_recriada
  FROM usuarios_legado u
  LEFT JOIN alunos a      ON a.email = lower(btrim(u.email))
  LEFT JOIN professores p ON p.email = lower(btrim(u.email))
  LEFT JOIN usuarios n    ON n.login = lower(btrim(u.email))
 ORDER BY 2;
