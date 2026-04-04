package br.com.campusapi.professor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProfessorRepository extends JpaRepository<Professor, UUID> {

    Optional<Professor>findByEmail(String email);
    Optional<Professor>findByRegistro(String registro);
    boolean existsByEmail(String email);
    boolean existsByRegistro(String registro);
}
