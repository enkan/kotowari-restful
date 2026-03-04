package kotowari.restful.data;

import enkan.component.BeansConverter;
import enkan.system.inject.ComponentInjector;
import kotowari.data.BodyDeserializable;
import kotowari.inject.ParameterInjector;
import kotowari.inject.parameter.BodySerializableInjector;
import kotowari.restful.Decision;
import kotowari.restful.DecisionPoint;
import kotowari.restful.inject.parameter.RestContextInjector;
import kotowari.restful.exception.MalformedBodyException;
import kotowari.restful.resource.AllowedMethods;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import static enkan.util.ReflectionUtils.tryReflection;

/**
 * A {@link Resource} implementation backed by a POJO class annotated with
 * {@link Decision @Decision}.
 *
 * <p>At construction time the class is scanned for {@code @Decision}-annotated methods.
 * Each method is compiled into a {@link java.util.function.Function Function&lt;RestContext, ?&gt;}
 * with pre-built argument resolvers ({@link MethodMeta}), so no reflection occurs
 * per request. Methods may be scoped to specific HTTP methods via
 * {@link Decision#method() @Decision(method={"POST"})}.
 *
 * <p>Argument injection priority per parameter:
 * <ol>
 *   <li>{@link RestContext} — injected directly</li>
 *   <li>Any matching {@link kotowari.inject.ParameterInjector} (e.g. {@code Parameters},
 *       {@code EntityManager}) — static, determined at construction time</li>
 *   <li>{@link RestContext#getValue(Class)} — dynamic lookup at invocation time</li>
 *   <li>Deserialized request body (direct or via {@code BeansConverter}) — dynamic</li>
 * </ol>
 *
 * <p>Instances are cached per resource class by
 * {@link kotowari.restful.middleware.ResourceInvokerMiddleware}.
 *
 * @author kawasima
 */
public class ClassResource implements Resource {
    private static final ParameterInjector<?> BODY_SERIALIZABLE_INJECTOR = new BodySerializableInjector<>();
    private static final RestContextInjector REST_CONTEXT_INJECTOR = new RestContextInjector();

    private final Map<DecisionPoint, Function<RestContext, ?>> functions;
    private final Resource parent;
    private final Object instance;
    private final Function<RestContext, ?> methodAllowedFunc;

    /**
     * Create argument objects by parameter injectors.
     *
     * @param context A context for REST
     * @param meta A metadata of the method
     * @return An object array for the method arguments
     */
    protected Object[] createArguments(RestContext context, MethodMeta meta) {
        Object[] arguments = new Object[meta.resolvers.length];
        for (int i = 0; i < meta.resolvers.length; i++) {
            arguments[i] = meta.resolvers[i].apply(context);
        }
        return arguments;
    }

    private Set<String> parseAllowedMethods(Class<?> resourceClass) {
        AllowedMethods allowedMethods = resourceClass.getAnnotation(AllowedMethods.class);
        if (allowedMethods == null) {
            return Set.of("GET", "HEAD");
        } else {
            return Arrays.stream(allowedMethods.value())
                    .map(m -> m.toUpperCase(Locale.US))
                    .collect(Collectors.toSet());
        }
    }

    /**
     * Scans {@code resourceClass} for {@code @Decision}-annotated methods and
     * pre-compiles argument resolvers.
     *
     * @param resourceClass      the POJO resource class to wrap
     * @param parent             the fallback resource (typically {@link DefaultResource})
     * @param componentInjector  injector for {@code @Inject} fields on the resource instance
     * @param parameterInjectors ordered list of parameter injectors
     * @param beansConverter     converter used to deserialize request bodies
     */
    public ClassResource(Class<?> resourceClass, Resource parent,
                         ComponentInjector componentInjector,
                         List<ParameterInjector<?>> parameterInjectors,
                         BeansConverter beansConverter) {
        instance = tryReflection(() -> componentInjector.inject(resourceClass.getConstructor().newInstance()));
        this.parent = parent;
        Set<String> allowedMethods = parseAllowedMethods(resourceClass);
        methodAllowedFunc = context -> allowedMethods.contains(context.getRequest().getRequestMethod().toUpperCase(Locale.US));
        functions = new HashMap<>();
        Map<DecisionPoint, List<Method>> resourceMethods = Arrays.stream(resourceClass.getMethods())
            .filter(method -> tryReflection(() -> method.getAnnotation(Decision.class) != null))
            .collect(Collectors.groupingBy(method -> {
                Decision decision = method.getAnnotation(Decision.class);
                return decision.value();
            }, Collectors.toList()));


        resourceMethods.forEach((point, methods) -> {
            Map<String, MethodMeta> httpMethodMap = new HashMap<>();
            final AtomicReference<MethodMeta> fallbackMethod = new AtomicReference<>();
            methods.forEach(method -> {
                Decision decision = method.getAnnotation(Decision.class);
                String[] targetMethods = decision.method();
                if (targetMethods.length > 0) {
                    for(String m : targetMethods) {
                        httpMethodMap.put(m, new MethodMeta(method, parameterInjectors, beansConverter));
                    }
                } else {
                    fallbackMethod.set(new MethodMeta(method, parameterInjectors, beansConverter));
                }
            });

            functions.put(point, context -> {
                MethodMeta meta = httpMethodMap.getOrDefault(
                    context.getRequest().getRequestMethod().toUpperCase(Locale.US),
                    fallbackMethod.get());
                if (meta != null) {
                    Object[] arguments = createArguments(context, meta);
                    return tryReflection(() -> meta.method.invoke(instance, arguments));
                } else {
                    return parent.getFunction(point).apply(context);
                }
            });
        });
    }

    @Override
    public Function<RestContext, ?> getFunction(DecisionPoint point) {
        Function<RestContext, ?> f = functions.get(point);
        if (f != null) return f;
        if (point == DecisionPoint.METHOD_ALLOWED) {
            return methodAllowedFunc;
        }
        return parent.getFunction(point);
    }

    @SuppressWarnings("unchecked")
    private static Function<RestContext, Object>[] buildResolvers(
            Parameter[] parameters,
            List<ParameterInjector<?>> parameterInjectors,
            BeansConverter beansConverter) {
        Function<RestContext, Object>[] resolvers = new Function[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Class<?> type = parameters[i].getType();
            if (RestContext.class.isAssignableFrom(type)) {
                resolvers[i] = ctx -> ctx;
            } else {
                ParameterInjector<?> injector = parameterInjectors.stream()
                        .filter(pi -> pi.isApplicable(type, null))
                        .findAny()
                        .orElse(null);
                if (injector != null) {
                    final ParameterInjector<?> captured = injector;
                    resolvers[i] = ctx -> captured.getInjectObject(ctx.getRequest());
                } else {
                    // context.getValue(type) is dynamic; body deserialization type check is also dynamic
                    resolvers[i] = ctx -> {
                        if (REST_CONTEXT_INJECTOR.isApplicable(type, ctx)) {
                            return REST_CONTEXT_INJECTOR.getInjectObject(ctx, type);
                        }
                        if (!(ctx.getRequest() instanceof BodyDeserializable)) {
                            return null;
                        }
                        Object deserializedBody = ((BodyDeserializable) ctx.getRequest()).getDeserializedBody();
                        if (deserializedBody == null) {
                            return null;
                        } else if (type.isAssignableFrom(deserializedBody.getClass())) {
                            return BODY_SERIALIZABLE_INJECTOR.getInjectObject(ctx.getRequest());
                        } else {
                            try {
                                return beansConverter.createFrom(deserializedBody, type);
                            } catch (IllegalArgumentException e) {
                                throw new MalformedBodyException(type, e);
                            }
                        }
                    };
                }
            }
        }
        return resolvers;
    }

    private static class MethodMeta {
        final Method method;
        final Function<RestContext, Object>[] resolvers;

        MethodMeta(Method method, List<ParameterInjector<?>> parameterInjectors, BeansConverter beansConverter) {
            this.method = method;
            this.resolvers = buildResolvers(method.getParameters(), parameterInjectors, beansConverter);
        }
    }
}
