package br.com.campusapi.auth;

import br.com.campusapi.auth.dto.AlterarSenhaRequestDTO;
import br.com.campusapi.auth.dto.LoginRequestDTO;
import br.com.campusapi.auth.dto.TokenResponseDTO;
import br.com.campusapi.exception.ApiException;
import br.com.campusapi.exception.CodigoErro;
import br.com.campusapi.exception.RequisicaoInvalidaException;
import br.com.campusapi.seguranca.LimitadorTentativasLogin;
import br.com.campusapi.seguranca.TokenService;
import br.com.campusapi.seguranca.UsuarioAutenticado;
import br.com.campusapi.usuario.PoliticaSenha;
import br.com.campusapi.usuario.Usuario;
import br.com.campusapi.usuario.UsuarioRepository;
import br.com.campusapi.usuario.dto.UsuarioResponseDTO;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UsuarioRepository usuarioRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final LimitadorTentativasLogin limitador;
    private final PoliticaSenha politicaSenha;
    private final Clock clock;

    /** Hash válido comparado quando o login não existe, para que o tempo de resposta não revele contas existentes. */
    private volatile String hashFicticio;

    /** Falha de autenticação genérica: mesma resposta para login inexistente, senha errada ou conta inativa. */
    public static class CredenciaisInvalidasException extends ApiException {
        public CredenciaisInvalidasException() {
            super(HttpStatus.UNAUTHORIZED, CodigoErro.CREDENCIAIS_INVALIDAS, "Login ou senha inválidos");
        }
    }

    public static class RefreshTokenInvalidoException extends ApiException {
        public RefreshTokenInvalidoException() {
            super(HttpStatus.UNAUTHORIZED, CodigoErro.TOKEN_INVALIDO, "Refresh token inválido, expirado ou revogado");
        }
    }

    @Transactional
    public TokenResponseDTO login(LoginRequestDTO dto, String ip) {
        String login = dto.login();
        limitador.verificar(login, ip);
        Optional<Usuario> encontrado = usuarioRepository.findByLogin(login);
        String hash = encontrado.map(Usuario::getSenhaHash).orElseGet(this::hashFicticio);
        boolean senhaConfere = passwordEncoder.matches(dto.senha(), hash);
        if (encontrado.isEmpty() || !senhaConfere || !encontrado.get().isAtivo()) {
            limitador.registrarFalha(login, ip);
            log.info("Falha de login (ip={})", ip);
            throw new CredenciaisInvalidasException();
        }
        limitador.registrarSucesso(login, ip);
        Usuario usuario = encontrado.get();
        if (passwordEncoder.upgradeEncoding(usuario.getSenhaHash())) {
            usuario.alterarSenha(passwordEncoder.encode(dto.senha()));
        }
        log.info("Login realizado: usuario={}", usuario.getId());
        return emitirTokens(usuario, UUID.randomUUID());
    }

    /**
     * Rotação: o token apresentado é revogado (ROTACIONADO) e um novo é emitido na mesma família. Reapresentar
     * um token já rotacionado indica vazamento: toda a família é revogada. As revogações são confirmadas mesmo
     * quando a resposta é 401 (noRollbackFor).
     */
    @Transactional(noRollbackFor = RefreshTokenInvalidoException.class)
    public TokenResponseDTO renovar(String valor) {
        Instant agora = clock.instant();
        RefreshToken atual = refreshTokenRepository.findParaAtualizacaoByTokenHash(RefreshTokenService.hash(valor))
                .orElseThrow(RefreshTokenInvalidoException::new);
        if (atual.isRevogado()) {
            if (atual.getMotivoRevogacao() == MotivoRevogacao.ROTACIONADO) {
                int revogados = refreshTokenRepository.revogarFamilia(atual.getFamiliaId(),
                        MotivoRevogacao.REUTILIZACAO_DETECTADA, agora);
                log.warn("Reutilização de refresh token detectada: usuario={} familia={} revogados={}",
                        atual.getUsuarioId(), atual.getFamiliaId(), revogados);
            }
            throw new RefreshTokenInvalidoException();
        }
        if (atual.isExpirado(agora)) {
            throw new RefreshTokenInvalidoException();
        }
        // A versão gravada no token cobre a corrida com troca de senha/perfil/desativação concorrente: um sucessor
        // emitido durante a revogação em massa herda a versão antiga e é rejeitado na próxima renovação.
        Usuario usuario = usuarioRepository.findById(atual.getUsuarioId())
                .filter(Usuario::isAtivo)
                .filter(u -> u.getVersaoCredenciais() == atual.getVersaoCredenciais())
                .orElse(null);
        if (usuario == null) {
            refreshTokenRepository.revogarFamilia(atual.getFamiliaId(), MotivoRevogacao.USUARIO_DESATIVADO, agora);
            throw new RefreshTokenInvalidoException();
        }
        RefreshTokenService.TokenEmitido novo = refreshTokenService.emitir(usuario, atual.getFamiliaId());
        atual.revogar(MotivoRevogacao.ROTACIONADO, agora, novo.registro().getId());
        return resposta(usuario, novo.valor());
    }

    /**
     * Revoga a sessão de renovação (família) do refresh token informado, mesmo que ele já tenha sido rotacionado
     * (o sucessor ativo também é revogado). Responde sempre com sucesso para não revelar se o token existia.
     * O access token em uso continua válido até expirar (ver README).
     */
    @Transactional
    public void logout(String valor) {
        refreshTokenRepository.findParaAtualizacaoByTokenHash(RefreshTokenService.hash(valor))
                .ifPresent(token -> {
                    refreshTokenRepository.revogarFamilia(token.getFamiliaId(), MotivoRevogacao.LOGOUT, clock.instant());
                    log.info("Logout: usuario={} familia={}", token.getUsuarioId(), token.getFamiliaId());
                });
    }

    @Transactional(readOnly = true)
    public UsuarioResponseDTO usuarioAtual(UsuarioAutenticado autenticado) {
        return usuarioRepository.findById(autenticado.id()).map(UsuarioResponseDTO::de)
                .orElseThrow(CredenciaisInvalidasException::new);
    }

    /**
     * Exige a senha atual. Após a troca, todos os refresh tokens são revogados e os access tokens emitidos
     * deixam de valer (versão das credenciais incrementada): o usuário deve autenticar-se novamente.
     */
    @Transactional
    public void alterarSenha(UsuarioAutenticado autenticado, AlterarSenhaRequestDTO dto, String ip) {
        String chave = "senha:" + autenticado.id();
        limitador.verificarCredencial(chave, ip);
        Usuario usuario = usuarioRepository.findById(autenticado.id()).orElseThrow(CredenciaisInvalidasException::new);
        if (!passwordEncoder.matches(dto.senhaAtual(), usuario.getSenhaHash())) {
            limitador.registrarFalhaCredencial(chave, ip);
            throw new RequisicaoInvalidaException(CodigoErro.SENHA_ATUAL_INCORRETA, "Senha atual incorreta", "senhaAtual");
        }
        limitador.registrarSucesso(chave, ip);
        if (passwordEncoder.matches(dto.novaSenha(), usuario.getSenhaHash())) {
            throw new RequisicaoInvalidaException(CodigoErro.VALIDACAO, "A nova senha deve ser diferente da atual", "novaSenha");
        }
        politicaSenha.validar(dto.novaSenha(), "novaSenha");
        usuario.alterarSenha(passwordEncoder.encode(dto.novaSenha()));
        usuario.invalidarTokensEmitidos();
        usuarioRepository.saveAndFlush(usuario);
        refreshTokenService.revogarTodos(usuario.getId(), MotivoRevogacao.SENHA_ALTERADA);
    }

    private String hashFicticio() {
        if (hashFicticio == null) {
            hashFicticio = passwordEncoder.encode(UUID.randomUUID().toString());
        }
        return hashFicticio;
    }

    private TokenResponseDTO emitirTokens(Usuario usuario, UUID familiaId) {
        return resposta(usuario, refreshTokenService.emitir(usuario, familiaId).valor());
    }

    private TokenResponseDTO resposta(Usuario usuario, String refreshToken) {
        return new TokenResponseDTO(tokenService.gerarAccessToken(usuario), "Bearer",
                tokenService.accessTokenTtlSegundos(), refreshToken, refreshTokenService.refreshTtlSegundos());
    }
}
