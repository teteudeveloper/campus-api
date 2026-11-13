package br.com.campusapi.disciplina;

import br.com.campusapi.comum.Textos;
import br.com.campusapi.matricula.MatriculaAluno;
import br.com.campusapi.seguranca.UsuarioAutenticado;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

final class DisciplinaSpecs {

    private DisciplinaSpecs() {
    }

    /**
     * Restrição de visibilidade aplicada na própria consulta (antes da paginação):
     * PROFESSOR vê as disciplinas sob sua responsabilidade; ALUNO vê aquelas em que tem matrícula.
     */
    static Specification<Disciplina> visiveisPara(UsuarioAutenticado usuario) {
        return switch (usuario.perfil()) {
            case ADMIN -> Specification.unrestricted();
            case PROFESSOR -> doProfessor(usuario.professorId());
            case ALUNO -> comMatriculaDoAluno(usuario.alunoId());
        };
    }

    static Specification<Disciplina> doProfessor(UUID professorId) {
        return (root, query, cb) -> cb.equal(root.get("professor").get("id"), professorId);
    }

    static Specification<Disciplina> comMatriculaDoAluno(UUID alunoId) {
        return (root, query, cb) -> {
            Subquery<UUID> sub = query.subquery(UUID.class);
            var matricula = sub.from(MatriculaAluno.class);
            sub.select(matricula.get("id")).where(
                    cb.equal(matricula.get("disciplina"), root),
                    cb.equal(matricula.get("aluno").get("id"), alunoId));
            return cb.exists(sub);
        };
    }

    static Specification<Disciplina> filtrar(String nome, String codigo, UUID professorId) {
        Specification<Disciplina> spec = Specification.unrestricted();
        if (nome != null && !nome.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("nome")), Textos.padraoContem(nome), '\\'));
        }
        if (codigo != null && !codigo.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("codigo"), Textos.normalizarIdentificador(codigo)));
        }
        if (professorId != null) {
            spec = spec.and(doProfessor(professorId));
        }
        return spec;
    }
}
