package kotowari.restful.data;

/**
 * A type-safe key for storing and retrieving values in a {@link RestContext}.
 *
 * <p>Each key binds a name to a value type, ensuring compile-time type safety
 * when putting and getting values. Define keys as {@code static final} fields
 * on resource classes:
 *
 * <pre>{@code
 * static final ContextKey<CustomerId> CUSTOMER_ID = ContextKey.of(CustomerId.class);
 * static final ContextKey<ContactMethod> TARGET_CM = ContextKey.of("target", ContactMethod.class);
 *
 * // put
 * context.put(CUSTOMER_ID, new CustomerId(42));
 *
 * // get — no cast needed
 * CustomerId id = context.get(CUSTOMER_ID).orElseThrow();
 * }</pre>
 *
 * <p>Unlike a {@code Class<T>}-based key, multiple keys can share the same value
 * type (e.g. two {@code ContextKey<ContactMethod>} instances with different names),
 * and sealed interface subtypes do not cause lookup mismatches.
 *
 * @param name the key name (used for identity and debugging)
 * @param type the value type (used for parameter injection resolution)
 * @param <T>  the value type
 * @author kawasima
 */
public record ContextKey<T>(String name, Class<T> type) {

    /**
     * Creates a key with an explicit name.
     *
     * @param name the key name
     * @param type the value type
     * @param <T>  the value type
     * @return a new context key
     */
    public static <T> ContextKey<T> of(String name, Class<T> type) {
        return new ContextKey<>(name, type);
    }

    /**
     * Creates a key named after the simple class name.
     *
     * @param type the value type
     * @param <T>  the value type
     * @return a new context key
     */
    public static <T> ContextKey<T> of(Class<T> type) {
        return new ContextKey<>(type.getSimpleName(), type);
    }
}
