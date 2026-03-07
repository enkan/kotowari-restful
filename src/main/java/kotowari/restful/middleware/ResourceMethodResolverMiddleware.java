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

    private static final Map<String, DecisionPoint> HTTP_TO_ACTION = Map.of(
            "POST", DecisionPoint.POST,
            "PUT", DecisionPoint.PUT,
            "DELETE", DecisionPoint.DELETE,
            "PATCH", DecisionPoint.PATCH,
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
        DecisionPoint targetPoint = HTTP_TO_ACTION.get(httpMethod);
        if (targetPoint == null) return null;

        for (Method method : resourceClass.getMethods()) {
            Decision decision = method.getAnnotation(Decision.class);
            if (decision == null) continue;
            if (decision.value() != targetPoint) continue;

            String[] methods = decision.method();
            if (methods.length == 0) {
                return method;
            }
            for (String m : methods) {
                if (httpMethod.equalsIgnoreCase(m)) {
                    return method;
                }
            }
        }
        return null;
    }

    private record MethodKey(Class<?> resourceClass, String httpMethod) {}
}
