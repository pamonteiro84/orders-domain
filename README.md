# orders-domain

An orders and payments platform, built in phases. The end goal is two services that talk through
events (`orders-service` and `payments-service`) and an AI assistant for the support team that
looks up the real state of the system and answers questions about store policies.

**Current state (phase 1):** the `orders-service`, an API to create, read, list and cancel orders
against a small product catalog. Prices are always taken from the catalog on the server, never
from the client.

**Stack:** Java 21, Spring Boot 4, Spring Data JPA (Hibernate), PostgreSQL 16, Flyway,
Testcontainers, Gradle.

## Roadmap

| Phase | Scope | Status |
|-------|-------|--------|
| 1. Orders | `orders-service` + Postgres: orders, catalog, cancellation, consistent errors, CI | In progress |
| 2. Production | JWT auth, `payments-service`, Kafka with the outbox pattern, Redis (idempotency, rate limiting, cache), observability | Planned |
| 3. AI | Support assistant with tool use, RAG with pgvector, MCP server, evals in CI | Planned |
| 4. Launch | Deploy or recorded demo, documentation, architecture decision records | Planned |

Order lifecycle: `PENDING` → `CANCELLED` (phase 1) or `PENDING` → `PAID` (phase 2, after a
successful payment). A paid or cancelled order cannot change again.

## Running locally

Requirements: Java 21 and Docker.

```bash
cp .env.example .env   # then fill in DB_USERNAME and DB_PASSWORD
make run               # starts Postgres (Docker Compose) and the app on :8080
```

Other targets:

| Command         | What it does                                        |
|-----------------|-----------------------------------------------------|
| `make test`     | Runs all tests (Postgres via Testcontainers)        |
| `make ci`       | Build, lint/format check and tests, same as CI      |
| `make format`   | Applies the code formatter (Spotless)               |
| `make stop`     | Stops the app running on :8080                      |
| `make db-down`  | Stops Postgres, keeps the data                      |
| `make db-reset` | Stops Postgres and **deletes** the local data       |

Health check: `curl localhost:8080/actuator/health`

## API

The database is seeded with six products; the examples use `adagio` (`...0001`, 39.99) and
`frappe` (`...0004`, 39.90).

### Register a user

```bash
curl -X POST localhost:8080/auth/register \
  -H 'Content-Type: application/json' \
  -d '{ "email": "ana@example.com", "password": "change-me-123" }'
```

`201 Created` with `id`, `email` and `createdAt`. The email is stored in lowercase and the password
as a BCrypt hash, never returned. An email already registered returns `409`.

### List products

```bash
curl localhost:8080/products
```

### Create an order

```bash
curl -i -X POST localhost:8080/orders \
  -H 'Content-Type: application/json' \
  -d '{
        "customerId": "11111111-1111-1111-1111-111111111111",
        "items": [
          { "productId": "00000000-0000-0000-0000-000000000001", "quantity": 2 },
          { "productId": "00000000-0000-0000-0000-000000000004", "quantity": 1 }
        ]
      }'
```

`201 Created` with a `Location: /orders/{id}` header. The order starts as `PENDING` and the
total (`119.88`) is calculated on the server.

### Get an order

```bash
curl localhost:8080/orders/{id}
```

### List a customer's orders

```bash
curl 'localhost:8080/orders?customerId=11111111-1111-1111-1111-111111111111&page=0&size=20'
```

Newest first. `size` is capped at 100. The response has `content`, `page`, `size`,
`totalElements` and `totalPages`.

### Cancel an order

```bash
curl -X POST localhost:8080/orders/{id}/cancel
```

Only `PENDING` orders can be cancelled. Cancelling again returns `409`.

## Errors

Every error is a [ProblemDetail](https://www.rfc-editor.org/rfc/rfc9457) with `status`, `title`
and `instance`, plus the data needed to act on it:

```json
{ "status": 409, "title": "Order cannot be cancelled", "instance": "/orders/{id}/cancel",
  "orderId": "{id}", "currentStatus": "CANCELLED" }
```

| Status | When                                                                 |
|--------|----------------------------------------------------------------------|
| 400    | Invalid body or parameters; validation errors include an `errors` list of `field` and `message` |
| 404    | Order not found (`orderId`)                                          |
| 409    | Order cannot be cancelled in its current status, or was modified concurrently; email already registered |
| 422    | The order references a product that does not exist (`productId`)     |
| 500    | Unexpected error; details are logged, never returned                 |

## Design decisions

- **Prices come from the server.** The request only carries `productId` and `quantity`; accepting
  a price from the client would let anyone set their own price.
- **Flyway owns the schema.** Hibernate runs with `ddl-auto: validate`, so it checks the entities
  against the migrations instead of changing the database.
- **`Order` is the aggregate root.** Items are only added through `Order.addItem`, which keeps the
  total consistent, and status rules live in `Order.cancel()`, not in the controller.
- **Optimistic locking.** `@Version` on `Order` makes the second of two concurrent updates fail
  with `409` instead of silently overwriting the first.
- **No N+1 when reading an order.** Items are loaded in the same query with `@EntityGraph`, and
  entities are converted to DTOs inside the transaction (`open-in-view: false`).

## Tests and CI

Unit tests cover the domain rules (`Order`) and the services with Mockito. Integration tests run
the full HTTP → database flow with MockMvc against a real Postgres started by Testcontainers.

GitHub Actions runs build, lint/format (Spotless, Checkstyle) and tests on every push and pull
request.
