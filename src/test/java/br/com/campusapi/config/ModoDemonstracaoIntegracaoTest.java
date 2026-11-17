package br.com.campusapi.config;

import br.com.campusapi.suporte.IntegracaoTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "app.demo.habilitada=true",
        "app.bootstrap.admin.login=admin.demo",
        "app.bootstrap.admin.senha=DemoSenha2026x"})
class ModoDemonstracaoIntegracaoTest extends IntegracaoTestBase {

    private static final String EXEMPLO_LOGIN =
            "$.paths['/auth/login'].post.requestBody.content['application/json'].examples['1. Administrador'].value";

    @Test
    void paginaDeDemonstracaoLiberadaELoginDoAdminPreenchido() throws Exception {
        mvc.perform(get("/demo/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/demo/demo.js")));
        mvc.perform(get("/demo/demo.js")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath(EXEMPLO_LOGIN + ".login").value("admin.demo"))
                .andExpect(jsonPath(EXEMPLO_LOGIN + ".senha").value("DemoSenha2026x"))
                .andExpect(jsonPath("$.paths['/matriculas/atualizar-notas/{id}'].patch.parameters[0].example")
                        .value(ExemplosOpenApi.ID_MATRICULA));
    }

    @Test
    void demaisRotasContinuamProtegidas() throws Exception {
        mvc.perform(get("/demo/outro.html")).andExpect(status().isUnauthorized());
        mvc.perform(get("/alunos")).andExpect(status().isUnauthorized());
    }
}
