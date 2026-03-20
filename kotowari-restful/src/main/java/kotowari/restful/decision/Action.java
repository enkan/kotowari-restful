package kotowari.restful.decision;

import kotowari.restful.DecisionPoint;
import kotowari.restful.data.Problem;
import kotowari.restful.data.RestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/**
 * An action node in the decision graph (POST, PUT, PATCH, DELETE).
 *
 * <p>Unlike {@link Decision}, an action always proceeds to the same next node
 * on success — there is no true/false branching. However, if the resource
 * function returns a {@link Problem}, the action short-circuits to an error
 * handler node instead.
 *
 * <p>Return value interpretation:
 * <ul>
 *   <li>{@link Problem} — sets the problem as the context message and its status
 *       code on the context, then routes to {@code errorNode}.</li>
 *   <li>Any other value (including {@code null} and {@code true}/{@code false})
 *       — proceeds to {@code nextNode}. The return value itself is ignored.</li>
 * </ul>
 *
 * <p>This separation from {@link Decision} makes the intent clearer: actions
 * perform side effects and either succeed or fail with a structured error.
 * Resource classes no longer need to manually call
 * {@code context.setMessage()} and {@code context.setStatus()} for error cases.
 *
 * @author kawasima
 */
public final class Action implements Node<Node<?>> {
    private static final Logger LOG = LoggerFactory.getLogger("kotowari.restful.decision");

    private final DecisionPoint point;
    private final Node<?> nextNode;
    private final Node<?> errorNode;

    /**
     * Creates an action node.
     *
     * @param point     the action decision point (e.g. {@code POST}, {@code PUT})
     * @param nextNode  the node to follow on success
     * @param errorNode the node to follow when the resource function returns a {@link Problem}
     */
    public Action(DecisionPoint point, Node<?> nextNode, Node<?> errorNode) {
        this.point = point;
        this.nextNode = nextNode;
        this.errorNode = errorNode;
    }

    @Override
    public Node<?> execute(RestContext context) {
        LOG.debug("{}", point);
        Function<RestContext, ?> func = context.getResourceFunction(point);
        context.recordTrace(point, "ACTION", null);
        if (func == null) {
            return nextNode;
        }
        Object result = func.apply(context);
        if (result instanceof Problem problem && errorNode != null) {
            context.setMessage(problem);
            context.setStatus(problem.getStatus());
            return errorNode;
        }
        return nextNode;
    }

    /**
     * Returns the name of the action point.
     *
     * @return the decision point name
     */
    public String getName() {
        return this.point.name();
    }

    @Override
    public String toString() {
        return "Action{point=" + point + '}';
    }
}
