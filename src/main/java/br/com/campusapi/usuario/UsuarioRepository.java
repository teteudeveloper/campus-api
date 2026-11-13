package br.com.campusapi.usuario;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, UUID>, JpaSpecificationExecutor<Usuario> {

    Optional<Usuario> findByLogin(String login);

    /** Bloqueia as contas ADMIN ativas (ordem estável evita deadlock) para serializar rebaixamentos/desativações. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.perfil = br.com.campusapi.usuario.Perfil.ADMIN and u.ativo = true order by u.id")
    List<Usuario> bloquearAdminsAtivos();

    boolean existsByLogin(String login);

    boolean existsByPerfil(Perfil perfil);

    boolean existsByAlunoId(UUID alunoId);

    boolean existsByProfessorId(UUID professorId);

    boolean existsByAlunoIdAndIdNot(UUID alunoId, UUID id);

    boolean existsByProfessorIdAndIdNot(UUID professorId, UUID id);
}
