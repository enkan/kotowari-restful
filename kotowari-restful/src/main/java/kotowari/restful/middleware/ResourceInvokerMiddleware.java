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
import kotowari.restful.trace.TraceStore;
import kotowari.util.ParameterUtils;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Terminal middleware that drives the kotowari-restful decision graph.
 *
 * <p>For each request, resolves the target resource class from the {@link Routable}
 * request, builds (or retrieves from cache) a {@link ClassResource}, and delegates
 * to {@link ResourceEngine#run(Resource, HttpRequest)}.
 *
 * <p>{@link ClassResource} instances are cached per class in a
 * {@link ConcurrentHashMap}, so reflection and resolver compilation happen only once.
 *
 * @param <RES> the response type (typically {@link ApiResponse})
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
    private boolean tracingEnabled = false;

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
        if (tracingEnabled) {
            engine.setTracingEnabled(true);
        }
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
    public <NNREQ, NNRES> RES handle(HttpRequest request, MiddlewareChain<Void, Void, NNREQ, NNRES> next) {
        if (request instanceof Routable) {
            Class<?> resourceClass = ((Routable) request).getControllerClass();

            ClassResource classResource = controllerCache
                    .computeIfAbsent(resourceClass, clazz -> new ClassResource(clazz, baseResource, componentInjector, parameterInjectors, beansConverter));

            ApiResponse response = engine.run(classResource, request);

            return (RES) response;
        } else {
            throw new MisconfigurationException("kotowari_restful.MISSING_IMPLEMENTATION", Routable.class);
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

    /**
     * When {@code true}, exception details are included in 500 error responses.
     * Useful for development; should be disabled in production.
     *
     * @param outputErrorReason {@code true} to include error details
     */
    public void setOutputErrorReason(boolean outputErrorReason) {
        this.outputErrorReason = outputErrorReason;
    }

    /**
     * Enables or disables per-request decision graph tracing.
     *
     * <p>When enabled, every node visited during graph traversal is recorded and stored
     * in the {@link TraceStore}. Intended for development use only.
     *
     * @param tracingEnabled {@code true} to enable tracing
     */
    public void setTracingEnabled(boolean tracingEnabled) {
        this.tracingEnabled = tracingEnabled;
    }

    /**
     * Returns the {@link TraceStore} that accumulates per-request traces.
     *
     * <p>Pass this to {@code TraceViewerEndpoint} (from {@code kotowari-restful-devel})
     * to expose a browser-accessible trace viewer.
     *
     * @return the trace store
     */
    public TraceStore getTraceStore() {
        return engine.getTraceStore();
    }
}
