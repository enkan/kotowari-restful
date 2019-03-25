package kotowari.restful.middleware;

import enkan.Middleware;
import enkan.MiddlewareChain;
import enkan.component.BeansConverter;
import enkan.data.HttpRequest;
import enkan.data.Routable;
import enkan.exception.MisconfigurationException;
import enkan.system.inject.ComponentInjector;
import kotowari.inject.ParameterInjector;
import kotowari.restful.ResourceEngine;
import kotowari.restful.data.ApiResponse;
import kotowari.restful.data.ClassResource;
import kotowari.restful.data.DefaultResource;
import kotowari.restful.data.Resource;
import kotowari.util.ParameterUtils;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A middleware for invoking a resource.
 *
 * @param <RES> The type of the response.
 * @author kawasima
 */
@enkan.annotation.Middleware(name = "resourceInvoker", dependencies = "params")
public class ResourceInvokerMiddleware<RES> implements Middleware<HttpRequest, RES, Void, Void> {
    private final Map<Class<?>, ClassResource> controllerCache = new ConcurrentHashMap<>();

    private final ComponentInjector componentInjector;
    private final ResourceEngine engine;

    private List<ParameterInjector<?>> parameterInjectors;
    private Resource baseResource;
    private boolean outputErrorReason = false;

    @Inject
    private BeansConverter beansConverter;

    public ResourceInvokerMiddleware(ComponentInjector componentInjector) {
        this.componentInjector = componentInjector;
        engine = new ResourceEngine();
    }

    @PostConstruct
    protected void setupParameterInjectors() {
        if (parameterInjectors == null) {
            parameterInjectors = ParameterUtils.getDefaultParameterInjectors();
        }
        if (baseResource == null) {
             baseResource = new DefaultResource();
        }
        if (outputErrorReason) {
            engine.setPrintStackTrace(true);
        }

    }

    private Object inject(Object controller) {
        if (componentInjector != null) {
            componentInjector.inject(controller);
        }
        return controller;
    }

    /**
     * {@inheritDoc}
     *
     * @param request {@inheritDoc}
     * @param next {@inheritDoc}
     * @return {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public RES handle(HttpRequest request, MiddlewareChain<Void, Void, ?, ?> next) {
        if (request instanceof Routable) {
            Class<?> resourceClass = ((Routable) request).getControllerClass();

            ClassResource classResource = controllerCache
                    .computeIfAbsent(resourceClass, clazz -> new ClassResource(clazz, baseResource, componentInjector, parameterInjectors, beansConverter));

            ApiResponse response = engine.run(classResource, request);

            return (RES) response;
        } else {
            throw new MisconfigurationException("kotowari.MISSING_IMPLEMENTATION", Routable.class);
        }
    }

    /**
     * Set the list of parameter injectors.
     *
     * @param parameterInjectors the list of parameter injectors
     */
    public void setParameterInjectors(List<ParameterInjector<?>> parameterInjectors) {
        this.parameterInjectors = parameterInjectors;
    }

    /**
     * Set the base resource.
     *
     * @param baseResource the base resource
     */
    public void setDefaultResource(Resource baseResource) {
        this.baseResource = baseResource;
    }

    public void setOutputErrorReason(boolean outputErrorReason) {
        this.outputErrorReason = outputErrorReason;
    }
}
