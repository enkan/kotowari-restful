package kotowari.restful.exception;

/**
 * Thrown when a request body cannot be converted to the expected parameter type.
 */
public class MalformedBodyException extends RuntimeException {
    public MalformedBodyException(Class<?> targetType, Throwable cause) {
        super("Failed to convert request body to " + targetType.getName(), cause);
    }
}
