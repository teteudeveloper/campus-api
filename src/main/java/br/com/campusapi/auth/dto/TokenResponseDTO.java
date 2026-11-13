package br.com.campusapi.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TokenResponseDTO(
        @Schema(description = "JWT para o cabeçalho Authorization: Bearer", example = "eyJhbGciOiJIUzI1NiJ9.exemplo.assinatura")
        String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(description = "Validade do access token em segundos", example = "900") long expiresIn,
        @Schema(description = "Token opaco de uso único para POST /auth/refresh", example = "Zm9vYmFyLXJlZnJlc2gtdG9rZW4tZXhlbXBsbw")
        String refreshToken,
        @Schema(description = "Validade do refresh token em segundos", example = "604800") long refreshExpiresIn) {

    @Override
    public String toString() {
        return "TokenResponseDTO[tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
    }
}
