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
| **Métricas** | [localhost:3000/api/metrics](http://localhost:3000/api/metrics) | Contadores protegidos por API key administrativa |
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

1. **Crie a configuração local**

   ```bash
   cp .env.example .env
   ```

   O Spring Dotenv carrega esse arquivo automaticamente; não use `source .env`.

2. **Suba somente a infraestrutura**

   ```bash
   docker compose up -d postgres redis
   ```

3. **Compile, execute os testes e instale os módulos**

   ```bash
   ./mvnw clean install
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

---

## 🔧 Configuração da aplicação

API e worker usam o `springboot4-dotenv`. Durante o desenvolvimento, a biblioteca adiciona o `.env` da raiz como um `PropertySource` do Spring; não é necessário exportar o arquivo no shell. Copie `.env.example` para `.env` e ajuste apenas a cópia local, que permanece ignorada pelo Git.

Exemplos de sobrescrita para produção:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://database:5432/hookroute
export SPRING_DATASOURCE_USERNAME=hookroute
export SPRING_DATASOURCE_PASSWORD=change-me
export SPRING_DATA_REDIS_URL=redis://redis:6379
export SPRING_PROFILES_ACTIVE=production
export HOOKROUTE_SECURITY_WEBHOOK_SECRET=replace-with-at-least-32-random-characters
export ADMIN_API_KEY=replace-with-at-least-32-random-characters
export HOOKROUTE_EGRESS_MODE=real
export HOOKROUTE_EGRESS_ALLOW_PAYLOAD_SIMULATION=false
```

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
