package br.com.campusapi.exception;

import br.com.campusapi.seguranca.LimitadorTentativasLogin;
import jakarta.servlet.http.HttpServletRequest;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Converte exceções em respostas ProblemDetail (application/problem+json) com {@code codigo},
 * {@code correlationId} e, em erros de validação, {@code erros}. Mensagens de exceções inesperadas,
 * SQL e stack traces nunca são enviados ao cliente; o detalhe técnico fica apenas no log do servidor.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Constraints do banco mapeadas para mensagens de negócio (a mensagem do PostgreSQL contém valores e não é exposta). */
    private static final Map<String, Violacao> VIOLACOES = Map.ofEntries(
            Map.entry("uk_alunos_email", new Violacao(CodigoErro.CONFLITO, "email", "E-mail já cadastrado para outro aluno")),
            Map.entry("uk_alunos_matricula", new Violacao(CodigoErro.CONFLITO, "matricula", "Número de matrícula já cadastrado")),
            Map.entry("uk_professores_email", new Violacao(CodigoErro.CONFLITO, "email", "E-mail já cadastrado para outro professor")),
            Map.entry("uk_professores_registro", new Violacao(CodigoErro.CONFLITO, "registro", "Registro já cadastrado")),
            Map.entry("uk_disciplinas_codigo", new Violacao(CodigoErro.CONFLITO, "codigo", "Código de disciplina já cadastrado")),
            Map.entry("uk_matriculas_aluno_disciplina", new Violacao(CodigoErro.MATRICULA_DUPLICADA, null, "Aluno já possui matrícula nesta disciplina")),
            Map.entry("uk_usuarios_login", new Violacao(CodigoErro.CONFLITO, "login", "Login já está em uso")),
            Map.entry("uk_usuarios_aluno", new Violacao(CodigoErro.VINCULO_EXISTENTE, "alunoId", "Aluno já está vinculado a outro usuário")),
            Map.entry("uk_usuarios_professor", new Violacao(CodigoErro.VINCULO_EXISTENTE, "professorId", "Professor já está vinculado a outro usuário")),
            Map.entry("fk_matriculas_aluno", new Violacao(CodigoErro.REGISTRO_EM_USO, null, "Aluno possui matrículas e não pode ser excluído")),
            Map.entry("fk_matriculas_disciplina", new Violacao(CodigoErro.REGISTRO_EM_USO, null, "Disciplina possui matrículas e não pode ser excluída")),
            Map.entry("fk_disciplinas_professor", new Violacao(CodigoErro.REGISTRO_EM_USO, null, "Professor é responsável por disciplinas e não pode ser excluído")),
            Map.entry("fk_usuarios_aluno", new Violacao(CodigoErro.REGISTRO_EM_USO, null, "Aluno possui usuário vinculado e não pode ser excluído")),
            Map.entry("fk_usuarios_professor", new Violacao(CodigoErro.REGISTRO_EM_USO, null, "Professor possui usuário vinculado e não pode ser excluído")));

    private record Violacao(String codigo, String campo, String mensagem) {
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApi(ApiException ex, HttpServletRequest request) {
        ProblemDetail problema = Problemas.criar(ex.getStatus(), ex.getCodigo(), ex.getStatus().getReasonPhrase(),
                ex.getMessage(), request.getRequestURI());
        if (ex.getCampo() != null) {
            Problemas.comErros(problema, List.of(new Problemas.ErroCampo(ex.getCampo(), ex.getMessage())));
        }
        log.debug("Erro de negócio {}: {}", ex.getCodigo(), ex.getMessage());
        return responder(problema);
    }

    @ExceptionHandler(LimitadorTentativasLogin.MuitasTentativasException.class)
    public ResponseEntity<ProblemDetail> handleMuitasTentativas(LimitadorTentativasLogin.MuitasTentativasException ex,
                                                                HttpServletRequest request) {
        ProblemDetail problema = Problemas.criar(ex.getStatus(), ex.getCodigo(), "Muitas requisições",
                ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(problema.getStatus())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSegundos()))
                .body(problema);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidacao(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<Problemas.ErroCampo> erros = ex.getBindingResult().getAllErrors().stream()
                .map(erro -> new Problemas.ErroCampo(
                        erro instanceof FieldError campo ? campo.getField() : erro.getObjectName(),
                        erro.getDefaultMessage()))
                .toList();
        return responder(Problemas.comErros(validacao("Um ou mais campos são inválidos", request), erros));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> handleValidacaoParametros(HandlerMethodValidationException ex,
                                                                   HttpServletRequest request) {
        List<Problemas.ErroCampo> erros = ex.getParameterValidationResults().stream()
                .flatMap(resultado -> resultado.getResolvableErrors().stream()
                        .map(erro -> new Problemas.ErroCampo(
                                resultado.getMethodParameter().getParameterName(), erro.getDefaultMessage())))
                .toList();
        return responder(Problemas.comErros(validacao("Um ou mais parâmetros são inválidos", request), erros));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleJsonInvalido(HttpMessageNotReadableException ex, HttpServletRequest request) {
        String detalhe = "Corpo da requisição ausente ou malformado";
        List<Problemas.ErroCampo> erros = List.of();
        Throwable causa = ex.getCause();
        if (causa instanceof UnrecognizedPropertyException desconhecida) {
            detalhe = "Campo não reconhecido: " + desconhecida.getPropertyName();
            erros = List.of(new Problemas.ErroCampo(desconhecida.getPropertyName(), "Campo não permitido neste recurso"));
        } else if (causa instanceof InvalidFormatException formato) {
            String campo = caminho(formato);
            detalhe = "Valor inválido para o campo " + campo;
            String mensagem = formato.getTargetType() != null && formato.getTargetType().isEnum()
                    ? "Valores aceitos: " + List.of(formato.getTargetType().getEnumConstants())
                    : "Valor em formato inválido";
            erros = List.of(new Problemas.ErroCampo(campo, mensagem));
        } else if (causa instanceof JacksonException jackson && !jackson.getPath().isEmpty()) {
            String campo = caminho(jackson);
            detalhe = "Valor inválido para o campo " + campo;
            erros = List.of(new Problemas.ErroCampo(campo, "Valor em formato inválido"));
        }
        ProblemDetail problema = Problemas.criar(HttpStatus.BAD_REQUEST, CodigoErro.JSON_INVALIDO,
                "Requisição inválida", detalhe, request.getRequestURI());
        return responder(erros.isEmpty() ? problema : Problemas.comErros(problema, erros));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTipoParametro(MethodArgumentTypeMismatchException ex,
                                                             HttpServletRequest request) {
        Class<?> tipo = ex.getRequiredType();
        String esperado = tipo == null ? "valor válido"
                : tipo.isEnum() ? "um dos valores " + List.of(tipo.getEnumConstants())
                : "java.util.UUID".equals(tipo.getName()) ? "UUID"
                : tipo.getSimpleName();
        String mensagem = "Parâmetro '" + ex.getName() + "' inválido; esperado " + esperado;
        ProblemDetail problema = Problemas.criar(HttpStatus.BAD_REQUEST, CodigoErro.PARAMETRO_INVALIDO,
                "Requisição inválida", mensagem, request.getRequestURI());
        return responder(Problemas.comErros(problema, List.of(new Problemas.ErroCampo(ex.getName(), mensagem))));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleParametroAusente(MissingServletRequestParameterException ex,
                                                                HttpServletRequest request) {
        String mensagem = "Parâmetro obrigatório ausente: " + ex.getParameterName();
        ProblemDetail problema = Problemas.criar(HttpStatus.BAD_REQUEST, CodigoErro.PARAMETRO_INVALIDO,
                "Requisição inválida", mensagem, request.getRequestURI());
        return responder(Problemas.comErros(problema, List.of(new Problemas.ErroCampo(ex.getParameterName(), mensagem))));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleIntegridade(DataIntegrityViolationException ex, HttpServletRequest request) {
        String constraint = nomeConstraint(ex);
        Violacao violacao = constraint == null ? null : VIOLACOES.get(constraint);
        log.warn("Violação de integridade no banco (constraint={})", constraint);
        if (violacao == null) {
            return responder(Problemas.criar(HttpStatus.CONFLICT, CodigoErro.CONFLITO, "Conflito",
                    "A operação viola uma regra de integridade dos dados", request.getRequestURI()));
        }
        ProblemDetail problema = Problemas.criar(HttpStatus.CONFLICT, violacao.codigo(), "Conflito",
                violacao.mensagem(), request.getRequestURI());
        if (violacao.campo() != null) {
            Problemas.comErros(problema, List.of(new Problemas.ErroCampo(violacao.campo(), violacao.mensagem())));
        }
        return responder(problema);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<ProblemDetail> handleConcorrencia(RuntimeException ex, HttpServletRequest request) {
        log.warn("Conflito de concorrência: {}", ex.getClass().getSimpleName());
        return responder(Problemas.criar(HttpStatus.CONFLICT, CodigoErro.CONFLITO_CONCORRENCIA, "Conflito",
                "O recurso foi alterado por outra requisição. Consulte o estado atual e tente novamente",
                request.getRequestURI()));
    }

    /** Lançada pela segurança de métodos (@PreAuthorize) e pelas verificações de propriedade nos serviços. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAcessoNegado(AccessDeniedException ex, HttpServletRequest request) {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null || autenticacao instanceof AnonymousAuthenticationToken) {
            return responder(naoAutenticado(request));
        }
        log.info("Acesso negado a {} {} para usuário {}", request.getMethod(), request.getRequestURI(),
                autenticacao.getName());
        return responder(Problemas.criar(HttpStatus.FORBIDDEN, CodigoErro.ACESSO_NEGADO, "Acesso negado",
                "Você não tem permissão para acessar este recurso", request.getRequestURI()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAutenticacao(AuthenticationException ex, HttpServletRequest request) {
        return responder(naoAutenticado(request));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleRotaInexistente(NoResourceFoundException ex, HttpServletRequest request) {
        return responder(Problemas.criar(HttpStatus.NOT_FOUND, CodigoErro.ROTA_NAO_ENCONTRADA, "Não encontrado",
                "Rota não encontrada", request.getRequestURI()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMetodo(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return responder(Problemas.criar(HttpStatus.METHOD_NOT_ALLOWED, CodigoErro.METODO_NAO_SUPORTADO,
                "Método não suportado", "Método " + ex.getMethod() + " não suportado nesta rota",
                request.getRequestURI()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMidia(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return responder(Problemas.criar(HttpStatus.UNSUPPORTED_MEDIA_TYPE, CodigoErro.TIPO_MIDIA_NAO_SUPORTADO,
                "Tipo de mídia não suportado", "Envie o corpo como application/json", request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleInesperado(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse respostaPadrao && respostaPadrao.getStatusCode().is4xxClientError()) {
            HttpStatusCode status = respostaPadrao.getStatusCode();
            log.debug("Erro de requisição tratado pelo Spring MVC: {}", ex.getClass().getSimpleName());
            return responder(Problemas.criar(status, CodigoErro.PARAMETRO_INVALIDO, "Requisição inválida",
                    "A requisição não pôde ser processada", request.getRequestURI()));
        }
        log.error("Erro inesperado em {} {}", request.getMethod(), request.getRequestURI(), ex);
        return responder(Problemas.criar(HttpStatus.INTERNAL_SERVER_ERROR, CodigoErro.ERRO_INTERNO,
                "Erro interno", "Ocorreu um erro inesperado. Informe o correlationId ao suporte",
                request.getRequestURI()));
    }

    private static ProblemDetail naoAutenticado(HttpServletRequest request) {
        return Problemas.criar(HttpStatus.UNAUTHORIZED, CodigoErro.NAO_AUTENTICADO, "Não autenticado",
                "Autenticação necessária", request.getRequestURI());
    }

    private static ProblemDetail validacao(String detalhe, HttpServletRequest request) {
        return Problemas.criar(HttpStatus.BAD_REQUEST, CodigoErro.VALIDACAO, "Requisição inválida", detalhe,
                request.getRequestURI());
    }

    private static ResponseEntity<ProblemDetail> responder(ProblemDetail problema) {
        return ResponseEntity.status(problema.getStatus()).body(problema);
    }

    private static String caminho(JacksonException ex) {
        return ex.getPath().stream()
                .map(ref -> ref.getPropertyName() != null ? ref.getPropertyName() : "[" + ref.getIndex() + "]")
                .collect(Collectors.joining("."));
    }

    private static String nomeConstraint(Throwable ex) {
        for (Throwable atual = ex; atual != null; atual = atual.getCause()) {
            if (atual instanceof ConstraintViolationException violacao && violacao.getConstraintName() != null) {
                return violacao.getConstraintName().toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }
}
