package br.com.campusapi.usuario.dto;

import br.com.campusapi.usuario.Perfil;
import br.com.campusapi.usuario.Usuario;

import java.time.Instant;
import java.util.UUID;

public record UsuarioResponseDTO(
        UUID id,
        String login,
        Perfil perfil,
        boolean ativo,
        UUID alunoId,
        UUID professorId,
        Instant criadoEm,
        Instant atualizadoEm) {

    public static UsuarioResponseDTO de(Usuario usuario) {
        return new UsuarioResponseDTO(usuario.getId(), usuario.getLogin(), usuario.getPerfil(), usuario.isAtivo(),
                usuario.getAlunoId(), usuario.getProfessorId(), usuario.getCriadoEm(), usuario.getAtualizadoEm());
    }
}
