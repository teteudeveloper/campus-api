-- Relatório AGREGADO das contas em usuarios_legado (sem e-mails, ids ou hashes). Somente leitura.
-- Uso: docker compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tA -F ' | ' < scripts/sql/contas-legadas-relatorio.sql
SELECT 'total_contas', count(*)::text FROM usuarios_legado;
SELECT 'role', role || '=' || count(*) FROM usuarios_legado GROUP BY role;
SELECT 'formato_hash', formato || '=' || count(*)
  FROM (SELECT CASE WHEN senha ~ '^\{bcrypt\}' THEN 'bcrypt-com-prefixo'
                    WHEN senha ~ '^\$2[aby]\$[0-9]{2}\$.{53}$' THEN 'bcrypt-sem-prefixo'
                    WHEN senha ~ '^\{' THEN 'outro-com-prefixo'
                    ELSE 'desconhecido' END AS formato
          FROM usuarios_legado) f
 GROUP BY formato;
SELECT 'emails_duplicados_normalizados', count(*)::text
  FROM (SELECT lower(btrim(email)) FROM usuarios_legado GROUP BY 1 HAVING count(*) > 1) d;
SELECT 'candidatas_por_email_aluno', count(*)::text
  FROM usuarios_legado u JOIN alunos a ON a.email = lower(btrim(u.email));
SELECT 'candidatas_por_email_professor', count(*)::text
  FROM usuarios_legado u JOIN professores p ON p.email = lower(btrim(u.email));
SELECT 'ja_recriadas_com_mesmo_login', count(*)::text
  FROM usuarios_legado u JOIN usuarios n ON n.login = lower(btrim(u.email));
