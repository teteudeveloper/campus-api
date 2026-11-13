package br.com.campusapi.seguranca;

import br.com.campusapi.aluno.Aluno;
import br.com.campusapi.disciplina.Disciplina;
import br.com.campusapi.matricula.MatriculaAluno;
import br.com.campusapi.professor.Professor;
import br.com.campusapi.suporte.IntegracaoTestBase;
import br.com.campusapi.usuario.Perfil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Matriz de permissões e proteção contra acesso indevido por troca de identificadores (IDOR).
 * Cenário: professor P1 leciona D1; professor P2 leciona D2. Aluno A1 matriculado em D1 e D2; aluno A2 só em D2.
 */
class AutorizacaoPorRecursoIntegracaoTest extends IntegracaoTestBase {

    private String admin;
    private String tokenP1;
    private String tokenP2;
    private String tokenA1;
    private String tokenA2;
    private Professor p1;
    private Professor p2;
    private Aluno a1;
    private Aluno a2;
    private Disciplina d1;
    private Disciplina d2;
    private MatriculaAluno a1d1;
    private MatriculaAluno a1d2;
    private MatriculaAluno a2d2;

    @BeforeEach
    void preparar() throws Exception {
        admin = tokenAdmin();
        p1 = novoProfessor("Professor Um", "P-1");
        p2 = novoProfessor("Professor Dois", "P-2");
        a1 = novoAluno("Aluno Um", "A-1");
        a2 = novoAluno("Aluno Dois", "A-2");
        d1 = novaDisciplina("D-1", p1);
        d2 = novaDisciplina("D-2", p2);
        a1d1 = novaMatricula(a1, d1);
        a1d2 = novaMatricula(a1, d2);
        a2d2 = novaMatricula(a2, d2);
        criarUsuario("prof.um", Perfil.PROFESSOR, null, p1.getId());
        criarUsuario("prof.dois", Perfil.PROFESSOR, null, p2.getId());
        criarUsuario("aluno.um", Perfil.ALUNO, a1.getId(), null);
        criarUsuario("aluno.dois", Perfil.ALUNO, a2.getId(), null);
        tokenP1 = token("prof.um");
        tokenP2 = token("prof.dois");
        tokenA1 = token("aluno.um");
        tokenA2 = token("aluno.dois");
    }

    // ---------- ALUNO ----------

    @Test
    void alunoAcessaSomenteOsProprioDados() throws Exception {
        getAs(tokenA1, "/alunos/{id}", a1.getId()).andExpect(status().isOk());
        getAs(tokenA1, "/alunos/{id}", a2.getId()).andExpect(status().isForbidden());
        getAs(tokenA1, "/alunos").andExpect(status().isForbidden());
        getAs(tokenA1, "/professores/{id}", p1.getId()).andExpect(status().isForbidden());
    }

    @Test
    void alunoNaoAcessaHistoricoDeOutroAluno() throws Exception {
        getAs(tokenA2, "/matriculas/emitir-historico/{id}", a1.getId())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
        getAs(tokenA2, "/matriculas/emitir-historico/{id}", a2.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disciplinas.length()").value(1));
    }

    @Test
    void alunoListaApenasAsPropriasMatriculasEDisciplinas() throws Exception {
        getAs(tokenA2, "/matriculas")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].id").value(a2d2.getId().toString()));
        getAs(tokenA2, "/matriculas?alunoId={id}", a1.getId()).andExpect(status().isForbidden());
        getAs(tokenA2, "/matriculas/{id}", a1d2.getId()).andExpect(status().isForbidden());
        getAs(tokenA2, "/disciplinas")
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].codigo").value("D-2"));
        getAs(tokenA2, "/disciplinas/{id}", d1.getId()).andExpect(status().isForbidden());
        getAs(tokenA1, "/disciplinas/{id}", d1.getId()).andExpect(status().isOk());
    }

    @Test
    void alunoNaoAlteraNotasStatusNemMatriculas() throws Exception {
        patchAs(tokenA1, Map.of("nota1", 10, "nota2", 10), "/matriculas/atualizar-notas/{id}", a1d1.getId())
                .andExpect(status().isForbidden());
        patchAs(tokenA1, null, "/matriculas/trancar/{id}", a1d1.getId()).andExpect(status().isForbidden());
        postAs(tokenA1, Map.of("alunoId", a1.getId(), "disciplinaId", d1.getId()), "/matriculas")
                .andExpect(status().isForbidden());
        postAs(tokenA1, null, "/disciplinas/{d}/alunos/{a}", d1.getId(), a2.getId()).andExpect(status().isForbidden());
        putAs(tokenA1, Map.of("nome", "X", "email", "x@x.com", "matricula", "A-1"), "/alunos/{id}", a1.getId())
                .andExpect(status().isForbidden());

        MatriculaAluno recarregada = matriculaRepository.findById(a1d1.getId()).orElseThrow();
        assertThat(recarregada.getNota1()).isNull();
        assertThat(recarregada.getStatus().name()).isEqualTo("MATRICULADO");
    }

    // ---------- PROFESSOR ----------

    @Test
    void professorLancaNotasSomenteNasSuasDisciplinas() throws Exception {
        patchAs(tokenP1, Map.of("nota1", 8), "/matriculas/atualizar-notas/{id}", a1d1.getId())
                .andExpect(status().isOk());
        patchAs(tokenP1, Map.of("nota1", 1), "/matriculas/atualizar-notas/{id}", a1d2.getId())
                .andExpect(status().isForbidden());

        assertThat(matriculaRepository.findById(a1d2.getId()).orElseThrow().getNota1()).isNull();
    }

    @Test
    void professorRemovidoDaDisciplinaPerdeAcessoAsNotas() throws Exception {
        deleteAs(admin, "/disciplinas/{id}/professor", d1.getId()).andExpect(status().isOk());
        patchAs(tokenP1, Map.of("nota1", 8), "/matriculas/atualizar-notas/{id}", a1d1.getId())
                .andExpect(status().isForbidden());
        getAs(tokenP1, "/matriculas").andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void professorVeSomenteMatriculasEDisciplinasSobSuaResponsabilidade() throws Exception {
        getAs(tokenP2, "/matriculas?size=1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.conteudo[0].disciplina.codigo").value("D-2"));
        getAs(tokenP2, "/matriculas?size=10")
                .andExpect(jsonPath("$.conteudo[*].aluno.id", containsInAnyOrder(a1.getId().toString(), a2.getId().toString())))
                .andExpect(jsonPath("$.conteudo[*].aluno.email").doesNotExist());
        // Filtro por disciplina de outro professor não vaza dados
        getAs(tokenP2, "/matriculas?disciplinaId={id}", d1.getId()).andExpect(jsonPath("$.totalElementos").value(0));
        getAs(tokenP2, "/matriculas/{id}", a1d1.getId()).andExpect(status().isForbidden());
        getAs(tokenP2, "/disciplinas")
                .andExpect(jsonPath("$.conteudo[*].codigo", everyItem(is("D-2"))));
        getAs(tokenP2, "/disciplinas/{id}", d1.getId()).andExpect(status().isForbidden());
        getAs(tokenP2, "/disciplinas/professor/{id}", p1.getId()).andExpect(status().isForbidden());
        getAs(tokenP2, "/disciplinas/professor/{id}", p2.getId()).andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    void professorNaoAcessaCadastrosNemHistoricosNemGestao() throws Exception {
        getAs(tokenP1, "/alunos/{id}", a1.getId()).andExpect(status().isForbidden());
        getAs(tokenP1, "/matriculas/emitir-historico/{id}", a1.getId()).andExpect(status().isForbidden());
        getAs(tokenP1, "/professores/{id}", p2.getId()).andExpect(status().isForbidden());
        getAs(tokenP1, "/professores/{id}", p1.getId()).andExpect(status().isOk());
        patchAs(tokenP1, null, "/matriculas/trancar/{id}", a1d1.getId()).andExpect(status().isForbidden());
        postAs(tokenP1, Map.of("alunoId", a2.getId(), "disciplinaId", d1.getId()), "/matriculas")
                .andExpect(status().isForbidden());
        putAs(tokenP1, Map.of("professorId", p1.getId()), "/disciplinas/{id}/professor", d2.getId())
                .andExpect(status().isForbidden());
        deleteAs(tokenP1, "/alunos/{id}", a1.getId()).andExpect(status().isForbidden());
    }

    // ---------- ADMIN ----------

    @Test
    void adminAcessaTodosOsRecursos() throws Exception {
        getAs(admin, "/matriculas").andExpect(jsonPath("$.totalElementos").value(3));
        getAs(admin, "/alunos").andExpect(jsonPath("$.totalElementos").value(2));
        getAs(admin, "/matriculas/emitir-historico/{id}", a1.getId()).andExpect(status().isOk());
        patchAs(admin, Map.of("nota1", 9, "nota2", 9), "/matriculas/atualizar-notas/{id}", a2d2.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APROVADO"));
    }

    // ---------- 401 x 403 e CORS ----------

    @Test
    void semTokenRetorna401EComPerfilInsuficienteRetorna403() throws Exception {
        getAs(null, "/alunos").andExpect(status().isUnauthorized());
        getAs(tokenA1, "/alunos").andExpect(status().isForbidden());
        getAs(null, "/rota/inexistente").andExpect(status().isUnauthorized());
        getAs(tokenA1, "/rota/inexistente").andExpect(status().isForbidden());
    }

    @Test
    void corsPermiteSomenteOrigensConfiguradas() throws Exception {
        mvc.perform(options("/alunos").header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
        mvc.perform(options("/alunos").header("Origin", "https://malicioso.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
