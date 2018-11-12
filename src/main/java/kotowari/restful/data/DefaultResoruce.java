package kotowari.restful.data;

import kotowari.restful.DecisionPoint;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static enkan.util.ThreadingUtils.some;
import static java.util.Map.*;
import static kotowari.restful.DecisionPoint.*;

public class DefaultResoruce implements Resource {
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

    private Map<DecisionPoint, Function<RestContext, ?>> defautFunctions = Map.ofEntries(
            entry(INITIALIZE_CONTEXT,     context -> context),
            entry(SERVICE_AVAILABLE,      TRUE),
            entry(KNOWN_METHOD,           testRequestMethod("GET", "HEAD", "OPTIONS", "POST", "PUT", "DELETE", "PATCH")),
            entry(URI_TOO_LONG,           FALSE),
            entry(METHOD_ALLOWED,         testRequestMethod("GET", "HEAD")),
            entry(MALFORMED,              FALSE),
            entry(IS_AUTHORIZED,             TRUE),
            entry(IS_ALLOWED,                TRUE),
            entry(VALID_CONTENT_HEADER,   TRUE),
            entry(KNOWN_CONTENT_TYPE,     TRUE),
            entry(VALID_ENTITY_LENGTH,    TRUE),
            entry(EXISTS,                 TRUE),
            entry(DID_EXIST,                FALSE),
            entry(IS_RESPOND_WITH_ENTITY,    FALSE),
            entry(IS_NEW,                    TRUE),
            entry(DOES_POST_REDIRECT,          FALSE),
            entry(DOES_PUT_TO_DIFFERENT_URL,   FALSE),
            entry(IS_MULTIPLE_REPRESENTATIONS, FALSE),
            entry(DOES_CONFLICT,               FALSE),
            entry(CAN_POST_TO_MISSING,    TRUE),
            entry(CAN_PUT_TO_MISSING,     TRUE),
            entry(IS_MOVED_PERMANENTLY,      FALSE),
            entry(IS_MOVED_TEMPORARILY,      FALSE),
            entry(IS_POST_ENACTED,           TRUE),
            entry(IS_PUT_ENACTED,            TRUE),
            entry(IS_PATCH_ENACTED,          TRUE),
            entry(IS_DELETE_ENACTED,         TRUE),
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
