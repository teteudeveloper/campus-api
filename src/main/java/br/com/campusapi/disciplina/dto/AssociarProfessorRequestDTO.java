package br.com.campusapi.disciplina.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssociarProfessorRequestDTO(@Schema(description = "Professor existente") @NotNull UUID professorId) {
}
