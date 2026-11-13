package br.com.campusapi.seguranca;

import br.com.campusapi.usuario.Usuario;
import br.com.campusapi.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Após a validação criptográfica do JWT, recarrega o usuário do banco. Assim, desativação, troca de senha e
 * mudança de perfil/vínculo têm efeito imediato sobre access tokens já emitidos: a claim {@code ver} deixa de
 * coincidir com {@code usuarios.versao_credenciais} e o token passa a ser rejeitado com 401.
 */
@Component
@RequiredArgsConstructor
public class JwtUsuarioConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UsuarioRepository usuarioRepository;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID id;
        long versao;
        try {
            id = UUID.fromString(jwt.getSubject());
            versao = ((Number) jwt.getClaims().get(JwtConfig.CLAIM_VERSAO_CREDENCIAIS)).longValue();
        } catch (RuntimeException e) {
            throw new InvalidBearerTokenException("Token inválido");
        }
        Usuario usuario = usuarioRepository.findById(id)
                .filter(Usuario::isAtivo)
                .filter(u -> u.getVersaoCredenciais() == versao)
                .orElseThrow(() -> new InvalidBearerTokenException("Token inválido ou revogado"));
        return new UsuarioAuthenticationToken(UsuarioAutenticado.de(usuario));
    }
}
