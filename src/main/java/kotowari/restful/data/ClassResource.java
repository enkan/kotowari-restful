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
 * A resource implementation based on a POJO.
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
