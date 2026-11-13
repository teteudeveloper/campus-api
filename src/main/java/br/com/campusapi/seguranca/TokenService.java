package br.com.campusapi.seguranca;

import br.com.campusapi.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Emite access tokens. O payload contém apenas identificadores técnicos (sub = id do usuário, ver = versão
 * das credenciais); perfil, login e dados pessoais não são incluídos e são resolvidos no servidor.
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final SegurancaProperties properties;
    private final Clock clock;

    public String gerarAccessToken(Usuario usuario) {
        Instant agora = clock.instant();
        SegurancaProperties.Jwt jwt = properties.jwt();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwt.issuer())
                .audience(List.of(jwt.audience()))
                .subject(usuario.getId().toString())
                .issuedAt(agora)
                .expiresAt(agora.plus(jwt.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .claim(JwtConfig.CLAIM_VERSAO_CREDENCIAIS, usuario.getVersaoCredenciais())
                .build();
        JwsHeader cabecalho = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return jwtEncoder.encode(JwtEncoderParameters.from(cabecalho, claims)).getTokenValue();
    }

    public long accessTokenTtlSegundos() {
        return properties.jwt().accessTokenTtl().toSeconds();
    }
}
