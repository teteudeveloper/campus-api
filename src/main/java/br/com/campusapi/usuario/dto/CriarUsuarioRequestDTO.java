package br.com.campusapi.usuario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import br.com.campusapi.comum.Textos;
import br.com.campusapi.usuario.Perfil;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Criação de conta pelo ADMIN. Regras de vínculo: ALUNO exige alunoId; PROFESSOR exige professorId;
 * ADMIN não aceita vínculo. O aluno/professor deve existir e não pode estar vinculado a outra conta.
 */
public record CriarUsuarioRequestDTO(
        @Schema(description = "Único; armazenado em minúsculas", example = "lucas.oliveira")
        @NotBlank @Size(min = 3, max = 100)
        @Pattern(regexp = "[A-Za-z0-9._@-]+", message = "deve conter apenas letras, números e . _ @ -")
        String login,
        @Schema(description = "Senha inicial individual: 10 a 64 caracteres, com letras e dígitos", example = "SenhaInicial2026", format = "password")
        @NotBlank @Size(min = 10, max = 64) String senha,
        @Schema(description = "ADMIN (sem vínculo), PROFESSOR (exige professorId) ou ALUNO (exige alunoId)")
        @NotNull Perfil perfil,
        @Schema(description = "Obrigatório para ALUNO; proibido nos demais perfis") UUID alunoId,
        @Schema(description = "Obrigatório para PROFESSOR; proibido nos demais perfis") UUID professorId) {

    public CriarUsuarioRequestDTO {
        login = Textos.normalizarLogin(login);
    }

    @Override
    public String toString() {
        return "CriarUsuarioRequestDTO[login=" + login + ", perfil=" + perfil + "]";
    }
}
