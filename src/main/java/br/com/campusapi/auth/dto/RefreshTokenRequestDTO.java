package br.com.campusapi.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequestDTO(
        @Schema(description = "Refresh token recebido no login ou na última renovação", example = "Zm9vYmFyLXJlZnJlc2gtdG9rZW4tZXhlbXBsbw")
        @NotBlank @Size(max = 200) String refreshToken) {

    @Override
    public String toString() {
        return "RefreshTokenRequestDTO[***]";
    }
}
