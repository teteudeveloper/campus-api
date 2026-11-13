-- V4: tabelas de controle e de auditoria são somente leitura para qualquer papel que não seja o dono do schema.
-- O usuário de runtime da aplicação recebe DML por "default privileges" em toda tabela criada pelo dono,
-- inclusive flyway_schema_history; escrita nela permitiria adulterar o histórico de migrações.
DO $$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT DISTINCT g.grantee, g.table_name
          FROM information_schema.role_table_grants g
         WHERE g.table_schema = current_schema()
           AND g.table_name IN ('flyway_schema_history', 'alunos_disciplinas_legado', 'usuarios_legado')
           AND g.privilege_type IN ('INSERT', 'UPDATE', 'DELETE', 'TRUNCATE')
           AND g.grantee <> current_user
           AND g.grantee <> (SELECT tableowner FROM pg_tables WHERE schemaname = current_schema() AND tablename = g.table_name)
    LOOP
        EXECUTE format('REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON %I FROM %I', r.table_name, r.grantee);
    END LOOP;
END
$$;
