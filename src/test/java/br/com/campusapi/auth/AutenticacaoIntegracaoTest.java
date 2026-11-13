package br.com.campusapi.auth;

import br.com.campusapi.auth.dto.TokenResponseDTO;
import br.com.campusapi.suporte.IntegracaoTestBase;
import br.com.campusapi.usuario.Perfil;
import br.com.campusapi.usuario.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AutenticacaoIntegracaoTest extends IntegracaoTestBase {

    @Autowired
    JwtEncoder jwtEncoder;
    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    private Usuario criarAdmin() {
        return criarUsuario("admin", Perfil.ADMIN, null, null);
    }

    private String loginJson(String login, String senha) {
        return corpo(Map.of("login", login, "senha", senha));
    }

    // ---------- login ----------

    @Test
    void loginValidoRetornaTokensSemCacheEComPayloadMinimo() throws Exception {
        Usuario admin = criarAdmin();
        var resposta = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("  ADMIN ", SENHA)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(600))
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();
        TokenResponseDTO tokens = json.readValue(resposta.getResponse().getContentAsString(), TokenResponseDTO.class);

        String payload = new String(Base64.getUrlDecoder().decode(tokens.accessToken().split("\\.")[1]),
                StandardCharsets.UTF_8);
        assertThat(payload).contains("\"sub\":\"" + admin.getId() + "\"", "\"iss\":\"campus-api\"",
                "\"aud\":\"campus-api-clients\"", "\"ver\":0");
        assertThat(payload).doesNotContain("admin\"", "ADMIN", "senha", "perfil");

        String hashPersistido = jdbc.queryForObject("select token_hash from refresh_tokens", String.class);
        assertThat(hashPersistido).hasSize(64).isNotEqualTo(tokens.refreshToken())
                .isEqualTo(RefreshTokenService.hash(tokens.refreshToken()));
    }

    @Test
    void senhaErradaLoginInexistenteEUsuarioInativoRecebemAMesmaResposta() throws Exception {
        criarAdmin();
        Usuario inativo = criarUsuario("inativo", Perfil.ADMIN, null, null);
        inativo.desativar();
        usuarioRepository.saveAndFlush(inativo);

        String[] respostas = new String[3];
        String[][] tentativas = {{"admin", "SenhaErrada999"}, {"nao.existe", SENHA}, {"inativo", SENHA}};
        for (int i = 0; i < tentativas.length; i++) {
            respostas[i] = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson(tentativas[i][0], tentativas[i][1])))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.codigo").value("CREDENCIAIS_INVALIDAS"))
                    .andReturn().getResponse().getContentAsString();
        }
        for (String resposta : respostas) {
            assertThat(json.readTree(resposta).get("detail").asString()).isEqualTo("Login ou senha inválidos");
        }
    }

    @Test
    void loginSemCamposRetornaErroDeValidacao() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"login\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.erros[?(@.campo=='senha')]").exists());
    }

    @Test
    void excessoDeTentativasBloqueiaInclusiveComSenhaCorretaERetornaRetryAfter() throws Exception {
        criarAdmin();
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson("admin", "SenhaErrada999")))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginJson("admin", SENHA)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.codigo").value("MUITAS_TENTATIVAS"));

        // Outro IP não é afetado pelo bloqueio da credencial
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginJson("admin", SENHA))
                        .with(req -> {
                            req.setRemoteAddr("10.1.2.3");
                            return req;
                        }))
                .andExpect(status().isOk());
    }

    // ---------- access token ----------

    @Test
    void tokenAusenteRetorna401ComCorrelationId() throws Exception {
        mvc.perform(get("/auth/me").header("X-Correlation-Id", "teste-123"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-Id", "teste-123"))
                .andExpect(header().string("WWW-Authenticate", containsString("Bearer")))
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"))
                .andExpect(jsonPath("$.correlationId").value("teste-123"));
    }

    @Test
    void correlationIdForaDoFormatoESubstituido() throws Exception {
        mvc.perform(get("/auth/me").header("X-Correlation-Id", "injeção\r\nlog"))
                .andExpect(header().string("X-Correlation-Id", not(containsString("log"))));
    }

    @Test
    void tokenAdulteradoRetorna401() throws Exception {
        Usuario admin = criarAdmin();
        Usuario outro = criarUsuario("outro", Perfil.ADMIN, null, null);
        String token = token("admin");
        String[] partes = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8)
                .replace(admin.getId().toString(), outro.getId().toString());
        String adulterado = partes[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + partes[2];

        getAs(adulterado, "/auth/me")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("TOKEN_INVALIDO"));
    }

    @Test
    void tokenSemAssinaturaAlgNoneRetorna401() throws Exception {
        Usuario admin = criarAdmin();
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        long exp = Instant.now().plusSeconds(300).getEpochSecond();
        String header = b64.encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = b64.encodeToString(("{\"sub\":\"" + admin.getId() + "\",\"iss\":\"campus-api\","
                + "\"aud\":\"campus-api-clients\",\"ver\":0,\"exp\":" + exp + "}").getBytes(StandardCharsets.UTF_8));

        getAs(header + "." + payload + ".", "/auth/me").andExpect(status().isUnauthorized());
    }

    @Test
    void tokenExpiradoEmissorOuAudienciaInvalidosRetornam401() throws Exception {
        Usuario admin = criarAdmin();
        Instant agora = Instant.now();
        String expirado = assinar(admin, "campus-api", "campus-api-clients",
                agora.minus(30, ChronoUnit.MINUTES), agora.minus(5, ChronoUnit.MINUTES));
        String outroEmissor = assinar(admin, "outro-emissor", "campus-api-clients", agora, agora.plusSeconds(300));
        String outraAudiencia = assinar(admin, "campus-api", "outra-api", agora, agora.plusSeconds(300));
        String valido = assinar(admin, "campus-api", "campus-api-clients", agora, agora.plusSeconds(300));

        getAs(expirado, "/auth/me").andExpect(status().isUnauthorized());
        getAs(outroEmissor, "/auth/me").andExpect(status().isUnauthorized());
        getAs(outraAudiencia, "/auth/me").andExpect(status().isUnauthorized());
        getAs(valido, "/auth/me").andExpect(status().isOk());
    }

    private String assinar(Usuario usuario, String emissor, String audiencia, Instant emitidoEm, Instant expiraEm) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(emissor).audience(List.of(audiencia)).subject(usuario.getId().toString())
                .issuedAt(emitidoEm).expiresAt(expiraEm).claim("ver", usuario.getVersaoCredenciais())
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    @Test
    void meRetornaUsuarioSemSenha() throws Exception {
        criarAdmin();
        getAs(token("admin"), "/auth/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("admin"))
                .andExpect(jsonPath("$.perfil").value("ADMIN"))
                .andExpect(jsonPath("$.senhaHash").doesNotExist())
                .andExpect(content().string(not(containsString("bcrypt"))));
    }

    // ---------- refresh token ----------

    @Test
    void renovacaoRotacionaOToken() throws Exception {
        criarAdmin();
        TokenResponseDTO inicial = login("admin", SENHA);
        TokenResponseDTO renovado = renovar(inicial.refreshToken());

        assertThat(renovado.refreshToken()).isNotEqualTo(inicial.refreshToken());
        getAs(renovado.accessToken(), "/auth/me").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select motivo_revogacao from refresh_tokens where token_hash = ?",
                String.class, RefreshTokenService.hash(inicial.refreshToken()))).isEqualTo("ROTACIONADO");
    }

    @Test
    void reutilizacaoDeRefreshTokenRevogaTodaASessao() throws Exception {
        Usuario admin = criarAdmin();
        TokenResponseDTO inicial = login("admin", SENHA);
        TokenResponseDTO renovado = renovar(inicial.refreshToken());

        postRefresh(inicial.refreshToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("TOKEN_INVALIDO"));
        // O token legítimo mais recente também foi revogado: a família inteira é considerada comprometida
        postRefresh(renovado.refreshToken()).andExpect(status().isUnauthorized());
        assertThat(refreshTokenRepository.countByUsuarioIdAndRevogadoEmIsNull(admin.getId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where motivo_revogacao = 'REUTILIZACAO_DETECTADA'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void refreshTokenInexistenteOuExpiradoRetorna401() throws Exception {
        criarAdmin();
        postRefresh("token-que-nao-existe").andExpect(status().isUnauthorized());

        TokenResponseDTO tokens = login("admin", SENHA);
        jdbc.update("update refresh_tokens set expira_em = now() - interval '1 minute'");
        postRefresh(tokens.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevogaRefreshTokenEAccessTokenPermaneceValidoAteExpirar() throws Exception {
        criarAdmin();
        TokenResponseDTO tokens = login("admin", SENHA);

        mvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("refreshToken", tokens.refreshToken()))))
                .andExpect(status().isNoContent());
        postRefresh(tokens.refreshToken()).andExpect(status().isUnauthorized());
        // Comportamento documentado: o access token é stateless e vale até o fim do TTL (curto)
        getAs(tokens.accessToken(), "/auth/me").andExpect(status().isOk());

        // Logout com token desconhecido não revela nada
        mvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("refreshToken", "inexistente"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void sessoesDeOutrosDispositivosNaoSaoAfetadasPeloLogout() throws Exception {
        criarAdmin();
        TokenResponseDTO dispositivo1 = login("admin", SENHA);
        TokenResponseDTO dispositivo2 = login("admin", SENHA);
        mvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("refreshToken", dispositivo1.refreshToken()))));

        postRefresh(dispositivo2.refreshToken()).andExpect(status().isOk());
    }

    @Test
    void endpointsPublicosDeAuthIgnoramBearerVencido() throws Exception {
        criarAdmin();
        TokenResponseDTO tokens = login("admin", SENHA);
        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer token.vencido.qualquer")
                        .content(corpo(Map.of("refreshToken", tokens.refreshToken()))))
                .andExpect(status().isOk());
    }

    @Test
    void logoutComTokenJaRotacionadoRevogaOSucessor() throws Exception {
        criarAdmin();
        TokenResponseDTO inicial = login("admin", SENHA);
        TokenResponseDTO renovado = renovar(inicial.refreshToken());
        mvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("refreshToken", inicial.refreshToken()))))
                .andExpect(status().isNoContent());
        postRefresh(renovado.refreshToken()).andExpect(status().isUnauthorized());
    }

    /**
     * Política estrita (documentada no README): duas renovações simultâneas com o mesmo refresh token são
     * indistinguíveis, no servidor, de um token copiado por terceiros. Uma vence (200) e a outra é tratada como
     * reutilização: a sessão inteira é revogada, inclusive o token que acabou de ser emitido.
     */
    @Test
    void renovacoesConcorrentesComOMesmoTokenEncerramASessao() throws Exception {
        criarAdmin();
        TokenResponseDTO tokens = login("admin", SENHA);
        String corpoRefresh = corpo(Map.of("refreshToken", tokens.refreshToken()));
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var largada = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Callable<java.net.http.HttpResponse<String>> renovar = () -> {
            largada.await();
            var request = java.net.http.HttpRequest.newBuilder(
                            java.net.URI.create("http://localhost:" + porta + "/auth/refresh"))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(corpoRefresh)).build();
            return java.net.http.HttpClient.newHttpClient()
                    .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        };
        var r1 = executor.submit(renovar);
        var r2 = executor.submit(renovar);
        largada.countDown();
        var respostas = java.util.List.of(r1.get(), r2.get());
        executor.shutdownNow();

        assertThat(respostas).extracting(java.net.http.HttpResponse::statusCode).containsExactlyInAnyOrder(200, 401);
        String vencedor = respostas.stream().filter(r -> r.statusCode() == 200).findFirst().orElseThrow().body();
        String novoRefresh = json.readTree(vencedor).get("refreshToken").asString();
        postRefresh(novoRefresh).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject(
                "select count(*) from refresh_tokens where motivo_revogacao = 'REUTILIZACAO_DETECTADA'", Integer.class))
                .isEqualTo(1);
        // O access token emitido pela renovação vencedora continua válido até expirar; o usuário refaz o login.
        getAs(json.readTree(vencedor).get("accessToken").asString(), "/auth/me").andExpect(status().isOk());
    }

    @Test
    void logoutComTokenJaRevogadoOuExpiradoRetorna204SemEfeitosColaterais() throws Exception {
        criarAdmin();
        TokenResponseDTO revogado = login("admin", SENHA);
        TokenResponseDTO outraSessao = login("admin", SENHA);
        TokenResponseDTO expirado = login("admin", SENHA);
        mvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("refreshToken", revogado.refreshToken()))));
        jdbc.update("update refresh_tokens set expira_em = now() - interval '1 minute' where token_hash = ?",
                RefreshTokenService.hash(expirado.refreshToken()));

        for (String token : new String[]{revogado.refreshToken(), expirado.refreshToken()}) {
            mvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(Map.of("refreshToken", token))))
                    .andExpect(status().isNoContent());
        }
        postRefresh(expirado.refreshToken()).andExpect(status().isUnauthorized());
        // Sessões independentes não são afetadas
        postRefresh(outraSessao.refreshToken()).andExpect(status().isOk());
    }

    @Test
    void refreshTokenEmitidoComVersaoAntigaDeCredenciaisEhRejeitado() throws Exception {
        // Simula a corrida: sucessor emitido enquanto a troca de senha revogava as sessões.
        criarAdmin();
        TokenResponseDTO tokens = login("admin", SENHA);
        jdbc.update("update usuarios set versao_credenciais = versao_credenciais + 1 where login = 'admin'");
        postRefresh(tokens.refreshToken()).andExpect(status().isUnauthorized());
    }

    // ---------- troca de senha, desativação, mudança de permissões ----------

    @Test
    void trocaDeSenhaExigeSenhaAtualEInvalidaTodasAsSessoes() throws Exception {
        criarAdmin();
        TokenResponseDTO tokens = login("admin", SENHA);

        putAs(tokens.accessToken(), Map.of("senhaAtual", "Incorreta12345", "novaSenha", "NovaSenha2026"), "/auth/senha")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SENHA_ATUAL_INCORRETA"));
        putAs(tokens.accessToken(), Map.of("senhaAtual", SENHA, "novaSenha", "semdigitos"), "/auth/senha")
                .andExpect(status().isBadRequest());

        putAs(tokens.accessToken(), Map.of("senhaAtual", SENHA, "novaSenha", "NovaSenha2026"), "/auth/senha")
                .andExpect(status().isNoContent());

        getAs(tokens.accessToken(), "/auth/me").andExpect(status().isUnauthorized());
        postRefresh(tokens.refreshToken()).andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginJson("admin", SENHA)))
                .andExpect(status().isUnauthorized());
        assertThat(login("admin", "NovaSenha2026").accessToken()).isNotBlank();
    }

    @Test
    void desativacaoInvalidaAccessERefreshTokensImediatamente() throws Exception {
        criarAdmin();
        String admin = token("admin");
        Usuario professor = criarUsuario("prof", Perfil.ADMIN, null, null);
        TokenResponseDTO tokensProf = login("prof", SENHA);

        postAs(admin, null, "/usuarios/{id}/desativar", professor.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));

        getAs(tokensProf.accessToken(), "/auth/me").andExpect(status().isUnauthorized());
        postRefresh(tokensProf.refreshToken()).andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginJson("prof", SENHA)))
                .andExpect(status().isUnauthorized());

        postAs(admin, null, "/usuarios/{id}/ativar", professor.getId()).andExpect(status().isOk());
        assertThat(login("prof", SENHA).accessToken()).isNotBlank();
    }

    @Test
    void mudancaDePerfilInvalidaTokensEmitidosComPermissoesAnteriores() throws Exception {
        criarAdmin();
        String admin = token("admin");
        var aluno = novoAluno("Aluno Rebaixado", "A-900");
        Usuario outroAdmin = criarUsuario("outro.admin", Perfil.ADMIN, null, null);
        TokenResponseDTO tokensAntigos = login("outro.admin", SENHA);

        putAs(admin, Map.of("perfil", "ALUNO", "alunoId", aluno.getId()), "/usuarios/{id}/perfil", outroAdmin.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil").value("ALUNO"));

        getAs(tokensAntigos.accessToken(), "/usuarios").andExpect(status().isUnauthorized());
        postRefresh(tokensAntigos.refreshToken()).andExpect(status().isUnauthorized());
        String novo = token("outro.admin");
        getAs(novo, "/usuarios").andExpect(status().isForbidden());
        getAs(novo, "/alunos/{id}", aluno.getId()).andExpect(status().isOk());
    }

    private TokenResponseDTO renovar(String refreshToken) throws Exception {
        String corpoResposta = postRefresh(refreshToken).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readValue(corpoResposta, TokenResponseDTO.class);
    }

    private org.springframework.test.web.servlet.ResultActions postRefresh(String refreshToken) throws Exception {
        return mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("refreshToken", refreshToken))));
    }
}
