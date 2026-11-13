package br.com.campusapi.comum;

import br.com.campusapi.exception.CodigoErro;
import br.com.campusapi.exception.RequisicaoInvalidaException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;
import java.util.TreeSet;

/**
 * Valida a ordenação contra uma lista de campos permitidos, aplica o tamanho máximo de página e acrescenta
 * o id como critério de desempate para que a paginação seja estável.
 */
public final class Paginacao {

    public static final int TAMANHO_MAXIMO = 100;

    private Paginacao() {
    }

    public static Pageable validar(Pageable pageable, Set<String> camposPermitidos, Sort ordenacaoPadrao) {
        for (Sort.Order ordem : pageable.getSort()) {
            if (!camposPermitidos.contains(ordem.getProperty())) {
                throw new RequisicaoInvalidaException(CodigoErro.PARAMETRO_INVALIDO,
                        "Ordenação não permitida pelo campo '" + ordem.getProperty() + "'. Campos permitidos: "
                                + String.join(", ", new TreeSet<>(camposPermitidos)), "sort");
            }
        }
        Sort ordenacao = pageable.getSort().isSorted() ? pageable.getSort() : ordenacaoPadrao;
        if (ordenacao.getOrderFor("id") == null) {
            ordenacao = ordenacao.and(Sort.by("id"));
        }
        int tamanho = Math.min(pageable.getPageSize(), TAMANHO_MAXIMO);
        if ((long) pageable.getPageNumber() * tamanho > Integer.MAX_VALUE) {
            throw new RequisicaoInvalidaException(CodigoErro.PARAMETRO_INVALIDO, "Número de página fora do intervalo", "page");
        }
        return PageRequest.of(pageable.getPageNumber(), tamanho, ordenacao);
    }
}
