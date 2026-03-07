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
 * <p>Values are stored and retrieved using {@link ContextKey} instances, providing
 * compile-time type safety. Each key binds a name to a value type:
 *
 * <pre>{@code
 * static final ContextKey<CustomerId> CUSTOMER_ID = ContextKey.of(CustomerId.class);
 * context.put(CUSTOMER_ID, new CustomerId(42));
 * CustomerId id = context.get(CUSTOMER_ID).orElseThrow();
 * }</pre>
 *
 * <p>A secondary type-based index is maintained so that the parameter injection
 * mechanism in {@link ClassResource} can resolve values by their declared type
 * without knowing the specific {@link ContextKey}.
 *
 * @author kawasima
 */
public class RestContext {
    private final Resource resource;
    private final HttpRequest request;
    private final Map<ContextKey<?>, Object> values;
    private final Map<Class<?>, Object> typeIndex;
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
        this.typeIndex = new HashMap<>();
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
     * Stores a value under the given typed key.
     *
     * <p>The value is also indexed by the key's {@linkplain ContextKey#type() type}
     * so that parameter injection can resolve it by class. If multiple keys share
     * the same type, the last value stored wins in the type index.
     *
     * @param <T>   the value type
     * @param key   the context key
     * @param value the value to store (must not be {@code null})
     * @throws NullPointerException if {@code key} or {@code value} is {@code null}
     */
    public <T> void put(ContextKey<T> key, T value) {
        values.put(key, value);
        typeIndex.put(key.type(), value);
    }

    /**
     * Retrieves a previously stored value by its typed key.
     *
     * @param <T> the value type
     * @param key the context key
     * @return an {@link Optional} containing the value, or empty if not found
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(ContextKey<T> key) {
        T value = (T) values.get(key);
        return Optional.ofNullable(value);
    }

    /**
     * Retrieves a stored value by its type from the secondary type index.
     *
     * <p>This method is used by the parameter injection mechanism when the caller
     * knows the desired type but not the specific {@link ContextKey}. If multiple
     * keys share the same type, the most recently stored value is returned.
     *
     * @param <T>  the expected type
     * @param type the class to look up
     * @return an {@link Optional} containing the value, or empty if not found
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getByType(Class<T> type) {
        T value = (T) typeIndex.get(type);
        return Optional.ofNullable(value);
    }
}
