package br.com.campusapi.aluno.dto;

import br.com.campusapi.aluno.Aluno;

import java.time.LocalDateTime;
import java.util.UUID;

public record AlunoResponseDTO(
        UUID id,
        String nome,
        String email,
        String matricula,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm) {

    public static AlunoResponseDTO de(Aluno aluno) {
        return new AlunoResponseDTO(aluno.getId(), aluno.getNome(), aluno.getEmail(), aluno.getMatricula(),
                aluno.getCriadoEm(), aluno.getAtualizadoEm());
    }
}
