-- V1: schema equivalente ao que o Hibernate gerava com ddl-auto=update antes da adoção do Flyway.
--
-- Banco vazio: cria as tabelas no formato legado; a V2 aplica constraints, tipos e regras atuais.
-- Banco existente (criado pelo Hibernate): todos os comandos são idempotentes (IF NOT EXISTS),
-- portanto nada é recriado nem descartado. Para adotar um banco existente, execute uma única vez com
-- FLYWAY_BASELINE_ON_MIGRATE=true (baseline na versão 0), conforme o README. A V1 roda normalmente
-- depois do baseline, e nenhuma migração é pulada.

CREATE TABLE IF NOT EXISTS professores (
    id            uuid         NOT NULL PRIMARY KEY,
    atualizado_em timestamp(6),
    criado_em     timestamp(6),
    email         varchar(100) NOT NULL,
    especialidade varchar(100),
    nome          varchar(100) NOT NULL,
    registro      varchar(20)  NOT NULL
);

CREATE TABLE IF NOT EXISTS alunos (
    id            uuid         NOT NULL PRIMARY KEY,
    atualizado_em timestamp(6),
    criado_em     timestamp(6),
    email         varchar(100) NOT NULL,
    matricula     varchar(20)  NOT NULL,
    nome          varchar(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS disciplinas (
    id            uuid         NOT NULL PRIMARY KEY,
    atualizado_em timestamp(6),
    carga_horaria integer,
    codigo        varchar(20)  NOT NULL,
    criado_em     timestamp(6),
    nome          varchar(100) NOT NULL,
    professor_id  uuid
);

CREATE TABLE IF NOT EXISTS alunos_disciplinas (
    disciplina_id uuid NOT NULL,
    aluno_id      uuid NOT NULL,
    PRIMARY KEY (disciplina_id, aluno_id)
);

CREATE TABLE IF NOT EXISTS matriculas (
    id            uuid             NOT NULL PRIMARY KEY,
    nota1         double precision,
    nota2         double precision,
    status        varchar(255),
    aluno_id      uuid,
    disciplina_id uuid
);
