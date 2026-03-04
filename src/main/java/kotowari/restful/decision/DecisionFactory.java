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
     * Creates an action node — a decision that always proceeds to the same
     * next node regardless of the function's return value.
     *
     * <p>Used for {@code POST}, {@code PUT}, {@code PATCH}, and {@code DELETE} actions.
     *
     * @param point the action decision point
     * @param next  the node to follow after the action executes
     * @return a new {@link Decision} where both branches lead to {@code next}
     */
    public static Decision action(DecisionPoint point, Node<?> next) {
        return decision(point, next, next);
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
