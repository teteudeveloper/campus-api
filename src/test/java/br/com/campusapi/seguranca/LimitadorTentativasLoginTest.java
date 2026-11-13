package br.com.campusapi.seguranca;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LimitadorTentativasLoginTest {

    private static class RelogioAjustavel extends Clock {
        private Instant agora = Instant.parse("2026-01-01T10:00:00Z");

        void avancar(Duration duracao) {
            agora = agora.plus(duracao);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    private final RelogioAjustavel relogio = new RelogioAjustavel();
    private final LimitadorTentativasLogin limitador = new LimitadorTentativasLogin(new SegurancaProperties(
            null, new SegurancaProperties.Login(3, 5, Duration.ofMinutes(15)), null), relogio);

    @Test
    void bloqueiaCredencialAposLimiteEDesbloqueiaAoFimDaJanela() {
        for (int i = 0; i < 3; i++) {
            limitador.verificar("ana", "1.1.1.1");
            limitador.registrarFalha("ana", "1.1.1.1");
        }
        assertThatThrownBy(() -> limitador.verificar("ana", "1.1.1.1"))
                .isInstanceOfSatisfying(LimitadorTentativasLogin.MuitasTentativasException.class,
                        e -> assertThat(e.getRetryAfterSegundos()).isEqualTo(900));
        assertThatCode(() -> limitador.verificar("ana", "2.2.2.2")).doesNotThrowAnyException();

        relogio.avancar(Duration.ofMinutes(15));
        assertThatCode(() -> limitador.verificar("ana", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void bloqueiaIpQueTentaVariosLogins() {
        for (int i = 0; i < 5; i++) {
            limitador.registrarFalha("usuario" + i, "9.9.9.9");
        }
        assertThatThrownBy(() -> limitador.verificar("outro", "9.9.9.9"))
                .isInstanceOf(LimitadorTentativasLogin.MuitasTentativasException.class);
    }

    @Test
    void sucessoZeraContagemDaCredencialEJanelaExpiradaReinicia() {
        limitador.registrarFalha("ana", "1.1.1.1");
        limitador.registrarFalha("ana", "1.1.1.1");
        limitador.registrarSucesso("ana", "1.1.1.1");
        limitador.registrarFalha("ana", "1.1.1.1");
        limitador.registrarFalha("ana", "1.1.1.1");
        assertThatCode(() -> limitador.verificar("ana", "1.1.1.1")).doesNotThrowAnyException();

        relogio.avancar(Duration.ofMinutes(16));
        limitador.registrarFalha("ana", "1.1.1.1");
        assertThatCode(() -> limitador.verificar("ana", "1.1.1.1")).doesNotThrowAnyException();
    }
}
