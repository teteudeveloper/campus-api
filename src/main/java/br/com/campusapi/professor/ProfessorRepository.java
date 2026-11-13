package br.com.campusapi.professor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProfessorRepository extends JpaRepository<Professor, UUID>, JpaSpecificationExecutor<Professor> {

    boolean existsByEmail(String email);

    boolean existsByRegistro(String registro);

    boolean existsByEmailAndIdNot(String email, UUID id);

    boolean existsByRegistroAndIdNot(String registro, UUID id);
}
