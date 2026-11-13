package br.com.campusapi.exception;

import org.springframework.http.HttpStatus;

/** Violação de unicidade, vínculo existente ou transição de estado inválida (HTTP 409). */
public class ConflitoException extends ApiException {

    public ConflitoException(String codigo, String mensagem) {
        super(HttpStatus.CONFLICT, codigo, mensagem);
    }

    public ConflitoException(String codigo, String mensagem, String campo) {
        super(HttpStatus.CONFLICT, codigo, mensagem, campo);
    }
}
