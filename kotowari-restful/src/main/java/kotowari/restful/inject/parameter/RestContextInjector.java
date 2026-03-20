package kotowari.restful.inject.parameter;

import kotowari.restful.data.RestContext;

/**
 * Resolves method parameters from the {@link RestContext} value store.
 *
 * <p>Used as a dynamic fallback resolver in {@link kotowari.restful.data.ClassResource}
 * when no static {@link kotowari.inject.ParameterInjector} matches a parameter type.
 * If the parameter type is {@code RestContext} itself, the context is returned directly;
 * otherwise the value is looked up via {@link RestContext#getByType(Class)}.
 *
 * @author kawasima
 */
public class RestContextInjector {

    /**
     * Tests whether this injector can provide a value for the given type.
     *
     * @param type    the parameter type to check
     * @param context the current request context (must not be {@code null})
     * @return {@code true} if the type is {@link RestContext} or a matching value exists in the context
     */
    public boolean isApplicable(Class<?> type, RestContext context) {
        return RestContext.class.isAssignableFrom(type) || context.getByType(type).isPresent();
    }

    /**
     * Returns the value to inject for the given type.
     *
     * @param <K>     the expected return type
     * @param context the current request context
     * @param type    the parameter type
     * @return the context itself (if type is {@code RestContext}), the stored value, or {@code null}
     */
    @SuppressWarnings("unchecked")
    public <K> K getInjectObject(RestContext context, Class<K> type) {
        if (RestContext.class.isAssignableFrom(type)) {
            return (K) context;
        } else {
            return context.getByType(type).orElse(null);
        }
    }

}
