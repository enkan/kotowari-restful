package kotowari.restful.example;

import enkan.Application;
import enkan.application.WebApplication;
import enkan.config.ApplicationFactory;
import enkan.data.HttpRequest;
import enkan.data.HttpResponse;
import enkan.middleware.ContentNegotiationMiddleware;
import enkan.middleware.MultipartParamsMiddleware;
import enkan.middleware.NestedParamsMiddleware;
import enkan.middleware.ParamsMiddleware;
import enkan.middleware.jooq.JooqDslContextMiddleware;
import enkan.middleware.jooq.JooqTransactionMiddleware;
import enkan.system.inject.ComponentInjector;
import kotowari.inject.ParameterInjector;
import kotowari.inject.parameter.*;
import kotowari.middleware.RoutingMiddleware;
import kotowari.middleware.SerDesMiddleware;
import kotowari.restful.example.inject.DSLContextInjector;
import kotowari.restful.example.resource.AddressResource;
import kotowari.restful.example.resource.AddressesResource;
import kotowari.restful.example.resource.CustomerResource;
import kotowari.restful.example.resource.CustomersResource;
import kotowari.restful.middleware.ResourceMethodResolverMiddleware;
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
 *   <li>{@code NestedParamsMiddleware} — expands dot-notation keys into nested maps</li>
 *   <li>{@code ContentNegotiationMiddleware} — rejects requests whose Accept header is not {@code application/json}</li>
 *   <li>{@code RoutingMiddleware} — matches the request path and sets the target resource class on the request</li>
 *   <li>{@code JooqDslContextMiddleware} — provides a jOOQ DSLContext on every request</li>
 *   <li>{@code SerDesMiddleware} — deserializes the JSON request body and serializes the response object to JSON</li>
 *   <li>{@code ResourceInvokerMiddleware} — drives the kotowari-restful decision graph and calls the resource class</li>
 * </ol>
 */
public class ExampleApplicationFactory implements ApplicationFactory<HttpRequest, HttpResponse> {
    @Override
    public Application<HttpRequest, HttpResponse> create(ComponentInjector injector) {
        Routes routes = Routes.define(r -> {
            r.all("/addresses").to(AddressesResource.class);
            r.all("/addresses/:id").to(AddressResource.class);
            r.post("/customers").to(CustomersResource.class);
            r.get("/customers/:id").to(CustomerResource.class);
        }).compile();

        List<ParameterInjector<?>> parameterInjectors = List.of(
                new HttpRequestInjector(),
                new ParametersInjector(),
                new SessionInjector(),
                new FlashInjector<>(),
                new PrincipalInjector(),
                new ConversationInjector(),
                new ConversationStateInjector(),
                new LocaleInjector(),
                new DSLContextInjector()
        );
        WebApplication app = new WebApplication();
        app.use(new ParamsMiddleware());
        app.use(new MultipartParamsMiddleware());
        app.use(new NestedParamsMiddleware());
        app.use(builder(new ContentNegotiationMiddleware())
                .set(ContentNegotiationMiddleware::setAllowedTypes, Set.of("application/json"))
                .build());
        app.use(new RoutingMiddleware(routes));
        app.use(new ResourceMethodResolverMiddleware<>());
        app.use(new JooqDslContextMiddleware<>());
        app.use(new JooqTransactionMiddleware<>());
        app.use(new SerDesMiddleware<>());
        app.use(builder(new ResourceInvokerMiddleware<>(injector))
                .set(ResourceInvokerMiddleware::setParameterInjectors, parameterInjectors)
                .set(ResourceInvokerMiddleware::setOutputErrorReason, true)
                .build());
        return app;
    }
}
