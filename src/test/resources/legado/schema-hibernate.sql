-- Schema gerado pelo Hibernate (ddl-auto=update) na versão anterior ao Flyway (commit a3df980).
-- Capturado com pg_dump --schema-only; usado nos testes de adoção do Flyway em banco existente.
CREATE TABLE alunos (
    id uuid NOT NULL,
    atualizado_em timestamp(6) without time zone,
    criado_em timestamp(6) without time zone,
    email character varying(100) NOT NULL,
    matricula character varying(20) NOT NULL,
    nome character varying(100) NOT NULL
);
CREATE TABLE alunos_disciplinas (
    disciplina_id uuid NOT NULL,
    aluno_id uuid NOT NULL
);
CREATE TABLE disciplinas (
    id uuid NOT NULL,
    atualizado_em timestamp(6) without time zone,
    carga_horaria integer,
    codigo character varying(20) NOT NULL,
    criado_em timestamp(6) without time zone,
    nome character varying(100) NOT NULL,
    professor_id uuid
);
CREATE TABLE matriculas (
    id uuid NOT NULL,
    nota1 double precision,
    nota2 double precision,
    status character varying(255),
    aluno_id uuid,
    disciplina_id uuid,
    CONSTRAINT matriculas_status_check CHECK (((status)::text = ANY ((ARRAY['MATRICULADO'::character varying, 'APROVADO'::character varying, 'REPROVADO'::character varying, 'TRANCADO'::character varying])::text[])))
);
CREATE TABLE professores (
    id uuid NOT NULL,
    atualizado_em timestamp(6) without time zone,
    criado_em timestamp(6) without time zone,
    email character varying(100) NOT NULL,
    especialidade character varying(100),
    nome character varying(100) NOT NULL,
    registro character varying(20) NOT NULL
);
ALTER TABLE ONLY alunos_disciplinas
    ADD CONSTRAINT alunos_disciplinas_pkey PRIMARY KEY (disciplina_id, aluno_id);
ALTER TABLE ONLY alunos
    ADD CONSTRAINT alunos_pkey PRIMARY KEY (id);
ALTER TABLE ONLY disciplinas
    ADD CONSTRAINT disciplinas_pkey PRIMARY KEY (id);
ALTER TABLE ONLY matriculas
    ADD CONSTRAINT matriculas_pkey PRIMARY KEY (id);
ALTER TABLE ONLY professores
    ADD CONSTRAINT professores_pkey PRIMARY KEY (id);
ALTER TABLE ONLY alunos
    ADD CONSTRAINT uk28q7cqbm7sj0l004x4y0mok8d UNIQUE (matricula);
ALTER TABLE ONLY disciplinas
    ADD CONSTRAINT ukb5dnj2813xk9kctu9sxhm7uok UNIQUE (codigo);
ALTER TABLE ONLY professores
    ADD CONSTRAINT ukebvmg1b68uw6oxcpcuuh2xvaj UNIQUE (email);
ALTER TABLE ONLY professores
    ADD CONSTRAINT ukj2uv8e1uw6nsc2dqgntoy8e7g UNIQUE (registro);
ALTER TABLE ONLY alunos
    ADD CONSTRAINT ukmbvpvjdhy6gsmbw6odlro3ub UNIQUE (email);
ALTER TABLE ONLY matriculas
    ADD CONSTRAINT fk5swe8t3v00ykmemb4bquhkmhq FOREIGN KEY (disciplina_id) REFERENCES disciplinas(id);
ALTER TABLE ONLY disciplinas
    ADD CONSTRAINT fkd4380yuo3728b5u4qhfyi07g6 FOREIGN KEY (professor_id) REFERENCES professores(id);
ALTER TABLE ONLY alunos_disciplinas
    ADD CONSTRAINT fkoe6nma8tbucsgjnolgym806cb FOREIGN KEY (disciplina_id) REFERENCES disciplinas(id);
ALTER TABLE ONLY alunos_disciplinas
    ADD CONSTRAINT fkpi68m3ej1avbcvp5dab9bg5lv FOREIGN KEY (aluno_id) REFERENCES alunos(id);
ALTER TABLE ONLY matriculas
    ADD CONSTRAINT fkrwsrt4qf6q7fghkawume7g1ya FOREIGN KEY (aluno_id) REFERENCES alunos(id);
