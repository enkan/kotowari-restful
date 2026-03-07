package kotowari.restful.middleware;

import enkan.DecoratorMiddleware;
import enkan.MiddlewareChain;
import enkan.annotation.Middleware;
import enkan.data.HttpRequest;
import enkan.data.Routable;
import kotowari.restful.Decision;
import kotowari.restful.DecisionPoint;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the "primary" method for the current HTTP request and sets it
 * on {@link Routable#setControllerMethod(Method)}.
 *
 * <p>In kotowari-restful, a resource class has multiple {@code @Decision}-annotated
 * methods. This middleware picks the action method that corresponds to the HTTP
 * method (e.g. POST → {@code @Decision(POST)}, GET → {@code @Decision(HANDLE_OK)})
 * so that downstream middleware (such as transaction middleware) can inspect
 * method-level annotations.
 */
@Middleware(name = "resourceMethodResolver", dependencies = {"routing"})
public class ResourceMethodResolverMiddleware<RES> implements DecoratorMiddleware<HttpRequest, RES> {

    private static final Map<String, DecisionPoint> WRITE_ACTION = Map.of(
            "POST", DecisionPoint.POST,
            "PUT", DecisionPoint.PUT,
            "DELETE", DecisionPoint.DELETE,
            "PATCH", DecisionPoint.PATCH
    );

    private static final Map<String, DecisionPoint> READ_ACTION = Map.of(
            "GET", DecisionPoint.HANDLE_OK,
            "HEAD", DecisionPoint.HANDLE_OK
    );

    private final ConcurrentHashMap<MethodKey, Method> cache = new ConcurrentHashMap<>();

    @Override
    public <NNREQ, NNRES> RES handle(HttpRequest request, MiddlewareChain<HttpRequest, RES, NNREQ, NNRES> chain) {
        if (request instanceof Routable routable && routable.getControllerMethod() == null) {
            Class<?> resourceClass = routable.getControllerClass();
            if (resourceClass != null) {
                String httpMethod = request.getRequestMethod().toUpperCase(Locale.US);
                Method resolved = cache.computeIfAbsent(
                        new MethodKey(resourceClass, httpMethod),
                        key -> resolveMethod(key.resourceClass, key.httpMethod));
                if (resolved != null) {
                    routable.setControllerMethod(resolved);
                }
            }
        }
        return chain.next(request);
    }

    private Method resolveMethod(Class<?> resourceClass, String httpMethod) {
        // Read methods resolve to HANDLE_OK
        DecisionPoint readPoint = READ_ACTION.get(httpMethod);
        if (readPoint != null) {
            return findMethodForPoint(resourceClass, readPoint, httpMethod);
        }

        // Write methods: prefer MALFORMED (so SerDesMiddleware sees JsonNode body parameter)
        // and fall back to the action method if no MALFORMED method is defined
        if (WRITE_ACTION.containsKey(httpMethod)) {
            Method malformed = findMethodForPoint(resourceClass, DecisionPoint.MALFORMED, httpMethod);
            if (malformed != null) return malformed;
            return findMethodForPoint(resourceClass, WRITE_ACTION.get(httpMethod), httpMethod);
        }

        return null;
    }

    private Method findMethodForPoint(Class<?> resourceClass, DecisionPoint point, String httpMethod) {
        Method fallback = null;
        for (Method method : resourceClass.getMethods()) {
            Decision decision = method.getAnnotation(Decision.class);
            if (decision == null || decision.value() != point) continue;
            String[] methods = decision.method();
            if (methods.length == 0) {
                fallback = method;
            } else {
                for (String m : methods) {
                    if (httpMethod.equalsIgnoreCase(m)) return method;
                }
            }
        }
        return fallback;
    }

    private record MethodKey(Class<?> resourceClass, String httpMethod) {}
}
