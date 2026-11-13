package br.com.campusapi.exception;

import br.com.campusapi.comum.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Fábrica do contrato de erro (RFC 9457) compartilhado pelo handler MVC e pelos handlers de segurança. */
public final class Problemas {

    private Problemas() {
    }

    public record ErroCampo(String campo, String mensagem) {
    }

    public static ProblemDetail criar(HttpStatusCode status, String codigo, String titulo, String detalhe,
                                      String caminho) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setType(URI.create("urn:campus-api:problema:" + codigo.toLowerCase(Locale.ROOT).replace('_', '-')));
        problema.setTitle(titulo);
        if (caminho != null) {
            problema.setInstance(URI.create(caminho));
        }
        problema.setProperty("codigo", codigo);
        problema.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        problema.setProperty("timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString());
        return problema;
    }

    public static ProblemDetail comErros(ProblemDetail problema, List<ErroCampo> erros) {
        problema.setProperty("erros", erros);
        return problema;
    }

    /** Representação plana usada quando a resposta é escrita fora do Spring MVC (filtros de segurança). */
    public static Map<String, Object> comoMapa(ProblemDetail problema) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("type", problema.getType().toString());
        corpo.put("title", problema.getTitle());
        corpo.put("status", problema.getStatus());
        corpo.put("detail", problema.getDetail());
        if (problema.getInstance() != null) {
            corpo.put("instance", problema.getInstance().toString());
        }
        if (problema.getProperties() != null) {
            corpo.putAll(problema.getProperties());
        }
        return corpo;
    }
}
