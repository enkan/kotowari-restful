# kotowari-restful example

A sample application demonstrating how to use kotowari-restful. It provides a REST API for managing customers and their contact methods.

## Prerequisites

- Java 25+
- Maven 3.9+
- [Hurl](https://hurl.dev/) (for running API tests)

## Getting Started

```bash
# Build kotowari-restful from the root directory
mvn install -DskipTests

# Start the example
cd example
mvn compile exec:java
```

The server starts at `http://localhost:3000`. Press `Ctrl-C` to stop.

### REPL Mode

```bash
cd example
mvn compile exec:java -Dexec.args="--repl"
```

Launches a JShell-based interactive REPL with `sql` and `jsonRequest` commands.

## API Endpoints

| Method | Path | Resource Class | Description |
|---|---|---|---|
| `POST` | `/customers` | `CustomersResource` | Create a customer |
| `GET` | `/customers/:id` | `CustomerResource` | Retrieve a customer |
| `POST` | `/customers/:id/contact-methods` | `ContactMethodsResource` | Add a secondary contact method |
| `DELETE` | `/customers/:id/contact-methods/:cmId` | `ContactMethodResource` | Remove a secondary contact method |
| `PUT` | `/customers/:id/contact-methods/:cmId/primary` | `ContactMethodPrimaryResource` | Promote a secondary to primary |

## Domain Model

```
Customer
├── name: PersonalName (firstName, lastName)
├── primaryContactMethod: ContactMethod (exactly one)
└── secondaryContactMethods: List<ContactMethod> (zero or more)

ContactMethod (sealed interface)
├── Email (label, emailAddress)
└── PostalAddress (label, address1, city, state, zipCode)
```

A customer always has exactly one primary contact method. Secondary contact methods can be added, removed, or promoted to primary.

### Business Rules

| Operation | Rule | Error Code |
|---|---|---|
| Add secondary | Must not duplicate an existing contact method | `duplicate` |
| Promote to primary | Target must be in the secondary list | `not_secondary` |
| Remove | Cannot directly remove the primary contact method | `cannot_remove_primary` |
| Remove | Target must be in the secondary list | `not_secondary` |

Business rules are implemented as pure functions in the `behavior` package:

- `AddSecondaryContactMethod` -- adds a secondary contact method
- `PromoteToPrimary` -- promotes a secondary to primary (demotes the old primary)
- `RemoveContactMethod` -- removes a secondary contact method

Each function implements `BiFunction<Customer, ContactMethod, Result<Customer>>`, returning either the updated `Customer` on success or error information via `Result`.

## Recommended Pattern: JsonNode + Raoh

kotowari-restful strongly recommends receiving the request body as `JsonNode` and decoding it with [Raoh](https://github.com/kawasima/raoh) in the `MALFORMED` decision point. This approach cleanly separates deserialization from validation and avoids impedance mismatches between the middleware chain and the decision graph.

### Why JsonNode + Raoh?

The kotowari middleware chain (`SerDesMiddleware`) was originally designed for a "one request = one handler method" model, where the body is deserialized into the handler's parameter type. However, the decision graph calls multiple `@Decision` methods per request (e.g. `MALFORMED` -> `POST` -> `HANDLE_CREATED`), so relying on `SerDesMiddleware` to pick the right target type is fragile.

By always deserializing to `JsonNode`, you get:

- **No ambiguity** -- `SerDesMiddleware` always deserializes to `JsonNode`, no need to guess which method's parameter type to use
- **Unified validation and decoding** -- Raoh's `decode()` converts `JsonNode` to a domain object and validates it in a single step, returning `Result<T>` (either `Ok` with the value or `Err` with structured issues)
- **Natural fit with the decision graph** -- `MALFORMED` handles both validation and type conversion; downstream methods receive fully validated domain objects via `context.putValue()`
- **RFC 9457 error responses for free** -- Raoh issues map directly to `Problem.Violation`, producing structured error responses

### The Pattern

```java
@AllowedMethods({"POST"})
public class CustomersResource {

    // 1. Decode + validate in MALFORMED
    @Decision(value = MALFORMED, method = {"POST"})
    public Problem isMalformed(JsonNode body, RestContext context) {
        return switch (CustomerDecoders.CUSTOMER.decode(body)) {
            case Ok<Customer> ok -> {
                context.putValue(ok.value());  // store for downstream
                yield null;                    // not malformed
            }
            case Err<Customer> err -> {
                List<Problem.Violation> violations = err.issues().asList().stream()
                    .map(issue -> new Problem.Violation(
                        issue.path().toString(), issue.code(), issue.message()))
                    .toList();
                yield Problem.fromViolationList(violations);  // -> 400
            }
        };
    }

    // 2. Use the validated domain object directly
    @Decision(POST)
    public boolean create(Customer customer, DSLContext dsl, RestContext context) {
        // customer is injected from context.getValue(Customer.class)
        CustomerRepository repo = new CustomerRepository(dsl);
        CustomerId id = repo.insert(customer);
        context.putValue(id);
        return true;
    }

    // 3. Build the response
    @Decision(HANDLE_CREATED)
    public CustomerResponse handleCreated(CustomerId id) {
        return new CustomerResponse(id);
    }
}
```

### Defining Raoh Decoders

Collect your decoders in a single class for easy reuse:

```java
public class CustomerDecoders {
    public static final Decoder<Customer> CUSTOMER = Decoder.object(
        Decoder.field("name", nameDecoder()),
        Decoder.field("primaryContactMethod", contactMethodDecoder()),
        Decoder.optionalField("secondaryContactMethods",
            Decoder.list(contactMethodDecoder()), List.of()),
        Customer::new
    );

    // ... other decoders
}
```

See `CustomerJsonDecoders.java` in this example for the full implementation.

## Decision Graph Patterns

kotowari-restful processes requests through a [Liberator](https://clojure-liberator.github.io/liberator/)-style decision graph. Resource classes override specific decision points with `@Decision`-annotated methods.

### Basic Resource Structure

```java
@AllowedMethods({"POST"})
public class CustomersResource {

    // Validation (MALFORMED -> true returns 400)
    @Decision(value = MALFORMED, method = {"POST"})
    public Problem isMalformed(JsonNode body, RestContext context) { ... }

    // POST action
    @Decision(POST)
    public boolean create(Customer customer, DSLContext dsl, RestContext context) { ... }

    // 201 response body
    @Decision(HANDLE_CREATED)
    public CustomerResponse handleCreated(CustomerId id, CustomerWithIds cwi) { ... }
}
```

### Parameter Injection

Arguments of `@Decision` methods are injected automatically:

| Parameter Type | Source |
|---|---|
| `RestContext` | The request context itself |
| `Parameters` | Routing / query parameters |
| `DSLContext` | jOOQ DSLContext (via `DSLContextInjector`) |
| `JsonNode` | Deserialized request body |
| Types stored via `context.putValue()` | Context value store |

Use `context.putValue()` to pass values computed in one decision point to a later one:

```java
// Store a validated object in MALFORMED
@Decision(value = MALFORMED, method = {"POST"})
public Problem isMalformed(JsonNode body, RestContext context) {
    Customer customer = decode(body);
    context.putValue(customer);  // stored in context
    return null;
}

// Receive Customer directly as a parameter in POST
@Decision(POST)
public boolean create(Customer customer, DSLContext dsl, RestContext context) {
    // customer is resolved by RestContextInjector via context.getValue(Customer.class)
    ...
}
```

### PUT (Update Existing Resource)

By default, PUT returns 201 Created (`NEW -> true`). To return 200 OK for updates, override the relevant decision points:

```java
@AllowedMethods({"PUT"})
public class ContactMethodPrimaryResource {

    @Decision(EXISTS)
    public boolean exists(Parameters params, DSLContext dsl, RestContext context) { ... }

    @Decision(CAN_PUT_TO_MISSING)
    public boolean canPutToMissing() { return false; }  // 404 if resource doesn't exist

    @Decision(NEW)
    public boolean isNew() { return false; }  // not a creation...

    @Decision(RESPOND_WITH_ENTITY)
    public boolean respondWithEntity() { return true; }  // include body -> 200 OK

    @Decision(PUT)
    public Object promote(...) { ... }

    @Decision(HANDLE_OK)
    public Object handleOk(...) { ... }
}
```

### DELETE

By default, `DELETE_ENACTED -> true` and `RESPOND_WITH_ENTITY -> false`, so DELETE returns 204 No Content automatically:

```java
@AllowedMethods({"DELETE"})
public class ContactMethodResource {

    @Decision(EXISTS)
    public boolean exists(Parameters params, DSLContext dsl, RestContext context) { ... }

    @Decision(DELETE)
    public Object delete(CustomerId id, DSLContext dsl, RestContext context) { ... }
    // -> 204 No Content automatically
}
```

## Testing

### Hurl API Tests

```bash
# Start the server, then in another terminal:
cd example
hurl --variable host=http://localhost:3000 src/test/hurl/contact_methods.hurl

# Show all requests and responses
hurl --verbose --variable host=http://localhost:3000 src/test/hurl/contact_methods.hurl

# Show full exchange including headers
hurl --very-verbose --variable host=http://localhost:3000 src/test/hurl/contact_methods.hurl
```

`contact_methods.hurl` runs the following scenario:

1. Create a customer with an email contact method
2. GET the customer to verify
3. Add a secondary postal address
4. Attempt duplicate addition (400)
5. Promote the secondary to primary
6. Attempt to promote an already-primary contact method (400)
7. Remove the demoted contact method
8. Attempt to remove the primary directly (400)
9. Access non-existent resources (404)

## Architecture

### Middleware Stack

Requests are processed through the following middleware chain (outermost to innermost):

1. `ParamsMiddleware` -- query string parsing
2. `MultipartParamsMiddleware` -- multipart/form-data parsing
3. `NestedParamsMiddleware` -- dot-notation key expansion
4. `ContentNegotiationMiddleware` -- Accept header validation (`application/json` only)
5. `RoutingMiddleware` -- URL pattern matching
6. `ResourceMethodResolverMiddleware` -- resolves the resource method for SerDes
7. `JooqDslContextMiddleware` -- provides jOOQ DSLContext
8. `JooqTransactionMiddleware` -- `@Transactional` support
9. `SerDesMiddleware` -- JSON deserialization / serialization
10. `ResourceInvokerMiddleware` -- executes the decision graph

### Component Wiring

```
datasource (HikariCP, H2 in-memory)
  ├── flyway (schema migrations)
  └── jooq (DSLContext)
beans (JacksonBeansConverter)
validator (BeansValidator)
app (ExampleApplicationFactory)
  └── http (Undertow, port 3000)
```

### Directory Layout

```
example/src/main/java/kotowari/restful/example/
├── DevMain.java                    -- entry point (direct / REPL mode)
├── ExampleApplicationFactory.java  -- middleware stack definition
├── ExampleSystemFactory.java       -- component wiring
├── behavior/                       -- business rules (pure functions)
│   ├── AddSecondaryContactMethod.java
│   ├── PromoteToPrimary.java
│   └── RemoveContactMethod.java
├── dao/                            -- data access
│   ├── CustomerMapper.java
│   └── CustomerRepository.java
├── data/                           -- domain model (records / sealed interfaces)
│   ├── Customer.java
│   ├── ContactMethod.java
│   ├── PersonalName.java
│   └── ...
├── inject/                         -- custom ParameterInjector
│   └── DSLContextInjector.java
└── resource/                       -- resource classes (@Decision)
    ├── CustomersResource.java
    ├── CustomerResource.java
    ├── ContactMethodsResource.java
    ├── ContactMethodResource.java
    ├── ContactMethodPrimaryResource.java
    ├── CustomerJsonDecoders.java
    └── CustomerResponse.java
```
