package br.com.campusapi.seguranca;

import br.com.campusapi.exception.CodigoErro;
import br.com.campusapi.exception.Problemas;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Respostas 401/403 geradas pela cadeia de filtros do Spring Security, no mesmo formato ProblemDetail do
 * GlobalExceptionHandler. O cabeçalho WWW-Authenticate segue a RFC 6750; a mensagem não revela o motivo
 * exato da rejeição do token (expirado, adulterado ou revogado).
 */
@Component
@RequiredArgsConstructor
public class ProblemaSegurancaHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemaSegurancaHandler.class);

    private final ObjectMapper objectMapper;
    private final BearerTokenAuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException excecao) throws IOException {
        bearerEntryPoint.commence(request, response, excecao);
        boolean tokenRejeitado = excecao instanceof InvalidBearerTokenException
                || response.getHeader(HttpHeaders.WWW_AUTHENTICATE) != null
                && response.getHeader(HttpHeaders.WWW_AUTHENTICATE).contains("invalid_token");
        if (tokenRejeitado) {
            log.debug("Bearer token rejeitado em {} {}", request.getMethod(), request.getRequestURI());
        }
        ProblemDetail problema = tokenRejeitado
                ? Problemas.criar(HttpStatus.UNAUTHORIZED, CodigoErro.TOKEN_INVALIDO, "Não autenticado",
                "Token de acesso inválido, expirado ou revogado", request.getRequestURI())
                : Problemas.criar(HttpStatus.UNAUTHORIZED, CodigoErro.NAO_AUTENTICADO, "Não autenticado",
                "Autenticação necessária", request.getRequestURI());
        escrever(response, problema);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException excecao) throws IOException {
        log.info("Acesso negado a {} {}", request.getMethod(), request.getRequestURI());
        escrever(response, Problemas.criar(HttpStatus.FORBIDDEN, CodigoErro.ACESSO_NEGADO, "Acesso negado",
                "Você não tem permissão para acessar este recurso", request.getRequestURI()));
    }

    private void escrever(HttpServletResponse response, ProblemDetail problema) throws IOException {
        response.setStatus(problema.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), Problemas.comoMapa(problema));
    }
}
