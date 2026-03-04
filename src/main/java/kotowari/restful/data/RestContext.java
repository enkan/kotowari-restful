package kotowari.restful.data;

import enkan.collection.Headers;
import enkan.data.HttpRequest;
import kotowari.restful.DecisionPoint;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Mutable per-request state that flows through the decision graph.
 *
 * <p>Holds the HTTP request, the resource that handles it, and a typed value store
 * for passing objects between decision points. Handlers and decisions read/write
 * the {@linkplain #setMessage(Object) message}, {@linkplain #setStatus(int) status},
 * {@linkplain #setHeaders(enkan.collection.Headers) headers}, and
 * {@linkplain #setException(Throwable) exception} fields to shape the final
 * {@link ApiResponse}.
 *
 * <p>Values stored via {@link #putValue(Object)} are keyed by their runtime class
 * and can be retrieved later with {@link #getValue(Class)}.
 * This mechanism allows upstream decisions (e.g. {@code EXISTS}) to make objects
 * available to downstream handlers (e.g. {@code HANDLE_OK}) without coupling them
 * directly.
 *
 * @author kawasima
 */
public class RestContext {
    private final Resource resource;
    private final HttpRequest request;
    private final Map<Object, Object> values;
    private Object message;
    private int status;
    private Headers headers;
    private Throwable exception;

    /**
     * Creates a new context for the given resource and request.
     *
     * @param resource the resource that handles this request
     * @param request  the incoming HTTP request
     */
    public RestContext(Resource resource, HttpRequest request) {
        this.resource = resource;
        this.request = request;
        this.values = new HashMap<>();
    }

    /**
     * Returns the function registered on the resource for the given decision point,
     * or {@code null} if no function is registered.
     *
     * @param point the decision point to look up
     * @return the resource function, or {@code null}
     */
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

    public Headers getHeaders() {
        return headers;
    }

    public void setHeaders(Headers headers) {
        this.headers = headers;
    }

    public Throwable getException() {
        return exception;
    }

    public void setException(Throwable exception) {
        this.exception = exception;
    }

    /**
     * Stores a value keyed by its runtime class.
     *
     * <p>A later decision point or handler can retrieve it via
     * {@link #getValue(Class)}. {@code null} values are silently ignored.
     *
     * @param <V>   the type of the value
     * @param value the value to store (ignored if {@code null})
     */
    public <V> void putValue(V value) {
        if (value == null) return;
        values.put(value.getClass(), value);
    }

    /**
     * Retrieves a previously stored value by its class key.
     *
     * @param <K> the expected type
     * @param key the class used as the lookup key
     * @return an {@link Optional} containing the value, or empty if not found
     */
    @SuppressWarnings("unchecked")
    public <K> Optional<K> getValue(Class<K> key) {
        K value = (K) values.get(key);
        return value == null ? Optional.empty() : Optional.of(value);
    }
}
