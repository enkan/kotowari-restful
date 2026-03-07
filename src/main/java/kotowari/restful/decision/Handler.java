package kotowari.restful.decision;

import kotowari.restful.DecisionPoint;
import kotowari.restful.data.ApiResponse;
import kotowari.restful.data.RestContext;
import kotowari.restful.data.SimpleMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.function.Function;

/**
 * A terminal node in the decision graph that produces an {@link ApiResponse}.
 *
 * <p>The response body is resolved in priority order:
 * <ol>
 *   <li>The return value of the resource function (if one is registered and it does not return
 *       {@code null}). If the function returns an {@link ApiResponse} directly, it is used as-is.</li>
 *   <li>{@link RestContext#getMessage()} — a message set by a prior decision.</li>
 *   <li>The {@code defaultMessage} passed at construction time.</li>
 * </ol>
 *
 * <p>The HTTP status code comes from {@link RestContext#getStatus()} if set,
 * otherwise from the {@code statusCode} passed at construction time.
 *
 * @author kawasima
 */
public final class Handler implements Node<ApiResponse> {
    private static final Logger LOG = LoggerFactory.getLogger("kotowari.restful.decision");

    private final DecisionPoint point;
    private final int statusCode;
    private final Object defaultMessage;

    /**
     * @param point      the decision point this handler represents
     * @param statusCode the default HTTP status code
     * @param message    the default response body text, or {@code null} for no default body
     */
    public Handler(DecisionPoint point, int statusCode, String message) {
        this.point = point;
        this.statusCode = statusCode;
        this.defaultMessage = message != null ? new SimpleMessage(message) : null;
    }

    @Override
    public ApiResponse execute(RestContext context) {
        LOG.debug("{}", point);
        Object message = defaultMessage;
        Function<RestContext, ?> ftest = context.getResourceFunction(point);
        if (ftest != null) {
            Object fres = ftest.apply(context);
            switch (fres) {
                case ApiResponse r -> { return r; }
                case null -> { /* leave defaultMessage; context message takes priority below */ }
                case String s -> message = new SimpleMessage(s);
                default -> message = fres;
            }
        }

        ApiResponse response = new ApiResponse();
        response.setStatus(context.getStatus().orElse(statusCode));
        response.setBody(context.getMessage().orElse(message));
        Optional.ofNullable(context.getHeaders())
                .ifPresent(headers -> response.getHeaders().putAll(headers));

        return response;
    }

    @Override
    public String toString() {
        return "Handler{" +
                "point=" + point +
                ", statusCode=" + statusCode +
                ", defaultMessage=" + defaultMessage +
                '}';
    }
}
