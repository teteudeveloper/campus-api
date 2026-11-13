package br.com.campusapi.exception;

import org.springframework.http.HttpStatus;

/** Entrada sintaticamente válida, mas rejeitada por regra de validação do serviço (HTTP 400). */
public class RequisicaoInvalidaException extends ApiException {

    public RequisicaoInvalidaException(String codigo, String mensagem) {
        super(HttpStatus.BAD_REQUEST, codigo, mensagem);
    }

    public RequisicaoInvalidaException(String codigo, String mensagem, String campo) {
        super(HttpStatus.BAD_REQUEST, codigo, mensagem, campo);
    }
}
