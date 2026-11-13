package br.com.campusapi.seguranca;

import br.com.campusapi.exception.ApiException;
import br.com.campusapi.exception.CodigoErro;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Proteção contra força bruta no login, em memória, com janela fixa:
 * <ul>
 *   <li>por credencial (login normalizado + IP): bloqueia após N falhas na janela;</li>
 *   <li>por IP: bloqueia após M falhas na janela, cobrindo tentativas contra vários logins.</li>
 * </ul>
 * Uma vez bloqueada, a chave rejeita novas tentativas (inclusive com senha correta) até o fim do bloqueio.
 * Limitação: o estado é local à instância; com várias réplicas, use um armazenamento compartilhado.
 */
@Component
public class LimitadorTentativasLogin {

    private static final int LIMITE_ENTRADAS = 50_000;

    private final SegurancaProperties.Login config;
    private final Clock clock;
    private final Map<String, Janela> janelas = new ConcurrentHashMap<>();

    public LimitadorTentativasLogin(SegurancaProperties properties, Clock clock) {
        this.config = properties.login();
        this.clock = clock;
    }

    private record Janela(Instant inicio, int falhas, Instant bloqueadoAte) {
    }

    public static class MuitasTentativasException extends ApiException {

        private final long retryAfterSegundos;

        public MuitasTentativasException(long retryAfterSegundos) {
            super(HttpStatus.TOO_MANY_REQUESTS, CodigoErro.MUITAS_TENTATIVAS,
                    "Muitas tentativas malsucedidas. Tente novamente mais tarde");
            this.retryAfterSegundos = retryAfterSegundos;
        }

        public long getRetryAfterSegundos() {
            return retryAfterSegundos;
        }
    }

    public void verificar(String chaveCredencial, String ip) {
        Instant agora = clock.instant();
        long espera = Math.max(restante(chaveCredencial(chaveCredencial, ip), agora), restante(chaveIp(ip), agora));
        if (espera > 0) {
            throw new MuitasTentativasException(espera);
        }
    }

    public void registrarFalha(String chaveCredencial, String ip) {
        Instant agora = clock.instant();
        incrementar(chaveCredencial(chaveCredencial, ip), config.maxTentativasPorCredencial(), agora);
        incrementar(chaveIp(ip), config.maxTentativasPorIp(), agora);
        if (janelas.size() > LIMITE_ENTRADAS) {
            limparExpiradas(agora);
        }
    }

    /** Variante sem o limite por IP, para operações autenticadas (ex.: troca de senha) que não devem afetar o login. */
    public void verificarCredencial(String chaveCredencial, String ip) {
        long espera = restante(chaveCredencial(chaveCredencial, ip), clock.instant());
        if (espera > 0) {
            throw new MuitasTentativasException(espera);
        }
    }

    public void registrarFalhaCredencial(String chaveCredencial, String ip) {
        incrementar(chaveCredencial(chaveCredencial, ip), config.maxTentativasPorCredencial(), clock.instant());
    }

    public void registrarSucesso(String chaveCredencial, String ip) {
        janelas.remove(chaveCredencial(chaveCredencial, ip));
    }

    /** Remove todo o estado; usado em testes e disponível para operação manual. */
    public void limpar() {
        janelas.clear();
    }

    private void incrementar(String chave, int limite, Instant agora) {
        janelas.compute(chave, (k, atual) -> {
            Janela janela = atual == null || expirada(atual, agora) ? new Janela(agora, 0, null) : atual;
            int falhas = janela.falhas() + 1;
            Instant bloqueio = falhas >= limite ? agora.plus(config.janela()) : janela.bloqueadoAte();
            return new Janela(janela.inicio(), falhas, bloqueio);
        });
    }

    private long restante(String chave, Instant agora) {
        Janela janela = janelas.get(chave);
        if (janela == null || janela.bloqueadoAte() == null || !janela.bloqueadoAte().isAfter(agora)) {
            return 0;
        }
        return Math.max(1, Duration.between(agora, janela.bloqueadoAte()).toSeconds());
    }

    private boolean expirada(Janela janela, Instant agora) {
        Instant fimJanela = janela.inicio().plus(config.janela());
        Instant fim = janela.bloqueadoAte() != null && janela.bloqueadoAte().isAfter(fimJanela)
                ? janela.bloqueadoAte() : fimJanela;
        return !fim.isAfter(agora);
    }

    private void limparExpiradas(Instant agora) {
        janelas.entrySet().removeIf(entrada -> expirada(entrada.getValue(), agora));
    }

    private static String chaveCredencial(String login, String ip) {
        return "c|" + login + "|" + ip;
    }

    private static String chaveIp(String ip) {
        return "i|" + ip;
    }
}
