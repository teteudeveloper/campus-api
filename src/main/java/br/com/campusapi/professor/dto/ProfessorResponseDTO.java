package br.com.campusapi.professor.dto;

import br.com.campusapi.professor.Professor;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProfessorResponseDTO(
        UUID id,
        String nome,
        String email,
        String registro,
        String especialidade,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm) {

    public static ProfessorResponseDTO de(Professor professor) {
        return new ProfessorResponseDTO(professor.getId(), professor.getNome(), professor.getEmail(),
                professor.getRegistro(), professor.getEspecialidade(), professor.getCriadoEm(),
                professor.getAtualizadoEm());
    }
}
