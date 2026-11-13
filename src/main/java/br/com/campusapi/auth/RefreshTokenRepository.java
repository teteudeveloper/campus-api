package br.com.campusapi.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /** Bloqueio pessimista: duas renovações simultâneas com o mesmo token não geram dois sucessores. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefreshToken r where r.tokenHash = :hash")
    Optional<RefreshToken> findParaAtualizacaoByTokenHash(@Param("hash") String hash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RefreshToken r set r.revogadoEm = :agora, r.motivoRevogacao = :motivo
             where r.usuarioId = :usuarioId and r.revogadoEm is null""")
    int revogarTodosDoUsuario(@Param("usuarioId") UUID usuarioId, @Param("motivo") MotivoRevogacao motivo,
                              @Param("agora") Instant agora);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RefreshToken r set r.revogadoEm = :agora, r.motivoRevogacao = :motivo
             where r.familiaId = :familiaId and r.revogadoEm is null""")
    int revogarFamilia(@Param("familiaId") UUID familiaId, @Param("motivo") MotivoRevogacao motivo,
                       @Param("agora") Instant agora);

    @Modifying
    @Query("delete from RefreshToken r where r.expiraEm < :limite")
    int excluirExpiradosAntesDe(@Param("limite") Instant limite);

    long countByUsuarioIdAndRevogadoEmIsNull(UUID usuarioId);
}
