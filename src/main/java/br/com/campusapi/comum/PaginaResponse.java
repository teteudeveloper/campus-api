package br.com.campusapi.comum;

import org.springframework.data.domain.Page;

import java.util.List;

/** Envelope de paginação estável, independente da serialização interna do Spring Data. */
public record PaginaResponse<T>(
        List<T> conteudo,
        int pagina,
        int tamanho,
        long totalElementos,
        int totalPaginas) {

    public static <T> PaginaResponse<T> de(Page<T> page) {
        return new PaginaResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
