package br.com.campusapi.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import br.com.campusapi.comum.Textos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequestDTO(
        @Schema(description = "Login (sem diferenciar maiúsculas)", example = "admin")
        @NotBlank @Size(max = 100) String login,
        @Schema(description = "Senha", example = "SenhaDoUsuario123", format = "password")
        @NotBlank @Size(max = 128) String senha) {

    public LoginRequestDTO {
        login = Textos.normalizarLogin(login);
    }

    @Override
    public String toString() {
        return "LoginRequestDTO[login=" + login + "]";
    }
}
