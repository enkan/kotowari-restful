package kotowari.restful.middleware;

import enkan.Middleware;
import enkan.MiddlewareChain;
import enkan.data.HttpRequest;
import enkan.data.Routable;
import enkan.exception.MisconfigurationException;
import enkan.system.inject.ComponentInjector;
import kotowari.inject.ParameterInjector;
import kotowari.restful.ResourceEngine;
import kotowari.restful.data.ApiResponse;
import kotowari.restful.data.ClassResource;
import kotowari.restful.data.DefaultResoruce;
import kotowari.restful.inject.parameter.RestContextInjector;
import kotowari.util.ParameterUtils;

import javax.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@enkan.annotation.Middleware(name = "resourceInvoker", dependencies = "params")
public class ResourceInvokerMiddleware<RES> implements Middleware<HttpRequest, RES, Void, Void> {
    private final Map<Class<?>, ClassResource> controllerCache = new ConcurrentHashMap<>();

    private final ComponentInjector componentInjector;
    private final ResourceEngine engine;
    private List<ParameterInjector<?>> parameterInjectors;

    public ResourceInvokerMiddleware(ComponentInjector componentInjector) {
        this.componentInjector = componentInjector;
        engine = new ResourceEngine();
    }

    @PostConstruct
    protected void setupParameterInjectors() {
        if (parameterInjectors == null) {
            parameterInjectors = ParameterUtils.getDefaultParameterInjectors();
        }
    }

    private Object inject(Object controller) {
        if (componentInjector != null) {
            componentInjector.inject(controller);
        }
        return controller;
    }

    @SuppressWarnings("unchecked")
    @Override
    public RES handle(HttpRequest request, MiddlewareChain<Void, Void, ?, ?> next) {
        if (request instanceof Routable) {
            Method resourceMethod = ((Routable) request).getControllerMethod();
            Class<?> resourceClass = resourceMethod.getDeclaringClass();

            ClassResource classResource = controllerCache
                    .computeIfAbsent(resourceClass, clazz -> new ClassResource(clazz, new DefaultResoruce(), componentInjector, parameterInjectors));

            ApiResponse response = engine.run(classResource, request);

            return (RES) response;
        } else {
            throw new MisconfigurationException("kotowari.MISSING_IMPLEMENTATION", Routable.class);
        }
    }
}
