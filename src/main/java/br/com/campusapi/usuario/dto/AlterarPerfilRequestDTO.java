package br.com.campusapi.usuario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import br.com.campusapi.usuario.Perfil;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Define perfil e vínculo de uma vez, com as mesmas regras de CriarUsuarioRequestDTO. */
public record AlterarPerfilRequestDTO(
        @Schema(description = "Novo perfil") @NotNull Perfil perfil,
        @Schema(description = "Obrigatório para ALUNO") UUID alunoId,
        @Schema(description = "Obrigatório para PROFESSOR") UUID professorId) {
}
