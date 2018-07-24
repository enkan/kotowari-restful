package kotowari.restful.inject.parameter;

import kotowari.restful.data.RestContext;

public class RestContextInjector {
    public boolean isApplicable(Class<?> type, RestContext context) {
        return RestContext.class.isAssignableFrom(type) || context.getValue(type).isPresent();
    }

    public <K> K getInjectObject(RestContext context, Class<K> type) {
        if (RestContext.class.isAssignableFrom(type)) {
            return (K) context;
        } else {
            return context.getValue(type).orElse(null);
        }
    }
}
