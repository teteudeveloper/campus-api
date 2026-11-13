package br.com.campusapi.matricula.dto;

import java.util.List;
import java.util.UUID;

/** Histórico do aluno; {@code disciplinas} é vazio (não 404) quando o aluno existe mas não tem matrículas. */
public record HistoricoAlunoResponseDTO(
        UUID alunoId,
        String nomeAluno,
        String emailAluno,
        String matriculaAluno,
        List<DisciplinasAlunoResponseDTO> disciplinas) {
}
