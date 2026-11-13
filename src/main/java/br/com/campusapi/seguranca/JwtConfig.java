package br.com.campusapi.seguranca;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Objects;

/**
 * Access tokens JWT assinados com HMAC-SHA256 (HS256). Apenas HS256 é aceito; tokens com outro algoritmo,
 * sem assinatura ("alg": "none"), expirados, de outro emissor ou de outra audiência são rejeitados.
 */
@Configuration
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);
    private static final int TAMANHO_MINIMO_CHAVE_BYTES = 32;
    static final String CLAIM_VERSAO_CREDENCIAIS = "ver";

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    SecretKey jwtSecretKey(SegurancaProperties properties) {
        SegurancaProperties.Jwt jwt = properties.jwt();
        byte[] chave;
        if (jwt.secret() == null || jwt.secret().isBlank()) {
            if (!jwt.permitirSegredoEfemero()) {
                throw new IllegalStateException(
                        "app.security.jwt.secret (JWT_SECRET) é obrigatório: informe uma chave Base64 com ao menos 256 bits");
            }
            chave = new byte[TAMANHO_MINIMO_CHAVE_BYTES];
            new SecureRandom().nextBytes(chave);
            log.warn("JWT_SECRET não definido: usando chave efêmera (somente desenvolvimento). Tokens não sobrevivem a reinícios.");
        } else {
            try {
                chave = Base64.getDecoder().decode(jwt.secret().strip());
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("app.security.jwt.secret deve estar em Base64");
            }
            if (chave.length < TAMANHO_MINIMO_CHAVE_BYTES) {
                throw new IllegalStateException("app.security.jwt.secret deve ter ao menos 256 bits (32 bytes)");
            }
        }
        return new SecretKeySpec(chave, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return NimbusJwtEncoder.withSecretKey(jwtSecretKey).algorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey, SegurancaProperties properties, Clock clock) {
        SegurancaProperties.Jwt jwt = properties.jwt();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        JwtTimestampValidator tempo = new JwtTimestampValidator(jwt.clockSkew());
        tempo.setClock(clock);
        OAuth2TokenValidator<Jwt> validador = new DelegatingOAuth2TokenValidator<>(
                tempo,
                new JwtClaimValidator<Object>(JwtClaimNames.EXP, Objects::nonNull),
                new JwtClaimValidator<Object>(JwtClaimNames.SUB, Objects::nonNull),
                new JwtClaimValidator<Object>(CLAIM_VERSAO_CREDENCIAIS, Objects::nonNull),
                new JwtIssuerValidator(jwt.issuer()),
                new JwtAudienceValidator(jwt.audience()));
        decoder.setJwtValidator(validador);
        return decoder;
    }
}
