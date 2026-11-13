package br.com.campusapi.usuario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RedefinirSenhaRequestDTO(
        @Schema(description = "Senha individual e temporária", example = "Temporaria2026x", format = "password")
        @NotBlank @Size(min = 10, max = 64) String novaSenha) {

    @Override
    public String toString() {
        return "RedefinirSenhaRequestDTO[***]";
    }
}
