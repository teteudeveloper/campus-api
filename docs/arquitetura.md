# Arquitetura

Aplicação Spring Boot única (monólito modular por domínio), PostgreSQL como único armazenamento e Flyway para o
schema. Não há cache, mensageria ou serviços externos.

## Pacotes e responsabilidades

| Pacote (`br.com.campusapi`) | Conteúdo | Responsabilidade |
|---|---|---|
| `aluno`, `professor`, `disciplina`, `matricula` | `*Controller`, `*Service`, `*Repository`, `*Specs`, entidade, `dto/` | domínio acadêmico |
| `usuario` | `Usuario`, `Perfil`, `UsuarioService`, `PoliticaSenha` | contas, perfis e vínculos (somente ADMIN) |
| `auth` | `AuthController`, `AuthService`, `RefreshToken*` | login, renovação, logout, troca de senha |
| `seguranca` | `SecurityConfig`, `JwtConfig`, `TokenService`, `JwtUsuarioConverter`, `ControleAcesso`, `LimitadorTentativasLogin`, `ProblemaSegurancaHandler` | autenticação, autorização, 401/403/429 |
| `config` | `OpenApiConfig`, `AdminBootstrap`, `VerificacaoConfiguracaoObrigatoria` | documentação, primeiro admin, falha rápida de configuração |
| `comum` | `CorrelationIdFilter`, `Paginacao`, `PaginaResponse`, `Textos` | infraestrutura HTTP compartilhada |
| `exception` | `GlobalExceptionHandler`, `Problemas`, `CodigoErro`, exceções | contrato de erro RFC 9457 |
| `enums` | `MatriculaAlunoStatusEnum` | status acadêmico |

Divisão por camada dentro de cada domínio:

- **Controller**: contrato HTTP, `@Valid`, `@PreAuthorize` por perfil, status (201/204) e `Location`.
- **Service**: transações (`@Transactional`), regras de negócio, **autorização por vínculo** (`ControleAcesso`),
  conversão para DTO dentro da transação (`open-in-view=false`).
- **Repository**: Spring Data JPA; `Specification` para filtros e escopo; `@EntityGraph` nas listagens;
  bloqueio pessimista onde há disputa (notas, trancamento, refresh token, contas ADMIN).
- **Entidade**: persistência e invariantes (`MatriculaAluno` concentra as transições de status e o cálculo da média).
- **DTO**: records com Bean Validation; normalização de texto no construtor compacto, antes da validação.

## Fluxo de uma requisição autenticada

```mermaid
sequenceDiagram
    participant C as Cliente
    participant F as CorrelationIdFilter
    participant S as Spring Security
    participant J as JwtUsuarioConverter
    participant Ct as Controller
    participant Sv as Service
    participant DB as PostgreSQL
    C->>F: cabeçalho Authorization com token Bearer
    F->>S: X-Correlation-Id registrado no MDC
    S->>S: valida assinatura HS256, exp, iss e aud
    S->>J: claims sub e ver
    J->>DB: busca o usuário por id e confere ativo e versão das credenciais
    J-->>S: UsuarioAutenticado com o papel do perfil
    S->>Ct: regra de URL com negação por padrão
    Ct->>Ct: PreAuthorize por perfil e validação do DTO
    Ct->>Sv: DTO de entrada
    Sv->>Sv: ControleAcesso verifica vínculo e propriedade
    Sv->>DB: consulta com escopo e paginação
    Sv-->>Ct: DTO de resposta
    Ct-->>C: JSON, ou application/problem+json em caso de erro
```

Falhas em cada etapa: token inválido/expirado/revogado → 401 (`ProblemaSegurancaHandler`); perfil ou vínculo
insuficiente → 403; validação → 400; conflito de estado ou unicidade → 409; exceção inesperada → 500 sem detalhes
internos, com o mesmo `correlationId` no log.

## Componentes e implantação local

```mermaid
flowchart LR
    subgraph Host ["Computador local"]
        DBeaver["DBeaver"]
        Navegador["Navegador"]
    end
    subgraph Compose ["Docker Compose, projeto campus-api"]
        MIG["campus-api-migracao: Flyway CLI 11.14.1, usuário campus_user, executa e termina"]
        API["campus-api-app: Spring Boot, perfil prod, usuário campus_app somente DML"]
        PG[("campus-api-db: PostgreSQL 16, volume campus_api_data")]
    end
    DBeaver -->|"127.0.0.1 na POSTGRES_PORT"| PG
    Navegador -->|"127.0.0.1 na APP_PORT"| API
    MIG -->|"postgres na porta 5432"| PG
    MIG -.->|"concluiu com sucesso"| API
    API -->|"postgres na porta 5432"| PG
```

## Decisões relevantes

- **Fonte única de verdade da matrícula**: `MatriculaAluno`; a relação N:N antiga foi reconciliada na V2.
- **Access token curto + usuário recarregado por requisição**: desativação, troca de senha e mudança de perfil
  invalidam tokens já emitidos sem lista de revogação (custo: uma consulta por chave primária por requisição).
- **Refresh token opaco, armazenado como SHA-256, com famílias e detecção de reutilização estrita** (ver README).
- **Escopo das listagens dentro da consulta** (`Specification`), nunca após a paginação.
- **Privilégio mínimo no banco**: a aplicação conecta como `campus_app` (somente DML; sem escrita no histórico do
  Flyway nem nas tabelas `*_legado`); as migrações usam o dono do schema em uma etapa separada (serviço
  `migracao` no Compose), de modo que a senha administrativa não fica no processo da API. No perfil dev a API ainda
  migra no próprio processo, por conveniência local.
- **Falha rápida de configuração**: sem `DB_*` ou `JWT_SECRET` fora do perfil dev, a inicialização é interrompida
  com a lista de variáveis ausentes.
- **Sem dependências adicionais** para limite de tentativas: implementação em memória adequada a uma instância.
