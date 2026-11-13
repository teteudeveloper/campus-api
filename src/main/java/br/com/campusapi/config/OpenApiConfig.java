package br.com.campusapi.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Documentação OpenAPI. Exposta apenas quando SWAGGER_ENABLED=true (padrão no perfil dev e no compose local).
 * Toda operação recebe as respostas de erro padronizadas (application/problem+json); operações públicas são
 * marcadas sem requisito de segurança (@SecurityRequirements vazio).
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Campus API", version = "1.0.0",
                description = """
                        API de gestão acadêmica: alunos, professores, disciplinas, matrículas, notas e histórico.

                        **Como autenticar no Swagger UI**
                        1. Em *Autenticação*, execute `POST /auth/login` com seu login e senha.
                        2. Copie o valor de `accessToken` da resposta.
                        3. Clique em **Authorize**, cole apenas o token (sem o prefixo `Bearer`) e confirme.
                        4. Execute as operações protegidas. O token expira em `expiresIn` segundos; renove com
                           `POST /auth/refresh` usando o `refreshToken` (uso único) e autorize novamente.

                        **Perfis**: ADMIN, PROFESSOR e ALUNO. Cada operação informa os perfis permitidos; além do perfil,
                        o servidor verifica o vínculo com o recurso (o aluno só acessa os próprios dados; o professor,
                        apenas suas disciplinas).

                        **Paginação**: `page` (inicia em 0), `size` (padrão 20, máximo 100) e `sort=campo,asc|desc`
                        (campos permitidos descritos em cada operação). Respostas paginadas usam o envelope
                        `{conteudo, pagina, tamanho, totalElementos, totalPaginas}`.

                        **Erros**: RFC 9457 (`application/problem+json`) com `codigo` estável e `correlationId`."""),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT",
        description = "Access token obtido em POST /auth/login")
public class OpenApiConfig {

    private static final Map<String, String> ERROS_COMUNS = new LinkedHashMap<>();
    private static final Map<String, String> ERROS_PROTEGIDOS = new LinkedHashMap<>();

    static {
        ERROS_COMUNS.put("400", "Requisição inválida (VALIDACAO, JSON_INVALIDO, PARAMETRO_INVALIDO)");
        ERROS_COMUNS.put("500", "Erro interno (ERRO_INTERNO; detalhes apenas no log, pelo correlationId)");
        ERROS_PROTEGIDOS.put("401", "Token ausente, inválido, expirado ou revogado (NAO_AUTENTICADO, TOKEN_INVALIDO)");
        ERROS_PROTEGIDOS.put("403", "Perfil ou vínculo sem permissão para o recurso (ACESSO_NEGADO)");
        ERROS_PROTEGIDOS.put("404", "Recurso não encontrado (RECURSO_NAO_ENCONTRADO)");
        ERROS_PROTEGIDOS.put("409", "Conflito: unicidade, vínculo, registro em uso ou transição de estado inválida");
    }

    @Bean
    OpenApiCustomizer respostasDeErroPadronizadas() {
        return openApi -> {
            Schema<?> erroCampo = new Schema<>().type("object")
                    .addProperty("campo", new Schema<>().type("string").example("email"))
                    .addProperty("mensagem", new Schema<>().type("string").example("deve ser um endereço de e-mail bem formado"));
            Schema<?> problema = new Schema<>().type("object")
                    .description("Erro no formato RFC 9457")
                    .addProperty("type", new Schema<>().type("string").example("urn:campus-api:problema:validacao"))
                    .addProperty("title", new Schema<>().type("string").example("Requisição inválida"))
                    .addProperty("status", new Schema<>().type("integer").example(400))
                    .addProperty("detail", new Schema<>().type("string").example("Um ou mais campos são inválidos"))
                    .addProperty("instance", new Schema<>().type("string").example("/alunos"))
                    .addProperty("codigo", new Schema<>().type("string").example("VALIDACAO"))
                    .addProperty("correlationId", new Schema<>().type("string").example("1f4c7c1e-0b7a-4a77-9b1e-1d2c3b4a5f60"))
                    .addProperty("timestamp", new Schema<>().type("string").format("date-time"))
                    .addProperty("erros", new Schema<>().type("array").items(erroCampo));
            openApi.getComponents().addSchemas("Problema", problema);

            openApi.getPaths().forEach((caminho, path) -> path.readOperationsMap().forEach((metodo, operacao) -> {
                boolean publica = operacao.getSecurity() != null && operacao.getSecurity().isEmpty();
                ERROS_COMUNS.forEach((codigo, descricao) -> adicionar(operacao, codigo, descricao));
                if (publica) {
                    adicionar(operacao, "401", caminho.equals("/auth/login")
                            ? "Login ou senha inválidos, ou conta inativa (CREDENCIAIS_INVALIDAS)"
                            : "Refresh token inválido, expirado, revogado ou reutilizado (TOKEN_INVALIDO)");
                } else {
                    ERROS_PROTEGIDOS.forEach((codigo, descricao) -> adicionar(operacao, codigo, descricao));
                }
                if (caminho.equals("/auth/login") || caminho.equals("/auth/senha")) {
                    adicionar(operacao, "429", "Muitas tentativas malsucedidas (MUITAS_TENTATIVAS); veja Retry-After");
                }
            }));
        };
    }

    private static void adicionar(Operation operacao, String codigo, String descricao) {
        operacao.getResponses().computeIfAbsent(codigo, c -> new ApiResponse().description(descricao)
                .content(new Content().addMediaType("application/problem+json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/Problema")))));
    }
}
