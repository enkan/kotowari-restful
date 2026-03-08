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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.function.Function;

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
 *   <li>{@link RestContext#getByType(Class)} — dynamic lookup at invocation time</li>
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
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final MethodType NO_ARG_INVOKER_TYPE = MethodType.methodType(Object.class);

    private final Map<DecisionPoint, Function<RestContext, ?>> functions;
    private final Resource parent;
    private final Function<RestContext, ?> methodAllowedFunc;
    private final MethodHandles.Lookup resourceLookup;
    private final Set<String> allowedMethods;

    /**
     * Create argument objects by parameter injectors.
     *
     * @param context A context for REST
     * @param meta A metadata of the method
     * @return An object array for the method arguments
     */
    private Object[] createArguments(RestContext context, MethodMeta meta) {
        Object[] arguments = new Object[meta.resolvers.length];
        for (int i = 0; i < meta.resolvers.length; i++) {
            arguments[i] = meta.resolvers[i].apply(context);
        }
        return arguments;
    }

    private static String normalizeHttpMethod(String method) {
        if (method == null) return "";
        return method.toUpperCase(Locale.US);
    }

    private Set<String> parseAllowedMethods(Class<?> resourceClass) {
        AllowedMethods allowedMethods = resourceClass.getAnnotation(AllowedMethods.class);
        if (allowedMethods == null) {
            return Set.of("GET", "HEAD");
        } else {
            String[] methods = allowedMethods.value();
            Set<String> normalized = new HashSet<>(methods.length);
            for (String method : methods) {
                normalized.add(normalizeHttpMethod(method));
            }
            return Collections.unmodifiableSet(normalized);
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
        Object instance;
        try {
            instance = componentInjector.inject(resourceClass.getConstructor().newInstance());
        } catch (NoSuchMethodException e) {
            throw new enkan.exception.MisconfigurationException(
                    "kotowari_restful.NO_DEFAULT_CONSTRUCTOR", resourceClass.getName(), e);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        this.parent = parent;
        this.resourceLookup = tryReflection(() -> MethodHandles.privateLookupIn(resourceClass, LOOKUP));
        this.allowedMethods = parseAllowedMethods(resourceClass);
        methodAllowedFunc = context -> allowedMethods.contains(normalizeHttpMethod(context.getRequest().getRequestMethod()));
        functions = new EnumMap<>(DecisionPoint.class);
        Map<DecisionPoint, List<Method>> resourceMethods = new EnumMap<>(DecisionPoint.class);
        for (Method method : resourceClass.getMethods()) {
            Decision decision = method.getAnnotation(Decision.class);
            if (decision == null) continue;
            resourceMethods.computeIfAbsent(decision.value(), ignored -> new ArrayList<>())
                    .add(method);
        }

        resourceMethods.forEach((point, methods) -> {
            Map<String, MethodMeta> httpMethodMap = new HashMap<>();
            MethodMeta fallbackMethod = null;
            for (Method method : methods) {
                Decision decision = method.getAnnotation(Decision.class);
                String[] targetMethods = decision.method();
                MethodMeta meta = new MethodMeta(method, instance, parameterInjectors, beansConverter, resourceLookup);
                if (targetMethods.length > 0) {
                    for(String m : targetMethods) {
                        httpMethodMap.put(normalizeHttpMethod(m), meta);
                    }
                } else {
                    fallbackMethod = meta;
                }
            }
            final MethodMeta fallbackMeta = fallbackMethod;

            functions.put(point, context -> {
                MethodMeta meta = httpMethodMap.getOrDefault(normalizeHttpMethod(context.getRequest().getRequestMethod()),
                        fallbackMeta);
                if (meta != null) {
                    return invokeMethod(context, meta);
                } else {
                    return parent.getFunction(point).apply(context);
                }
            });
        });
    }

    @Override
    public Set<String> getAllowedMethods() {
        return allowedMethods;
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

    private Object invokeMethod(RestContext context, MethodMeta meta) {
        try {
            if (meta.resolvers.length == 0) {
                return meta.noArgInvoker.invokeExact();
            }
            Object[] arguments = createArguments(context, meta);
            return meta.argInvoker.invokeExact(arguments);
        } catch (Throwable t) {
            switch (t) {
                case Error error -> throw error;
                case RuntimeException runtimeException -> throw runtimeException;
                case Exception exception -> throw new RuntimeException(exception);
                default -> throw new InternalError(t);
            }
        }
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
                        .filter(pi -> pi.isApplicable(type))
                        .findAny()
                        .orElse(null);
                if (injector != null) {
                    final ParameterInjector<?> captured = injector;
                    resolvers[i] = ctx -> captured.getInjectObject(ctx.getRequest());
                } else {
                    // context.getByType(type) is dynamic; body deserialization type check is also dynamic
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
                            } catch (Exception e) {
                                throw new enkan.exception.MisconfigurationException(
                                        "kotowari_restful.UNCONVERTIBLE_BODY_PARAMETER",
                                        deserializedBody.getClass().getName(), type.getName(), e);
                            }
                        }
                    };
                }
            }
        }
        return resolvers;
    }

    protected static class MethodMeta {
        final Function<RestContext, Object>[] resolvers;
        final MethodHandle noArgInvoker;
        final MethodHandle argInvoker;

        MethodMeta(Method method,
                   Object instance,
                   List<ParameterInjector<?>> parameterInjectors,
                   BeansConverter beansConverter,
                   MethodHandles.Lookup resourceLookup) {
            this.resolvers = buildResolvers(method.getParameters(), parameterInjectors, beansConverter);
            MethodHandle boundMethod = tryReflection(() -> resourceLookup.unreflect(method)).bindTo(instance);
            if (resolvers.length == 0) {
                this.noArgInvoker = boundMethod.asType(NO_ARG_INVOKER_TYPE);
                this.argInvoker = null;
            } else {
                this.noArgInvoker = null;
                this.argInvoker = boundMethod
                        .asSpreader(Object[].class, resolvers.length)
                        .asType(MethodType.methodType(Object.class, Object[].class));
            }
        }
    }
}
