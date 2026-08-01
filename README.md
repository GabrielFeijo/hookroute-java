# 🚀 HookRoute Java - Backend de Webhooks Assíncronos

> Backend Spring Boot para ingestão, roteamento, auditoria e entrega assíncrona de webhooks com PostgreSQL, transactional outbox, Redis Streams, replay operacional, DLQ e observabilidade.

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?logo=postgresql)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-red?logo=redis)](https://redis.io/)
[![Maven](https://img.shields.io/badge/Maven-3.9-C71A36?logo=apachemaven)](https://maven.apache.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-blue?logo=docker)](https://www.docker.com/)

## 📋 Sobre o Projeto

O **HookRoute Java** recebe webhooks com baixa latência, persiste o evento e sua mensagem de outbox na mesma transação e responde com `202 Accepted`. A publicação no Redis e toda entrega externa são executadas por um worker independente, mantendo a API de ingestão desacoplada do processamento pesado.

Esta implementação preserva o contrato público do [HookRoute NestJS](https://github.com/GabrielFeijo/hookroute) e explora recursos do ecossistema Java para oferecer uma arquitetura modular, resiliente e operacionalmente segura.

---

## 🌐 Demonstração Local

| Serviço | URL | Descrição |
| --- | --- | --- |
| **API** | [localhost:3000/api](http://localhost:3000/api) | Prefixo principal da API HookRoute |
| **Swagger** | [localhost:3000/docs](http://localhost:3000/docs) | Documentação OpenAPI interativa |
| **Health Check** | [localhost:3000/api/health](http://localhost:3000/api/health) | Saúde do PostgreSQL e Redis |
| **Métricas** | [localhost:3000/api/metrics](http://localhost:3000/api/metrics) | Contadores de filas, outbox e eventos |
| **Prometheus** | [localhost:3000/actuator/prometheus](http://localhost:3000/actuator/prometheus) | Métricas técnicas da API |
| **Worker Health** | [localhost:3001/actuator/health](http://localhost:3001/actuator/health) | Saúde do processo worker |
| **PostgreSQL** | `localhost:5432` | Persistência local via Docker Compose |
| **Redis** | `localhost:6379` | Streams e consumer groups locais |

---

## 🚀 Início Rápido

### Pré-requisitos

Certifique-se de ter instalado:

- [Java](https://adoptium.net/) 21
- [Docker](https://www.docker.com/get-started)
- [Docker Compose](https://docs.docker.com/compose/)

O Maven 3.9 é obtido automaticamente pelo Maven Wrapper incluído no repositório.

### Execução completa com Docker

1. **Clone o repositório**

   ```bash
   git clone https://github.com/GabrielFeijo/hookroute-java.git
   cd hookroute-java
   ```

2. **Construa e inicie todos os serviços**

   ```bash
   docker compose up --build
   ```

3. **Acesse a documentação**

   Abra [http://localhost:3000/docs](http://localhost:3000/docs) no navegador.

As imagens executam API e worker como usuário não-root. PostgreSQL e Redis usam volumes persistentes e health checks antes da inicialização das aplicações.

### Execução para desenvolvimento

1. **Prepare o ambiente local**

   ```bash
   cp .env.example .env
   set -a
   source .env
   set +a
   ```

2. **Suba somente a infraestrutura**

   ```bash
   docker compose up -d postgres redis
   ```

3. **Compile e execute os testes**

   ```bash
   ./mvnw clean verify
   ```

4. **Inicie a API e o worker em terminais separados**

   ```bash
   ./mvnw -pl hookroute-api -am spring-boot:run
   ```

   ```bash
   ./mvnw -pl hookroute-worker -am spring-boot:run
   ```

O Flyway aplica automaticamente as migrations na inicialização. O Hibernate opera com `ddl-auto=validate` e nunca cria ou altera o schema implicitamente.

---

## 📦 Estrutura do Projeto

```text
hookroute-java/
├── hookroute-domain/                  # Regras e tipos puros de domínio
│   └── src/main/java/.../domain/
│
├── hookroute-application/             # Casos de uso e portas da aplicação
│   └── src/main/java/.../application/
│
├── hookroute-infrastructure/          # JPA, Flyway, Redis, outbox e egress
│   ├── src/main/java/.../infrastructure/
│   │   ├── config/                    # Configurações tipadas e beans
│   │   ├── messaging/                 # Publisher do outbox e consumer Redis
│   │   ├── persistence/               # Entidades e repositories JPA
│   │   └── service/                   # Ingestão, lifecycle, replay e egress
│   └── src/main/resources/db/migration/
│
├── hookroute-api/                     # Aplicação HTTP, DTOs e OpenAPI
│   └── src/main/java/.../api/
│       ├── controller/                # Ingestão, eventos e observabilidade
│       ├── dto/                       # Contratos públicos validados
│       └── http/                      # HMAC, correlation ID e erros
│
├── hookroute-worker/                  # Processo dedicado de publicação e entrega
├── docker-compose.yml                 # PostgreSQL, Redis, API e worker
├── Dockerfile.api                     # Imagem não-root da API
├── Dockerfile.worker                  # Imagem não-root do worker
├── pom.xml                            # Agregador Maven multi-módulo
└── README.md
```

### Módulos Maven

| Módulo | Responsabilidade |
| --- | --- |
| `hookroute-domain` | Modela payload, status e job de entrega sem depender de Spring, JPA, Redis ou HTTP. |
| `hookroute-application` | Define comandos, resultados e portas dos casos de uso. |
| `hookroute-infrastructure` | Implementa persistência, outbox, mensageria, lifecycle, replay, observabilidade e egress. |
| `hookroute-api` | Expõe os endpoints REST, valida DTOs, processa HMAC e publica o OpenAPI. |
| `hookroute-worker` | Inicializa o publisher do outbox e o consumidor que realiza as entregas. |

---

## 🧱 Arquitetura

```text
Client / Webhook Provider
        |
        v
Ingestion API
        |
        |  transação única
        v
PostgreSQL: webhook_event + outbox_message
        |
        v
Outbox Publisher
        |
        v
Redis Streams / Consumer Group
        |
        v
Worker + Delivery Lease
        |
        v
Egress + Circuit Breaker
        |
        v
External Destination
```

### Fluxo de Ingestão e Entrega

1. A API valida `eventId`, `eventType` e `data`, além da assinatura HMAC quando configurada.
2. O serviço de ingestão deduplica `eventId` sob lock transacional do PostgreSQL.
3. Evento e outbox são persistidos na mesma transação; a API responde `202 Accepted`.
4. O publisher seleciona mensagens vencidas com lock, publica no Redis Stream e registra o resultado.
5. O worker adquire exclusivamente a combinação `jobId + attempt` por lease no banco.
6. A entrega externa é realizada com `Idempotency-Key` e `X-Correlation-ID`.
7. Sucesso, retry ou DLQ são persistidos antes de a mensagem Redis ser confirmada.

Se o PostgreSQL estiver temporariamente indisponível durante o processamento, a mensagem permanece pendente. Redis e banco cooperam para recuperação sem permitir que duas instâncias executem simultaneamente a mesma tentativa.

---

## 🔄 Lifecycle dos Eventos

Cada webhook pode passar pelos seguintes estados:

```text
received -> queued -> processing -> delivered
                         |
                         v
                       failed -> retry -> processing
                         |
                         v
                  dead_lettered
```

O PostgreSQL mantém três estruturas principais:

| Tabela | Responsabilidade |
| --- | --- |
| `webhook_event` | Estado atual, payload, origem, idempotência, correlation ID e timestamps. |
| `event_delivery_attempt` | Histórico durável de cada job/tentativa, incluindo destino, erro e duração. |
| `outbox_message` | Publicação transacional para o stream principal ou DLQ, com retry próprio. |

Um replay gera um novo `jobId`, restaura o evento para `queued` e torna mensagens anteriores obsoletas. Tentativas antigas são reconhecidas sem executar novamente o webhook ou sobrescrever o estado atual.

---

## 🔧 Configuração de Ambiente

Use `.env.example` como referência. Para execução direta pelo Maven, exporte o arquivo no shell antes de iniciar API e worker.

```env
PORT=3000
WORKER_MANAGEMENT_PORT=3001
DATABASE_URL=jdbc:postgresql://localhost:5432/hookroute
DATABASE_USER=postgres
DATABASE_PASSWORD=postgres
REDIS_URL=redis://localhost:6379

WEBHOOK_SECRET=
WEBHOOK_SIGNATURE_TOLERANCE_MS=300000

EGRESS_MODE=simulated
WEBHOOK_DESTINATION_BASE_URL=https://example.com/webhooks
EGRESS_TIMEOUT_MS=3000
EGRESS_SIMULATED_LATENCY_MS=50
EGRESS_CIRCUIT_FAILURE_THRESHOLD=3
EGRESS_CIRCUIT_OPEN_MS=30000
EGRESS_ALLOW_PAYLOAD_SIMULATION=true

QUEUE_BATCH_SIZE=10
QUEUE_POLL_DELAY_MS=250
QUEUE_VISIBILITY_TIMEOUT_MS=30000
OUTBOX_POLL_DELAY_MS=200
```

| Variável | Descrição |
| --- | --- |
| `PORT` | Porta HTTP da API. |
| `WORKER_MANAGEMENT_PORT` | Porta dos endpoints Actuator do worker. |
| `DATABASE_URL` | URL JDBC do PostgreSQL. |
| `DATABASE_USER` / `DATABASE_PASSWORD` | Credenciais do banco. |
| `REDIS_URL` | URL usada pelo Spring Data Redis. |
| `WEBHOOK_SECRET` | Segredo opcional para validar HMAC. |
| `WEBHOOK_SIGNATURE_TOLERANCE_MS` | Janela temporal aceita para a assinatura. |
| `EGRESS_MODE` | Modo `simulated` ou `real`. |
| `WEBHOOK_DESTINATION_BASE_URL` | URL base do destino externo. |
| `EGRESS_TIMEOUT_MS` | Timeout máximo da chamada externa. |
| `EGRESS_SIMULATED_LATENCY_MS` | Latência padrão no modo simulado. |
| `EGRESS_CIRCUIT_FAILURE_THRESHOLD` | Falhas consecutivas necessárias para abrir o circuito. |
| `EGRESS_CIRCUIT_OPEN_MS` | Tempo de permanência do circuito aberto. |
| `EGRESS_ALLOW_PAYLOAD_SIMULATION` | Permite que o payload controle status e latência simulados. |
| `QUEUE_BATCH_SIZE` | Máximo de mensagens lidas ou recuperadas por ciclo. |
| `QUEUE_POLL_DELAY_MS` | Intervalo entre leituras do stream principal. |
| `QUEUE_VISIBILITY_TIMEOUT_MS` | Tempo para considerar uma tentativa abandonada e liberar seu lease. |
| `OUTBOX_POLL_DELAY_MS` | Intervalo de publicação do outbox. |

---

## 📝 Comandos Disponíveis

```bash
# Build e qualidade
./mvnw clean verify
./mvnw test
./mvnw package

# Execução local
./mvnw -pl hookroute-api -am spring-boot:run
./mvnw -pl hookroute-worker -am spring-boot:run

# Infraestrutura
docker compose up -d postgres redis
docker compose logs -f api worker
docker compose down

# Stack completa
docker compose up --build
docker compose config
```

O pipeline de CI executa `./mvnw --batch-mode clean verify`, valida o Compose e constrói separadamente as imagens da API e do worker.
