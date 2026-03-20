package kotowari.restful.data;

import kotowari.restful.DecisionPoint;

import java.util.Set;
import java.util.function.Function;

/**
 * REST Resource interface.
 *
 * @author kawasima
 */
public interface Resource {
    /**
     * Get a handle function at the given decision point.
     *
     * @param point A decision point
     * @return A handle function at the given decision point
     */
    Function<RestContext, ?> getFunction(DecisionPoint point);

    /**
     * Returns the set of HTTP methods allowed on this resource.
     *
     * <p>This value is used to populate the {@code Allow} response header on
     * 405 Method Not Allowed and 200 OPTIONS responses, as required by
     * RFC 7231 §6.5.5 and RFC 9110 §9.3.7.
     *
     * @return an unmodifiable set of uppercase HTTP method names
     */
    default Set<String> getAllowedMethods() {
        return Set.of("GET", "HEAD");
    }
}
