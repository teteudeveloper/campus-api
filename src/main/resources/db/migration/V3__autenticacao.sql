-- V3: usuários, perfis e refresh tokens.
--
-- Bancos existentes podem ter uma tabela "usuarios" criada fora desta aplicação (ex.: experimento anterior com
-- colunas email/role/senha). Ela não é descartada nem convertida: contas sem vínculo controlado com aluno/professor
-- e com perfis desconhecidos não podem ser migradas sem inventar dados. A tabela é preservada como usuarios_legado
-- e as contas devem ser recriadas pelo administrador (ver README > Migrações).
DO $$
BEGIN
    IF to_regclass('usuarios') IS NOT NULL THEN
        ALTER TABLE usuarios RENAME TO usuarios_legado;
        IF to_regclass('usuarios_pkey') IS NOT NULL THEN
            ALTER INDEX usuarios_pkey RENAME TO usuarios_legado_pkey;
        END IF;
        COMMENT ON TABLE usuarios_legado IS
            'Somente leitura. Tabela "usuarios" pré-existente, preservada na V3; contas não migradas automaticamente.';
        RAISE NOTICE 'Tabela usuarios pré-existente preservada como usuarios_legado (% linha(s))',
            (SELECT count(*) FROM usuarios_legado);
    END IF;
    IF to_regclass('refresh_tokens') IS NOT NULL THEN
        RAISE EXCEPTION 'Tabela refresh_tokens pré-existente encontrada; remova-a ou renomeie-a antes de migrar (ver README)';
    END IF;
END
$$;

CREATE TABLE usuarios (
    id                 uuid         NOT NULL PRIMARY KEY,
    login              varchar(100) NOT NULL,
    senha_hash         varchar(255) NOT NULL,
    perfil             varchar(20)  NOT NULL,
    ativo              boolean      NOT NULL DEFAULT true,
    aluno_id           uuid,
    professor_id       uuid,
    versao_credenciais bigint       NOT NULL DEFAULT 0,
    versao             bigint       NOT NULL DEFAULT 0,
    criado_em          timestamptz  NOT NULL,
    atualizado_em      timestamptz  NOT NULL,
    CONSTRAINT uk_usuarios_login UNIQUE (login),
    CONSTRAINT uk_usuarios_aluno UNIQUE (aluno_id),
    CONSTRAINT uk_usuarios_professor UNIQUE (professor_id),
    CONSTRAINT fk_usuarios_aluno FOREIGN KEY (aluno_id) REFERENCES alunos (id) ON DELETE RESTRICT,
    CONSTRAINT fk_usuarios_professor FOREIGN KEY (professor_id) REFERENCES professores (id) ON DELETE RESTRICT,
    CONSTRAINT ck_usuarios_login_normalizado CHECK (login = lower(btrim(login)) AND length(login) >= 3),
    CONSTRAINT ck_usuarios_perfil CHECK (perfil IN ('ADMIN', 'PROFESSOR', 'ALUNO')),
    CONSTRAINT ck_usuarios_vinculo CHECK (
        (perfil = 'ADMIN' AND aluno_id IS NULL AND professor_id IS NULL)
        OR (perfil = 'PROFESSOR' AND professor_id IS NOT NULL AND aluno_id IS NULL)
        OR (perfil = 'ALUNO' AND aluno_id IS NOT NULL AND professor_id IS NULL))
);

COMMENT ON COLUMN usuarios.versao_credenciais IS
    'Incrementada em troca de senha, desativação e mudança de perfil/vínculo; invalida access tokens já emitidos.';

CREATE TABLE refresh_tokens (
    id                  uuid        NOT NULL PRIMARY KEY,
    usuario_id          uuid        NOT NULL,
    familia_id          uuid        NOT NULL,
    versao_credenciais  bigint      NOT NULL,
    token_hash          varchar(64) NOT NULL,
    criado_em           timestamptz NOT NULL,
    expira_em           timestamptz NOT NULL,
    revogado_em         timestamptz,
    motivo_revogacao    varchar(30),
    substituido_por_id  uuid,
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE,
    CONSTRAINT ck_refresh_tokens_motivo CHECK (motivo_revogacao IS NULL OR motivo_revogacao IN
        ('ROTACIONADO', 'LOGOUT', 'REUTILIZACAO_DETECTADA', 'SENHA_ALTERADA', 'USUARIO_DESATIVADO',
         'PERFIL_ALTERADO', 'SENHA_REDEFINIDA')),
    CONSTRAINT ck_refresh_tokens_revogacao CHECK ((revogado_em IS NULL) = (motivo_revogacao IS NULL))
);

COMMENT ON COLUMN refresh_tokens.versao_credenciais IS
    'Versão das credenciais do usuário na emissão; divergência com usuarios.versao_credenciais invalida o token.';
COMMENT ON COLUMN refresh_tokens.token_hash IS 'SHA-256 (hex) do refresh token; o valor em claro nunca é persistido.';

CREATE INDEX idx_refresh_tokens_usuario ON refresh_tokens (usuario_id);
CREATE INDEX idx_refresh_tokens_usuario_ativos ON refresh_tokens (usuario_id) WHERE revogado_em IS NULL;
CREATE INDEX idx_refresh_tokens_familia ON refresh_tokens (familia_id);
CREATE INDEX idx_refresh_tokens_expira_em ON refresh_tokens (expira_em);
