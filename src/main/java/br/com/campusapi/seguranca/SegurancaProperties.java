package br.com.campusapi.seguranca;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/** Configuração externa de segurança (prefixo {@code app.security}). Ver .env.example e README. */
@Validated
@ConfigurationProperties(prefix = "app.security")
public record SegurancaProperties(@Valid @NotNull Jwt jwt, @Valid @NotNull Login login, @Valid @NotNull Cors cors) {

    /**
     * @param secret                 chave HMAC em Base64 (mínimo 256 bits). Obrigatória fora do perfil dev/test.
     * @param permitirSegredoEfemero gera uma chave aleatória quando {@code secret} está vazio (apenas desenvolvimento;
     *                               tokens deixam de valer a cada reinício).
     */
    public record Jwt(
            String secret,
            boolean permitirSegredoEfemero,
            @NotBlank String issuer,
            @NotBlank String audience,
            @NotNull Duration accessTokenTtl,
            @NotNull Duration refreshTokenTtl,
            @NotNull Duration clockSkew) {
    }

    /** Limites de tentativas de login malsucedidas por credencial (login+IP) e por IP, em janela fixa. */
    public record Login(
            @Min(1) int maxTentativasPorCredencial,
            @Min(1) int maxTentativasPorIp,
            @NotNull Duration janela) {
    }

    public record Cors(List<String> allowedOrigins) {

        public List<String> allowedOrigins() {
            return allowedOrigins == null ? List.of() : allowedOrigins.stream()
                    .map(String::strip).filter(origem -> !origem.isEmpty()).toList();
        }
    }
}
