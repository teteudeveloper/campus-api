package br.com.campusapi.cadastro;

import br.com.campusapi.aluno.Aluno;
import br.com.campusapi.disciplina.Disciplina;
import br.com.campusapi.professor.Professor;
import br.com.campusapi.suporte.IntegracaoTestBase;
import br.com.campusapi.usuario.Perfil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CadastrosIntegracaoTest extends IntegracaoTestBase {

    private String admin;

    @BeforeEach
    void preparar() throws Exception {
        admin = tokenAdmin();
    }

    // ---------- alunos ----------

    @Test
    void crudDeAlunoComNormalizacao() throws Exception {
        var criado = lerJson(postAs(admin, Map.of("nome", "  Lucas   Oliveira ", "email", " Lucas.Oliveira@Email.COM ",
                "matricula", " 2024a01 "), "/alunos")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/alunos/")))
                .andExpect(jsonPath("$.nome").value("Lucas Oliveira"))
                .andExpect(jsonPath("$.email").value("lucas.oliveira@email.com"))
                .andExpect(jsonPath("$.matricula").value("2024A01")));
        String id = criado.get("id").asString();

        getAs(admin, "/alunos/{id}", id).andExpect(status().isOk());
        putAs(admin, Map.of("nome", "Lucas Silva", "email", "lucas@email.com", "matricula", "2024A01"), "/alunos/{id}", id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Lucas Silva"));
        getAs(admin, "/alunos?nome=SILV").andExpect(jsonPath("$.totalElementos").value(1));
        getAs(admin, "/alunos?email=LUCAS@email.com").andExpect(jsonPath("$.totalElementos").value(1));
        deleteAs(admin, "/alunos/{id}", id).andExpect(status().isNoContent());
        getAs(admin, "/alunos/{id}", id).andExpect(status().isNotFound());
    }

    @Test
    void validacaoDeAlunoRetornaErrosPorCampo() throws Exception {
        postAs(admin, Map.of("nome", "", "email", "invalido", "matricula", "x".repeat(21)), "/alunos")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.erros[?(@.campo=='nome')]").exists())
                .andExpect(jsonPath("$.erros[?(@.campo=='email')]").exists())
                .andExpect(jsonPath("$.erros[?(@.campo=='matricula')]").exists());
        postAs(admin, Map.of("nome", "Sem Email", "matricula", "1"), "/alunos")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[0].campo").value("email"));
    }

    @Test
    void unicidadeDeEmailEMatriculaIgnoraCaixaEEspacos() throws Exception {
        postAs(admin, Map.of("nome", "Um", "email", "um@email.com", "matricula", "M1"), "/alunos").andExpect(status().isCreated());
        postAs(admin, Map.of("nome", "Dois", "email", " UM@email.com", "matricula", "M2"), "/alunos")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erros[0].campo").value("email"));
        postAs(admin, Map.of("nome", "Dois", "email", "dois@email.com", "matricula", "m1 "), "/alunos")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erros[0].campo").value("matricula"));
        Aluno dois = novoAluno("Dois", "M2");
        putAs(admin, Map.of("nome", "Dois", "email", "um@email.com", "matricula", "M2"), "/alunos/{id}", dois.getId())
                .andExpect(status().isConflict());
    }

    @Test
    void conflitoDeUnicidadeNoBancoEhTraduzidoSemExporSql() throws Exception {
        Aluno existente = novoAluno("Existente", "M-DB");
        // Inserção direta simulando corrida entre verificação e gravação: a constraint do banco decide.
        Aluno duplicado = new Aluno();
        duplicado.setNome("Duplicado");
        duplicado.setEmail(existente.getEmail());
        duplicado.setMatricula("M-DB2");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> alunoRepository.saveAndFlush(duplicado))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        postAs(admin, Map.of("nome", "X", "email", existente.getEmail(), "matricula", "M-DB3"), "/alunos")
                .andExpect(status().isConflict())
                .andExpect(content().string(not(containsString("uk_alunos_email"))))
                .andExpect(content().string(not(containsString("SQL"))));
    }

    @Test
    void exclusaoDeAlunoComMatriculasOuUsuarioEhBloqueada() throws Exception {
        Aluno comMatricula = novoAluno("Com Matricula", "M10");
        novaMatricula(comMatricula, novaDisciplina("D-10", null));
        Aluno comUsuario = novoAluno("Com Usuario", "M11");
        criarUsuario("com.usuario", Perfil.ALUNO, comUsuario.getId(), null);

        deleteAs(admin, "/alunos/{id}", comMatricula.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("REGISTRO_EM_USO"));
        deleteAs(admin, "/alunos/{id}", comUsuario.getId()).andExpect(status().isConflict());
        assertThat(alunoRepository.count()).isEqualTo(2);
        assertThat(matriculaRepository.count()).isEqualTo(1);
    }

    // ---------- professores ----------

    @Test
    void crudDeProfessorEExclusaoComVinculos() throws Exception {
        var criado = lerJson(postAs(admin, Map.of("nome", "Ana Paula", "email", "Ana@Campus.com", "registro", "prof-2019-001",
                "especialidade", " Física "), "/professores")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registro").value("PROF-2019-001"))
                .andExpect(jsonPath("$.especialidade").value("Física")));
        UUID id = UUID.fromString(criado.get("id").asString());

        postAs(admin, Map.of("nome", "Outra", "email", "outra@campus.com", "registro", "PROF-2019-001"), "/professores")
                .andExpect(status().isConflict());
        Map<String, Object> semEspecialidade = new HashMap<>(Map.of("nome", "Ana Paula", "email", "ana@campus.com",
                "registro", "PROF-2019-001"));
        putAs(admin, semEspecialidade, "/professores/{id}", id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.especialidade").isEmpty());

        Disciplina disciplina = novaDisciplina("D-20", professorRepository.findById(id).orElseThrow());
        deleteAs(admin, "/professores/{id}", id).andExpect(status().isConflict());
        deleteAs(admin, "/disciplinas/{id}/professor", disciplina.getId()).andExpect(status().isOk());
        deleteAs(admin, "/professores/{id}", id).andExpect(status().isNoContent());
        assertThat(disciplinaRepository.findById(disciplina.getId())).isPresent();
    }

    // ---------- disciplinas ----------

    @Test
    void crudDeDisciplinaESemanticaDoProfessor() throws Exception {
        Professor p1 = novoProfessor("Prof Um", "R1");
        Professor p2 = novoProfessor("Prof Dois", "R2");
        var criada = lerJson(postAs(admin, Map.of("nome", "Cálculo I", "codigo", "mat-101", "cargaHoraria", 80,
                "professorId", p1.getId()), "/disciplinas")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value("MAT-101"))
                .andExpect(jsonPath("$.professor.nome").value("Prof Um"))
                .andExpect(jsonPath("$.alunos").doesNotExist()));
        UUID id = UUID.fromString(criada.get("id").asString());

        // PUT sem professorId mantém o professor (compatibilidade com o contrato anterior)
        putAs(admin, Map.of("nome", "Cálculo I", "codigo", "MAT-101", "cargaHoraria", 90), "/disciplinas/{id}", id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cargaHoraria").value(90))
                .andExpect(jsonPath("$.professor.id").value(p1.getId().toString()));
        putAs(admin, Map.of("professorId", p2.getId()), "/disciplinas/{id}/professor", id)
                .andExpect(jsonPath("$.professor.id").value(p2.getId().toString()));
        putAs(admin, Map.of("professorId", UUID.randomUUID()), "/disciplinas/{id}/professor", id)
                .andExpect(status().isNotFound());
        deleteAs(admin, "/disciplinas/{id}/professor", id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.professor").isEmpty());

        getAs(admin, "/disciplinas/professor/{id}", p2.getId()).andExpect(jsonPath("$.totalElementos").value(0));
        getAs(admin, "/disciplinas/professor/{id}", UUID.randomUUID()).andExpect(status().isNotFound());
        deleteAs(admin, "/disciplinas/{id}", id).andExpect(status().isNoContent());
    }

    @Test
    void validacaoDeDisciplina() throws Exception {
        postAs(admin, Map.of("nome", "Sem carga", "codigo", "X-1"), "/disciplinas")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[0].campo").value("cargaHoraria"));
        postAs(admin, Map.of("nome", "Carga zero", "codigo", "X-1", "cargaHoraria", 0), "/disciplinas")
                .andExpect(status().isBadRequest());
        postAs(admin, Map.of("nome", "Código inválido", "codigo", "X 1 #", "cargaHoraria", 10), "/disciplinas")
                .andExpect(status().isBadRequest());
        postAs(admin, Map.of("nome", "Professor inexistente", "codigo", "X-2", "cargaHoraria", 10,
                "professorId", UUID.randomUUID()), "/disciplinas")
                .andExpect(status().isNotFound());
        postAs(admin, Map.of("nome", "A", "codigo", "X-3", "cargaHoraria", 10), "/disciplinas").andExpect(status().isCreated());
        postAs(admin, Map.of("nome", "B", "codigo", "x-3", "cargaHoraria", 10), "/disciplinas").andExpect(status().isConflict());
    }

    @Test
    void exclusaoDeDisciplinaComMatriculasEhBloqueadaEPreservaHistorico() throws Exception {
        Disciplina disciplina = novaDisciplina("D-30", null);
        novaMatriculaComNotas(novoAluno("Aluno", "M30"), disciplina, "9", "9");
        deleteAs(admin, "/disciplinas/{id}", disciplina.getId()).andExpect(status().isConflict());
        assertThat(matriculaRepository.count()).isEqualTo(1);
    }

    // ---------- paginação e contrato de erros ----------

    @Test
    void paginacaoRespeitaLimiteMaximoOrdenacaoValidadaEOrdemEstavel() throws Exception {
        for (int i = 0; i < 5; i++) {
            novoAluno("Mesmo Nome", "P" + i);
        }
        getAs(admin, "/alunos?size=500").andExpect(jsonPath("$.tamanho").value(100));
        getAs(admin, "/alunos?sort=senha,asc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
        String pagina1 = getAs(admin, "/alunos?size=2&page=0&sort=nome").andReturn().getResponse().getContentAsString();
        String pagina2 = getAs(admin, "/alunos?size=2&page=1&sort=nome").andReturn().getResponse().getContentAsString();
        var ids1 = json.readTree(pagina1).get("conteudo").findValuesAsString("id");
        var ids2 = json.readTree(pagina2).get("conteudo").findValuesAsString("id");
        assertThat(ids1).doesNotContainAnyElementsOf(ids2);
        getAs(admin, "/alunos?size=2&page=10").andExpect(jsonPath("$.conteudo").isEmpty());
        getAs(admin, "/alunos?size=100&page=2147483647")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }

    @Test
    void errosDeContratoSaoPadronizados() throws Exception {
        getAs(admin, "/alunos/{id}", "123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[0].campo").value("id"));
        postAs(admin, "{nome:", "/alunos")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("JSON_INVALIDO"))
                .andExpect(content().string(not(containsString("tools.jackson"))));
        postAs(admin, Map.of("nome", "A", "email", "a@a.com", "matricula", "M", "id", UUID.randomUUID()), "/alunos")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[0].campo").value("id"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/alunos")
                        .header("Authorization", "Bearer " + admin).contentType("text/plain").content("x"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/alunos")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isMethodNotAllowed());
        getAs(admin, "/alunos/{id}", UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:campus-api:problema:recurso-nao-encontrado"))
                .andExpect(jsonPath("$.instance").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
