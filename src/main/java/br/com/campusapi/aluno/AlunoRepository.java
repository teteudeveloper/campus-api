package br.com.campusapi.aluno;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AlunoRepository extends JpaRepository<Aluno, UUID>, JpaSpecificationExecutor<Aluno> {

    boolean existsByEmail(String email);

    boolean existsByMatricula(String matricula);

    boolean existsByEmailAndIdNot(String email, UUID id);

    boolean existsByMatriculaAndIdNot(String matricula, UUID id);
}
