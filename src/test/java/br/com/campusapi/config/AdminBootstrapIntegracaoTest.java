package br.com.campusapi.config;

import br.com.campusapi.suporte.IntegracaoTestBase;
import br.com.campusapi.usuario.Perfil;
import br.com.campusapi.usuario.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Mesmas propriedades do FluxoAcademicoCompletoTest, para reaproveitar o contexto Spring em cache. */
@TestPropertySource(properties = {
        "app.bootstrap.admin.login=diretoria",
        "app.bootstrap.admin.senha=Bootstrap2026!"})
class AdminBootstrapIntegracaoTest extends IntegracaoTestBase {

    @Autowired
    AdminBootstrap bootstrap;

    @Test
    void criaOAdministradorUmaUnicaVez() throws Exception {
        bootstrap.run(null);
        bootstrap.run(null);
        assertThat(usuarioRepository.findAll()).hasSize(1)
                .allMatch(u -> u.getPerfil() == Perfil.ADMIN && u.getLogin().equals("diretoria"));
        assertThat(login("diretoria", "Bootstrap2026!").accessToken()).isNotBlank();
    }

    @Test
    void naoSobrescreveSenhaNemReativaAdministradorExistente() {
        Usuario existente = criarUsuario("diretoria", Perfil.ADMIN, null, null);
        existente.desativar();
        usuarioRepository.saveAndFlush(existente);
        String hashAntes = existente.getSenhaHash();

        bootstrap.run(null);

        Usuario depois = usuarioRepository.findByLogin("diretoria").orElseThrow();
        assertThat(depois.getSenhaHash()).isEqualTo(hashAntes);
        assertThat(depois.isAtivo()).isFalse();
        assertThat(usuarioRepository.count()).isEqualTo(1);
    }

    @Test
    void naoPromoveContaExistenteComOMesmoLogin() {
        var aluno = novoAluno("Aluno", "B-1");
        criarUsuario("diretoria", Perfil.ALUNO, aluno.getId(), null);

        assertThatThrownBy(() -> bootstrap.run(null)).isInstanceOf(IllegalStateException.class);
        assertThat(usuarioRepository.findByLogin("diretoria").orElseThrow().getPerfil()).isEqualTo(Perfil.ALUNO);
    }
}
