package br.com.campusapi.exception;

import org.springframework.http.HttpStatus;

/**
 * Exceção de negócio com status HTTP e código de erro estável, convertida em ProblemDetail
 * pelo {@link GlobalExceptionHandler}. A mensagem é exibida ao cliente e não deve conter dados internos.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String codigo;
    private final String campo;

    public ApiException(HttpStatus status, String codigo, String mensagem) {
        this(status, codigo, mensagem, null);
    }

    public ApiException(HttpStatus status, String codigo, String mensagem, String campo) {
        super(mensagem);
        this.status = status;
        this.codigo = codigo;
        this.campo = campo;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getCampo() {
        return campo;
    }
}
