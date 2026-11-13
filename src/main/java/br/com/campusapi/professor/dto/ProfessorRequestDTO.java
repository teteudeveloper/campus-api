package br.com.campusapi.professor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import br.com.campusapi.comum.Textos;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Contrato de criação (POST) e substituição completa (PUT) de professor. */
public record ProfessorRequestDTO(
        @Schema(example = "Carlos Mendes") @NotBlank @Size(max = 100) String nome,
        @Schema(description = "Único; armazenado em minúsculas", example = "carlos.mendes@campus.edu")
        @NotBlank @Email @Size(max = 100) String email,
        @Schema(description = "Único; armazenado em maiúsculas", example = "PROF-001")
        @NotBlank @Size(max = 20)
        @Pattern(regexp = "[A-Za-z0-9._/-]*", message = "deve conter apenas letras, números e . _ / -")
        String registro,
        @Schema(description = "Opcional; no PUT, ausente limpa o valor", example = "Matemática")
        @Size(max = 100) String especialidade) {

    public ProfessorRequestDTO {
        nome = Textos.normalizarNome(nome);
        email = Textos.normalizarEmail(email);
        registro = Textos.normalizarIdentificador(registro);
        especialidade = Textos.normalizarNome(especialidade);
    }
}
