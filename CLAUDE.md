# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
mvn compile

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=ResourceEngineTest

# Run a single test method
mvn test -Dtest=CreateArgumentsTest#injectRestContextItself

# Build with dev profile (includes example app sources)
mvn compile -Pdev
```

## Architecture

kotowari-restful implements the [Liberator](https://clojure-liberator.github.io/liberator/)-style decision graph for HTTP. A request flows through a fixed directed acyclic graph of `Decision` and `Handler` nodes defined in `ResourceEngine.createDefaultGraph()`.

### Core flow

```
HTTP Request
    → ResourceInvokerMiddleware
        → ResourceEngine.run()
            → RestContext (request + resource)
            → runDecisionGraph(): traverse Decision/Handler nodes
                → Decision.execute(): evaluates a boolean test, branches to thenNode or elseNode
                → Handler.execute(): builds ApiResponse with status + body
```

### Key classes

- **`ResourceEngine`** — owns the decision graph (lazily initialized, `volatile` field). Catches `MalformedBodyException` → 400, all other exceptions → 500.
- **`DecisionPoint`** (enum) — every node in the graph. Divided into: user-customizable decisions, internal HTTP header decisions, actions (`POST`/`PUT`/`PATCH`/`DELETE`), and handlers (`HANDLE_OK` etc.).
- **`Decision`** — evaluates a `Function<RestContext, ?>`. Returns `thenNode` when result is truthy, `elseNode` when falsy. Only `Boolean`, `Problem`, and `String` results set a context message; other non-null values are treated as truthy without touching the response body.
- **`Handler`** — terminal node. Returns `ApiResponse` with a fixed status code. The body comes from (in priority order): the resource function return value → `context.getMessage()` → the default message.
- **`ClassResource`** — wraps a POJO resource class. Scans `@Decision`-annotated methods and builds a `Function<RestContext, ?>` map keyed by `DecisionPoint`. Supports per-HTTP-method dispatch via `@Decision(value=…, method={"GET"})`.
- **`MethodMeta`** — caches per-method argument resolvers (`Function<RestContext, Object>[]`) built once at construction time, avoiding per-request `parameterInjectors.stream()` scanning.
- **`DefaultResource`** — provides the default implementations for all decision points (e.g. `EXISTS → true`, `MALFORMED → false`). Used as the parent resource for `ClassResource`.
- **`RestContext`** — mutable per-request state: the `HttpRequest`, a typed value store (`putValue`/`getValue`), the response message, status override, and headers.
- **`Problem`** — immutable RFC 9457 problem JSON DTO. Use factory methods (`valueOf`, `fromViolations`, `fromException`); no setters.

### Argument injection priority (in `buildResolvers`)

1. `RestContext.class.isAssignableFrom(type)` → inject `context` (static, pre-computed)
2. Any `ParameterInjector.isApplicable(type, null)` match → call `injector.getInjectObject(request)` (static, pre-computed)
3. Fallback resolver (runtime): `context.getValue(type)` (dynamic context store) → deserialized body direct assign → `beansConverter.createFrom()` (throws `MalformedBodyException` on failure → 400)

### Test helpers

- `JacksonBeansConverterFactory` lives in `src/test/java/enkan/component/jackson/` (same package as `JacksonBeansConverter`) to access the `protected lifecycle()` method for test setup.
- Mix `BodyDeserializable` onto `DefaultHttpRequest` via `MixinUtils.mixin(req, BodyDeserializable.class)` when tests need a deserializable body.
