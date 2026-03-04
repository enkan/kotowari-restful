package kotowari.restful.example;

import enkan.Application;
import enkan.application.WebApplication;
import enkan.config.ApplicationFactory;
import enkan.middleware.ContentNegotiationMiddleware;
import enkan.middleware.MultipartParamsMiddleware;
import enkan.middleware.NestedParamsMiddleware;
import enkan.middleware.ParamsMiddleware;
import enkan.middleware.jpa.EntityManagerMiddleware;
import enkan.middleware.jpa.NonJtaTransactionMiddleware;
import enkan.system.inject.ComponentInjector;
import kotowari.inject.ParameterInjector;
import kotowari.inject.parameter.*;
import kotowari.middleware.RoutingMiddleware;
import kotowari.middleware.SerDesMiddleware;
import kotowari.restful.example.resource.AddressResource;
import kotowari.restful.example.resource.AddressesResource;
import kotowari.restful.middleware.ResourceInvokerMiddleware;
import kotowari.routing.Routes;

import java.util.List;
import java.util.Set;

import static enkan.util.BeanBuilder.builder;

/**
 * Builds the middleware stack for the example REST application.
 *
 * <p>Request processing order (outermost → innermost):
 * <ol>
 *   <li>{@code ParamsMiddleware} — parses query string and URL-encoded form body into {@code Parameters}</li>
 *   <li>{@code MultipartParamsMiddleware} — parses multipart/form-data</li>
 *   <li>{@code NestedParamsMiddleware} — expands dot-notation keys into nested maps (e.g. {@code foo.bar=1})</li>
 *   <li>{@code ContentNegotiationMiddleware} — rejects requests whose Accept header is not {@code application/json}</li>
 *   <li>{@code RoutingMiddleware} — matches the request path and sets the target resource class on the request</li>
 *   <li>{@code EntityManagerMiddleware} — opens a JPA EntityManager and binds it to the request scope</li>
 *   <li>{@code NonJtaTransactionMiddleware} — wraps the remaining stack in a RESOURCE_LOCAL JPA transaction</li>
 *   <li>{@code SerDesMiddleware} — deserializes the JSON request body and serializes the response object to JSON</li>
 *   <li>{@code ResourceInvokerMiddleware} — drives the kotowari-restful decision graph and calls the resource class</li>
 * </ol>
 *
 * <p>Routes:
 * <ul>
 *   <li>{@code /addresses}     → {@link AddressesResource}  (collection: GET list, POST create)</li>
 *   <li>{@code /addresses/:id} → {@link AddressResource}    (single item: GET, PUT, DELETE)</li>
 * </ul>
 *
 * <p>{@code parameterInjectors} lists the types that can be injected into {@code @Decision} method
 * parameters. The injectors are evaluated in order; the first matching one wins.
 * {@code EntityManagerInjector} allows resource methods to declare {@code EntityManager em}
 * as a parameter and receive the request-scoped EntityManager automatically.
 */
public class ExampleApplicationFactory implements ApplicationFactory {
    @Override
    public Application create(ComponentInjector injector) {
        Routes routes = Routes.define(r -> {
            r.all("/addresses").to(AddressesResource.class);
            r.all("/addresses/:id").to(AddressResource.class);
        }).compile();

        // Declares which types can be injected into @Decision method parameters.
        // Route path variables (e.g. :id) are merged into Parameters by RoutingMiddleware.
        List<ParameterInjector<?>> parameterInjectors = List.of(
                new HttpRequestInjector(),
                new ParametersInjector(),
                new SessionInjector(),
                new FlashInjector<>(),
                new PrincipalInjector(),
                new ConversationInjector(),
                new ConversationStateInjector(),
                new LocaleInjector(),
                new EntityManagerInjector()
        );
        WebApplication app = new WebApplication();
        app.use(new ParamsMiddleware());
        app.use(new MultipartParamsMiddleware());
        app.use(new NestedParamsMiddleware());
        app.use(builder(new ContentNegotiationMiddleware())
                .set(ContentNegotiationMiddleware::setAllowedTypes, Set.of("application/json"))
                .build());
        app.use(new RoutingMiddleware(routes));
        app.use(new EntityManagerMiddleware<>());
        app.use(new NonJtaTransactionMiddleware<>());
        app.use(new SerDesMiddleware<>());
        app.use(builder(new ResourceInvokerMiddleware<>(injector))
                .set(ResourceInvokerMiddleware::setParameterInjectors, parameterInjectors)
                .set(ResourceInvokerMiddleware::setOutputErrorReason, true)
                .build());
        return app;
    }
}
