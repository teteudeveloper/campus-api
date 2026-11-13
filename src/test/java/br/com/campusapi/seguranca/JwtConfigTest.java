package br.com.campusapi.seguranca;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtConfigTest {

    private static SegurancaProperties propriedades(String segredo, boolean efemero) {
        return new SegurancaProperties(
                new SegurancaProperties.Jwt(segredo, efemero, "campus-api", "campus-api-clients",
                        Duration.ofMinutes(15), Duration.ofDays(7), Duration.ofSeconds(30)),
                new SegurancaProperties.Login(5, 20, Duration.ofMinutes(15)),
                new SegurancaProperties.Cors(null));
    }

    @Test
    void falhaComMensagemClaraQuandoOSegredoNaoEhInformadoForaDoDev() {
        assertThatThrownBy(() -> new JwtConfig().jwtSecretKey(propriedades("", false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void rejeitaSegredoCurtoOuForaDeBase64() {
        String curto = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new JwtConfig().jwtSecretKey(propriedades(curto, false)))
                .hasMessageContaining("256 bits");
        assertThatThrownBy(() -> new JwtConfig().jwtSecretKey(propriedades("não é base64!", false)))
                .hasMessageContaining("Base64");
    }

    @Test
    void aceitaSegredoDe256BitsEChaveEfemeraApenasQuandoPermitida() {
        String valido = Base64.getEncoder().encodeToString(new byte[32]);
        assertThat(new JwtConfig().jwtSecretKey(propriedades(valido, false)).getEncoded()).hasSize(32);
        assertThat(new JwtConfig().jwtSecretKey(propriedades(null, true)).getEncoded()).hasSize(32);
    }
}
