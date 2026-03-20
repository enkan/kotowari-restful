package kotowari.restful.data;

import kotowari.restful.DecisionPoint;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import static java.util.Map.entry;
import static kotowari.restful.DecisionPoint.*;

/**
 * Provides sensible default functions for all decision points in the graph.
 *
 * <p>Used as the fallback parent of {@link ClassResource}: when a user's POJO
 * does not provide a {@code @Decision} method for a given point, the engine
 * falls through to these defaults (e.g. {@code EXISTS → true},
 * {@code MALFORMED → false}, {@code METHOD_ALLOWED → GET/HEAD}).
 *
 * <p>Can be subclassed to change application-wide defaults by overriding
 * {@link #getDefaultFunctions()} and passing the instance to
 * {@link kotowari.restful.middleware.ResourceInvokerMiddleware#setDefaultResource(Resource)}.
 *
 * @author kawasima
 */
public class DefaultResource implements Resource {
    private static final Function<RestContext, ?> TRUE = (context) -> true;
    private static final Function<RestContext, ?> FALSE = (context) -> false;
    public static Function<RestContext, ?> testRequestMethod(String... methods) {
        Set<String> methodSet = Set.of(methods);
        return context -> {
            String method = context.getRequest().getRequestMethod();
            return method != null && methodSet.contains(method.toUpperCase(Locale.US));
        };
    }

    private final Map<DecisionPoint, Function<RestContext, ?>> defaultFunctions = Map.ofEntries(
            // User-customizable decisions
            entry(INITIALIZE_CONTEXT,     context -> true),
            entry(SERVICE_AVAILABLE,      TRUE),
            entry(KNOWN_METHOD,           testRequestMethod("GET", "HEAD", "OPTIONS", "POST", "PUT", "DELETE", "PATCH")),
            entry(URI_TOO_LONG,           FALSE),
            entry(METHOD_ALLOWED,         testRequestMethod("GET", "HEAD")),
            entry(MALFORMED,              FALSE),
            entry(AUTHORIZED,             TRUE),
            entry(ALLOWED,                TRUE),
            entry(VALID_CONTENT_HEADER,   TRUE),
            entry(KNOWN_CONTENT_TYPE,     TRUE),
            entry(VALID_ENTITY_LENGTH,    TRUE),
            entry(MEDIA_TYPE_AVAILABLE,   TRUE),
            entry(LANGUAGE_AVAILABLE,     TRUE),
            entry(CHARSET_AVAILABLE,      TRUE),
            entry(ENCODING_AVAILABLE,     TRUE),
            entry(EXISTS,                 TRUE),
            entry(EXISTED,                FALSE),
            entry(RESPOND_WITH_ENTITY,    FALSE),
            entry(NEW,                    TRUE),
            entry(POST_REDIRECT,          FALSE),
            entry(PUT_TO_DIFFERENT_URL,   FALSE),
            entry(MULTIPLE_REPRESENTATIONS, FALSE),
            entry(CONFLICT,               FALSE),
            entry(IF_MATCH_STAR,          context -> Objects.equals("*", context.getRequest().getHeaders().get("if-match"))),
            entry(ETAG_MATCHES_FOR_IF_MATCH, FALSE),
            entry(ETAG_MATCHES_FOR_IF_NONE,  FALSE),
            entry(MODIFIED_SINCE,         FALSE),
            entry(UNMODIFIED_SINCE,       FALSE),
            entry(CAN_POST_TO_MISSING,    TRUE),
            entry(CAN_POST_TO_GONE,       TRUE),
            entry(CAN_PUT_TO_MISSING,     TRUE),
            entry(MOVED_PERMANENTLY,      FALSE),
            entry(MOVED_TEMPORARILY,      FALSE),
            entry(POST_ENACTED,           TRUE),
            entry(PUT_ENACTED,            TRUE),
            entry(PATCH_ENACTED,          TRUE),
            entry(DELETE_ENACTED,         TRUE),
            entry(PROCESSABLE,            TRUE),

            // Actions
            entry(POST,                   TRUE),
            entry(PUT,                    TRUE),
            entry(DELETE,                 TRUE),
            entry(PATCH,                  TRUE),

            // Handlers
            entry(HANDLE_OK,              context -> "OK"),
            entry(HANDLE_CREATED,         context -> null),
            entry(HANDLE_ACCEPTED,        context -> null),
            entry(HANDLE_NO_CONTENT,      context -> null),
            entry(HANDLE_MULTIPLE_REPRESENTATIONS, context -> null),
            entry(HANDLE_MOVED_PERMANENTLY, context -> null),
            entry(HANDLE_SEE_OTHER,       context -> null),
            entry(HANDLE_NOT_MODIFIED,    context -> null),
            entry(HANDLE_MOVED_TEMPORARILY, context -> null),
            entry(HANDLE_MALFORMED,       context -> "Bad request."),
            entry(HANDLE_UNAUTHORIZED,    context -> "Not authorized."),
            entry(HANDLE_FORBIDDEN,       context -> "Forbidden."),
            entry(HANDLE_NOT_FOUND,       context -> "Resource not found."),
            entry(HANDLE_METHOD_NOT_ALLOWED, context -> "Method not allowed."),
            entry(HANDLE_NOT_ACCEPTABLE,  context -> "No acceptable resource available."),
            entry(HANDLE_CONFLICT,        context -> "Conflict."),
            entry(HANDLE_GONE,            context -> "Resource is gone."),
            entry(HANDLE_PRECONDITION_FAILED, context -> "Precondition failed."),
            entry(HANDLE_REQUEST_ENTITY_TOO_LARGE, context -> "Request entity too large."),
            entry(HANDLE_URI_TOO_LONG,    context -> "Request URI too long."),
            entry(HANDLE_UNSUPPORTED_MEDIA_TYPE, context -> "Unsupported media type."),
            entry(HANDLE_UNPROCESSABLE_ENTITY, context -> "Unprocessable entity."),
            entry(HANDLE_EXCEPTION,       context -> "Internal server error."),
            entry(HANDLE_NOT_IMPLEMENTED, context -> "Not implemented."),
            entry(HANDLE_UNKNOWN_METHOD,  context -> "Unknown method."),
            entry(HANDLE_SERVICE_NOT_AVAILABLE, context -> "Service not available."),
            entry(HANDLE_OPTIONS,         context -> null)
        );

    @Override
    public Function<RestContext, ?> getFunction(DecisionPoint point) {
        return defaultFunctions.get(point);
    }


    protected Map<DecisionPoint, Function<RestContext, ?>> getDefaultFunctions() {
        return defaultFunctions;
    }
}
