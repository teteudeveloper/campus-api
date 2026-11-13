package br.com.campusapi.matricula.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** O status inicial é sempre MATRICULADO e não é aceito no corpo. */
public record CriarMatriculaRequestDTO(
        @Schema(description = "Aluno existente") @NotNull UUID alunoId,
        @Schema(description = "Disciplina existente") @NotNull UUID disciplinaId) {
}
