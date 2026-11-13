package br.com.campusapi.matricula;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MatriculaAlunoRepository extends JpaRepository<MatriculaAluno, UUID>,
        JpaSpecificationExecutor<MatriculaAluno> {

    @Override
    @EntityGraph(attributePaths = {"aluno", "disciplina", "disciplina.professor"})
    Page<MatriculaAluno> findAll(Specification<MatriculaAluno> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"aluno", "disciplina", "disciplina.professor"})
    Optional<MatriculaAluno> findComDetalhesById(UUID id);

    @EntityGraph(attributePaths = {"disciplina", "disciplina.professor"})
    List<MatriculaAluno> findByAlunoId(UUID alunoId, Sort sort);

    /** Bloqueio pessimista: lançamentos de nota concorrentes na mesma matrícula são serializados. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MatriculaAluno m where m.id = :id")
    Optional<MatriculaAluno> findParaAtualizacaoById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MatriculaAluno m where m.aluno.id = :alunoId and m.disciplina.id = :disciplinaId")
    Optional<MatriculaAluno> findParaAtualizacaoByAlunoIdAndDisciplinaId(@Param("alunoId") UUID alunoId,
                                                                         @Param("disciplinaId") UUID disciplinaId);

    boolean existsByAlunoIdAndDisciplinaId(UUID alunoId, UUID disciplinaId);

    boolean existsByAlunoId(UUID alunoId);

    boolean existsByDisciplinaId(UUID disciplinaId);
}
