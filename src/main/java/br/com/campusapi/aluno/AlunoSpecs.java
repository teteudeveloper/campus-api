package br.com.campusapi.aluno;

import br.com.campusapi.comum.Textos;
import org.springframework.data.jpa.domain.Specification;

final class AlunoSpecs {

    private AlunoSpecs() {
    }

    static Specification<Aluno> filtrar(String nome, String email, String matricula) {
        Specification<Aluno> spec = Specification.unrestricted();
        if (nome != null && !nome.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("nome")), Textos.padraoContem(nome), '\\'));
        }
        if (email != null && !email.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("email"), Textos.normalizarEmail(email)));
        }
        if (matricula != null && !matricula.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("matricula"), Textos.normalizarIdentificador(matricula)));
        }
        return spec;
    }
}
