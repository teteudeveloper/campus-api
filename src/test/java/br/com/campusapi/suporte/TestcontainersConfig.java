package br.com.campusapi.suporte;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * PostgreSQL real em contêiner, compartilhado pelos testes de integração. O contêiner é inicializado com o mesmo
 * script do docker-compose (docker/postgres/init), de modo que a aplicação roda com o usuário de runtime
 * somente-DML e o Flyway com o dono do schema, exatamente como em produção.
 */
public final class TestcontainersConfig {

    public static final String IMAGEM_POSTGRES = "postgres:16-alpine";
    public static final String USUARIO_APLICACAO = "campus_app";
    private static final String SENHA_APLICACAO = "senha-aplicacao-teste";

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(IMAGEM_POSTGRES)
            .withEnv("APP_DB_USER", USUARIO_APLICACAO)
            .withEnv("APP_DB_PASSWORD", SENHA_APLICACAO)
            .withCopyFileToContainer(
                    MountableFile.forHostPath("docker/postgres/init/10-usuario-aplicacao.sh", 0755),
                    "/docker-entrypoint-initdb.d/10-usuario-aplicacao.sh");

    private TestcontainersConfig() {
    }

    public static void registrar(DynamicPropertyRegistry registry) {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> USUARIO_APLICACAO);
        registry.add("spring.datasource.password", () -> SENHA_APLICACAO);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }
}
