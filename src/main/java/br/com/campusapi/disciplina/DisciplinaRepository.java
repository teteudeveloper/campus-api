package br.com.campusapi.disciplina;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DisciplinaRepository extends JpaRepository<Disciplina, UUID>, JpaSpecificationExecutor<Disciplina> {

    @Override
    @EntityGraph(attributePaths = "professor")
    Page<Disciplina> findAll(Specification<Disciplina> spec, Pageable pageable);

    @EntityGraph(attributePaths = "professor")
    Optional<Disciplina> findComProfessorById(UUID id);

    boolean existsByCodigo(String codigo);

    boolean existsByCodigoAndIdNot(String codigo, UUID id);

    boolean existsByProfessor_Id(UUID professorId);
}
