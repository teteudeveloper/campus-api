package br.com.campusapi.aluno.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import br.com.campusapi.comum.Textos;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Contrato de criação (POST) e substituição completa (PUT) de aluno. */
public record AlunoRequestDTO(
        @Schema(example = "Lucas Oliveira") @NotBlank @Size(max = 100) String nome,
        @Schema(description = "Único; armazenado em minúsculas", example = "lucas.oliveira@campus.edu")
        @NotBlank @Email @Size(max = 100) String email,
        @Schema(description = "Único; armazenado em maiúsculas", example = "2026001")
        @NotBlank @Size(max = 20)
        @Pattern(regexp = "[A-Za-z0-9._/-]*", message = "deve conter apenas letras, números e . _ / -")
        String matricula) {

    /** Normaliza antes da validação: espaços, e-mail em minúsculas e matrícula em maiúsculas. */
    public AlunoRequestDTO {
        nome = Textos.normalizarNome(nome);
        email = Textos.normalizarEmail(email);
        matricula = Textos.normalizarIdentificador(matricula);
    }
}
