package br.com.campusapi.usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Conta de acesso. O vínculo com aluno/professor é guardado por id (com FK no banco) e só pode ser
 * alterado pelo administrador. {@code versaoCredenciais} é gravada no access token e incrementada sempre
 * que tokens já emitidos devem deixar de valer.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String login;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Perfil perfil;

    @Column(nullable = false)
    private boolean ativo;

    @Column(name = "aluno_id")
    private UUID alunoId;

    @Column(name = "professor_id")
    private UUID professorId;

    @Column(name = "versao_credenciais", nullable = false)
    private long versaoCredenciais;

    @Version
    @Column(nullable = false)
    private Long versao;

    @Column(nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(nullable = false)
    private Instant atualizadoEm;

    public Usuario(String login, String senhaHash, Perfil perfil, UUID alunoId, UUID professorId) {
        this.login = login;
        this.senhaHash = senhaHash;
        this.ativo = true;
        definirPerfil(perfil, alunoId, professorId);
    }

    public void definirPerfil(Perfil perfil, UUID alunoId, UUID professorId) {
        this.perfil = perfil;
        this.alunoId = perfil == Perfil.ALUNO ? alunoId : null;
        this.professorId = perfil == Perfil.PROFESSOR ? professorId : null;
    }

    public void alterarSenha(String novoHash) {
        this.senhaHash = novoHash;
    }

    public void ativar() {
        this.ativo = true;
    }

    public void desativar() {
        this.ativo = false;
    }

    /** Invalida imediatamente todos os access tokens emitidos para este usuário. */
    public void invalidarTokensEmitidos() {
        this.versaoCredenciais++;
    }

    @PrePersist
    void prePersist() {
        this.criadoEm = Instant.now();
        this.atualizadoEm = this.criadoEm;
    }

    @PreUpdate
    void preUpdate() {
        this.atualizadoEm = Instant.now();
    }
}
