package kotowari.restful.data;

import enkan.collection.Headers;
import enkan.data.HttpRequest;
import kotowari.restful.DecisionPoint;
import kotowari.restful.trace.RequestTrace;
import kotowari.restful.trace.TraceEntry;

import java.time.Instant;
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
 * without knowing the specific {@link ContextKey}. If multiple keys share the
 * same type, the last {@link #put} wins in the type index. When a method
 * parameter's type matches both a context-stored value and the deserialized
 * request body, the context value takes precedence. In such cases, accept
 * {@code RestContext} as a parameter and use {@link #get(ContextKey)} explicitly.
 *
 * @author kawasima
 */
public class RestContext {
    /**
     * Key for the parsed {@code If-Modified-Since} date, stored by the
     * {@code IF_MODIFIED_SINCE_VALID_DATE} decision node when the header
     * contains a valid HTTP-date.
     *
     * <p>Resource classes that override {@code MODIFIED_SINCE} can retrieve
     * this value to compare against the resource's last modification time:
     * <pre>{@code
     * @Decision(MODIFIED_SINCE)
     * public boolean modifiedSince(RestContext ctx) {
     *     Instant clientDate = ctx.get(RestContext.IF_MODIFIED_SINCE_DATE).orElseThrow();
     *     return myLastModified.isAfter(clientDate);
     * }
     * }</pre>
     *
     * <p><b>Note:</b> Both this key and {@link #IF_UNMODIFIED_SINCE_DATE} share
     * the {@code Instant} type in the type-based index. Always use the explicit
     * {@link ContextKey} via {@link #get(ContextKey)} rather than relying on
     * type-based parameter injection for {@code Instant}.
     */
    public static final ContextKey<Instant> IF_MODIFIED_SINCE_DATE =
            ContextKey.of("ifModifiedSinceDate", Instant.class);

    /**
     * Key for the parsed {@code If-Unmodified-Since} date, stored by the
     * {@code IF_UNMODIFIED_SINCE_VALID_DATE} decision node when the header
     * contains a valid HTTP-date.
     */
    public static final ContextKey<Instant> IF_UNMODIFIED_SINCE_DATE =
            ContextKey.of("ifUnmodifiedSinceDate", Instant.class);

    private final Resource resource;
    private final HttpRequest request;
    private final Map<ContextKey<?>, Object> values;
    private final Map<Class<?>, Object> typeIndex;
    private Object message;
    private int status;
    private Headers headers;
    private Throwable exception;
    private RequestTrace trace;

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

    /**
     * Adds a single response header to the context.
     *
     * <p>If no headers object has been set yet, one is created automatically.
     * This is the preferred way for decision functions to set individual response
     * headers such as {@code WWW-Authenticate} or {@code Location}.
     *
     * @param name  the header name (case-insensitive per HTTP spec)
     * @param value the header value
     */
    public void addHeader(String name, String value) {
        if (headers == null) {
            headers = Headers.empty();
        }
        headers.put(name, value);
    }

    /**
     * Enables request tracing for this context.
     *
     * <p>Once enabled, every {@link Decision}, {@link kotowari.restful.decision.Action},
     * and {@link kotowari.restful.decision.Handler} node visited during graph traversal
     * is recorded via {@link #recordTrace(DecisionPoint, String, Boolean)}.
     */
    public void enableTracing() {
        this.trace = new RequestTrace();
    }

    /**
     * Records a single node visit. This is a no-op when tracing is not enabled.
     *
     * @param point  the decision point of the visited node
     * @param kind   the node kind: {@code "DECISION"}, {@code "ACTION"}, or {@code "HANDLER"}
     * @param result the boolean result, or {@code null} for action/handler nodes
     */
    public void recordTrace(DecisionPoint point, String kind, Boolean result) {
        if (trace != null) {
            trace.record(new TraceEntry(point, kind, result));
        }
    }

    /**
     * Returns the accumulated request trace, if tracing was enabled for this context.
     *
     * @return an {@link Optional} containing the trace, or empty if tracing was not enabled
     */
    public Optional<RequestTrace> getTrace() {
        return Optional.ofNullable(trace);
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
     * @param value the value to store (ignored if {@code null})
     */
    public <T> void put(ContextKey<T> key, T value) {
        if (value == null) return;
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
