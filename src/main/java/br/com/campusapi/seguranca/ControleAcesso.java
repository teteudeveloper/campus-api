package br.com.campusapi.seguranca;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

/**
 * Verificações de vínculo e propriedade usadas pelos serviços. A verificação de perfil por si só não basta:
 * um ALUNO só acessa o próprio aluno e um PROFESSOR só as disciplinas sob sua responsabilidade.
 */
@Component
public class ControleAcesso {

    public UsuarioAutenticado usuarioAtual() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao != null && autenticacao.getPrincipal() instanceof UsuarioAutenticado usuario) {
            return usuario;
        }
        throw new AccessDeniedException("Usuário não autenticado");
    }

    /** ADMIN ou o próprio aluno. */
    public void exigirAcessoAoAluno(UUID alunoId) {
        UsuarioAutenticado usuario = usuarioAtual();
        if (usuario.isAdmin() || (usuario.isAluno() && Objects.equals(usuario.alunoId(), alunoId))) {
            return;
        }
        throw negado();
    }

    /** ADMIN ou o próprio professor. */
    public void exigirAcessoAoProfessor(UUID professorId) {
        UsuarioAutenticado usuario = usuarioAtual();
        if (usuario.isAdmin() || (usuario.isProfessor() && Objects.equals(usuario.professorId(), professorId))) {
            return;
        }
        throw negado();
    }

    /** ADMIN ou o professor responsável pela disciplina (professorResponsavelId pode ser nulo). */
    public void exigirResponsavelPelaDisciplina(UUID professorResponsavelId) {
        UsuarioAutenticado usuario = usuarioAtual();
        if (usuario.isAdmin()) {
            return;
        }
        if (usuario.isProfessor() && professorResponsavelId != null
                && professorResponsavelId.equals(usuario.professorId())) {
            return;
        }
        throw negado();
    }

    public AccessDeniedException negado() {
        return new AccessDeniedException("Acesso negado ao recurso");
    }
}
