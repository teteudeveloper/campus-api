package br.com.campusapi.usuario;

import br.com.campusapi.aluno.Aluno;
import br.com.campusapi.professor.Professor;
import br.com.campusapi.suporte.IntegracaoTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GestaoUsuariosIntegracaoTest extends IntegracaoTestBase {

    private String admin;
    private Aluno aluno;
    private Professor professor;

    @BeforeEach
    void preparar() throws Exception {
        admin = tokenAdmin();
        aluno = novoAluno("Ana Aluna", "2026001");
        professor = novoProfessor("Paulo Professor", "PROF-1");
    }

    private Map<String, Object> usuario(String login, String perfil, UUID alunoId, UUID professorId) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("login", login);
        corpo.put("senha", "SenhaInicial1");
        corpo.put("perfil", perfil);
        corpo.put("alunoId", alunoId);
        corpo.put("professorId", professorId);
        return corpo;
    }

    @Test
    void adminCriaContaVinculadaComLoginNormalizadoESenhaComHash() throws Exception {
        postAs(admin, usuario("Ana.Aluna", "ALUNO", aluno.getId(), null), "/usuarios")
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.login").value("ana.aluna"))
                .andExpect(jsonPath("$.alunoId").value(aluno.getId().toString()))
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.senhaHash").doesNotExist());

        String hash = jdbc.queryForObject("select senha_hash from usuarios where login = 'ana.aluna'", String.class);
        assertThat(hash).startsWith("{bcrypt}").doesNotContain("SenhaInicial1");
        assertThat(login("ana.aluna", "SenhaInicial1").accessToken()).isNotBlank();
    }

    @Test
    void regrasDeVinculoSaoValidadas() throws Exception {
        postAs(admin, usuario("aluno.a1", "ALUNO", null, null), "/usuarios").andExpect(status().isBadRequest());
        postAs(admin, usuario("aluno.a2", "ALUNO", aluno.getId(), professor.getId()), "/usuarios").andExpect(status().isBadRequest());
        postAs(admin, usuario("prof.p1", "PROFESSOR", aluno.getId(), null), "/usuarios").andExpect(status().isBadRequest());
        postAs(admin, usuario("adm2", "ADMIN", aluno.getId(), null), "/usuarios").andExpect(status().isBadRequest());
        postAs(admin, usuario("aluno.a3", "ALUNO", UUID.randomUUID(), null), "/usuarios").andExpect(status().isNotFound());
        postAs(admin, usuario("x", "ALUNO", aluno.getId(), null), "/usuarios")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erros[0].campo").value("login"));
        postAs(admin, usuario("perfil.invalido", "SUPERUSER", null, null), "/usuarios")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("JSON_INVALIDO"));
    }

    @Test
    void alunoOuProfessorNaoPodemSerVinculadosADuasContas() throws Exception {
        postAs(admin, usuario("primeira", "ALUNO", aluno.getId(), null), "/usuarios").andExpect(status().isCreated());
        postAs(admin, usuario("segunda", "ALUNO", aluno.getId(), null), "/usuarios")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("VINCULO_EXISTENTE"));
        postAs(admin, usuario("primeira", "PROFESSOR", null, professor.getId()), "/usuarios")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erros[0].campo").value("login"));
    }

    @Test
    void senhaFracaEhRejeitada() throws Exception {
        Map<String, Object> corpo = usuario("fraca", "ADMIN", null, null);
        corpo.put("senha", "somenteletras");
        postAs(admin, corpo, "/usuarios").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[0].campo").value("senha"));
    }

    @Test
    void naoExisteCadastroPublicoNemEscalacaoDePrivilegio() throws Exception {
        criarUsuario("aluno", Perfil.ALUNO, aluno.getId(), null);
        criarUsuario("prof", Perfil.PROFESSOR, null, professor.getId());
        String tokenAluno = token("aluno");
        String tokenProf = token("prof");

        // sem autenticação
        mvc.perform(post("/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(usuario("novo", "ADMIN", null, null))))
                .andExpect(status().isUnauthorized());
        // perfis não administrativos não criam contas, não mudam perfis, não listam usuários
        postAs(tokenAluno, usuario("novo", "ADMIN", null, null), "/usuarios").andExpect(status().isForbidden());
        postAs(tokenProf, usuario("novo", "ADMIN", null, null), "/usuarios").andExpect(status().isForbidden());
        Usuario contaAluno = usuarioRepository.findByLogin("aluno").orElseThrow();
        putAs(tokenAluno, Map.of("perfil", "ADMIN"), "/usuarios/{id}/perfil", contaAluno.getId())
                .andExpect(status().isForbidden());
        getAs(tokenProf, "/usuarios").andExpect(status().isForbidden());
        // campo de perfil injetado em endpoint de usuário autenticado é rejeitado
        putAs(tokenAluno, Map.of("senhaAtual", SENHA, "novaSenha", "OutraSenha123", "perfil", "ADMIN"), "/auth/senha")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("JSON_INVALIDO"));
        // rota de cadastro público inexistente é negada
        mvc.perform(post("/auth/registrar").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());

        assertThat(usuarioRepository.findByLogin("aluno").orElseThrow().getPerfil()).isEqualTo(Perfil.ALUNO);
        assertThat(usuarioRepository.count()).isEqualTo(3);
    }

    @Test
    void adminNaoPodeDesativarNemRebaixarAPropriaConta() throws Exception {
        Usuario proprio = usuarioRepository.findByLogin("admin").orElseThrow();
        postAs(admin, null, "/usuarios/{id}/desativar", proprio.getId())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("OPERACAO_NAO_PERMITIDA"));
        putAs(admin, Map.of("perfil", "ALUNO", "alunoId", aluno.getId()), "/usuarios/{id}/perfil", proprio.getId())
                .andExpect(status().isConflict());
    }

    @Test
    void doisAdminsDesativandoUmAoOutroSimultaneamenteDeixamUmAtivo() throws Exception {
        Usuario primeiro = usuarioRepository.findByLogin("admin").orElseThrow();
        Usuario segundo = criarUsuario("segundo.admin", Perfil.ADMIN, null, null);
        String tokenSegundo = token("segundo.admin");

        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var largada = new java.util.concurrent.CountDownLatch(1);
        java.util.function.BiFunction<String, UUID, java.util.concurrent.Future<Integer>> desativar = (token, alvo) ->
                executor.submit(() -> {
                    largada.await();
                    var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                                    "http://localhost:" + porta + "/usuarios/" + alvo + "/desativar"))
                            .header("Authorization", "Bearer " + token)
                            .POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build();
                    return java.net.http.HttpClient.newHttpClient()
                            .send(request, java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode();
                });
        var r1 = desativar.apply(admin, segundo.getId());
        var r2 = desativar.apply(tokenSegundo, primeiro.getId());
        largada.countDown();
        int s1 = r1.get();
        int s2 = r2.get();
        executor.shutdownNow();

        assertThat(java.util.List.of(s1, s2)).contains(200);
        assertThat(usuarioRepository.findAll()).filteredOn(u -> u.getPerfil() == Perfil.ADMIN && u.isAtivo()).hasSize(1);
    }

    @Test
    void listagemDeUsuariosFiltraEPagina() throws Exception {
        criarUsuario("aluno", Perfil.ALUNO, aluno.getId(), null);
        criarUsuario("prof", Perfil.PROFESSOR, null, professor.getId());
        getAs(admin, "/usuarios?perfil=ALUNO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].login").value("aluno"));
        getAs(admin, "/usuarios?size=1&sort=login,asc")
                .andExpect(jsonPath("$.tamanho").value(1))
                .andExpect(jsonPath("$.totalPaginas").value(3));
        getAs(admin, "/usuarios?sort=senhaHash,asc").andExpect(status().isBadRequest());
    }

    @Test
    void redefinicaoDeSenhaPeloAdminRevogaSessoes() throws Exception {
        Usuario prof = criarUsuario("prof", Perfil.PROFESSOR, null, professor.getId());
        var tokens = login("prof", SENHA);
        putAs(admin, Map.of("novaSenha", "Redefinida2026"), "/usuarios/{id}/senha", prof.getId())
                .andExpect(status().isNoContent());
        getAs(tokens.accessToken(), "/auth/me").andExpect(status().isUnauthorized());
        assertThat(login("prof", "Redefinida2026").accessToken()).isNotBlank();
    }
}
