package br.com.campusapi.migracao;

import br.com.campusapi.suporte.TestcontainersConfig;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Adoção do Flyway em um banco criado pela versão anterior (Hibernate ddl-auto=update). O schema de partida é o
 * dump real gerado pelo Hibernate (src/test/resources/legado/schema-hibernate.sql).
 */
class MigracaoBancoLegadoTest {

    private static final String A1 = "11111111-1111-1111-1111-111111111111";
    private static final String A2 = "22222222-2222-2222-2222-222222222222";
    private static final String P1 = "33333333-3333-3333-3333-333333333333";
    private static final String D1 = "44444444-4444-4444-4444-444444444444";
    private static final String D2 = "55555555-5555-5555-5555-555555555555";

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(TestcontainersConfig.IMAGEM_POSTGRES);
    private static DataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeAll
    static void iniciar() {
        POSTGRES.start();
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @AfterAll
    static void parar() {
        POSTGRES.stop();
    }

    @BeforeEach
    void recriarSchemaLegado() throws Exception {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        try (Connection conexao = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(conexao, new ClassPathResource("legado/schema-hibernate.sql"));
        }
        jdbc.update("insert into professores (id, nome, email, registro, criado_em) values (?::uuid, ' Carlos  Mendes ', 'Carlos@Campus.com', 'prof-1', now())", P1);
        jdbc.update("insert into alunos (id, nome, email, matricula, criado_em) values (?::uuid, 'Lucas', ' Lucas@Email.com ', '2024001', now())", A1);
        jdbc.update("insert into alunos (id, nome, email, matricula, criado_em) values (?::uuid, 'Mariana', 'mariana@email.com', '2024002', now())", A2);
        jdbc.update("insert into disciplinas (id, nome, codigo, carga_horaria, professor_id) values (?::uuid, 'Cálculo', 'mat-101', 80, ?::uuid)", D1, P1);
        jdbc.update("insert into disciplinas (id, nome, codigo, carga_horaria) values (?::uuid, 'Física', 'FIS-101', null)", D2);
        // Matrícula com notas lançadas pela versão anterior (double precision)
        jdbc.update("insert into matriculas (id, aluno_id, disciplina_id, nota1, nota2, status) values (gen_random_uuid(), ?::uuid, ?::uuid, 8.0, 7.5, 'APROVADO')", A1, D1);
        // Vínculo N:N antigo que já possui matrícula correspondente e vínculo sem matrícula
        jdbc.update("insert into alunos_disciplinas (disciplina_id, aluno_id) values (?::uuid, ?::uuid)", D1, A1);
        jdbc.update("insert into alunos_disciplinas (disciplina_id, aluno_id) values (?::uuid, ?::uuid)", D2, A2);
    }

    private Flyway flyway(boolean baseline) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(baseline)
                .baselineVersion("0")
                .load();
    }

    @Test
    void bancoLegadoSemOptInDeBaselineNaoEhMigradoAutomaticamente() {
        assertThatThrownBy(() -> flyway(false).migrate())
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("non-empty schema");
        assertThat(tabelaExiste("alunos_disciplinas")).isTrue();
    }

    @Test
    void adocaoReconciliaVinculosNormalizaDadosEPreservaNotas() {
        var resultado = flyway(true).migrate();
        assertThat(resultado.success).isTrue();
        assertThat(jdbc.queryForList("select version from flyway_schema_history where success order by installed_rank", String.class))
                .containsExactly("0", "1", "2", "3", "4");

        // Notas e status originais preservados, convertidos para numeric(4,2)
        Map<String, Object> original = jdbc.queryForMap(
                "select nota1, nota2, status, origem from matriculas where aluno_id = ?::uuid and disciplina_id = ?::uuid", A1, D1);
        assertThat(original.get("nota1").toString()).isEqualTo("8.00");
        assertThat(original.get("nota2").toString()).isEqualTo("7.50");
        assertThat(original).containsEntry("status", "APROVADO").containsEntry("origem", "API");

        // Vínculo antigo sem matrícula vira matrícula MATRICULADO sem notas, sem duplicar o que já existia
        assertThat(jdbc.queryForObject("select count(*) from matriculas", Integer.class)).isEqualTo(2);
        Map<String, Object> reconciliada = jdbc.queryForMap(
                "select nota1, nota2, status, origem from matriculas where aluno_id = ?::uuid and disciplina_id = ?::uuid", A2, D2);
        assertThat(reconciliada.get("nota1")).isNull();
        assertThat(reconciliada.get("nota2")).isNull();
        assertThat(reconciliada).containsEntry("status", "MATRICULADO").containsEntry("origem", "MIGRACAO_LEGADO");

        // Tabela antiga preservada para auditoria
        assertThat(tabelaExiste("alunos_disciplinas")).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from alunos_disciplinas_legado", Integer.class)).isEqualTo(2);

        // Normalização
        assertThat(jdbc.queryForObject("select email from alunos where id = ?::uuid", String.class, A1)).isEqualTo("lucas@email.com");
        assertThat(jdbc.queryForMap("select nome, email, registro from professores"))
                .containsEntry("nome", "Carlos Mendes").containsEntry("email", "carlos@campus.com").containsEntry("registro", "PROF-1");
        assertThat(jdbc.queryForObject("select codigo from disciplinas where id = ?::uuid", String.class, D1)).isEqualTo("MAT-101");

        // Constraints nomeadas substituem as geradas pelo Hibernate
        List<String> constraints = jdbc.queryForList("""
                select conname from pg_constraint c join pg_class t on t.oid = c.conrelid
                 where t.relname in ('alunos','professores','disciplinas','matriculas') and contype in ('u','f','c')""", String.class);
        assertThat(constraints).contains("uk_matriculas_aluno_disciplina", "fk_matriculas_aluno", "ck_matriculas_nota1",
                "uk_alunos_email", "fk_disciplinas_professor");
        assertThat(constraints).noneMatch(nome -> nome.startsWith("uk") && !nome.startsWith("uk_"));
        assertThat(constraints).noneMatch(nome -> nome.startsWith("fk") && !nome.startsWith("fk_"));

        assertThatThrownBy(() -> jdbc.update(
                "insert into matriculas (id, aluno_id, disciplina_id, status) values (gen_random_uuid(), ?::uuid, ?::uuid, 'MATRICULADO')", A1, D1))
                .hasMessageContaining("uk_matriculas_aluno_disciplina");
    }

    @Test
    void tabelaUsuariosPreExistenteEhPreservadaComoLegado() {
        // Cenário encontrado em um banco real de desenvolvimento: tabela "usuarios" criada fora da aplicação.
        jdbc.execute("""
                create table usuarios (id uuid not null primary key, email varchar(150) not null, role varchar(20) not null
                    check (role in ('USER','ADMIN')), senha varchar(255) not null, constraint ukkfsp0s1tflm1cwlj8idhqsad0 unique (email))""");
        jdbc.update("insert into usuarios values (gen_random_uuid(), 'antigo@campus.com', 'USER', 'hash-antigo')");

        flyway(true).migrate();

        assertThat(jdbc.queryForObject("select email from usuarios_legado", String.class)).isEqualTo("antigo@campus.com");
        assertThat(jdbc.queryForObject("select count(*) from usuarios", Integer.class)).isZero();
        assertThat(jdbc.queryForList("select column_name from information_schema.columns where table_name = 'usuarios'",
                String.class)).contains("login", "perfil", "versao_credenciais");
    }

    @Test
    void schemaMigradoDoLegadoEhIdenticoAoDeUmBancoNovo() {
        flyway(true).migrate();
        List<Map<String, Object>> legado = descreverColunas();

        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        flyway(false).migrate();
        List<Map<String, Object>> novo = descreverColunas();

        assertThat(legado).isEqualTo(novo);
    }

    @Test
    void conflitosAbortamAMigracaoSemAlterarDadosEPodemSerCorrigidos() {
        jdbc.update("insert into matriculas (id, aluno_id, disciplina_id, nota1, nota2, status) values (gen_random_uuid(), ?::uuid, ?::uuid, 8.0, 7.5, 'APROVADO')", A1, D1);
        jdbc.update("insert into matriculas (id, aluno_id, disciplina_id, nota1, status) values (gen_random_uuid(), ?::uuid, ?::uuid, 11, 'MATRICULADO')", A2, D1);
        jdbc.update("insert into alunos (id, nome, email, matricula) values (gen_random_uuid(), 'Dup', 'LUCAS@email.com', '2024003')");

        assertThatThrownBy(() -> flyway(true).migrate())
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("Migração V2 abortada")
                .hasMessageContaining("matrículas duplicadas")
                .hasMessageContaining("nota fora de 0..10")
                .hasMessageContaining("e-mail(s) de aluno duplicado(s)");

        // Nada da V2 foi aplicado
        assertThat(tabelaExiste("alunos_disciplinas")).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from matriculas", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select data_type from information_schema.columns where table_name = 'matriculas' and column_name = 'nota1'",
                String.class)).isEqualTo("double precision");
        assertThat(jdbc.queryForObject("select email from alunos where id = ?::uuid", String.class, A1)).isEqualTo(" Lucas@Email.com ");

        // Correção manual dos dados conforme o README e nova execução
        jdbc.update("delete from matriculas where id = (select id from matriculas where aluno_id = ?::uuid and disciplina_id = ?::uuid limit 1)", A1, D1);
        jdbc.update("update matriculas set nota1 = 10 where nota1 = 11");
        jdbc.update("update alunos set email = 'dup@email.com' where matricula = '2024003'");
        flyway(true).migrate();
        assertThat(jdbc.queryForObject("select count(*) from matriculas", Integer.class)).isEqualTo(3);
    }

    private boolean tabelaExiste(String nome) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from information_schema.tables where table_schema = 'public' and table_name = ?)",
                Boolean.class, nome));
    }

    private List<Map<String, Object>> descreverColunas() {
        return jdbc.queryForList("""
                select table_name, column_name, data_type, is_nullable, character_maximum_length, numeric_precision, numeric_scale
                  from information_schema.columns
                 where table_schema = 'public' and table_name in ('alunos','professores','disciplinas','matriculas','usuarios','refresh_tokens')
                 order by table_name, column_name""");
    }
}
