package br.com.campusapi.usuario;

import br.com.campusapi.exception.CodigoErro;
import br.com.campusapi.exception.RequisicaoInvalidaException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Política mínima de senha: 10 a 64 caracteres, no máximo 72 bytes em UTF-8 (limite do BCrypt, acima do qual
 * o restante seria ignorado), com ao menos uma letra e um dígito.
 */
@Component
public class PoliticaSenha {

    public static final int TAMANHO_MINIMO = 10;
    public static final int TAMANHO_MAXIMO = 64;
    private static final int LIMITE_BYTES_BCRYPT = 72;

    public void validar(String senha, String campo) {
        if (senha == null || senha.isBlank() || senha.length() < TAMANHO_MINIMO || senha.length() > TAMANHO_MAXIMO) {
            throw invalida(campo, "Senha deve ter entre " + TAMANHO_MINIMO + " e " + TAMANHO_MAXIMO + " caracteres");
        }
        if (senha.getBytes(StandardCharsets.UTF_8).length > LIMITE_BYTES_BCRYPT) {
            throw invalida(campo, "Senha excede " + LIMITE_BYTES_BCRYPT + " bytes em UTF-8");
        }
        if (senha.chars().noneMatch(Character::isLetter) || senha.chars().noneMatch(Character::isDigit)) {
            throw invalida(campo, "Senha deve conter ao menos uma letra e um dígito");
        }
    }

    private static RequisicaoInvalidaException invalida(String campo, String mensagem) {
        return new RequisicaoInvalidaException(CodigoErro.VALIDACAO, mensagem, campo);
    }
}
