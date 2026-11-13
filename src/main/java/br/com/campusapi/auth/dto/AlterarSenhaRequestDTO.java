package br.com.campusapi.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AlterarSenhaRequestDTO(
        @Schema(example = "SenhaAtual123", format = "password") @NotBlank @Size(max = 128) String senhaAtual,
        @Schema(description = "10 a 64 caracteres, com letras e dígitos", example = "NovaSenha2026", format = "password")
        @NotBlank @Size(min = 10, max = 64) String novaSenha) {

    @Override
    public String toString() {
        return "AlterarSenhaRequestDTO[***]";
    }
}
