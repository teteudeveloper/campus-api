package br.com.campusapi.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * Refresh token persistido apenas como hash SHA-256. Tokens emitidos a partir do mesmo login compartilham
 * a {@code familiaId}; a reapresentação de um token já rotacionado revoga a família inteira.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken implements Persistable<UUID> {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Column(name = "familia_id", nullable = false, updatable = false)
    private UUID familiaId;

    @Column(name = "versao_credenciais", nullable = false, updatable = false)
    private long versaoCredenciais;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64, updatable = false)
    private String tokenHash;

    @Column(nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(nullable = false, updatable = false)
    private Instant expiraEm;

    private Instant revogadoEm;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private MotivoRevogacao motivoRevogacao;

    @Column(name = "substituido_por_id")
    private UUID substituidoPorId;

    public RefreshToken(UUID usuarioId, UUID familiaId, long versaoCredenciais, String tokenHash, Instant criadoEm,
                        Instant expiraEm) {
        this.id = UUID.randomUUID();
        this.usuarioId = usuarioId;
        this.familiaId = familiaId;
        this.versaoCredenciais = versaoCredenciais;
        this.tokenHash = tokenHash;
        this.criadoEm = criadoEm;
        this.expiraEm = expiraEm;
    }

    /** Id atribuído pela aplicação: sinaliza inserção direta (persist), sem o SELECT prévio do merge. */
    @Transient
    private boolean novo = true;

    @Override
    public boolean isNew() {
        return novo;
    }

    @PostLoad
    @PostPersist
    void marcarPersistido() {
        this.novo = false;
    }

    public boolean isRevogado() {
        return revogadoEm != null;
    }

    public boolean isExpirado(Instant agora) {
        return !expiraEm.isAfter(agora);
    }

    public void revogar(MotivoRevogacao motivo, Instant agora, UUID substituidoPor) {
        if (revogadoEm == null) {
            this.revogadoEm = agora;
            this.motivoRevogacao = motivo;
            this.substituidoPorId = substituidoPor;
        }
    }
}
