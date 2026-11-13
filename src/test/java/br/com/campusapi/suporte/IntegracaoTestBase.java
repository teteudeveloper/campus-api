package br.com.campusapi.suporte;

import br.com.campusapi.aluno.Aluno;
import br.com.campusapi.aluno.AlunoRepository;
import br.com.campusapi.auth.dto.TokenResponseDTO;
import br.com.campusapi.disciplina.Disciplina;
import br.com.campusapi.disciplina.DisciplinaRepository;
import br.com.campusapi.matricula.MatriculaAluno;
import br.com.campusapi.matricula.MatriculaAlunoRepository;
import br.com.campusapi.professor.Professor;
import br.com.campusapi.professor.ProfessorRepository;
import br.com.campusapi.seguranca.LimitadorTentativasLogin;
import br.com.campusapi.usuario.Perfil;
import br.com.campusapi.usuario.Usuario;
import br.com.campusapi.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base dos testes de integração: aplicação completa (filtros de segurança, MVC, JPA, Flyway) sobre PostgreSQL
 * real. O banco é limpo antes de cada teste; os dados são criados pelo próprio teste.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegracaoTestBase {

    protected static final String SENHA = "SenhaForte123";

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected UsuarioRepository usuarioRepository;
    @Autowired
    protected AlunoRepository alunoRepository;
    @Autowired
    protected ProfessorRepository professorRepository;
    @Autowired
    protected DisciplinaRepository disciplinaRepository;
    @Autowired
    protected MatriculaAlunoRepository matriculaRepository;
    @Autowired
    protected PasswordEncoder passwordEncoder;
    @Autowired
    protected LimitadorTentativasLogin limitador;

    @LocalServerPort
    protected int porta;

    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry registry) {
        TestcontainersConfig.registrar(registry);
    }

    /** DELETE (e não TRUNCATE): os testes usam o usuário de runtime, que só tem permissões de DML. */
    @BeforeEach
    void limparBanco() {
        for (String tabela : new String[]{"refresh_tokens", "usuarios", "matriculas", "disciplinas", "alunos", "professores"}) {
            jdbc.update("DELETE FROM " + tabela);
        }
        limitador.limpar();
    }

    // ----- dados -----

    protected Usuario criarUsuario(String login, Perfil perfil, UUID alunoId, UUID professorId) {
        return usuarioRepository.saveAndFlush(
                new Usuario(login, passwordEncoder.encode(SENHA), perfil, alunoId, professorId));
    }

    protected Aluno novoAluno(String nome, String matricula) {
        Aluno aluno = new Aluno();
        aluno.setNome(nome);
        aluno.setEmail(matricula.toLowerCase() + "@aluno.campus.test");
        aluno.setMatricula(matricula);
        return alunoRepository.saveAndFlush(aluno);
    }

    protected Professor novoProfessor(String nome, String registro) {
        Professor professor = new Professor();
        professor.setNome(nome);
        professor.setEmail(registro.toLowerCase() + "@prof.campus.test");
        professor.setRegistro(registro);
        return professorRepository.saveAndFlush(professor);
    }

    protected Disciplina novaDisciplina(String codigo, Professor professor) {
        Disciplina disciplina = new Disciplina();
        disciplina.setNome("Disciplina " + codigo);
        disciplina.setCodigo(codigo);
        disciplina.setCargaHoraria(60);
        disciplina.setProfessor(professor);
        return disciplinaRepository.saveAndFlush(disciplina);
    }

    protected MatriculaAluno novaMatricula(Aluno aluno, Disciplina disciplina) {
        return matriculaRepository.saveAndFlush(new MatriculaAluno(aluno, disciplina));
    }

    protected MatriculaAluno novaMatriculaComNotas(Aluno aluno, Disciplina disciplina, String nota1, String nota2) {
        MatriculaAluno matricula = new MatriculaAluno(aluno, disciplina);
        matricula.atualizarNotas(nota1 == null ? null : new BigDecimal(nota1), nota2 == null ? null : new BigDecimal(nota2));
        return matriculaRepository.saveAndFlush(matricula);
    }

    // ----- autenticação -----

    protected TokenResponseDTO login(String login, String senha) throws Exception {
        MvcResult resultado = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(new Credenciais(login, senha))))
                .andExpect(status().isOk())
                .andReturn();
        return json.readValue(resultado.getResponse().getContentAsString(), TokenResponseDTO.class);
    }

    protected String token(String login) throws Exception {
        return login(login, SENHA).accessToken();
    }

    protected String tokenAdmin() throws Exception {
        if (usuarioRepository.findByLogin("admin").isEmpty()) {
            criarUsuario("admin", Perfil.ADMIN, null, null);
        }
        return token("admin");
    }

    protected record Credenciais(String login, String senha) {
    }

    // ----- HTTP -----

    protected ResultActions getAs(String token, String url, Object... vars) throws Exception {
        return mvc.perform(autenticado(get(url, vars), token));
    }

    protected ResultActions deleteAs(String token, String url, Object... vars) throws Exception {
        return mvc.perform(autenticado(delete(url, vars), token));
    }

    protected ResultActions postAs(String token, Object body, String url, Object... vars) throws Exception {
        return mvc.perform(comCorpo(autenticado(post(url, vars), token), body));
    }

    protected ResultActions putAs(String token, Object body, String url, Object... vars) throws Exception {
        return mvc.perform(comCorpo(autenticado(put(url, vars), token), body));
    }

    protected ResultActions patchAs(String token, Object body, String url, Object... vars) throws Exception {
        return mvc.perform(comCorpo(autenticado(patch(url, vars), token), body));
    }

    protected MockHttpServletRequestBuilder autenticado(MockHttpServletRequestBuilder builder, String token) {
        return token == null ? builder : builder.header("Authorization", "Bearer " + token);
    }

    private MockHttpServletRequestBuilder comCorpo(MockHttpServletRequestBuilder builder, Object body) {
        if (body == null) {
            return builder;
        }
        return builder.contentType(MediaType.APPLICATION_JSON).content(body instanceof String s ? s : corpo(body));
    }

    protected String corpo(Object objeto) {
        return json.writeValueAsString(objeto);
    }

    protected JsonNode lerJson(ResultActions acoes) throws Exception {
        return json.readTree(acoes.andReturn().getResponse().getContentAsString());
    }
}
