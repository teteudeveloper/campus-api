package br.com.campusapi.exception;

import org.springframework.http.HttpStatus;

public class RecursoNaoEncontradoException extends ApiException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(HttpStatus.NOT_FOUND, CodigoErro.RECURSO_NAO_ENCONTRADO, mensagem);
    }
}
