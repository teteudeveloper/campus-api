# Campus API

Academic management REST API — sistema de gerenciamento acadêmico construído com Java, Spring Boot e PostgreSQL.

---

## Visão Geral

Campus API é uma API REST para gerenciamento de sistemas acadêmicos. O sistema permite o cadastro e a administração de Alunos, Professores, Disciplinas e Matrículas, com regras de negócio acadêmicas reais: controle de status de matrícula, lançamento de notas, cálculo automático de média e emissão de histórico.

---

## Problema que o Projeto Resolve

Sistemas acadêmicos precisam gerenciar entidades com relacionamentos complexos e regras de negócio específicas: um professor leciona várias disciplinas, um aluno pode estar matriculado em múltiplas disciplinas, matrículas têm status controlados (MATRICULADO, APROVADO, REPROVADO, TRANCADO) e médias são calculadas automaticamente. Sem uma API estruturada, essas operações se tornam difíceis de manter e testar.

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

---

## Como Rodar Localmente

Suba o PostgreSQL pelo Docker:

```bash
docker compose up -d
```

Execute a API:

```bash
./mvnw spring-boot:run
```

A API fica disponível em:

```text
http://localhost:8080
```

O PostgreSQL do projeto fica disponível para IntelliJ e DBeaver em:

```text
Host: localhost
Porta: 5433
Database: campus_api
Usuario: campus_user
Senha: campus_pass
JDBC URL: jdbc:postgresql://localhost:5433/campus_api
```

> A porta local `5433` foi usada para evitar conflito com outro PostgreSQL já existente em `5432`.

---

## Arquitetura

```
Controller  →  Service  →  Repository  →  PostgreSQL
```

Organização por domínio (feature-based packages): cada domínio agrupa todos os seus componentes no mesmo pacote.

```
br.com.campusapi/
├── aluno/
├── professor/
├── disciplina/
├── matricula/
│   └── dto/
├── enums/
└── exception/
```

---

## Modelagem

```
Professor (1) ───── (N) Disciplina
Aluno     (N) ───── (N) Disciplina   [tabela: alunos_disciplinas]
Aluno     (1) ───── (N) MatriculaAluno
Disciplina(1) ───── (N) MatriculaAluno
```

### Status da Matrícula

```
MATRICULADO  →  (lançar notas)  →  APROVADO (média >= 7.0)
                                →  REPROVADO (média < 7.0)
MATRICULADO  →  (trancar)       →  TRANCADO
```

---

## Endpoints e Exemplos de Requisição

### Alunos

**POST /alunos — Criar aluno**

```http
POST http://localhost:8080/alunos
Content-Type: application/json

{
  "nome": "Lucas Oliveira",
  "email": "lucas.oliveira@email.com",
  "matricula": "2024001"
}
```

Resposta — 201 Created:

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

**GET /alunos — Listar todos**

```http
GET http://localhost:8080/alunos
```

Resposta — 200 OK:

```json
[
  {
    "id": "c3d4e5f6-0003-0003-0003-000000000001",
    "nome": "Lucas Oliveira",
    "email": "lucas.oliveira@email.com",
    "matricula": "2024001"
  },
  {
    "id": "c3d4e5f6-0003-0003-0003-000000000002",
    "nome": "Mariana Costa",
    "email": "mariana.costa@email.com",
    "matricula": "2024002"
  }
]
```

---

**GET /alunos/{id} — Buscar por ID**

```http
GET http://localhost:8080/alunos/c3d4e5f6-0003-0003-0003-000000000001
```

Resposta — 200 OK:

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

**PUT /alunos/{id} — Atualizar aluno**

```http
PUT http://localhost:8080/alunos/c3d4e5f6-0003-0003-0003-000000000001
Content-Type: application/json

{
  "nome": "Lucas Oliveira Silva",
  "email": "lucas.silva@email.com",
  "matricula": "2024001"
}
```

Resposta — 200 OK:

```json
{
  "id": "c3d4e5f6-0003-0003-0003-000000000001",
  "nome": "Lucas Oliveira Silva",
  "email": "lucas.silva@email.com",
  "matricula": "2024001",
  "criadoEm": "2026-04-04T16:51:54.893",
  "atualizadoEm": "2026-04-05T10:00:00.000"
}
```

---

**DELETE /alunos/{id} — Remover aluno**

```http
DELETE http://localhost:8080/alunos/c3d4e5f6-0003-0003-0003-000000000001
```

Resposta — 204 No Content (sem corpo)

---

**GET /alunos/{id} — Recurso inexistente**

```http
GET http://localhost:8080/alunos/00000000-0000-0000-0000-000000000000
```

Resposta — 404 Not Found:

```json
{
  "status": 404,
  "erro": "Recurso não encontrado",
  "mensagem": "Aluno não encontrado com id: 00000000-0000-0000-0000-000000000000",
  "timestamp": "2026-04-05T14:44:00"
}
```

---

### Professores

**POST /professores — Criar professor**

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

Resposta — 201 Created:

```json
{
  "id": "a1b2c3d4-0001-0001-0001-000000000001",
  "nome": "Carlos Eduardo Mendes",
  "email": "carlos.mendes@campusapi.com",
  "registro": "PROF-2019-001",
  "especialidade": "Matematica",
  "disciplinas": [],
  "criadoEm": "2026-04-04T16:51:54.888",
  "atualizadoEm": "2026-04-04T16:51:54.888"
}
```

---

**GET /professores — Listar todos**

```http
GET http://localhost:8080/professores
```

Resposta — 200 OK:

```json
[
  {
    "id": "a1b2c3d4-0001-0001-0001-000000000001",
    "nome": "Carlos Eduardo Mendes",
    "especialidade": "Matematica"
  },
  {
    "id": "a1b2c3d4-0001-0001-0001-000000000002",
    "nome": "Ana Paula Ferreira",
    "especialidade": "Fisica"
  }
]
```

---

**GET /professores/{id} — Buscar por ID**

```http
GET http://localhost:8080/professores/a1b2c3d4-0001-0001-0001-000000000001
```

Resposta — 200 OK:

```json
{
  "id": "a1b2c3d4-0001-0001-0001-000000000001",
  "nome": "Carlos Eduardo Mendes",
  "email": "carlos.mendes@campusapi.com",
  "registro": "PROF-2019-001",
  "especialidade": "Matematica",
  "disciplinas": [
    {
      "id": "b2c3d4e5-0002-0002-0002-000000000001",
      "nome": "Calculo I",
      "codigo": "MAT-101"
    }
  ]
}
```

---

**PUT /professores/{id} — Atualizar professor**

```http
PUT http://localhost:8080/professores/a1b2c3d4-0001-0001-0001-000000000001
Content-Type: application/json

{
  "nome": "Carlos Eduardo Mendes",
  "email": "carlos.mendes@campusapi.com",
  "registro": "PROF-2019-001",
  "especialidade": "Matematica Aplicada"
}
```

Resposta — 200 OK:

```json
{
  "id": "a1b2c3d4-0001-0001-0001-000000000001",
  "nome": "Carlos Eduardo Mendes",
  "especialidade": "Matematica Aplicada"
}
```

---

**DELETE /professores/{id} — Remover professor**

```http
DELETE http://localhost:8080/professores/a1b2c3d4-0001-0001-0001-000000000001
```

Resposta — 204 No Content (sem corpo)

---

### Disciplinas

**POST /disciplinas — Criar disciplina vinculada a professor**

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

Resposta — 201 Created:

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
  "alunos": []
}
```

---

**GET /disciplinas — Listar todas**

```http
GET http://localhost:8080/disciplinas
```

Resposta — 200 OK:

```json
[
  {
    "id": "b2c3d4e5-0002-0002-0002-000000000001",
    "nome": "Calculo I",
    "codigo": "MAT-101",
    "cargaHoraria": 80
  },
  {
    "id": "b2c3d4e5-0002-0002-0002-000000000002",
    "nome": "Fisica I",
    "codigo": "FIS-101",
    "cargaHoraria": 60
  }
]
```

---

**GET /disciplinas/professor/{professorId} — Listar disciplinas por professor**

```http
GET http://localhost:8080/disciplinas/professor/a1b2c3d4-0001-0001-0001-000000000001
```

Resposta — 200 OK:

```json
[
  {
    "id": "b2c3d4e5-0002-0002-0002-000000000001",
    "nome": "Calculo I",
    "codigo": "MAT-101",
    "cargaHoraria": 80
  },
  {
    "id": "b2c3d4e5-0002-0002-0002-000000000002",
    "nome": "Calculo II",
    "codigo": "MAT-102",
    "cargaHoraria": 80
  }
]
```

---

**POST /disciplinas/{id}/alunos/{alunoId} — Matricular aluno em disciplina**

```http
POST http://localhost:8080/disciplinas/b2c3d4e5-0002-0002-0002-000000000001/alunos/c3d4e5f6-0003-0003-0003-000000000001
```

Resposta — 200 OK:

```json
{
  "id": "b2c3d4e5-0002-0002-0002-000000000001",
  "nome": "Calculo I",
  "alunos": [
    {
      "id": "c3d4e5f6-0003-0003-0003-000000000001",
      "nome": "Lucas Oliveira",
      "matricula": "2024001"
    }
  ]
}
```

---

**DELETE /disciplinas/{id}/alunos/{alunoId} — Remover matrícula da disciplina**

```http
DELETE http://localhost:8080/disciplinas/b2c3d4e5-0002-0002-0002-000000000001/alunos/c3d4e5f6-0003-0003-0003-000000000001
```

Resposta — 200 OK (retorna disciplina sem o aluno)

---

**DELETE /disciplinas/{id} — Remover disciplina**

```http
DELETE http://localhost:8080/disciplinas/b2c3d4e5-0002-0002-0002-000000000009
```

Resposta — 204 No Content (sem corpo)

---

### Matrículas

**POST /matriculas — Criar matrícula**

O status é definido automaticamente como MATRICULADO pelo sistema. O cliente não envia o status.

```http
POST http://localhost:8080/matriculas
Content-Type: application/json

{
  "alunoId": "c3d4e5f6-0003-0003-0003-000000000001",
  "disciplinaId": "b2c3d4e5-0002-0002-0002-000000000001"
}
```

Resposta — 201 Created:

```json
{
  "id": "d4e5f6a7-0004-0004-0004-000000000001",
  "aluno": {
    "id": "c3d4e5f6-0003-0003-0003-000000000001",
    "nome": "Lucas Oliveira",
    "email": "lucas.oliveira@email.com",
    "matricula": "2024001"
  },
  "disciplina": {
    "id": "b2c3d4e5-0002-0002-0002-000000000001",
    "nome": "Calculo I",
    "codigo": "MAT-101",
    "cargaHoraria": 80
  },
  "nota1": null,
  "nota2": null,
  "status": "MATRICULADO"
}
```

---

**PATCH /matriculas/trancar/{id} — Trancar matrícula**

Só funciona se o status atual for MATRICULADO. Retorna 400 se o status for diferente.

```http
PATCH http://localhost:8080/matriculas/trancar/{id}
```

Resposta — 204 No Content (sem corpo)

Resposta de erro — 400 Bad Request (se status não for MATRICULADO):

```json
{
  "status": 400,
  "mensagem": "So e possivel trancar matricula com status MATRICULADO"
}
```

---

**PATCH /matriculas/atualizar-notas/{id} — Atualizar notas**

Pode enviar nota1, nota2 ou ambas. Quando as duas estiverem preenchidas, a média é calculada automaticamente e o status muda para APROVADO (média >= 7.0) ou REPROVADO (média < 7.0).

```http
PATCH http://localhost:8080/matriculas/atualizar-notas/{id}
Content-Type: application/json

{
  "nota1": 8.0,
  "nota2": 7.5
}
```

Resposta — 204 No Content (sem corpo)

Enviar apenas uma nota também é válido:

```http
PATCH http://localhost:8080/matriculas/atualizar-notas/{id}
Content-Type: application/json

{
  "nota1": 8.0
}
```

---

**GET /matriculas/emitir-historico/{alunoId} — Emitir histórico do aluno**

```http
GET http://localhost:8080/matriculas/emitir-historico/c3d4e5f6-0003-0003-0003-000000000001
```

Resposta — 200 OK:

```json
{
  "nomeAluno": "Lucas Oliveira",
  "emailAluno": "lucas.oliveira@email.com",
  "matriculaAluno": "2024001",
  "disciplinas": [
    {
      "nomeDisciplina": "Calculo I",
      "nomeProfessor": "Carlos Eduardo Mendes",
      "nota1": 8.0,
      "nota2": 7.5,
      "media": 7.75,
      "status": "APROVADO"
    },
    {
      "nomeDisciplina": "Algoritmos e Programacao",
      "nomeProfessor": "Roberto Lima",
      "nota1": 5.0,
      "nota2": 4.0,
      "media": 4.5,
      "status": "REPROVADO"
    },
    {
      "nomeDisciplina": "Estrutura de Dados",
      "nomeProfessor": "Roberto Lima",
      "nota1": null,
      "nota2": null,
      "media": null,
      "status": "MATRICULADO"
    }
  ]
}
```

---

## Regras de Negócio

- Toda matrícula é criada com status **MATRICULADO** automaticamente
- Só é possível trancar uma matrícula com status **MATRICULADO**
- Quando nota1 e nota2 são lançadas, a média é calculada: **(nota1 + nota2) / 2**
- Média >= 7.0 → status **APROVADO**
- Média < 7.0 → status **REPROVADO**
- É possível lançar apenas uma nota por vez (PATCH parcial)

---

## Banco de Dados

Tabelas geradas automaticamente pelo Hibernate:

| Tabela | Descrição |
|---|---|
| `alunos` | Dados dos alunos |
| `professores` | Dados dos professores |
| `disciplinas` | Disciplinas vinculadas a professores |
| `alunos_disciplinas` | Tabela intermediária N:N |
| `matriculas` | Matrículas com notas e status |

---

## Decisões Técnicas

- **UUID** como ID — imprevisível e seguro para APIs públicas
- **DTO** separado da Entity — desacopla contrato da API da estrutura do banco
- **Feature-based packages** — organização por domínio, não por camada
- **GlobalExceptionHandler** — respostas de erro padronizadas em JSON
- **FetchType.LAZY** — carregamento sob demanda para melhor performance
- **Injeção por construtor** — mais testável que @Autowired no campo
- **@PrePersist / @PreUpdate** — timestamps automáticos sem intervenção manual
- **@Enumerated(EnumType.STRING)** — status salvo como texto legível no banco

---

## Autor

Desenvolvido como projeto de portfólio — UNIESP, Disciplina de Backend Java/Spring Boot.
