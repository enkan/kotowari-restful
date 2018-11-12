package kotowari.restful.data;

import enkan.data.HttpRequest;
import kotowari.restful.DecisionPoint;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * The context of RESTful API.
 *
 * A context contains a request object and a resource object.
 *
 * @author kawasima
 */
public class RestContext {
    private final Resource resource;
    private final HttpRequest request;
    private final Map<Object, Object> values;
    private Object message;
    private int status;

    public RestContext(Resource resource, HttpRequest request) {
        this.resource = resource;
        this.request = request;
        this.values = new HashMap<>();
    }

    public Function<RestContext,?> getResourceFunction(DecisionPoint point) {
        return resource.getFunction(point);
    }

    public HttpRequest getRequest() {
        return request;
    }

    public Optional<Integer> getStatus() {
        return status == 0 ? Optional.empty() : Optional.of(status);
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public Optional<Object> getMessage() {
        return message == null ? Optional.empty() : Optional.of(message);
    }

    public void setMessage(Object message) {
        this.message = message;
    }

    public <V> void putValue(V value) {
        if (value == null) return;
        values.put(value.getClass(), value);
    }

    public <K> Optional<K> getValue(Class<K> key) {
        K value = (K) values.get(key);
        return value == null ? Optional.empty() : Optional.of(value);
    }
}
