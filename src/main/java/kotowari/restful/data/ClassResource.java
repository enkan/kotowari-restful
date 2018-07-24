package kotowari.restful.data;

import enkan.data.HttpRequest;
import enkan.system.inject.ComponentInjector;
import kotowari.inject.ParameterInjector;
import kotowari.inject.parameter.BodySerializableInjector;
import kotowari.restful.Decision;
import kotowari.restful.DecisionPoint;
import kotowari.restful.inject.parameter.RestContextInjector;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import static enkan.util.ReflectionUtils.*;

public class ClassResource implements Resource {
    private static final ParameterInjector<?> BODY_SERIALIZABLE_INJECTOR = new BodySerializableInjector<>();
    private static final RestContextInjector REST_CONTEXT_INJECTOR = new RestContextInjector();

    private Map<DecisionPoint, Function<RestContext, ?>> functions;
    private Resource parent;
    private Object instance;

    protected Object[] createArguments(RestContext context, MethodMeta meta, List<ParameterInjector<?>> parameterInjectors) {
        Object[] arguments = new Object[meta.method.getParameterCount()];

        int i = 0;
        final HttpRequest req = context.getRequest();
        for (Parameter parameter : meta.method.getParameters()) {
            Class<?> type = parameter.getType();
            final int parameterIndex = i;
            ParameterInjector<?> parameterInjector = parameterInjectors.stream()
                    .filter(injector -> injector.isApplicable(type, req))
                    .findAny()
                    .orElse(BODY_SERIALIZABLE_INJECTOR);

            if (parameterInjector == BODY_SERIALIZABLE_INJECTOR) {
                if (REST_CONTEXT_INJECTOR.isApplicable(type, context)) {
                    arguments[parameterIndex] = REST_CONTEXT_INJECTOR.getInjectObject(context, type);

                } else {
                    arguments[parameterIndex] = BODY_SERIALIZABLE_INJECTOR.getInjectObject(req);
                }
            } else {
                arguments[parameterIndex] = parameterInjector.getInjectObject(req);
            }
            i++;
        }
        return arguments;
    }


    public ClassResource(Class<?> resourceClass, Resource parent,
                         ComponentInjector componentInjector,
                         List<ParameterInjector<?>> parameterInjectors) {
        instance = tryReflection(() -> componentInjector.inject(resourceClass.getConstructor().newInstance()));
        this.parent = parent;
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
                        httpMethodMap.put(m, new MethodMeta(method));
                    }
                } else {
                    fallbackMethod.set(new MethodMeta(method));
                }
            });

            functions.put(point, context -> {
                MethodMeta meta = httpMethodMap.getOrDefault(
                    context.getRequest().getRequestMethod().toUpperCase(Locale.US),
                    fallbackMethod.get());
                Object[] arguments = createArguments(context, meta, parameterInjectors);
                return tryReflection(() -> meta.method.invoke(instance, arguments));
            });
        });
    }
    @Override
    public Function<RestContext, ?> getFunction(DecisionPoint point) {
        Function<RestContext, ?> f = functions.get(point);
        if (f != null) return f;
        return parent.getFunction(point);
    }

    private static class MethodMeta {
        Method method;
        Parameter[] parameters;

        MethodMeta(Method method) {
            this.method = method;
            parameters = method.getParameters();
            Arrays.stream(parameters)
                    .map(param -> param.getName());
        }
    }
}
