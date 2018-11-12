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
import kotowari.restful.middleware.ResourceInvokerMiddleware;
import kotowari.restful.example.resource.AddressesResource;
import kotowari.routing.Routes;

import java.util.List;

import static enkan.util.BeanBuilder.builder;

public class ExampleApplicationFactory implements ApplicationFactory {
    @Override
    public Application create(ComponentInjector injector) {
        Routes routes = Routes.define(r -> {
            r.all("/addresses").to(AddressesResource.class);
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
                new EntityManagerInjector()
        );
        WebApplication app = new WebApplication();
        app.use(new ParamsMiddleware<>());
        app.use(new MultipartParamsMiddleware<>());
        app.use(new NestedParamsMiddleware<>());
        app.use(new ContentNegotiationMiddleware<>());
        app.use(new RoutingMiddleware<>(routes));
        app.use(new EntityManagerMiddleware<>());
        app.use(new NonJtaTransactionMiddleware<>());
        app.use(new SerDesMiddleware<>());
        app.use(builder(new ResourceInvokerMiddleware<>(injector))
                .set(ResourceInvokerMiddleware::setParameterInjectors, parameterInjectors)
                .build());
        return app;
    }
}
