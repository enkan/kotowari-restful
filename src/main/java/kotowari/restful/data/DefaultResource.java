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
            entry(EXISTS,                 TRUE),
            entry(EXISTED,                FALSE),
            entry(RESPOND_WITH_ENTITY,    FALSE),
            entry(NEW,                    TRUE),
            entry(POST_REDIRECT,          FALSE),
            entry(PUT_TO_DIFFERENT_URL,   FALSE),
            entry(MULTIPLE_REPRESENTATIONS, FALSE),
            entry(CONFLICT,               FALSE),
            entry(IF_MATCH_STAR,          context -> Objects.equals("*", context.getRequest().getHeaders().get("if-match"))),
            entry(CAN_POST_TO_MISSING,    TRUE),
            entry(CAN_PUT_TO_MISSING,     TRUE),
            entry(MOVED_PERMANENTLY,      FALSE),
            entry(MOVED_TEMPORARILY,      FALSE),
            entry(POST_ENACTED,           TRUE),
            entry(PUT_ENACTED,            TRUE),
            entry(PATCH_ENACTED,          TRUE),
            entry(DELETE_ENACTED,         TRUE),
            entry(PROCESSABLE,            TRUE),

            // Handlers
            entry(HANDLE_OK,              context -> "OK"),
            entry(POST,                   TRUE),
            entry(PUT,                    TRUE),
            entry(DELETE,                 TRUE),
            entry(PATCH,                  TRUE)
        );

    @Override
    public Function<RestContext, ?> getFunction(DecisionPoint point) {
        return defaultFunctions.get(point);
    }


    protected Map<DecisionPoint, Function<RestContext, ?>> getDefaultFunctions() {
        return defaultFunctions;
    }
}
