package kotowari.restful.data;

import jakarta.ws.rs.core.MediaType;
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

    /**
     * Returns the set of media types accepted by this resource's {@code PATCH}
     * handler, used to populate the {@code Accept-Patch} response header on
     * successful {@code OPTIONS} responses and {@code 405 Method Not Allowed}
     * responses, as required by RFC 5789 §3.1.
     *
     * <p>The default implementation returns an empty set, which suppresses
     * emission of the {@code Accept-Patch} header. Resources that handle
     * {@code PATCH} should override this to advertise the patch document
     * formats they accept (e.g. {@code application/merge-patch+json} per
     * RFC 7396, or {@code application/json-patch+json} per RFC 6902).
     *
     * @return the set of media types accepted for PATCH, or an empty set
     *         if PATCH is not supported or no {@code Accept-Patch} header
     *         should be emitted
     */
    default Set<MediaType> getAcceptPatchMediaTypes() {
        return Set.of();
    }
}
