package br.com.campusapi.disciplina.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import br.com.campusapi.comum.Textos;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Criação (POST) e atualização (PUT) de disciplina.
 * {@code professorId}: no POST, nulo cria a disciplina sem professor; no PUT, nulo ou ausente mantém o professor
 * atual (compatível com o contrato anterior). Para remover o professor use DELETE /disciplinas/{id}/professor.
 */
public record DisciplinaRequestDTO(
        @Schema(example = "Cálculo I") @NotBlank @Size(max = 100) String nome,
        @Schema(description = "Único; armazenado em maiúsculas", example = "MAT-101")
        @NotBlank @Size(max = 20)
        @Pattern(regexp = "[A-Za-z0-9._/-]*", message = "deve conter apenas letras, números e . _ / -")
        String codigo,
        @Schema(description = "Horas (1 a 1000)", example = "80") @NotNull @Min(1) @Max(1000) Integer cargaHoraria,
        @Schema(description = "Opcional. POST: nulo cria sem professor. PUT: nulo/ausente mantém o atual")
        UUID professorId) {

    public DisciplinaRequestDTO {
        nome = Textos.normalizarNome(nome);
        codigo = Textos.normalizarIdentificador(codigo);
    }
}
