package br.com.campusapi.matricula;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MatriculaAlunoRepository extends JpaRepository<MatriculaAluno, UUID> {

    List<MatriculaAluno> findByAlunoId(UUID alunoId);
}
