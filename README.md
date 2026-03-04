# kotowari-restful

kotowari-restful is a RESTful API framework built on top of [enkan](https://github.com/enkan/enkan) / [kotowari](https://github.com/enkan/kotowari). It models HTTP semantics as a decision graph, automatically returning RFC-compliant status codes without boilerplate.

## Requirements

- Java 21+
- enkan/kotowari 0.12.0+

## Installation

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>net.unit8.enkan</groupId>
    <artifactId>kotowari-restful</artifactId>
    <version>0.12.0</version>
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
| Type stored via `context.putValue()` | Value previously stored in the context |

Use `context.putValue()` to pass objects computed in an earlier decision point to a later one:

```java
@Decision(value = MALFORMED, method = {"GET"})
public Problem validateSearch(Parameters params, RestContext context) {
    SearchParams searchParams = converter.createFrom(params, SearchParams.class);
    context.putValue(searchParams);  // available in downstream decision points
    Set<ConstraintViolation<SearchParams>> violations = validator.validate(searchParams);
    return violations.isEmpty() ? null : Problem.fromViolations(violations);
}

@Decision(HANDLE_OK)
public List<Article> list(SearchParams params) {  // injected from context
    return repository.search(params);
}
```

## Validation and Error Responses

Use `BeansValidator` to validate beans, and convert violations to a Problem JSON response with `Problem.fromViolations()`.

```java
@Inject
private BeansValidator validator;

@Decision(value = MALFORMED, method = {"POST"})
public Problem validatePost(Article article) {
    Set<ConstraintViolation<Article>> violations = validator.validate(article);
    return violations.isEmpty() ? null : Problem.fromViolations(violations);
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

## Full Example

```java
@AllowedMethods({"GET", "POST"})
public class AddressesResource {

    @Inject
    private BeansValidator validator;

    @Inject
    private BeansConverter beansConverter;

    @Decision(value = MALFORMED, method = {"GET"})
    public Problem validateGet(Parameters params, RestContext context) {
        AddressSearchParams searchParams = beansConverter.createFrom(params, AddressSearchParams.class);
        context.putValue(searchParams);
        Set<ConstraintViolation<AddressSearchParams>> violations = validator.validate(searchParams);
        return violations.isEmpty() ? null : Problem.fromViolations(violations);
    }

    @Decision(value = MALFORMED, method = {"POST"})
    public Problem validatePost(Address address) {
        Set<ConstraintViolation<Address>> violations = validator.validate(address);
        return violations.isEmpty() ? null : Problem.fromViolations(violations);
    }

    @Decision(HANDLE_OK)
    public List<Address> list(AddressSearchParams params, EntityManager em) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Address> query = cb.createQuery(Address.class);
        query.select(query.from(Address.class));
        return em.createQuery(query)
                .setFirstResult(params.getOffset())
                .setMaxResults(params.getLimit())
                .getResultList();
    }

    @Decision(POST)
    public void create(Address address, EntityManager em, RestContext context) {
        em.persist(address);
        context.putValue(address);  // makes the persisted entity available to HANDLE_CREATED
    }

    @Decision(HANDLE_CREATED)
    public Address handleCreated(Address address) {
        return address;
    }
}
```

## License

Eclipse Public License 2.0
