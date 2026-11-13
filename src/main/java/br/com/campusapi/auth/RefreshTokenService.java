package br.com.campusapi.auth;

import br.com.campusapi.seguranca.SegurancaProperties;
import br.com.campusapi.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Geração e revogação de refresh tokens. O valor entregue ao cliente tem 256 bits aleatórios; como a
 * entropia é alta, SHA-256 é suficiente para o armazenamento (não é necessário hash lento como em senhas).
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final Duration RETENCAO_APOS_EXPIRAR = Duration.ofDays(1);

    private final RefreshTokenRepository repository;
    private final SegurancaProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public record TokenEmitido(String valor, RefreshToken registro) {
    }

    public TokenEmitido emitir(Usuario usuario, UUID familiaId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String valor = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant agora = clock.instant();
        RefreshToken registro = repository.save(new RefreshToken(usuario.getId(), familiaId,
                usuario.getVersaoCredenciais(), hash(valor), agora,
                agora.plus(properties.jwt().refreshTokenTtl())));
        return new TokenEmitido(valor, registro);
    }

    public void revogarTodos(UUID usuarioId, MotivoRevogacao motivo) {
        int revogados = repository.revogarTodosDoUsuario(usuarioId, motivo, clock.instant());
        log.info("Sessões de renovação revogadas: usuario={} motivo={} quantidade={}", usuarioId, motivo, revogados);
    }

    public long refreshTtlSegundos() {
        return properties.jwt().refreshTokenTtl().toSeconds();
    }

    /** Remove periodicamente tokens expirados há mais de um dia. */
    @Scheduled(cron = "${app.security.refresh-token-limpeza-cron:0 30 3 * * *}")
    @Transactional
    public void excluirExpirados() {
        int removidos = repository.excluirExpiradosAntesDe(clock.instant().minus(RETENCAO_APOS_EXPIRAR));
        log.info("Limpeza de refresh tokens expirados: {} removidos", removidos);
    }

    public static String hash(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
