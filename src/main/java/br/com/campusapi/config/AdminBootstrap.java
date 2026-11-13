package br.com.campusapi.config;

import br.com.campusapi.comum.Textos;
import br.com.campusapi.usuario.Perfil;
import br.com.campusapi.usuario.PoliticaSenha;
import br.com.campusapi.usuario.Usuario;
import br.com.campusapi.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cria o primeiro administrador a partir de BOOTSTRAP_ADMIN_LOGIN e BOOTSTRAP_ADMIN_PASSWORD, somente se ainda
 * não existir nenhum usuário ADMIN. Sem essas variáveis nada é feito. Não há senha padrão no código; depois do
 * primeiro login, troque a senha (PUT /auth/senha) e remova as variáveis do ambiente.
 */
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final PoliticaSenha politicaSenha;

    @Value("${app.bootstrap.admin.login:}")
    private String login;

    @Value("${app.bootstrap.admin.senha:}")
    private String senha;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (login == null || login.isBlank()) {
            return;
        }
        if (senha == null || senha.isBlank()) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_LOGIN definido sem BOOTSTRAP_ADMIN_PASSWORD");
        }
        if (usuarioRepository.existsByPerfil(Perfil.ADMIN)) {
            log.info("Bootstrap de administrador ignorado: já existe usuário ADMIN. Remova BOOTSTRAP_ADMIN_* do ambiente.");
            return;
        }
        String loginNormalizado = Textos.normalizarLogin(login);
        if (usuarioRepository.existsByLogin(loginNormalizado)) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_LOGIN já pertence a um usuário que não é ADMIN");
        }
        politicaSenha.validar(senha, "BOOTSTRAP_ADMIN_PASSWORD");
        Usuario admin = usuarioRepository.save(
                new Usuario(loginNormalizado, passwordEncoder.encode(senha), Perfil.ADMIN, null, null));
        log.warn("Administrador inicial criado (id={}). Troque a senha e remova BOOTSTRAP_ADMIN_* do ambiente.",
                admin.getId());
    }
}
