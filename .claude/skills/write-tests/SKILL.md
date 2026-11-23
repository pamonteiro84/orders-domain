---
name: write-tests
description: "Writes tests in orders-service following the project's conventions (domain without Spring, service with Mockito, controller with MockMvc and Testcontainers). Use only when the user explicitly asks to write or create tests (for example 'escreve testes', 'cria os testes') or requests /write-tests."
---

# Write tests

Write the tests the user asked for, in the style of the existing ones. Before writing, read the test
file of the class in question and reuse its constants and helpers. If the class has no test file yet,
read the equivalent one in another package.

## Which kind of test

Only two kinds are wanted: unit tests for logic (domain rules and services) and integration tests for
the full HTTP → DB path. Do not write tests for DTOs, mappers, repositories without custom queries,
fakes or configuration on their own: those are covered by the integration tests that go through
them. A DB rule, such as a unique index, is proved through the endpoint that triggers it.

Start at the lowest level that can prove the behaviour. If the user names no level, start with unit
tests. Paths are relative to `orders-service/src/test/java/dev/paulo/orders_service/`, and `<module>`
is `order`, `product` or the module under test.

- **Domain rule** (such as `Order.cancel()`): `<module>/domain/<Class>Test.java`. No Spring and no
  mocks, just `new Order(...)`.
- **Service logic:** `<module>/service/<Class>Test.java`, with `@ExtendWith(MockitoExtension.class)`,
  `@Mock` on the repositories and `@InjectMocks` on the service. Use `ArgumentCaptor` to check what
  was saved, and `verify(..., never())` to prove nothing was saved.
- **HTTP → DB path:** `<module>/controller/<Class>Test.java`, with `@SpringBootTest`,
  `@AutoConfigureMockMvc` and `@Import(TestcontainersConfiguration.class)`. Use it for status codes,
  JSON, `ProblemDetail` errors and persistence.

## What to cover

Never stop at the happy path. Before writing, read the code under test and list every path through
it: each `if`, `throw`, `switch` case and early `return`. Write at least one test per path, and check
each of these that applies:

- **Errors:** every exception and status code the code can produce (`400`, `401`, `404`, `409`,
  `422`), and that nothing changed afterwards (no row saved, no event published, no charge).
- **Invalid input:** missing or blank fields, wrong formats, unknown ids, values out of range.
- **Limits:** empty lists, zero and negative quantities, maximum sizes such as the page cap.
- **Ownership:** another customer's resource returns `404` and is left untouched.
- **State rules:** every transition that is not allowed, such as paying a `CANCELLED` order.
- **Repetition:** the same request or event twice (idempotency keys, duplicate `eventId`).
- **Concurrency:** two requests or a request and an event at the same time, when the code locks or
  relies on a unique index.
- **Events:** the event is published when it should be, and not published when the operation fails.

In the reply, list the cases you covered and any you left out, with the reason.

## Conventions

- **Naming:** `method_condition_result`, such as `cancel_alreadyCancelled_throws` or
  `getOrder_withUnknownId_returns404`.
- **Comments:** none in test code, including Javadoc. If a test needs explaining, say it in the reply
  to the user.
- **Assertions:** AssertJ in unit tests (`assertThat`, `assertThatThrownBy`), `jsonPath` in
  integration tests.
- **Money:** `isEqualByComparingTo("119.88")`, never `isEqualTo`, because `BigDecimal.equals`
  distinguishes `119.88` from `119.880`.
- **Exceptions:** besides the type, check that the state did not change after the exception was
  thrown.
- **Data:** use products from the `V1` seed (`ADAGIO` costs 39.99, `FRAPPE` costs 39.90). Ids that do
  not exist end in `999`.
- **Isolation:** integration tests share the database and do not clean it. When the result depends on
  other rows, as in a listing, use a customer from `UUID.randomUUID()`.
- **Creating data:** in integration tests, create orders through the API, with the `createOrderFor`
  helper.

## After writing

1. Run the test class with `./gradlew :orders-service:test --tests '*ClassName'`. Integration tests
   need Docker, so use `make test` if they fail because the environment is missing.
2. Check the results for how many tests actually ran, rather than trusting the exit code alone. A
   filter that matches nothing is not a pass.
3. Run `make lint`, because Spotless and Checkstyle also cover test files. If it fails on formatting,
   run `make format` and check again.
4. If a test fails because production code is missing, do not write that code: say what is missing
   and teach the user to read the error, from the root cause upwards.

## Limits

Never run `git commit`, `git push` or `git revert`. The user runs them.

## How to reply

Reply to the user in European Portuguese, even though this file is in English, and keep it compact:
what each test proves, the real result and, if there is one, a new concept explained in one sentence,
with the bridge to Rust when one exists.
