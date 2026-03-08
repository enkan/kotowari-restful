# kotowari-restful

[![Maven Central](https://img.shields.io/maven-central/v/net.unit8.enkan/kotowari-restful.svg)](https://central.sonatype.com/artifact/net.unit8.enkan/kotowari-restful)
[![License](https://img.shields.io/badge/License-EPL%202.0-blue.svg)](https://www.eclipse.org/legal/epl-2.0/)
[![Java](https://img.shields.io/badge/Java-25%2B-orange.svg)](https://jdk.java.net/)

A declarative RESTful API framework for Java, built on [enkan](https://github.com/enkan/enkan) / [kotowari](https://github.com/enkan/enkan).

## Why kotowari-restful?

- **Decision graph, not boilerplate** — Inspired by [Liberator](https://clojure-liberator.github.io/liberator/), HTTP semantics are modeled as a fixed decision graph. You only override the points you care about; the framework handles correct status codes, content negotiation, conditional requests, and error responses automatically.
- **RFC-compliant by default** — 200, 201, 204, 301, 304, 400, 401, 403, 404, 405, 409, 412, 422, 500... all produced from the same graph without manual if/else chains.
- **Type-safe validation with Raoh** — Decode and validate request input using [Raoh](https://github.com/kawasima/raoh) decoders. Pattern matching on `Result<T>` eliminates stringly-typed error handling.
- **Zero-reflection at runtime** — Method handles and pre-built argument resolvers are compiled at startup. No per-request reflection.
- **RFC 9457 Problem Details** — Validation errors and exceptions are returned as structured `application/problem+json` responses out of the box.

## Requirements

- Java 25+
- enkan/kotowari 0.13.0+

## Project Structure

This is a multi-module Maven project:

```text
kotowari-restful/           ← root (parent POM)
├── kotowari-restful/       ← core library
└── kotowari-restful-devel/ ← development tools (request tracing, graph visualization)
```

The `example/` directory is an independent Maven project demonstrating usage.

## Installation

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>net.unit8.enkan</groupId>
    <artifactId>kotowari-restful</artifactId>
    <version>0.13.0</version>
</dependency>
```

For development-time request tracing, also add:

```xml
<dependency>
    <groupId>net.unit8.enkan</groupId>
    <artifactId>kotowari-restful-devel</artifactId>
    <version>0.13.0</version>
</dependency>
```

## Getting Started

### 1. Define a resource class

Annotate methods with `@Decision` to customize specific points in the decision graph. Everything else falls back to sensible defaults (e.g. `GET`/`HEAD` allowed, resource exists, authorized).

```java
import kotowari.restful.Decision;
import kotowari.restful.resource.AllowedMethods;
import static kotowari.restful.DecisionPoint.*;

@AllowedMethods({"GET", "POST"})
public class ArticlesResource {

    @Decision(HANDLE_OK)
    public List<Article> list() {
        return articleRepository.findAll();
    }

    @Decision(POST)
    public Article create(Article article) {
        return articleRepository.save(article);
    }
}
```

### 2. Wire up routing and `ResourceInvokerMiddleware`

```java
Routes routes = Routes.define(r -> {
    r.all("/articles").to(ArticlesResource.class);
}).compile();

List<ParameterInjector<?>> parameterInjectors = List.of(
        new HttpRequestInjector(),
        new ParametersInjector(),
        new SessionInjector()
);

WebApplication app = new WebApplication();
app.use(new ParamsMiddleware<>());
app.use(new NestedParamsMiddleware<>());
app.use(builder(new ContentNegotiationMiddleware<>())
        .set(ContentNegotiationMiddleware::setAllowedTypes, Set.of("application/json"))
        .build());
app.use(new RoutingMiddleware<>(routes));
app.use(new SerDesMiddleware<>());
app.use(builder(new ResourceInvokerMiddleware<>(injector))
        .set(ResourceInvokerMiddleware::setParameterInjectors, parameterInjectors)
        .build());
```

## Decision Points

The decision graph evaluates each request through a fixed sequence of decision points. Override any point by annotating a method with `@Decision`.

### Commonly used points

| Decision Point | Default | Description |
|---|---|---|
| `SERVICE_AVAILABLE` | `true` | Returns 503 when `false` |
| `KNOWN_METHOD` | GET/HEAD/OPTIONS/POST/PUT/DELETE/PATCH | Returns 501 for unknown methods |
| `METHOD_ALLOWED` | GET/HEAD | Returns 405 for disallowed methods (override with `@AllowedMethods`) |
| `MALFORMED` | `false` | Returns 400 when `true` or when a `Problem` is returned |
| `AUTHORIZED` | `true` | Returns 401 when `false` |
| `ALLOWED` | `true` | Returns 403 when `false` |
| `PROCESSABLE` | `true` | Returns 422 when `false` |
| `EXISTS` | `true` | Returns 404 when `false` |
| `CONFLICT` | `false` | Returns 409 when `true` |
| `POST` | `true` | Executes the POST action |
| `PUT` | `true` | Executes the PUT action |
| `PATCH` | `true` | Executes the PATCH action |
| `DELETE` | `true` | Executes the DELETE action |
| `HANDLE_OK` | `"ok"` | Response body for 200 |
| `HANDLE_CREATED` | `null` | Response body for 201 |
| `HANDLE_NOT_FOUND` | `"Resource not found"` | Response body for 404 |
| `HANDLE_EXCEPTION` | `null` | Response body for 500; `context.getException()` holds the thrown exception |

### Per-HTTP-method dispatch

Use the `method` attribute on `@Decision` to provide different implementations per HTTP method.

```java
@Decision(value = MALFORMED, method = {"GET"})
public Problem validateGet(Parameters params) {
    // validate query parameters
}

@Decision(value = MALFORMED, method = {"POST", "PUT"})
public Problem validateBody(Article article) {
    // validate request body
}
```

## Parameter Injection

Method parameters on `@Decision` methods are injected automatically:

| Type | Injected value |
|---|---|
| `RestContext` | The full request context |
| `Parameters` | Query string / form parameters |
| `HttpRequest` | The raw HTTP request |
| `Session` | The session |
| `Principal` | The authenticated user |
| Any POJO | Deserialized request body (JSON → POJO) |
| Type stored via `context.put(key, value)` | Value previously stored in the context |

Use `ContextKey<T>` and `context.put()` to pass objects computed in an earlier decision point to a later one.

> **Note:** Type-based injection resolves by `Class<?>`, so if the same type appears as both a request body parameter and a context-stored value, the context value shadows the body. When you need both in the same method, accept `RestContext` and call `context.get(KEY)` explicitly for the context-stored value.

```java
static final ContextKey<SearchParams> SEARCH_PARAMS = ContextKey.of(SearchParams.class);

@Decision(value = MALFORMED, method = {"GET"})
public Problem validateSearch(Parameters params, RestContext context) {
    return switch (SEARCH_PARAMS_DECODER.decode(params)) {
        case Ok<SearchParams> ok -> {
            context.put(SEARCH_PARAMS, ok.value());
            yield null;
        }
        case Err<SearchParams> err -> {
            List<Problem.Violation> violations = err.issues().asList().stream()
                    .map(issue -> new Problem.Violation(issue.path().toString(), issue.code(), issue.message()))
                    .toList();
            yield Problem.fromViolationList(violations);
        }
    };
}

@Decision(HANDLE_OK)
public List<Article> list(SearchParams params) {  // injected from context
    return repository.search(params);
}
```

## Validation and Error Responses

Use [Raoh](https://github.com/kawasima/raoh) decoders to decode and validate request input.
A `Decoder` returns `Result<T>` — either `Ok<T>` (success) or `Err<T>` (failure with issues).
Use a `switch` expression to handle both cases and produce a `Problem` on error:

```java
// Define a decoder once as a constant
static final JsonDecoder<Article> ARTICLE_DECODER = combine(
        field("title", string().trim().nonBlank().maxLength(200).map(String::new)),
        field("publishedAt", isoLocalDate())
).apply(Article::new)::decode;

@Decision(value = MALFORMED, method = {"POST"})
public Problem validatePost(JsonNode body, RestContext context) {
    return switch (ARTICLE_DECODER.decode(body)) {
        case Ok<Article> ok -> {
            context.put(ARTICLE, ok.value());
            yield null;
        }
        case Err<Article> err -> {
            List<Problem.Violation> violations = err.issues().asList().stream()
                    .map(issue -> new Problem.Violation(issue.path().toString(), issue.code(), issue.message()))
                    .toList();
            yield Problem.fromViolationList(violations);
        }
    };
}
```

Example response when validation fails (RFC 9457):

```json
{
  "type": "about:blank",
  "title": "Malformed",
  "status": 400,
  "violations": [
    { "field": "title", "message": "must not be blank" },
    { "field": "publishedAt", "message": "must not be null" }
  ]
}
```

## Allowed Methods

Annotate your resource class with `@AllowedMethods` to override the default (`GET`/`HEAD`). Requests using any other method automatically receive a 405 response.

```java
@AllowedMethods({"GET", "HEAD", "POST", "PUT", "DELETE"})
public class ArticlesResource {
    // ...
}
```

## Request Tracing (Development)

`kotowari-restful-devel` provides a Liberator-style decision graph visualizer that shows which nodes each request passed through, color-coded on the SVG graph.

### Enable tracing

```java
ResourceInvokerMiddleware<HttpResponse> resourceInvoker =
        builder(new ResourceInvokerMiddleware<HttpResponse>(injector))
                .set(ResourceInvokerMiddleware::setParameterInjectors, parameterInjectors)
                .set(ResourceInvokerMiddleware::setTracingEnabled, true)
                .build();
```

### Mount the dev endpoints

Add `TraceSvgEndpoint` and `TraceViewerEndpoint` to your application **before** `ContentNegotiationMiddleware` (they serve HTML/SVG, not JSON):

```java
import kotowari.restful.devel.TraceSvgEndpoint;
import kotowari.restful.devel.TraceViewerEndpoint;
import static enkan.predicate.PathPredicate.GET;
import static enkan.util.Predicates.envIn;

app.use(GET("/_dev/trace\\.svg").and(envIn("development")), "traceSvg", new TraceSvgEndpoint());
app.use(GET("/_dev/trace.*").and(envIn("development")), "traceViewer",
        new TraceViewerEndpoint(resourceInvoker.getTraceStore()));
```

### Available endpoints

| URL | Description |
| --- | --- |
| `/_dev/trace` | List of all recorded traces (newest first), with request time, method, and URI |
| `/_dev/trace/<id>` | Decision graph SVG with visited nodes highlighted for a specific request |
| `/_dev/trace.svg` | The raw decision graph SVG |

### Color coding

| Color | Meaning |
| --- | --- |
| Green | Decision node evaluated to `true` |
| Red | Decision node evaluated to `false` |
| Blue | Action or handler node |
| Orange edge | Traversed edge between consecutive nodes |

At most 100 traces are retained in memory; oldest entries are evicted automatically.

### Regenerating the decision graph SVG

The DOT source is at `kotowari-restful-devel/src/main/resources/kotowari/restful/trace/decision-graph.dot`.
To regenerate the SVG:

```bash
dot -Tsvg kotowari-restful-devel/src/main/resources/kotowari/restful/trace/decision-graph.dot \
    -o kotowari-restful-devel/src/main/resources/kotowari/restful/trace/decision-graph.svg
```

## Full Example

The following example shows a collection resource that supports paginated listing (`GET`) and creation (`POST`).
Input is decoded and validated using Raoh decoders; results are persisted via jOOQ with explicit transactions.

```java
@AllowedMethods({"GET", "POST"})
public class ArticlesResource {

    static final ContextKey<ArticleSearchParams> SEARCH_PARAMS = ContextKey.of(ArticleSearchParams.class);
    static final ContextKey<Article> ARTICLE = ContextKey.of(Article.class);

    // Raoh decoder: validates and maps JSON → Article
    private static final JsonDecoder<Article> ARTICLE_DECODER = combine(
            field("title", string().trim().nonBlank().maxLength(200).map(String::new)),
            field("publishedAt", isoLocalDate())
    ).apply(Article::new)::decode;

    @Decision(value = MALFORMED, method = {"GET"})
    public Problem validateGet(Parameters params, RestContext context) {
        return switch (SEARCH_PARAMS_DECODER.decode(params)) {
            case Ok<ArticleSearchParams> ok -> {
                context.put(SEARCH_PARAMS, ok.value());
                yield null;
            }
            case Err<ArticleSearchParams> err -> {
                List<Problem.Violation> violations = err.issues().asList().stream()
                        .map(issue -> new Problem.Violation(issue.path().toString(), issue.code(), issue.message()))
                        .toList();
                yield Problem.fromViolationList(violations);
            }
        };
    }

    @Decision(value = MALFORMED, method = {"POST"})
    public Problem validatePost(JsonNode body, RestContext context) {
        return switch (ARTICLE_DECODER.decode(body)) {
            case Ok<Article> ok -> {
                context.put(ARTICLE, ok.value());
                yield null;
            }
            case Err<Article> err -> {
                List<Problem.Violation> violations = err.issues().asList().stream()
                        .map(issue -> new Problem.Violation(issue.path().toString(), issue.code(), issue.message()))
                        .toList();
                yield Problem.fromViolationList(violations);
            }
        };
    }

    @Decision(HANDLE_OK)
    public List<Article> list(ArticleSearchParams params, DSLContext dsl) {
        return dsl.selectFrom(ARTICLES)
                .offset(params.getOffset())
                .limit(params.getLimit())
                .fetchInto(Article.class);
    }

    @Decision(POST)
    public boolean create(Article article, DSLContext dsl, RestContext context) {
        dsl.transaction(cfg -> {
            var rec = DSL.using(cfg)
                    .insertInto(ARTICLES, ARTICLES.TITLE, ARTICLES.PUBLISHED_AT)
                    .values(article.title(), article.publishedAt())
                    .returningResult(ARTICLES.ID)
                    .fetchOne();
            context.put(ARTICLE, new Article(rec.get(ARTICLES.ID), article.title(), article.publishedAt()));
        });
        return true;
    }

    @Decision(HANDLE_CREATED)
    public Article handleCreated(Article article) {
        return article;
    }
}
```

## License

Eclipse Public License 2.0
