# Operação local: PostgreSQL, DBeaver, boot, backup

## 1. Configuração inicial (uma vez)

```bash
./scripts/configurar-ambiente-local.sh   # gera .env (permissão 600, ignorado pelo Git) com segredos aleatórios
docker compose up -d postgres            # PostgreSQL 16 com restart: unless-stopped
```

O script escolhe uma porta livre para o PostgreSQL (5433 se disponível; nesta máquina, **5435**, pois 5433 e 5434
já estão em uso por outros projetos) e para a API (8080 se disponível). Não sobrescreve um `.env` existente.

### Volume já existente (versão anterior do projeto)

O PostgreSQL só aplica `POSTGRES_USER/POSTGRES_PASSWORD/POSTGRES_DB` na **primeira** inicialização do volume.
Trocar esses valores no `.env` não altera contas de um volume já criado. Para alinhar o banco ao `.env`:

```bash
./scripts/db-provisionar.sh   # define a senha do administrador e cria/atualiza o usuário campus_app (somente DML)
```

O script usa o socket local dentro do contêiner e não exibe senhas. É idempotente.

## 2. Usuários do banco

| Usuário | Uso | Permissões |
|---|---|---|
| `campus_user` (`POSTGRES_USER`) | administração, DBeaver para manutenção, etapa de migração (`migracao`) | superusuário do contêiner, dono do schema |
| `campus_app` (`APP_DB_USER`) | API em execução; DBeaver para consulta/edição de dados | `SELECT/INSERT/UPDATE/DELETE`; sem DDL; somente leitura em `flyway_schema_history` e `*_legado` |

## 3. Conexão pelo DBeaver

Nova conexão → **PostgreSQL**:

| Campo | Valor |
|---|---|
| Host | `127.0.0.1` |
| Porta | valor de `POSTGRES_PORT` no `.env` (nesta máquina: `5435`) |
| Database | `campus_api` |
| Usuário | `campus_app` (dados) ou `campus_user` (administração) |
| Senha | `APP_DB_PASSWORD` ou `POSTGRES_PASSWORD` no arquivo `.env` da raiz do projeto |
| SSL | desabilitado (`sslmode=disable`): o servidor local não usa SSL e só escuta em 127.0.0.1 |
| URL JDBC | `jdbc:postgresql://127.0.0.1:5435/campus_api` |

Consultar a senha sem abrir o arquivo inteiro: `grep '^APP_DB_PASSWORD=' .env | cut -d= -f2-`.

Conexão validada pelo usuário no DBeaver nesta máquina, além do teste automatizado por TCP com os mesmos dados.

Endereços diferentes conforme quem conecta:

| Cliente | Endereço |
|---|---|
| DBeaver / `./mvnw spring-boot:run` (no computador) | `127.0.0.1:<POSTGRES_PORT>` |
| API dentro do Docker Compose | `postgres:5432` (nome do serviço, porta interna) |

## 4. Comandos do dia a dia

```bash
docker compose up -d postgres                                  # iniciar
docker compose stop postgres                                   # parar (não volta sozinho até novo "up"/"start")
docker compose start postgres                                  # reiniciar após stop
docker compose logs -f postgres                                # acompanhar logs
docker compose ps                                              # estado e saúde
docker inspect -f '{{.State.Health.Status}}' campus-api-db     # healthy | starting | unhealthy
docker compose exec postgres pg_isready -U campus_user -d campus_api

docker compose --profile app up -d --build                     # banco → migração (termina) → API
docker compose --profile app run --rm --no-deps migracao info  # estado das migrações (somente leitura)
docker compose --profile app stop app                          # parar só a API
docker compose logs -f app
curl -s http://127.0.0.1:8080/actuator/health                  # {"status":"UP"}
```

Nunca use `docker compose down -v`: o `-v` apaga o volume `campus-api_campus_api_data` com todos os dados.

## 5. Inicialização automática após reiniciar o computador

Estado verificado nesta máquina (CachyOS, Docker 29.9 **rootful**, sem Docker Desktop):

| Item | Estado | Verificação |
|---|---|---|
| `docker.service` | `enabled` (inicia no boot) | `systemctl is-enabled docker.service` |
| `docker.socket` | `disabled`, ativado como dependência (`Requires=`) do serviço | `systemctl cat docker.service` |
| `containerd.service` | `disabled`, iniciado como dependência (`Wants=`) do serviço | `systemctl cat docker.service` |
| Política do contêiner | `restart: unless-stopped` | `docker inspect -f '{{.HostConfig.RestartPolicy.Name}}' campus-api-db` |
| Evidência no boot atual | daemon iniciado pelo systemd durante o boot; contêineres de outros projetos com `unless-stopped` voltaram sozinhos | `journalctl -b -u docker.service` |

Com isso o PostgreSQL volta após o reboot, **exceto** se tiver sido parado manualmente (`docker compose stop`),
que é o comportamento de `unless-stopped`. A confirmação definitiva só ocorre após um reboot real:

```bash
docker ps --filter name=campus-api-db --format '{{.Status}}'   # deve mostrar "Up ... (healthy)"
```

Se em outra máquina o serviço não estiver habilitado: `sudo systemctl enable --now docker.service`.
Em Docker rootless: `systemctl --user enable --now docker` e `sudo loginctl enable-linger $USER`.
No Docker Desktop: ativar "Start Docker Desktop when you sign in" nas configurações.

A API (`campus-api-app`) não tem política de restart: sobe sob demanda com `docker compose --profile app up -d`.

## 6. Backup e recuperação

```bash
./scripts/db-backup.sh [rotulo]
```

Gera `backups/<data>-<rotulo>/` (diretório ignorado pelo Git, permissão 700) com `campus_api.dump` (formato custom),
`roles.sql` (sem senhas), `SHA256SUMS` e contagens. O backup só é dado como válido depois de restaurado em um
contêiner temporário com contagens idênticas às da origem.

Recuperação (substitui os dados atuais; faça um backup antes):

```bash
D=backups/<diretorio>
(cd "$D" && sha256sum -c SHA256SUMS)
docker compose --profile app stop app
docker compose exec -T postgres psql -U campus_user -d postgres -c "DROP DATABASE campus_api WITH (FORCE)"
docker compose exec -T postgres psql -U campus_user -d postgres -c "CREATE DATABASE campus_api OWNER campus_user"
docker compose exec -T postgres pg_restore -U campus_user -d campus_api --exit-on-error < "$D/campus_api.dump"
./scripts/db-provisionar.sh   # reaplica senhas e permissões de campus_app
```

Backups existentes nesta máquina:
- `backups/20261113-190831-pre-flyway/`: banco **antes** da adoção do Flyway (estado legado), verificado.
- `backups/20261113-192539-pos-flyway/`: após a migração V1–V4, verificado.
- `backups/20261113-192520-pos-flyway-INCOMPLETO/`: tentativa interrompida durante a validação do script; não use.

## 7. Solução de problemas

| Sintoma | Causa provável | Ação |
|---|---|---|
| `required variable POSTGRES_PASSWORD is missing` | `.env` ausente | `./scripts/configurar-ambiente-local.sh` |
| `Bind for 127.0.0.1:5433 failed: port is already allocated` | porta usada por outro projeto | altere `POSTGRES_PORT` no `.env` |
| `password authentication failed` no DBeaver | volume antigo com outra senha | `./scripts/db-provisionar.sh` |
| API: `Configuração obrigatória ausente: ...` | variáveis não definidas fora do perfil dev | defina-as ou use o `.env` com o compose |
| `migracao`: `Found non-empty schema(s) "public" but no schema history table` | banco legado sem adoção do Flyway | backup e uma vez `FLYWAY_BASELINE_ON_MIGRATE=true docker compose --profile app run --rm migracao` |
| `app` não inicia: `dependency failed to start: container campus-api-migracao exited (1)` | migração falhou | `docker compose logs migracao` |
| API: `Migração V2 abortada: ...` | dados legados com conflito | veja README > Migrações > Conflitos |
| `permission denied for table ...` na API | permissões de `campus_app` desatualizadas | `./scripts/db-provisionar.sh` |
| Lombok `EndPosTable` ao compilar | JDK diferente de 21 | use `JAVA_HOME` com JDK 21 |
| `BuildKit is enabled but the buildx component is missing` | buildx ausente | `DOCKER_BUILDKIT=0 docker compose --profile app build` |
