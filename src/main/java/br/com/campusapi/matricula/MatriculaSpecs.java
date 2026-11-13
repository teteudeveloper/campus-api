package br.com.campusapi.matricula;

import br.com.campusapi.enums.MatriculaAlunoStatusEnum;
import br.com.campusapi.seguranca.UsuarioAutenticado;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

final class MatriculaSpecs {

    private MatriculaSpecs() {
    }

    /** Restrição de visibilidade aplicada na consulta paginada, nunca depois dela. */
    static Specification<MatriculaAluno> visiveisPara(UsuarioAutenticado usuario) {
        return switch (usuario.perfil()) {
            case ADMIN -> Specification.unrestricted();
            case PROFESSOR -> (root, query, cb) ->
                    cb.equal(root.get("disciplina").get("professor").get("id"), usuario.professorId());
            case ALUNO -> doAluno(usuario.alunoId());
        };
    }

    static Specification<MatriculaAluno> doAluno(UUID alunoId) {
        return (root, query, cb) -> cb.equal(root.get("aluno").get("id"), alunoId);
    }

    static Specification<MatriculaAluno> filtrar(UUID alunoId, UUID disciplinaId, MatriculaAlunoStatusEnum status) {
        Specification<MatriculaAluno> spec = Specification.unrestricted();
        if (alunoId != null) {
            spec = spec.and(doAluno(alunoId));
        }
        if (disciplinaId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("disciplina").get("id"), disciplinaId));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        return spec;
    }
}
