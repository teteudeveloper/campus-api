package br.com.campusapi.aluno;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AlunoRepository extends JpaRepository<Aluno, UUID> {

    Optional<Aluno> findByEmail(String email);
    Optional<Aluno> findByMatricula(String matricula);
    boolean existsByEmail(String email);
    boolean existsByMatricula(String matricula);
}

