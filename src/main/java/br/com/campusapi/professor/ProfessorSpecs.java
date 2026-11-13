package br.com.campusapi.professor;

import br.com.campusapi.comum.Textos;
import org.springframework.data.jpa.domain.Specification;

final class ProfessorSpecs {

    private ProfessorSpecs() {
    }

    static Specification<Professor> filtrar(String nome, String especialidade, String registro) {
        Specification<Professor> spec = Specification.unrestricted();
        if (nome != null && !nome.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("nome")), Textos.padraoContem(nome), '\\'));
        }
        if (especialidade != null && !especialidade.isBlank()) {
            spec = spec.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("especialidade")), Textos.padraoContem(especialidade), '\\'));
        }
        if (registro != null && !registro.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("registro"), Textos.normalizarIdentificador(registro)));
        }
        return spec;
    }
}
