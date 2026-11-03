---
name: write-tests
description: "Writes tests in orders-service following the project's conventions (domain without Spring, service with Mockito, controller with MockMvc and Testcontainers). Use only when the user explicitly asks to write or create tests (for example 'escreve testes', 'cria os testes') or requests /write-tests."
---

# Write tests

Write the tests the user asked for, in the style of the existing ones. Before writing, read the test
file of the class in question and reuse its constants and helpers. If the class has no test file yet,
read the equivalent one in another package.

## Which kind of test

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

## Conventions

- **Naming:** `method_condition_result`, such as `cancel_alreadyCancelled_throws` or
  `getOrder_withUnknownId_returns404`.
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

## How to reply

Reply to the user in European Portuguese, even though this file is in English, and keep it compact:
what each test proves, the real result and, if there is one, a new concept explained in one sentence,
with the bridge to Rust when one exists.
