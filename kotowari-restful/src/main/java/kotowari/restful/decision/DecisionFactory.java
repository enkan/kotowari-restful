package kotowari.restful.decision;

import kotowari.restful.DecisionPoint;
import kotowari.restful.data.RestContext;

import java.util.function.Function;

/**
 * Static factory methods for constructing decision graph nodes.
 *
 * <p>Intended to be used via {@code import static}:
 * <pre>{@code
 * import static kotowari.restful.decision.DecisionFactory.*;
 *
 * Node<?> exists = decision(EXISTS, ifMatchExists, doesIfMatchStarExistForMissing);
 * Node<?> handleOK = handler(HANDLE_OK, 200, "ok");
 * }</pre>
 *
 * @author kawasima
 */
public class DecisionFactory {

    /**
     * Creates a decision node with an explicit inline test function.
     *
     * @param point        the decision point
     * @param test         the inline test function (may be overridden by a resource-level function)
     * @param thenDecision the node to follow when the test is truthy
     * @param elseDecision the node to follow when the test is falsy
     * @return a new {@link Decision}
     */
    public static Decision decision(DecisionPoint point,
                                    Function<RestContext, ?> test,
                                    Node<?> thenDecision,
                                    Node<?> elseDecision) {
        return new Decision(point, test, thenDecision, elseDecision);
    }

    /**
     * Creates a decision node that relies solely on the resource-level function
     * (or the {@link kotowari.restful.data.DefaultResource} fallback).
     *
     * @param point        the decision point
     * @param thenDecision the node to follow when the test is truthy
     * @param elseDecision the node to follow when the test is falsy
     * @return a new {@link Decision} with no inline test
     */
    public static Decision decision(DecisionPoint point,
                                    Node<?> thenDecision,
                                    Node<?> elseDecision) {
        return decision(point, null, thenDecision, elseDecision);
    }

    /**
     * Creates an action node that proceeds to {@code next} on success, or
     * routes to a default error handler when the resource function returns
     * a {@link kotowari.restful.data.Problem}.
     *
     * <p>Used for {@code POST}, {@code PUT}, {@code PATCH}, {@code DELETE},
     * and {@code INITIALIZE_CONTEXT} actions.
     *
     * @param point the action decision point
     * @param next  the node to follow after the action executes successfully
     * @return a new {@link Action}
     */
    public static Action action(DecisionPoint point, Node<?> next) {
        return new Action(point, next, null);
    }

    /**
     * Creates an action node with an explicit error handler node.
     *
     * <p>When the resource function returns a {@link kotowari.restful.data.Problem},
     * the action sets it as the context message and status, then routes to
     * {@code errorNode} instead of {@code next}.
     *
     * @param point     the action decision point
     * @param next      the node to follow on success
     * @param errorNode the node to follow when the resource function returns a Problem
     * @return a new {@link Action}
     */
    public static Action action(DecisionPoint point, Node<?> next, Node<?> errorNode) {
        return new Action(point, next, errorNode);
    }

    /**
     * Creates a terminal handler node.
     *
     * @param point      the handler decision point (e.g. {@code HANDLE_OK})
     * @param statusCode the default HTTP status code
     * @param message    the default response body text, or {@code null}
     * @return a new {@link Handler}
     */
    public static Handler handler(DecisionPoint point, int statusCode, String message) {
        return new Handler(point, statusCode, message);
    }

}
