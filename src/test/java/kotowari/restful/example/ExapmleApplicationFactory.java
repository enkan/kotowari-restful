package kotowari.restful.example;

import enkan.Application;
import enkan.application.WebApplication;
import enkan.config.ApplicationFactory;
import enkan.middleware.ContentNegotiationMiddleware;
import enkan.middleware.MultipartParamsMiddleware;
import enkan.middleware.NestedParamsMiddleware;
import enkan.middleware.ParamsMiddleware;
import enkan.system.inject.ComponentInjector;
import kotowari.middleware.RoutingMiddleware;
import kotowari.middleware.SerDesMiddleware;
import kotowari.restful.middleware.ResourceInvokerMiddleware;
import kotowari.restful.example.resource.AddressesResource;
import kotowari.routing.Routes;

public class ExapmleApplicationFactory implements ApplicationFactory {
    @Override
    public Application create(ComponentInjector injector) {
        Routes routes = Routes.define(r -> {
            r.all("/addresses").to(AddressesResource.class, "getClass");
        }).compile();

        WebApplication app = new WebApplication();
        app.use(new ParamsMiddleware<>());
        app.use(new MultipartParamsMiddleware<>());
        app.use(new NestedParamsMiddleware<>());
        app.use(new ContentNegotiationMiddleware<>());
        app.use(new RoutingMiddleware<>(routes));
        app.use(new SerDesMiddleware<>());
        app.use(new ResourceInvokerMiddleware<>(injector));
        return app;
    }
}
