package kotowari.restful.data;

import kotowari.restful.DecisionPoint;

import java.util.Map;
import java.util.function.Function;

import static java.util.Map.*;
import static kotowari.restful.DecisionPoint.*;

public class DefaultResoruce implements Resource {
    private static Function<RestContext, ?> TRUE = (context) -> true;
    private static Function<RestContext, ?> FALSE = (context) -> false;
    private Function<RestContext, ?> testRequestMethod(String... methods) {
        return context -> {
            String method = context.getRequest().getRequestMethod();
            return true;
        };
    }

    private Map<DecisionPoint, Function<RestContext, ?>> defautFunctions = Map.ofEntries(
            entry(INITIALIZE_CONTEXT,     context -> context),
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
        return defautFunctions.get(point);
    }
}
