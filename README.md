# HookRoute Java

Backend Spring Boot para ingestão, auditoria e entrega assíncrona de webhooks. Este projeto preserva os contratos do
HookRoute NestJS, usando PostgreSQL, transactional outbox e Redis Streams para processamento resiliente.

## Arquitetura

```text
Client -> API -> PostgreSQL (event + outbox) -> Redis Stream -> Worker -> Egress
                    |                               |
                    +-- audit / attempts            +-- retry / DLQ
```

- `hookroute-domain`: tipos de domínio sem dependências de framework.
- `hookroute-application`: contratos dos casos de uso.
- `hookroute-infrastructure`: JPA, Flyway, Redis Streams, outbox, worker e egress.
- `hookroute-api`: aplicação HTTP e OpenAPI.
- `hookroute-worker`: publisher do outbox e consumidor assíncrono.

## Requisitos

- Java 21
- Docker e Docker Compose

O Maven é fornecido pelo wrapper do repositório.

## Execução rápida

```bash
cp .env.example .env
docker compose up --build
```

Serviços:

- API: http://localhost:3000/api
- Swagger: http://localhost:3000/docs
- Health da API: http://localhost:3000/api/health
- Actuator/Prometheus: http://localhost:3000/actuator/prometheus
- Health do worker: http://localhost:3001/actuator/health

Para executar a infraestrutura e as aplicações fora dos containers:

```bash
docker compose up -d postgres redis
./mvnw clean verify
./mvnw -pl hookroute-api -am spring-boot:run
./mvnw -pl hookroute-worker -am spring-boot:run
```

## Ingestão

```bash
curl -X POST http://localhost:3000/api/ingestion/webhooks \
  -H 'Content-Type: application/json' \
  -H 'x-correlation-id: demo-1' \
  -d '{
    "eventId": "evt_123",
    "eventType": "order.created",
    "data": {"id": "order-1", "amount": 129.90}
  }'
```

A resposta é `202 Accepted`. Evento e mensagem são gravados atomicamente no PostgreSQL; o worker publica o outbox no
stream `webhook-events`, executa até cinco tentativas (backoff 1/2/4/8 segundos) e envia a falha final para
`webhook-events-dlq`.

## Endpoints compatíveis

```text
POST /api/ingestion/webhooks
GET  /api/events
GET  /api/events/failures
GET  /api/events/{id}
POST /api/events/{id}/replay
GET  /api/health
GET  /api/metrics
```

Filtros de eventos: `status`, `eventType`, `jobId`, `limit` (1–100) e `offset`.

## HMAC

Quando `WEBHOOK_SECRET` está preenchido, envie:

```text
x-webhook-timestamp: epoch em milissegundos
x-webhook-signature: sha256=<HMAC_SHA256(timestamp.rawBody)>
```

## Egress

O destino é `{WEBHOOK_DESTINATION_BASE_URL}/{eventType}`. `EGRESS_MODE=simulated` é o padrão. Em modo real, o worker faz
POST JSON com timeout, correlation ID, idempotency key e circuit breaker por destino. Payloads podem simular status e
latência usando `data.simulate`; desative isso em produção com `EGRESS_ALLOW_PAYLOAD_SIMULATION=false`.

## Qualidade

```bash
./mvnw clean verify
docker compose config
```

As migrations Flyway ficam em `hookroute-infrastructure/src/main/resources/db/migration` e o Hibernate apenas valida o
schema. Consulte [docs/PARITY.md](docs/PARITY.md) para diferenças intencionais em relação à versão NestJS.
