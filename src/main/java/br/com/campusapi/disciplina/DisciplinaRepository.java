package br.com.campusapi.disciplina;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DisciplinaRepository extends JpaRepository<Disciplina, UUID> {

    Optional<Disciplina> findByCodigo(String codigo);
    boolean existsByCodigo(String codigo);
}