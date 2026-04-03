package br.com.campusapi.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(RecursoNaoEncontradoException.class)
  public ResponseEntity<Map <String, Object>> handleRecursoNaoEncontradoException(
          RecursoNaoEncontradoException ex) {
    Map<String, Object> erro = new HashMap<>();
    erro.put("status", HttpStatus.NOT_FOUND.value());
    erro.put("erro", "Recurso não encontrado");
    erro.put("mensagem", ex.getMessage());
    erro.put("timestamp", LocalDateTime.now());

    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(erro);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map <String, Object>> handleException(Exception ex) {
    Map<String, Object> erro = new HashMap<>();
    erro.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
    erro.put("erro", "Erro interno do servidor");
    erro.put("mensagem", ex.getMessage());
    erro.put("timestamp", LocalDateTime.now());

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(erro);
  }
}
