package br.com.campusapi.disciplina.dto;

import br.com.campusapi.disciplina.Disciplina;
import br.com.campusapi.professor.Professor;

import java.time.LocalDateTime;
import java.util.UUID;

public record DisciplinaResponseDTO(
        UUID id,
        String nome,
        String codigo,
        Integer cargaHoraria,
        ProfessorResumoDTO professor,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm) {

    public record ProfessorResumoDTO(UUID id, String nome) {
    }

    public static DisciplinaResponseDTO de(Disciplina disciplina) {
        Professor professor = disciplina.getProfessor();
        return new DisciplinaResponseDTO(disciplina.getId(), disciplina.getNome(), disciplina.getCodigo(),
                disciplina.getCargaHoraria(),
                professor == null ? null : new ProfessorResumoDTO(professor.getId(), professor.getNome()),
                disciplina.getCriadoEm(), disciplina.getAtualizadoEm());
    }
}
