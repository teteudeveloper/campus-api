package br.com.campusapi.seguranca;

import br.com.campusapi.usuario.Perfil;
import br.com.campusapi.usuario.Usuario;

import java.security.Principal;
import java.util.UUID;

/** Principal disponível em cada requisição autenticada, carregado do banco a partir do access token. */
public record UsuarioAutenticado(UUID id, String login, Perfil perfil, UUID alunoId, UUID professorId)
        implements Principal {

    public static UsuarioAutenticado de(Usuario usuario) {
        return new UsuarioAutenticado(usuario.getId(), usuario.getLogin(), usuario.getPerfil(),
                usuario.getAlunoId(), usuario.getProfessorId());
    }

    public boolean isAdmin() {
        return perfil == Perfil.ADMIN;
    }

    public boolean isProfessor() {
        return perfil == Perfil.PROFESSOR;
    }

    public boolean isAluno() {
        return perfil == Perfil.ALUNO;
    }

    @Override
    public String getName() {
        return id.toString();
    }
}
