package br.com.campusapi.fluxo;

import br.com.campusapi.config.AdminBootstrap;
import br.com.campusapi.suporte.IntegracaoTestBase;
import br.com.campusapi.usuario.Perfil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fluxo de ponta a ponta sobre HTTP real (servidor em porta aleatória + PostgreSQL em contêiner):
 * bootstrap do administrador, cadastros, vínculos de contas, matrícula, notas, histórico, bloqueios,
 * renovação e logout.
 */
@TestPropertySource(properties = {
        "app.bootstrap.admin.login=diretoria",
        "app.bootstrap.admin.senha=Bootstrap2026!"})
class FluxoAcademicoCompletoTest extends IntegracaoTestBase {

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    AdminBootstrap adminBootstrap;

    private record Resposta(int status, JsonNode corpo) {
    }

    private Resposta chamar(String metodo, String caminho, String token, Object corpo) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho))
                .header("Content-Type", "application/json")
                .method(metodo, corpo == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corpo)));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> resposta = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode node = resposta.body() == null || resposta.body().isBlank() ? null : json.readTree(resposta.body());
        return new Resposta(resposta.statusCode(), node);
    }

    private String texto(Resposta resposta, String campo) {
        return resposta.corpo().get(campo).asString();
    }

    @Test
    void fluxoCompleto() throws Exception {
        // 1-2. Banco e aplicação inicializados; administrador criado pelo bootstrap (idempotente)
        adminBootstrap.run(null);
        adminBootstrap.run(null);
        assertThat(usuarioRepository.findAll()).filteredOn(u -> u.getPerfil() == Perfil.ADMIN).hasSize(1);

        // 3. Autenticar
        Resposta login = chamar("POST", "/auth/login", null, Map.of("login", "diretoria", "senha", "Bootstrap2026!"));
        assertThat(login.status()).isEqualTo(200);
        String admin = texto(login, "accessToken");

        // 4. Cadastrar professor e aluno (e um segundo aluno para verificar bloqueios)
        Resposta professor = chamar("POST", "/professores", admin, Map.of("nome", "Carlos Mendes",
                "email", "carlos@campus.edu", "registro", "PROF-001", "especialidade", "Matemática"));
        Resposta aluno = chamar("POST", "/alunos", admin, Map.of("nome", "Lucas Oliveira",
                "email", "lucas@campus.edu", "matricula", "2026001"));
        Resposta outroAluno = chamar("POST", "/alunos", admin, Map.of("nome", "Mariana Costa",
                "email", "mariana@campus.edu", "matricula", "2026002"));
        assertThat(professor.status()).isEqualTo(201);
        assertThat(aluno.status()).isEqualTo(201);
        assertThat(outroAluno.status()).isEqualTo(201);
        String professorId = texto(professor, "id");
        String alunoId = texto(aluno, "id");
        String outroAlunoId = texto(outroAluno, "id");

        // 5. Criar e vincular as contas
        assertThat(chamar("POST", "/usuarios", admin, Map.of("login", "carlos", "senha", "Professor2026",
                "perfil", "PROFESSOR", "professorId", professorId)).status()).isEqualTo(201);
        assertThat(chamar("POST", "/usuarios", admin, Map.of("login", "lucas", "senha", "Aluno2026xx",
                "perfil", "ALUNO", "alunoId", alunoId)).status()).isEqualTo(201);
        assertThat(chamar("POST", "/usuarios", admin, Map.of("login", "mariana", "senha", "Aluna2026xx",
                "perfil", "ALUNO", "alunoId", outroAlunoId)).status()).isEqualTo(201);

        // 6. Criar disciplina e associar professor
        Resposta disciplina = chamar("POST", "/disciplinas", admin, Map.of("nome", "Cálculo I", "codigo", "MAT-101",
                "cargaHoraria", 80));
        String disciplinaId = texto(disciplina, "id");
        Resposta associacao = chamar("PUT", "/disciplinas/" + disciplinaId + "/professor", admin,
                Map.of("professorId", professorId));
        assertThat(associacao.corpo().get("professor").get("id").asString()).isEqualTo(professorId);
        Resposta outraDisciplina = chamar("POST", "/disciplinas", admin, Map.of("nome", "Física I",
                "codigo", "FIS-101", "cargaHoraria", 60));

        // 7. Matricular aluno
        Resposta matricula = chamar("POST", "/matriculas", admin, Map.of("alunoId", alunoId, "disciplinaId", disciplinaId));
        assertThat(matricula.status()).isEqualTo(201);
        assertThat(texto(matricula, "status")).isEqualTo("MATRICULADO");
        String matriculaId = texto(matricula, "id");
        Resposta matriculaOutraDisciplina = chamar("POST", "/disciplinas/" + texto(outraDisciplina, "id")
                + "/alunos/" + outroAlunoId, admin, null);
        assertThat(matriculaOutraDisciplina.status()).isEqualTo(201);

        // 8. Professor lança notas
        Resposta loginProfessor = chamar("POST", "/auth/login", null, Map.of("login", "carlos", "senha", "Professor2026"));
        String tokenProfessor = texto(loginProfessor, "accessToken");
        Resposta notas = chamar("PATCH", "/matriculas/atualizar-notas/" + matriculaId, tokenProfessor,
                Map.of("nota1", 8.0, "nota2", 7.5));
        assertThat(notas.status()).isEqualTo(200);
        assertThat(notas.corpo().get("media").decimalValue()).isEqualByComparingTo("7.75");
        assertThat(texto(notas, "status")).isEqualTo("APROVADO");

        // 9. Aluno consulta o próprio histórico
        Resposta loginAluno = chamar("POST", "/auth/login", null, Map.of("login", "lucas", "senha", "Aluno2026xx"));
        String tokenAluno = texto(loginAluno, "accessToken");
        Resposta historico = chamar("GET", "/matriculas/emitir-historico/" + alunoId, tokenAluno, null);
        assertThat(historico.status()).isEqualTo(200);
        JsonNode linha = historico.corpo().get("disciplinas").get(0);
        assertThat(linha.get("nomeDisciplina").asString()).isEqualTo("Cálculo I");
        assertThat(linha.get("nomeProfessor").asString()).isEqualTo("Carlos Mendes");
        assertThat(linha.get("status").asString()).isEqualTo("APROVADO");

        // 10. Bloqueios de acesso indevido
        assertThat(chamar("GET", "/matriculas/emitir-historico/" + outroAlunoId, tokenAluno, null).status()).isEqualTo(403);
        assertThat(chamar("PATCH", "/matriculas/atualizar-notas/" + matriculaId, tokenAluno,
                Map.of("nota1", 10)).status()).isEqualTo(403);
        String matriculaAlheia = texto(matriculaOutraDisciplina, "id");
        assertThat(chamar("PATCH", "/matriculas/atualizar-notas/" + matriculaAlheia, tokenProfessor,
                Map.of("nota1", 10)).status()).isEqualTo(403);
        assertThat(chamar("POST", "/usuarios", tokenProfessor, Map.of("login", "hacker", "senha", "Hacker20266",
                "perfil", "ADMIN")).status()).isEqualTo(403);
        assertThat(chamar("GET", "/alunos", null, null).status()).isEqualTo(401);

        // 11. Renovar a autenticação e encerrar a sessão
        Resposta renovado = chamar("POST", "/auth/refresh", null, Map.of("refreshToken", texto(loginAluno, "refreshToken")));
        assertThat(renovado.status()).isEqualTo(200);
        assertThat(chamar("GET", "/auth/me", texto(renovado, "accessToken"), null).corpo().get("login").asString())
                .isEqualTo("lucas");
        assertThat(chamar("POST", "/auth/logout", null, Map.of("refreshToken", texto(renovado, "refreshToken"))).status())
                .isEqualTo(204);
        assertThat(chamar("POST", "/auth/refresh", null, Map.of("refreshToken", texto(renovado, "refreshToken"))).status())
                .isEqualTo(401);
    }
}
