package kotowari.restful.data;

import kotowari.restful.DecisionPoint;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static enkan.util.ThreadingUtils.some;
import static java.util.Map.entry;
import static kotowari.restful.DecisionPoint.*;

/**
 * A default resource.
 *
 * @author kawasima
 */
public class DefaultResource implements Resource {
    private static Function<RestContext, ?> TRUE = (context) -> true;
    private static Function<RestContext, ?> FALSE = (context) -> false;
    private Function<RestContext, ?> testRequestMethod(String... methods) {
        Set<String> methodSet = Arrays.stream(methods)
                .map(m -> m.toUpperCase(Locale.US))
                .collect(Collectors.toSet());
        return context -> {
            String method = some(context.getRequest().getRequestMethod(), m -> m.toUpperCase(Locale.US))
                    .orElse("");
            return methodSet.contains(method);
        };
    }

    private Map<DecisionPoint, Function<RestContext, ?>> defaultFunctions = Map.ofEntries(
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
