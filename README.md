# Campus API

Academic management REST API — sistema de gerenciamento acadêmico construído com Java, Spring Boot e PostgreSQL.

---

## Visão Geral

Campus API é uma API REST para gerenciamento de sistemas acadêmicos. O sistema permite o cadastro e a administração de Alunos, Professores e Disciplinas, incluindo o controle de matrículas com relacionamentos entre as entidades.

O projeto foi desenvolvido com foco em padrões de mercado, boas práticas de arquitetura backend e domínio do ecossistema Java moderno, sendo adequado para demonstrar competência técnica em entrevistas e processos seletivos.

---

## Problema que o Projeto Resolve

Sistemas acadêmicos precisam gerenciar entidades com relacionamentos complexos: um professor leciona várias disciplinas, um aluno pode estar matriculado em múltiplas disciplinas e uma disciplina pode ter múltiplos alunos. Sem uma API estruturada, essas operações se tornam difíceis de manter, escalar e testar.

---

## Solução

A Campus API expõe endpoints REST para todas as operações de gerenciamento acadêmico, encapsulando as regras de negócio em uma arquitetura em camadas bem definida. A API recebe e retorna dados em formato JSON, trata erros de forma padronizada e persiste os dados em um banco PostgreSQL containerizado com Docker.

---

## Funcionalidades

- Cadastro, consulta, atualização e remoção de Alunos
- Cadastro, consulta, atualização e remoção de Professores
- Cadastro, consulta, atualização e remoção de Disciplinas
- Vinculação de Professor responsável a uma Disciplina
- Matrícula de Alunos em Disciplinas
- Cancelamento de matrícula de Alunos em Disciplinas
- Respostas de erro padronizadas com status HTTP corretos

---

## Arquitetura

O projeto adota arquitetura em camadas com organização por domínio (feature-based packages). Cada domínio agrupa todos os seus componentes no mesmo pacote, tornando o código navegável e coeso.

```
Controller  →  Service  →  Repository  →  PostgreSQL
```

| Camada | Responsabilidade |
|---|---|
| Controller | Recebe a requisição HTTP, delega ao Service, retorna a resposta com o status HTTP correto |
| Service | Executa as regras de negócio, valida existência de recursos, orquestra as operações |
| Repository | Interface JPA que abstrai o acesso ao banco de dados |
| Entity | Classe Java mapeada para uma tabela do banco via JPA/Hibernate |
| DTO | Objeto de transferência que define exatamente o que o cliente pode enviar na requisição |

---

## Modelagem de Domínio

### Aluno

Representa um estudante matriculado no sistema. Possui nome, e-mail único e matrícula única. Pode estar vinculado a múltiplas disciplinas através da tabela intermediária `alunos_disciplinas`.

### Professor

Representa um docente do sistema. Possui nome, e-mail único, registro único e especialidade. Pode ser responsável por múltiplas disciplinas, refletindo a realidade acadêmica onde um professor leciona mais de uma matéria dentro de sua área de conhecimento.

### Disciplina

Representa uma matéria do sistema. Possui nome, código único e carga horária. Está vinculada a um Professor responsável (relacionamento N:1) e pode ter múltiplos Alunos matriculados (relacionamento N:N).

### Relacionamentos

```
Professor (1) ───── (N) Disciplina
Aluno     (N) ───── (N) Disciplina
                         tabela intermediária: alunos_disciplinas
```

---

## Estrutura do Projeto

```
campus-api/
├── src/
│   └── main/
│       ├── java/br/com/campusapi/
│       │   ├── CampusApiApplication.java
│       │   ├── aluno/
│       │   │   ├── Aluno.java
│       │   │   ├── AlunoDTO.java
│       │   │   ├── AlunoRepository.java
│       │   │   ├── AlunoService.java
│       │   │   └── AlunoController.java
│       │   ├── professor/
│       │   │   ├── Professor.java
│       │   │   ├── ProfessorDTO.java
│       │   │   ├── ProfessorRepository.java
│       │   │   ├── ProfessorService.java
│       │   │   └── ProfessorController.java
│       │   ├── disciplina/
│       │   │   ├── Disciplina.java
│       │   │   ├── DisciplinaDTO.java
│       │   │   ├── DisciplinaRepository.java
│       │   │   ├── DisciplinaService.java
│       │   │   └── DisciplinaController.java
│       │   └── exception/
│       │       ├── GlobalExceptionHandler.java
│       │       └── RecursoNaoEncontradoException.java
│       └── resources/
│           └── application.properties
├── docker-compose.yml
└── pom.xml
```

---

## Stack Tecnológica

| Tecnologia | Versão | Função |
|---|---|---|
| Java | 21 LTS | Linguagem principal |
| Spring Boot | 4.0.5 | Framework web e configuração automática |
| Spring Data JPA | — | Abstração sobre Hibernate para persistência |
| Hibernate ORM | 7.2.7 | Implementação JPA, geração de SQL e DDL |
| PostgreSQL | 16 | Banco de dados relacional |
| Docker | — | Containerização do banco de dados |
| Lombok | — | Geração de código boilerplate em tempo de compilação |
| Maven | — | Gerenciamento de dependências e build |
| HikariCP | — | Pool de conexões JDBC incluso no Spring Boot |
| Tomcat | 11.0.20 | Servidor HTTP embutido |

---

## Endpoints

### Alunos

| Método | Endpoint | Status | Descrição |
|---|---|---|---|
| GET | `/alunos` | 200 | Lista todos os alunos |
| GET | `/alunos/{id}` | 200 | Busca aluno por ID |
| POST | `/alunos` | 201 | Cria novo aluno |
| PUT | `/alunos/{id}` | 200 | Atualiza dados do aluno |
| DELETE | `/alunos/{id}` | 204 | Remove aluno |

### Professores

| Método | Endpoint | Status | Descrição |
|---|---|---|---|
| GET | `/professores` | 200 | Lista todos os professores |
| GET | `/professores/{id}` | 200 | Busca professor por ID |
| POST | `/professores` | 201 | Cria novo professor |
| PUT | `/professores/{id}` | 200 | Atualiza dados do professor |
| DELETE | `/professores/{id}` | 204 | Remove professor |

### Disciplinas

| Método | Endpoint | Status | Descrição |
|---|---|---|---|
| GET | `/disciplinas` | 200 | Lista todas as disciplinas |
| GET | `/disciplinas/{id}` | 200 | Busca disciplina por ID |
| POST | `/disciplinas` | 201 | Cria nova disciplina |
| PUT | `/disciplinas/{id}` | 200 | Atualiza disciplina |
| POST | `/disciplinas/{id}/alunos/{alunoId}` | 200 | Matricula aluno na disciplina |
| DELETE | `/disciplinas/{id}/alunos/{alunoId}` | 200 | Remove matrícula do aluno |
| DELETE | `/disciplinas/{id}` | 204 | Remove disciplina |

---

## Exemplos de Requisições e Respostas

### POST /professores — Criar Professor

**Requisição**

```http
POST http://localhost:8080/professores
Content-Type: application/json

{
  "nome": "Carlos Eduardo Mendes",
  "email": "carlos.mendes@campusapi.com",
  "registro": "PROF-2019-001",
  "especialidade": "Matematica"
}
```

**Resposta — 201 Created**

```json
{
  "id": "a1b2c3d4-0001-0001-0001-000000000001",
  "nome": "Carlos Eduardo Mendes",
  "email": "carlos.mendes@campusapi.com",
  "registro": "PROF-2019-001",
  "especialidade": "Matematica Aplicada",
  "disciplinas": [],
  "criadoEm": "2026-04-04T16:51:54.888",
  "atualizadoEm": "2026-04-05T14:39:11.073"
}
```

---

### POST /alunos — Criar Aluno

**Requisição**

```http
POST http://localhost:8080/alunos
Content-Type: application/json

{
  "nome": "Lucas Oliveira",
  "email": "lucas.oliveira@email.com",
  "matricula": "2024001"
}
```

**Resposta — 201 Created**

```json
{
  "id": "c3d4e5f6-0003-0003-0003-000000000001",
  "nome": "Lucas Oliveira",
  "email": "lucas.oliveira@email.com",
  "matricula": "2024001",
  "criadoEm": "2026-04-04T16:51:54.893",
  "atualizadoEm": "2026-04-04T16:51:54.893"
}
```

---

### POST /disciplinas — Criar Disciplina vinculada a Professor

**Requisição**

```http
POST http://localhost:8080/disciplinas
Content-Type: application/json

{
  "nome": "Calculo I",
  "codigo": "MAT-101",
  "cargaHoraria": 80,
  "professorId": "a1b2c3d4-0001-0001-0001-000000000001"
}
```

**Resposta — 201 Created**

```json
{
  "id": "b2c3d4e5-0002-0002-0002-000000000001",
  "nome": "Calculo I",
  "codigo": "MAT-101",
  "cargaHoraria": 80,
  "professor": {
    "id": "a1b2c3d4-0001-0001-0001-000000000001",
    "nome": "Carlos Eduardo Mendes",
    "especialidade": "Matematica Aplicada"
  },
  "alunos": [],
  "criadoEm": "2026-04-04T16:51:54.891",
  "atualizadoEm": "2026-04-04T16:51:54.891"
}
```

---

### POST /disciplinas/{id}/alunos/{alunoId} — Matricular Aluno

**Requisição**

```http
POST http://localhost:8080/disciplinas/b2c3d4e5-0002-0002-0002-000000000001/alunos/c3d4e5f6-0003-0003-0003-000000000001
```

**Resposta — 200 OK**

```json
{
  "id": "b2c3d4e5-0002-0002-0002-000000000001",
  "nome": "Calculo I",
  "codigo": "MAT-101",
  "cargaHoraria": 80,
  "professor": {
    "id": "a1b2c3d4-0001-0001-0001-000000000001",
    "nome": "Carlos Eduardo Mendes",
    "especialidade": "Matematica Aplicada"
  },
  "alunos": [
    {
      "id": "c3d4e5f6-0003-0003-0003-000000000001",
      "nome": "Lucas Oliveira",
      "email": "lucas.oliveira@email.com",
      "matricula": "2024001"
    }
  ],
  "criadoEm": "2026-04-04T16:51:54.891",
  "atualizadoEm": "2026-04-04T16:51:54.891"
}
```

---

### GET /alunos — Listar Todos os Alunos

**Requisição**

```http
GET http://localhost:8080/alunos
```

**Resposta — 200 OK**

```json
[
  { "id": "c3d4e5f6-0003-0003-0003-000000000001", "nome": "Lucas Oliveira",        "email": "lucas.oliveira@email.com",      "matricula": "2024001" },
  { "id": "c3d4e5f6-0003-0003-0003-000000000002", "nome": "Mariana Costa",         "email": "mariana.costa@email.com",       "matricula": "2024002" },
  { "id": "c3d4e5f6-0003-0003-0003-000000000003", "nome": "Pedro Henrique Santos", "email": "pedro.santos@email.com",        "matricula": "2024003" },
  { "id": "c3d4e5f6-0003-0003-0003-000000000004", "nome": "Fernanda Rodrigues",    "email": "fernanda.rodrigues@email.com",  "matricula": "2024004" },
  { "id": "c3d4e5f6-0003-0003-0003-000000000005", "nome": "Gabriel Almeida",       "email": "gabriel.almeida@email.com",     "matricula": "2024005" },
  { "id": "c3d4e5f6-0003-0003-0003-000000000006", "nome": "Beatriz Nascimento",    "email": "beatriz.nascimento@email.com",  "matricula": "2024006" },
  { "id": "c3d4e5f6-0003-0003-0003-000000000007", "nome": "Rafael Pereira",        "email": "rafael.pereira@email.com",      "matricula": "2024007" },
  { "id": "c3d4e5f6-0003-0003-0003-000000000008", "nome": "Camila Martins",        "email": "camila.martins@email.com",      "matricula": "2024008" },
  { "id": "c3d4e5f6-0003-0003-0003-000000000009", "nome": "Thiago Barbosa",        "email": "thiago.barbosa@email.com",      "matricula": "2024009" },
  { "id": "c3d4e5f6-0003-0003-0003-000000000010", "nome": "Juliana Lima",          "email": "juliana.lima@email.com",        "matricula": "2024010" }
]
```

---

### GET /alunos/{id} — Recurso Inexistente

**Requisição**

```http
GET http://localhost:8080/alunos/00000000-0000-0000-0000-000000000000
```

**Resposta — 404 Not Found**

```json
{
  "status": 404,
  "erro": "Recurso não encontrado",
  "mensagem": "Aluno não encontrado com id: 00000000-0000-0000-0000-000000000000",
  "timestamp": "2026-04-05T14:44:00"
}
```

---

## Banco de Dados — Estado Real

Banco `campus_api` conectado em `localhost:5432`. Todas as tabelas foram geradas automaticamente pelo Hibernate a partir das entidades JPA com `ddl-auto=update`.

### Visão geral das tabelas

| Tabela | Registros |
|---|---|
| `professores` | 6 |
| `disciplinas` | 12 |
| `alunos` | 10 |
| `alunos_disciplinas` | 30 |

---

### professores

| nome | email | especialidade | registro |
|---|---|---|---|
| Ana Paula Ferreira | ana.ferreira@campusapi.com | Fisica | PROF-2020-002 |
| Roberto Lima | roberto.lima@campusapi.com | Ciencia da Computacao | PROF-2021-003 |
| Patricia Souza | patricia.souza@campusapi.com | Quimica | PROF-2018-004 |
| Fernando Costa | fernando.costa@campusapi.com | Engenharia de Software | PROF-2022-005 |
| Marcos Vinicius Teixeira | marcos.teixeira@campusapi.com | Inteligencia Artificial | PROF-2023-006 |
| Carlos Eduardo Mendes | carlos.mendes@campusapi.com | Matematica Aplicada | PROF-2019-001 |

---

### disciplinas

| nome | codigo | carga_horaria | professor responsável |
|---|---|---|---|
| Calculo I | MAT-101 | 80h | Carlos Eduardo Mendes |
| Calculo II | MAT-102 | 80h | Carlos Eduardo Mendes |
| Algebra Linear | MAT-201 | 60h | Carlos Eduardo Mendes |
| Fisica I | FIS-101 | 60h | Ana Paula Ferreira |
| Fisica II | FIS-102 | 60h | Ana Paula Ferreira |
| Algoritmos e Programacao | CC-101 | 80h | Roberto Lima |
| Estrutura de Dados | CC-201 | 60h | Roberto Lima |
| Banco de Dados | CC-301 | 60h | Roberto Lima |
| Quimica Geral | QUI-101 | 60h | Patricia Souza |
| Engenharia de Software | ES-101 | 80h | Fernando Costa |
| Padroes de Projeto | ES-201 | 60h | Fernando Costa |
| Arquitetura de Software | ES-301 | 60h | Fernando Costa |

---

### alunos

| nome | email | matricula |
|---|---|---|
| Lucas Oliveira | lucas.oliveira@email.com | 2024001 |
| Mariana Costa | mariana.costa@email.com | 2024002 |
| Pedro Henrique Santos | pedro.santos@email.com | 2024003 |
| Fernanda Rodrigues | fernanda.rodrigues@email.com | 2024004 |
| Gabriel Almeida | gabriel.almeida@email.com | 2024005 |
| Beatriz Nascimento | beatriz.nascimento@email.com | 2024006 |
| Rafael Pereira | rafael.pereira@email.com | 2024007 |
| Camila Martins | camila.martins@email.com | 2024008 |
| Thiago Barbosa | thiago.barbosa@email.com | 2024009 |
| Juliana Lima | juliana.lima@email.com | 2024010 |

---

### alunos_disciplinas

Tabela intermediária gerada automaticamente pelo Hibernate para o relacionamento `@ManyToMany`. Contém 30 registros representando as matrículas ativas entre alunos e disciplinas.

| disciplina_id | aluno_id |
|---|---|
| b2c3d4e5-...-000000000001 | c3d4e5f6-...-000000000001 |
| b2c3d4e5-...-000000000006 | c3d4e5f6-...-000000000001 |
| b2c3d4e5-...-000000000007 | c3d4e5f6-...-000000000001 |
| b2c3d4e5-...-000000000001 | c3d4e5f6-...-000000000002 |
| b2c3d4e5-...-000000000004 | c3d4e5f6-...-000000000002 |
| b2c3d4e5-...-000000000009 | c3d4e5f6-...-000000000002 |
| b2c3d4e5-...-000000000006 | c3d4e5f6-...-000000000003 |
| b2c3d4e5-...-000000000007 | c3d4e5f6-...-000000000003 |
| b2c3d4e5-...-000000000008 | c3d4e5f6-...-000000000003 |
| b2c3d4e5-...-000000000002 | c3d4e5f6-...-000000000004 |
| b2c3d4e5-...-000000000003 | c3d4e5f6-...-000000000004 |
| b2c3d4e5-...-000000000005 | c3d4e5f6-...-000000000004 |

> 30 registros totais. Exibição parcial — cada aluno está matriculado em 3 disciplinas.

---

## Decisões Técnicas

**UUID como identificador**
Identificadores sequenciais (Long) são previsíveis e expõem a estrutura interna da API. UUID garante que IDs sejam impossíveis de adivinhar, o que é o padrão em APIs REST modernas.

**DTO separado da Entidade**
Expor a entidade JPA diretamente no Controller acopla o contrato da API à estrutura do banco. Com DTO, o cliente envia apenas os campos permitidos e o sistema controla os campos gerados internamente (id, criadoEm, atualizadoEm).

**Organização por domínio (feature-based)**
A alternativa por camada torna difícil navegar pelo código conforme o projeto cresce. Com organização por domínio, tudo relacionado a um contexto fica em um único pacote — fácil de ler, modificar e testar.

**GlobalExceptionHandler centralizado**
Com `@RestControllerAdvice`, qualquer `RecursoNaoEncontradoException` lançada em qualquer Service é interceptada e convertida em uma resposta JSON padronizada com o status HTTP correto, sem duplicação de código nos Controllers.

**FetchType.LAZY nos relacionamentos**
O carregamento EAGER carregaria todos os dados relacionados a cada consulta, mesmo quando desnecessário. LAZY carrega os dados apenas quando explicitamente acessados, evitando queries desnecessárias.

**@PrePersist e @PreUpdate**
Os timestamps `criadoEm` e `atualizadoEm` são gerenciados automaticamente pelo JPA antes de cada inserção e atualização, eliminando a necessidade de preencher esses campos manualmente no Service.

**Injeção de dependência por construtor**
Com `@RequiredArgsConstructor` do Lombok e campos `final`, o construtor gerado permite injeção pelo Spring e substituição simples em testes, sendo mais testável que `@Autowired` no campo.

---