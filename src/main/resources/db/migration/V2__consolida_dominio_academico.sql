-- V2: consolida o domínio acadêmico.
--
-- 1. Pré-verificação: detecta dados que não podem ser convertidos sem decisão humana e aborta a migração
--    inteira (a migração roda em uma transação; nada é alterado). Consulte "Migrações" no README.
-- 2. Normaliza e-mails (minúsculas), identificadores (maiúsculas) e espaços.
-- 3. Substitui as constraints com nomes gerados pelo Hibernate por constraints nomeadas.
-- 4. Converte notas para numeric(4,2) e torna obrigatórios os vínculos da matrícula.
-- 5. Reconcilia alunos_disciplinas -> matriculas (MatriculaAluno passa a ser a única fonte de verdade).
--    A tabela antiga é preservada como alunos_disciplinas_legado para auditoria.

-- Impede escritas concorrentes (ex.: instância antiga da API ainda ativa) entre a pré-verificação e as constraints.
LOCK TABLE alunos, professores, disciplinas, matriculas, alunos_disciplinas IN SHARE ROW EXCLUSIVE MODE;

DO $$
DECLARE
    problemas text := '';
    qtd bigint;
BEGIN
    SELECT count(*) INTO qtd FROM matriculas
     WHERE aluno_id IS NULL OR disciplina_id IS NULL OR status IS NULL;
    IF qtd > 0 THEN
        problemas := problemas || format(E'\n- %s matrícula(s) sem aluno_id, disciplina_id ou status', qtd);
    END IF;

    SELECT count(*) INTO qtd FROM matriculas
     WHERE status IS NOT NULL AND status NOT IN ('MATRICULADO', 'APROVADO', 'REPROVADO', 'TRANCADO');
    IF qtd > 0 THEN
        problemas := problemas || format(E'\n- %s matrícula(s) com status desconhecido', qtd);
    END IF;

    SELECT count(*) INTO qtd FROM matriculas m
     WHERE (m.aluno_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM alunos a WHERE a.id = m.aluno_id))
        OR (m.disciplina_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM disciplinas d WHERE d.id = m.disciplina_id));
    IF qtd > 0 THEN
        problemas := problemas || format(E'\n- %s matrícula(s) apontando para aluno ou disciplina inexistente', qtd);
    END IF;

    SELECT count(*) INTO qtd FROM disciplinas d
     WHERE d.professor_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM professores p WHERE p.id = d.professor_id);
    IF qtd > 0 THEN
        problemas := problemas || format(E'\n- %s disciplina(s) apontando para professor inexistente', qtd);
    END IF;

    SELECT count(*) INTO qtd FROM (
        SELECT aluno_id, disciplina_id FROM matriculas
         WHERE aluno_id IS NOT NULL AND disciplina_id IS NOT NULL
         GROUP BY aluno_id, disciplina_id HAVING count(*) > 1) d;
    IF qtd > 0 THEN
        problemas := problemas || format(E'\n- %s par(es) aluno/disciplina com matrículas duplicadas', qtd);
    END IF;

    SELECT count(*) INTO qtd FROM matriculas
     WHERE (nota1 IS NOT NULL AND (nota1 < 0 OR nota1 > 10 OR nota1::numeric <> round(nota1::numeric, 2)))
        OR (nota2 IS NOT NULL AND (nota2 < 0 OR nota2 > 10 OR nota2::numeric <> round(nota2::numeric, 2)));
    IF qtd > 0 THEN
        problemas := problemas || format(E'\n- %s matrícula(s) com nota fora de 0..10 ou com mais de 2 casas decimais', qtd);
    END IF;

    SELECT count(*) INTO qtd FROM matriculas
     WHERE (status IN ('APROVADO', 'REPROVADO') AND (nota1 IS NULL OR nota2 IS NULL))
        OR (status = 'MATRICULADO' AND nota1 IS NOT NULL AND nota2 IS NOT NULL)
        OR (status = 'APROVADO' AND (nota1::numeric + nota2::numeric) / 2 < 7.0)
        OR (status = 'REPROVADO' AND (nota1::numeric + nota2::numeric) / 2 >= 7.0);
    IF qtd > 0 THEN
        problemas := problemas || format(E'\n- %s matrícula(s) com status incoerente com as notas', qtd);
    END IF;

    SELECT count(*) INTO qtd FROM (
        SELECT lower(btrim(email)) FROM alunos GROUP BY 1 HAVING count(*) > 1) d;
    IF qtd > 0 THEN
        problemas := problemas || format(E'\n- %s e-mail(s) de aluno duplicado(s) ignorando maiúsculas/espaços', qtd);
    END IF;

    SELECT count(*) INTO qtd FROM (
        SELECT upper(btrim(matricula)) FROM alunos GROUP BY 1 HAVING count(*) > 1) d;
    IF qtd > 0 THEN
        problemas := problemas || format(E'\n- %s número(s) de matrícula de aluno duplicado(s) ignorando maiúsculas/espaços', qtd);
    END IF;

    SELECT count(*) INTO qtd FROM (
        SELECT lower(btrim(email)) FROM professores GROUP BY 1 HAVING count(*) > 1) d;
    IF qtd > 0 THEN
        problemas := problemas || format(E'\n- %s e-mail(s) de professor duplicado(s) ignorando maiúsculas/espaços', qtd);
    END IF;

    SELECT count(*) INTO qtd FROM (
        SELECT upper(btrim(registro)) FROM professores GROUP BY 1 HAVING count(*) > 1) d;
    IF qtd > 0 THEN
        problemas := problemas || format(E'\n- %s registro(s) de professor duplicado(s) ignorando maiúsculas/espaços', qtd);
    END IF;

    SELECT count(*) INTO qtd FROM (
        SELECT upper(btrim(codigo)) FROM disciplinas GROUP BY 1 HAVING count(*) > 1) d;
    IF qtd > 0 THEN
        problemas := problemas || format(E'\n- %s código(s) de disciplina duplicado(s) ignorando maiúsculas/espaços', qtd);
    END IF;

    SELECT count(*) INTO qtd FROM disciplinas WHERE carga_horaria IS NOT NULL AND carga_horaria <= 0;
    IF qtd > 0 THEN
        problemas := problemas || format(E'\n- %s disciplina(s) com carga horária menor ou igual a zero', qtd);
    END IF;

    IF problemas <> '' THEN
        RAISE EXCEPTION 'Migração V2 abortada: dados legados exigem correção manual antes da consolidação:%', problemas
            USING HINT = 'Nenhuma alteração foi aplicada. Veja a seção "Migrações > Conflitos" do README para as consultas de diagnóstico.';
    END IF;
END
$$;

-- Normalização de dados existentes
-- (somente linhas que de fato mudam, evitando reescrever tabelas inteiras)
UPDATE alunos SET email = lower(btrim(email)),
                  matricula = upper(btrim(matricula)),
                  nome = regexp_replace(btrim(nome), '\s+', ' ', 'g')
 WHERE email IS DISTINCT FROM lower(btrim(email))
    OR matricula IS DISTINCT FROM upper(btrim(matricula))
    OR nome IS DISTINCT FROM regexp_replace(btrim(nome), '\s+', ' ', 'g');
UPDATE professores SET email = lower(btrim(email)),
                       registro = upper(btrim(registro)),
                       nome = regexp_replace(btrim(nome), '\s+', ' ', 'g'),
                       especialidade = nullif(regexp_replace(btrim(especialidade), '\s+', ' ', 'g'), '')
 WHERE email IS DISTINCT FROM lower(btrim(email))
    OR registro IS DISTINCT FROM upper(btrim(registro))
    OR nome IS DISTINCT FROM regexp_replace(btrim(nome), '\s+', ' ', 'g')
    OR especialidade IS DISTINCT FROM nullif(regexp_replace(btrim(especialidade), '\s+', ' ', 'g'), '');
UPDATE disciplinas SET codigo = upper(btrim(codigo)),
                       nome = regexp_replace(btrim(nome), '\s+', ' ', 'g')
 WHERE codigo IS DISTINCT FROM upper(btrim(codigo))
    OR nome IS DISTINCT FROM regexp_replace(btrim(nome), '\s+', ' ', 'g');

-- Remove apenas as constraints UNIQUE, FOREIGN KEY e CHECK geradas pelo Hibernate (nomes uk/fk + hash e
-- matriculas_status_check). Constraints criadas manualmente por um DBA são preservadas.
DO $$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT c.conrelid::regclass AS tabela, c.conname
          FROM pg_constraint c
          JOIN pg_class t ON t.oid = c.conrelid
          JOIN pg_namespace n ON n.oid = t.relnamespace
         WHERE n.nspname = current_schema()
           AND t.relname IN ('alunos', 'professores', 'disciplinas', 'matriculas', 'alunos_disciplinas')
           AND c.contype IN ('u', 'f', 'c')
           AND (c.conname ~ '^(uk|fk)[0-9a-z]{20,30}$' OR c.conname = 'matriculas_status_check')
         ORDER BY CASE c.contype WHEN 'f' THEN 0 ELSE 1 END
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', r.tabela, r.conname);
    END LOOP;
END
$$;

-- Controle de concorrência otimista
ALTER TABLE alunos      ADD COLUMN versao bigint NOT NULL DEFAULT 0;
ALTER TABLE professores ADD COLUMN versao bigint NOT NULL DEFAULT 0;
ALTER TABLE disciplinas ADD COLUMN versao bigint NOT NULL DEFAULT 0;

-- Matrículas: tipos, obrigatoriedade, auditoria
ALTER TABLE matriculas
    ALTER COLUMN nota1 TYPE numeric(4, 2) USING round(nota1::numeric, 2),
    ALTER COLUMN nota2 TYPE numeric(4, 2) USING round(nota2::numeric, 2),
    ALTER COLUMN status TYPE varchar(20),
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN aluno_id SET NOT NULL,
    ALTER COLUMN disciplina_id SET NOT NULL,
    ADD COLUMN versao bigint NOT NULL DEFAULT 0,
    ADD COLUMN criado_em timestamp(6),
    ADD COLUMN atualizado_em timestamp(6),
    ADD COLUMN origem varchar(20) NOT NULL DEFAULT 'API';

COMMENT ON COLUMN matriculas.origem IS
    'API: criada pela aplicação. MIGRACAO_LEGADO: reconciliada a partir de alunos_disciplinas na V2 (sem notas).';
COMMENT ON COLUMN matriculas.criado_em IS 'Nulo para matrículas anteriores à V2 (data original desconhecida).';

-- Reconciliação: todo vínculo de alunos_disciplinas sem matrícula correspondente vira matrícula MATRICULADO, sem notas.
-- Vínculos que apontam para aluno/disciplina inexistentes não são migrados e permanecem na tabela legada.
INSERT INTO matriculas (id, aluno_id, disciplina_id, status, versao, origem)
SELECT gen_random_uuid(), ad.aluno_id, ad.disciplina_id, 'MATRICULADO', 0, 'MIGRACAO_LEGADO'
  FROM alunos_disciplinas ad
  JOIN alunos a ON a.id = ad.aluno_id
  JOIN disciplinas d ON d.id = ad.disciplina_id
 WHERE NOT EXISTS (SELECT 1 FROM matriculas m
                    WHERE m.aluno_id = ad.aluno_id AND m.disciplina_id = ad.disciplina_id);

DO $$
DECLARE
    orfaos bigint;
BEGIN
    SELECT count(*) INTO orfaos FROM alunos_disciplinas ad
     WHERE NOT EXISTS (SELECT 1 FROM alunos a WHERE a.id = ad.aluno_id)
        OR NOT EXISTS (SELECT 1 FROM disciplinas d WHERE d.id = ad.disciplina_id);
    IF orfaos > 0 THEN
        RAISE NOTICE '% vínculo(s) de alunos_disciplinas apontam para registros inexistentes e permanecem apenas em alunos_disciplinas_legado', orfaos;
    END IF;
END
$$;

ALTER TABLE alunos_disciplinas RENAME TO alunos_disciplinas_legado;
COMMENT ON TABLE alunos_disciplinas_legado IS
    'Somente leitura. Vínculos N:N anteriores à V2, já reconciliados em matriculas. Pode ser removida após auditoria.';

-- Constraints nomeadas
ALTER TABLE alunos
    ADD CONSTRAINT uk_alunos_email UNIQUE (email),
    ADD CONSTRAINT uk_alunos_matricula UNIQUE (matricula);

ALTER TABLE professores
    ADD CONSTRAINT uk_professores_email UNIQUE (email),
    ADD CONSTRAINT uk_professores_registro UNIQUE (registro);

ALTER TABLE disciplinas
    ADD CONSTRAINT uk_disciplinas_codigo UNIQUE (codigo),
    ADD CONSTRAINT ck_disciplinas_carga_horaria CHECK (carga_horaria IS NULL OR carga_horaria > 0),
    ADD CONSTRAINT fk_disciplinas_professor FOREIGN KEY (professor_id) REFERENCES professores (id) ON DELETE RESTRICT;

ALTER TABLE matriculas
    ADD CONSTRAINT uk_matriculas_aluno_disciplina UNIQUE (aluno_id, disciplina_id),
    ADD CONSTRAINT fk_matriculas_aluno FOREIGN KEY (aluno_id) REFERENCES alunos (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_matriculas_disciplina FOREIGN KEY (disciplina_id) REFERENCES disciplinas (id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_matriculas_status CHECK (status IN ('MATRICULADO', 'APROVADO', 'REPROVADO', 'TRANCADO')),
    ADD CONSTRAINT ck_matriculas_nota1 CHECK (nota1 IS NULL OR nota1 BETWEEN 0 AND 10),
    ADD CONSTRAINT ck_matriculas_nota2 CHECK (nota2 IS NULL OR nota2 BETWEEN 0 AND 10),
    ADD CONSTRAINT ck_matriculas_status_notas CHECK (
        status NOT IN ('APROVADO', 'REPROVADO') OR (nota1 IS NOT NULL AND nota2 IS NOT NULL)),
    ADD CONSTRAINT ck_matriculas_origem CHECK (origem IN ('API', 'MIGRACAO_LEGADO'));

CREATE INDEX idx_disciplinas_professor_id ON disciplinas (professor_id);
CREATE INDEX idx_matriculas_disciplina_id ON matriculas (disciplina_id);
