package kotowari.restful.example.data;

/**
 * Typed wrapper for the database primary key of a {@code contact_method} row.
 *
 * @param value the raw {@code contact_method.id} value
 */
public record ContactMethodId(long value) {
}
