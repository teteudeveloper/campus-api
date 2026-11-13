package br.com.campusapi.matricula;

import br.com.campusapi.aluno.Aluno;
import br.com.campusapi.disciplina.Disciplina;
import br.com.campusapi.professor.Professor;
import br.com.campusapi.suporte.IntegracaoTestBase;
import br.com.campusapi.usuario.Perfil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MatriculaNotasIntegracaoTest extends IntegracaoTestBase {

    private String admin;
    private String tokenProfessor;
    private Professor professor;
    private Aluno aluno;
    private Disciplina disciplina;

    @BeforeEach
    void preparar() throws Exception {
        admin = tokenAdmin();
        professor = novoProfessor("Carla Docente", "PROF-10");
        aluno = novoAluno("Bruno Aluno", "2026010");
        disciplina = novaDisciplina("MAT-101", professor);
        criarUsuario("carla", Perfil.PROFESSOR, null, professor.getId());
        tokenProfessor = token("carla");
    }

    private String urlNotas(MatriculaAluno matricula) {
        return "/matriculas/atualizar-notas/" + matricula.getId();
    }

    // ---------- criação ----------

    @Test
    void criaMatriculaComStatusInicialMatriculado() throws Exception {
        postAs(admin, Map.of("alunoId", aluno.getId(), "disciplinaId", disciplina.getId()), "/matriculas")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/matriculas/")))
                .andExpect(jsonPath("$.status").value("MATRICULADO"))
                .andExpect(jsonPath("$.nota1").isEmpty())
                .andExpect(jsonPath("$.media").isEmpty())
                .andExpect(jsonPath("$.aluno.nome").value("Bruno Aluno"))
                .andExpect(jsonPath("$.disciplina.nomeProfessor").value("Carla Docente"));
    }

    @Test
    void statusNaoPodeSerDefinidoNaCriacao() throws Exception {
        postAs(admin, Map.of("alunoId", aluno.getId(), "disciplinaId", disciplina.getId(), "status", "APROVADO"),
                "/matriculas")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[0].campo").value("status"));
    }

    @Test
    void validaExistenciaDeAlunoEDisciplinaECamposObrigatorios() throws Exception {
        postAs(admin, Map.of("alunoId", UUID.randomUUID(), "disciplinaId", disciplina.getId()), "/matriculas")
                .andExpect(status().isNotFound());
        postAs(admin, Map.of("alunoId", aluno.getId(), "disciplinaId", UUID.randomUUID()), "/matriculas")
                .andExpect(status().isNotFound());
        postAs(admin, Map.of("alunoId", aluno.getId()), "/matriculas")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[0].campo").value("disciplinaId"));
        postAs(admin, "{\"alunoId\":\"nao-e-uuid\",\"disciplinaId\":\"" + disciplina.getId() + "\"}", "/matriculas")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("JSON_INVALIDO"));
    }

    @Test
    void matriculaDuplicadaRetorna409PorQualquerRota() throws Exception {
        postAs(admin, Map.of("alunoId", aluno.getId(), "disciplinaId", disciplina.getId()), "/matriculas")
                .andExpect(status().isCreated());
        postAs(admin, Map.of("alunoId", aluno.getId(), "disciplinaId", disciplina.getId()), "/matriculas")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("MATRICULA_DUPLICADA"));
        postAs(admin, null, "/disciplinas/{d}/alunos/{a}", disciplina.getId(), aluno.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("MATRICULA_DUPLICADA"));
        assertThat(matriculaRepository.count()).isEqualTo(1);
    }

    @Test
    void matriculasConcorrentesDoMesmoParResultamEmUmaUnicaMatricula() throws Exception {
        int requisicoes = 8;
        String corpo = corpo(Map.of("alunoId", aluno.getId(), "disciplinaId", disciplina.getId()));
        List<Integer> status = executarConcorrentemente(requisicoes, () -> {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/matriculas"))
                    .header("Authorization", "Bearer " + admin)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(corpo)).build();
            return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        });

        assertThat(status).filteredOn(s -> s == 201).hasSize(1);
        assertThat(status).filteredOn(s -> s == 409).hasSize(requisicoes - 1);
        assertThat(matriculaRepository.count()).isEqualTo(1);
    }

    // ---------- trancamento ----------

    @Test
    void trancamentoSomenteAPartirDeMatriculadoEBloqueiaNotas() throws Exception {
        MatriculaAluno matricula = novaMatricula(aluno, disciplina);
        patchAs(admin, null, "/matriculas/trancar/{id}", matricula.getId()).andExpect(status().isNoContent());
        patchAs(admin, null, "/matriculas/trancar/{id}", matricula.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TRANSICAO_INVALIDA"));
        patchAs(tokenProfessor, Map.of("nota1", 8), urlNotas(matricula))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TRANSICAO_INVALIDA"));

        MatriculaAluno finalizada = novaMatriculaComNotas(novoAluno("Outro", "2026011"), disciplina, "8", "8");
        patchAs(admin, null, "/matriculas/trancar/{id}", finalizada.getId()).andExpect(status().isConflict());
        patchAs(admin, null, "/matriculas/trancar/{id}", UUID.randomUUID()).andExpect(status().isNotFound());
    }

    // ---------- notas ----------

    @ParameterizedTest
    @ValueSource(strings = {"0", "10", "0.00", "10.00", "7.25"})
    void aceitaNotasNosLimites(String nota) throws Exception {
        MatriculaAluno matricula = novaMatricula(aluno, disciplina);
        patchAs(tokenProfessor, "{\"nota1\":" + nota + "}", urlNotas(matricula))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nota1").value(new BigDecimal(nota).doubleValue()))
                .andExpect(jsonPath("$.status").value("MATRICULADO"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "10.01", "11", "-1", "7.125", "100"})
    void rejeitaNotasForaDoIntervaloOuComMaisDeDuasCasas(String nota) throws Exception {
        MatriculaAluno matricula = novaMatricula(aluno, disciplina);
        patchAs(tokenProfessor, "{\"nota2\":" + nota + "}", urlNotas(matricula))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.erros[0].campo").value("nota2"));
        assertThat(matriculaRepository.findById(matricula.getId()).orElseThrow().getNota2()).isNull();
    }

    @Test
    void rejeitaAtualizacaoVaziaOuSomenteComNulosENotaNaoNumerica() throws Exception {
        MatriculaAluno matricula = novaMatricula(aluno, disciplina);
        patchAs(tokenProfessor, "{}", urlNotas(matricula))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[0].mensagem").value("Informe ao menos uma nota (nota1 ou nota2)"));
        patchAs(tokenProfessor, "{\"nota1\":null,\"nota2\":null}", urlNotas(matricula)).andExpect(status().isBadRequest());
        patchAs(tokenProfessor, "{\"nota1\":\"dez\"}", urlNotas(matricula))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("JSON_INVALIDO"));
        patchAs(tokenProfessor, "{\"nota1\":", urlNotas(matricula))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("JSON_INVALIDO"));
    }

    @ParameterizedTest(name = "{0} e {1} -> média {2} {3}")
    @CsvSource({
            "7.0, 7.0, 7.0, APROVADO",
            "6.0, 8.0, 7.0, APROVADO",
            "10, 4, 7.0, APROVADO",
            "6.99, 7.0, 6.995, REPROVADO",
            "6.98, 7.0, 6.99, REPROVADO",
            "0, 0, 0.0, REPROVADO",
            "10, 10, 10.0, APROVADO"})
    void calculaMediaExataEStatusComMinimoDeSete(String n1, String n2, double media, String status) throws Exception {
        MatriculaAluno matricula = novaMatricula(aluno, disciplina);
        patchAs(tokenProfessor, "{\"nota1\":" + n1 + ",\"nota2\":" + n2 + "}", urlNotas(matricula))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media").value(media))
                .andExpect(jsonPath("$.status").value(status));
    }

    @Test
    void notasIncompletasMantemMatriculadoECampoAusenteNaoAlteraNotaExistente() throws Exception {
        MatriculaAluno matricula = novaMatricula(aluno, disciplina);
        patchAs(tokenProfessor, Map.of("nota1", 8), urlNotas(matricula))
                .andExpect(jsonPath("$.status").value("MATRICULADO"))
                .andExpect(jsonPath("$.media").isEmpty());
        patchAs(tokenProfessor, "{\"nota1\":null,\"nota2\":5}", urlNotas(matricula))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nota1").value(8.0))
                .andExpect(jsonPath("$.nota2").value(5.0))
                .andExpect(jsonPath("$.media").value(6.5))
                .andExpect(jsonPath("$.status").value("REPROVADO"));
    }

    @Test
    void correcaoAutorizadaRecalculaStatusEmAmbosOsSentidos() throws Exception {
        MatriculaAluno matricula = novaMatriculaComNotas(aluno, disciplina, "5", "6");
        patchAs(tokenProfessor, Map.of("nota2", 9), urlNotas(matricula))
                .andExpect(jsonPath("$.media").value(7.0))
                .andExpect(jsonPath("$.status").value("APROVADO"));
        patchAs(admin, Map.of("nota1", 4.5), urlNotas(matricula))
                .andExpect(jsonPath("$.media").value(6.75))
                .andExpect(jsonPath("$.status").value("REPROVADO"));
        getAs(admin, "/matriculas/emitir-historico/{id}", aluno.getId())
                .andExpect(jsonPath("$.disciplinas[0].media").value(6.75))
                .andExpect(jsonPath("$.disciplinas[0].status").value("REPROVADO"));
    }

    @Test
    void lancamentosConcorrentesDeNotasDiferentesNaoSePerdem() throws Exception {
        MatriculaAluno matricula = novaMatricula(aluno, disciplina);
        String url = "http://localhost:" + porta + urlNotas(matricula);
        List<String> corpos = List.of("{\"nota1\":8}", "{\"nota2\":6}");
        java.util.concurrent.atomic.AtomicInteger indice = new java.util.concurrent.atomic.AtomicInteger();
        List<Integer> status = executarConcorrentemente(2, () -> {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Authorization", "Bearer " + tokenProfessor)
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(corpos.get(indice.getAndIncrement())))
                    .build();
            return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        });
        assertThat(status).containsOnly(200);
        MatriculaAluno resultado = matriculaRepository.findById(matricula.getId()).orElseThrow();
        assertThat(resultado.getNota1()).isEqualByComparingTo("8");
        assertThat(resultado.getNota2()).isEqualByComparingTo("6");
        assertThat(resultado.getStatus().name()).isEqualTo("APROVADO");
    }

    @Test
    void notasSaoRetornadasComEscalaConsistente() throws Exception {
        MatriculaAluno matricula = novaMatricula(aluno, disciplina);
        String patch = patchAs(tokenProfessor, "{\"nota1\":8.5,\"nota2\":7}", urlNotas(matricula))
                .andReturn().getResponse().getContentAsString();
        String get = getAs(admin, "/matriculas/{id}", matricula.getId()).andReturn().getResponse().getContentAsString();
        assertThat(patch).contains("\"nota1\":8.50", "\"nota2\":7.00", "\"media\":7.75");
        assertThat(get).contains("\"nota1\":8.50", "\"nota2\":7.00", "\"media\":7.75");
    }

    // ---------- consulta, cancelamento e histórico ----------

    @Test
    void consultaPorIdEListagemComFiltros() throws Exception {
        MatriculaAluno matricula = novaMatriculaComNotas(aluno, disciplina, "9", "9");
        novaMatricula(novoAluno("Outro Aluno", "2026012"), disciplina);

        getAs(admin, "/matriculas/{id}", matricula.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media").value(9.0));
        getAs(admin, "/matriculas/{id}", UUID.randomUUID()).andExpect(status().isNotFound());
        getAs(admin, "/matriculas/{id}", "nao-uuid")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
        getAs(admin, "/matriculas?status=APROVADO").andExpect(jsonPath("$.totalElementos").value(1));
        getAs(admin, "/matriculas?status=INVALIDO").andExpect(status().isBadRequest());
        getAs(admin, "/matriculas?alunoId={id}", aluno.getId()).andExpect(jsonPath("$.totalElementos").value(1));
        getAs(admin, "/matriculas?disciplinaId={id}&sort=aluno.nome,desc", disciplina.getId())
                .andExpect(jsonPath("$.conteudo[0].aluno.nome").value("Outro Aluno"));
        getAs(admin, "/matriculas?sort=nota1,asc").andExpect(status().isBadRequest());
    }

    @Test
    void cancelamentoSomenteDeMatriculaSemNotas() throws Exception {
        novaMatricula(aluno, disciplina);
        Aluno comNota = novoAluno("Com Nota", "2026013");
        novaMatriculaComNotas(comNota, disciplina, "5", null);

        deleteAs(admin, "/disciplinas/{d}/alunos/{a}", disciplina.getId(), comNota.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TRANSICAO_INVALIDA"));
        deleteAs(admin, "/disciplinas/{d}/alunos/{a}", disciplina.getId(), aluno.getId())
                .andExpect(status().isNoContent());
        deleteAs(admin, "/disciplinas/{d}/alunos/{a}", disciplina.getId(), aluno.getId())
                .andExpect(status().isNotFound());
        assertThat(matriculaRepository.count()).isEqualTo(1);
    }

    @Test
    void historicoDistingueAlunoInexistenteDeAlunoSemMatriculas() throws Exception {
        getAs(admin, "/matriculas/emitir-historico/{id}", UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
        getAs(admin, "/matriculas/emitir-historico/{id}", aluno.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeAluno").value("Bruno Aluno"))
                .andExpect(jsonPath("$.disciplinas").isEmpty());
    }

    @Test
    void historicoRefleteTodasAsMatriculasComStatusEMedia() throws Exception {
        Disciplina outra = novaDisciplina("FIS-101", null);
        novaMatriculaComNotas(aluno, disciplina, "8", "7.5");
        MatriculaAluno trancada = novaMatricula(aluno, outra);
        patchAs(admin, null, "/matriculas/trancar/{id}", trancada.getId());

        getAs(admin, "/matriculas/emitir-historico/{id}", aluno.getId())
                .andExpect(jsonPath("$.disciplinas.length()").value(2))
                .andExpect(jsonPath("$.disciplinas[?(@.codigoDisciplina=='MAT-101')].media").value(7.75))
                .andExpect(jsonPath("$.disciplinas[?(@.codigoDisciplina=='MAT-101')].status").value("APROVADO"))
                .andExpect(jsonPath("$.disciplinas[?(@.codigoDisciplina=='FIS-101')].status").value("TRANCADO"))
                .andExpect(jsonPath("$.disciplinas[?(@.codigoDisciplina=='FIS-101')].nomeProfessor")
                        .value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())));
    }

    private List<Integer> executarConcorrentemente(int quantidade, Callable<Integer> tarefa) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(quantidade);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<Integer>> futuros = new ArrayList<>();
            for (int i = 0; i < quantidade; i++) {
                futuros.add(executor.submit(() -> {
                    largada.await();
                    return tarefa.call();
                }));
            }
            largada.countDown();
            List<Integer> resultados = new ArrayList<>();
            for (Future<Integer> futuro : futuros) {
                resultados.add(futuro.get());
            }
            return resultados;
        } finally {
            executor.shutdownNow();
        }
    }
}
