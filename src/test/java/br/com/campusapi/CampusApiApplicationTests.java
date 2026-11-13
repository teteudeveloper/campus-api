package br.com.campusapi;

import br.com.campusapi.suporte.IntegracaoTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Inicialização completa: migrações Flyway aplicadas e schema validado pelo Hibernate (ddl-auto=validate). */
class CampusApiApplicationTests extends IntegracaoTestBase {

	@Test
	void contextoIniciaComMigracoesAplicadas() {
		Integer versao = jdbc.queryForObject(
				"select max(cast(version as integer)) from flyway_schema_history where success", Integer.class);
		assertThat(versao).isEqualTo(4);
		Integer legado = jdbc.queryForObject(
				"select count(*) from information_schema.tables where table_name = 'alunos_disciplinas_legado'",
				Integer.class);
		assertThat(legado).isEqualTo(1);
	}

	@Test
	void aplicacaoRodaComUsuarioSemPrivilegiosDeDdl() {
		assertThat(jdbc.queryForObject("select current_user", String.class))
				.isEqualTo(br.com.campusapi.suporte.TestcontainersConfig.USUARIO_APLICACAO);
		assertThat(jdbc.queryForObject("select tableowner from pg_tables where tablename = 'matriculas'", String.class))
				.isNotEqualTo(br.com.campusapi.suporte.TestcontainersConfig.USUARIO_APLICACAO);
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.execute("create table invasao (id int)"))
				.isInstanceOf(org.springframework.jdbc.BadSqlGrammarException.class);
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.execute("drop table matriculas"))
				.isInstanceOf(org.springframework.jdbc.BadSqlGrammarException.class);
		// Histórico de migrações e tabelas de auditoria: somente leitura para a aplicação (V4)
		assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history", Integer.class)).isPositive();
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("delete from flyway_schema_history"))
				.isInstanceOf(org.springframework.dao.DataAccessException.class);
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("delete from alunos_disciplinas_legado"))
				.isInstanceOf(org.springframework.dao.DataAccessException.class);
	}

	@Test
	void healthPublicoSemDetalhes() throws Exception {
		mvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.components").doesNotExist());
	}

	@Test
	void documentacaoOpenApiDescreveAutenticacaoBearer() throws Exception {
		mvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
				.andExpect(jsonPath("$.components.schemas.Problema").exists())
				.andExpect(jsonPath("$.paths['/auth/login'].post.security").isEmpty());
	}

	@Test
	void outrosEndpointsDoActuatorNaoSaoExpostos() throws Exception {
		mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
		mvc.perform(get("/actuator/env").header("Authorization", "Bearer " + tokenAdmin()))
				.andExpect(status().isForbidden());
	}
}
