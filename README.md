# orders-domain

An orders and payments platform, built in phases. The end goal is two services that talk through
events (`orders-service` and `payments-service`) and an AI assistant for the support team that
looks up the real state of the system and answers questions about store policies.

**Current state (phase 2 in progress):** the `orders-service`, an API where customers register, log
in with a JWT and create, read, list and cancel their own orders against a small product catalog.
Prices are always taken from the catalog on the server, never from the client. The
`payments-service` takes payments for those orders through a simulated provider, with safe retries
backed by Redis. New and cancelled orders reach `payments-service` as Kafka events, published
through a transactional outbox. Payment results do not flow back to `orders-service` yet.

**Stack:** Java 21, Spring Boot 4, Spring Data JPA (Hibernate), PostgreSQL 16, Redis 7, Apache
Kafka 4, Flyway, Testcontainers, Gradle.

## Roadmap

| Phase | Scope | Status |
|-------|-------|--------|
| 1. Orders | `orders-service` + Postgres: orders, catalog, cancellation, consistent errors, CI | Done |
| 2. Production | JWT auth, `payments-service`, Kafka with the outbox pattern, Redis (idempotency, rate limiting, cache), observability | In progress |
| 3. AI | Support assistant with tool use, RAG with pgvector, MCP server, evals in CI | Planned |
| 4. Launch | Deploy or recorded demo, documentation, architecture decision records | Planned |

Order lifecycle: `PENDING` → `CANCELLED` (phase 1) or `PENDING` → `PAID` (phase 2, after a
successful payment). A paid or cancelled order cannot change again.

## Running locally

Requirements: Java 21, Docker and OpenSSL.

```bash
cp .env.example .env   # then fill in ORDERS_DB_USERNAME, ORDERS_DB_PASSWORD and PAYMENTS_DB_PASSWORD

# RSA key pair that signs and verifies the JWTs; keys/ is ignored by git
mkdir -p keys
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out keys/private.pem
openssl pkey -in keys/private.pem -pubout -out keys/public.pem

make run               # starts Postgres, Redis and Kafka (Docker Compose), orders-service on :8080 and payments-service on :8081
```

Kafka UI runs on `localhost:8085`, to browse the `orders` topic and its messages.

The keys under each service's `src/test/resources/test-keys/` are used only by the tests.

Other targets:

| Command             | What it does                                            |
|---------------------|---------------------------------------------------------|
| `make run-orders`   | Starts the infrastructure and only `orders-service`     |
| `make run-payments` | Starts the infrastructure and only `payments-service`   |
| `make test`         | Runs all tests (Postgres, Redis and Kafka via Testcontainers) |
| `make ci`           | Build, lint/format check and tests, same as CI          |
| `make format`       | Applies the code formatter (Spotless)                   |
| `make stop`         | Stops the apps running on :8080 and :8081               |
| `make db-down`      | Stops the infrastructure containers, keeps the data     |
| `make db-reset`     | Stops the infrastructure containers and **deletes** the local data |

Health checks: `curl localhost:8080/actuator/health` and `curl localhost:8081/actuator/health`

## API

The database is seeded with six products; the examples use `adagio` (`...0001`, 39.99) and
`frappe` (`...0004`, 39.90).

The same requests are in a [Bruno](https://www.usebruno.com) collection under `bruno/`, with the
`local` environment. Run `Login` first: it stores the token used by the other requests.

### Register a user

```bash
curl -X POST localhost:8080/auth/register \
  -H 'Content-Type: application/json' \
  -d '{ "email": "ana@example.com", "password": "change-me-123" }'
```

`201 Created` with `id`, `email` and `createdAt`. The email is stored in lowercase and the password
as a BCrypt hash, never returned. An email already registered returns `409`.

### Log in

```bash
curl -X POST localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{ "email": "ana@example.com", "password": "change-me-123" }'
```

`200 OK` with an `accessToken` (JWT signed with RS256, valid for 15 minutes). A wrong password or an
unknown email both return the same `401`.

Every `/orders` and `/payments` endpoint requires this token. The examples below read it from a variable:

```bash
export TOKEN=<accessToken from the login response>
```

### List products

```bash
curl localhost:8080/products
```

### Create an order

```bash
curl -i -X POST localhost:8080/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
        "items": [
          { "productId": "00000000-0000-0000-0000-000000000001", "quantity": 2 },
          { "productId": "00000000-0000-0000-0000-000000000004", "quantity": 1 }
        ]
      }'
```

`201 Created` with a `Location: /orders/{id}` header. The order belongs to the customer in the token,
starts as `PENDING` and the total (`119.88`) is calculated on the server.

### Get an order

```bash
curl -H "Authorization: Bearer $TOKEN" localhost:8080/orders/{id}
```

An order that belongs to another customer returns `404`.

### List your orders

```bash
curl -H "Authorization: Bearer $TOKEN" 'localhost:8080/orders?page=0&size=20'
```

Newest first. `size` is capped at 100. The response has `content`, `page`, `size`,
`totalElements` and `totalPages`.

### Cancel an order

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" localhost:8080/orders/{id}/cancel
```

Only `PENDING` orders can be cancelled. Cancelling again returns `409`.

### Pay an order

Served by `payments-service` on `:8081`, with the same token.

```bash
curl -i -X POST localhost:8081/payments \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Idempotency-Key: checkout-7f3c9a10' \
  -H 'Content-Type: application/json' \
  -d '{ "orderId": "{id}" }'
```

`201 Created` with a `Location: /payments/{id}` header and `id`, `orderId`, `status` (`SUCCEEDED` or
`FAILED`), `amount` and `createdAt`. The amount is read on the server, never sent by the client. The
provider is simulated by `FakePaymentProvider`, which currently approves every payment.

`payments-service` reads orders from its own `orders_view` table, filled by the `order.created` and
`order.cancelled` events. A new order reaches it a moment after it is created; paying before that
returns `404`, and paying a cancelled order returns `409`.

`Idempotency-Key` is required (up to 255 characters) and is chosen by the client, once per payment
attempt. It is scoped to the customer in the token:

| The same key is sent again...           | Response                                    |
|-----------------------------------------|---------------------------------------------|
| After the first request finished        | The original response, without a new charge |
| While the first request is still running | `409`                                       |
| With a different `orderId`              | `422`                                       |

The stored response is kept for 24 hours. A request that fails, such as a `404`, frees the key so
it can be retried.

## Errors

Every error is a [ProblemDetail](https://www.rfc-editor.org/rfc/rfc9457) with `status`, `title`
and `instance`, plus the data needed to act on it:

```json
{ "status": 409, "title": "Order cannot be cancelled", "instance": "/orders/{id}/cancel",
  "orderId": "{id}", "currentStatus": "CANCELLED" }
```

| Status | When                                                                 |
|--------|----------------------------------------------------------------------|
| 400    | Invalid body or parameters, or a missing or invalid `Idempotency-Key`; validation errors include an `errors` list of `field` and `message` |
| 401    | Missing, invalid or expired token; invalid email or password         |
| 404    | Order not found, or it belongs to another customer (`orderId`)       |
| 409    | Order cannot be cancelled or paid in its current status, or was modified concurrently; email already registered; payment in progress or already made |
| 422    | The order references a product that does not exist (`productId`); `Idempotency-Key` already used for another order |
| 500    | Unexpected error; details are logged, never returned                 |

## Design decisions

- **Prices come from the server.** The request only carries `productId` and `quantity`; accepting
  a price from the client would let anyone set their own price.
- **The customer comes from the token.** Orders are always filtered by the token's `sub`, never by
  an id sent by the client, so a customer cannot read or change someone else's orders. Those orders
  return `404` rather than `403`, which would reveal that they exist.
- **JWT signed with RS256.** The private key signs and the public key only verifies, so
  `payments-service` validates tokens without being able to create them.
- **One database per service.** `payments-service` never reads the `orders-service` database. It
  keeps its own `orders_view`, so a schema change or an outage in one service does not break the
  other.
- **Idempotency in Redis, before the charge.** `SET NX` with a 60-second expiry reserves the key, so
  of two simultaneous requests only one reaches the provider. The reservation expires by itself if
  the app dies mid-charge, and the finished response replaces it for 24 hours. This runs outside the
  database transaction, so a response is only stored after the commit.
- **Postgres is the final guarantee.** A partial unique index allows one `SUCCEEDED` payment per
  order. If Redis is down, payments continue without a reservation and a duplicate is rejected by
  the index with `409`.
- **Transactional outbox.** `orders-service` never calls Kafka inside a request. The event is saved
  to `outbox_events` in the same transaction as the order, so either both are saved or neither is.
  A scheduled relay publishes pending rows, waits for Kafka's acknowledgement and only then marks
  them as published; a failed send is retried on the next run.
- **One topic per aggregate, keyed by order id.** All events of an order go to the `orders` topic
  with the order id as the key, so they land in the same partition and are consumed in order: a
  `cancelled` is never applied before its `created`.
- **Idempotent consumer.** Delivery is at least once, so the same event can arrive twice.
  `payments-service` records each `eventId` in `processed_events` with `INSERT ... ON CONFLICT DO
  NOTHING`, in the same transaction as the update to `orders_view`; a duplicate inserts nothing and
  is skipped.
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
the full HTTP → database flow with MockMvc against a real Postgres started by Testcontainers, which
also starts a real Kafka for both services and a real Redis for `payments-service`. Event tests
send and read real Kafka messages and wait for the result with Awaitility.

GitHub Actions runs build, lint/format (Spotless, Checkstyle) and tests on every push and pull
request.
