package kotowari.restful.decision;

import kotowari.restful.DecisionPoint;
import kotowari.restful.data.Problem;
import kotowari.restful.data.RestContext;
import kotowari.restful.data.SimpleMessage;
import kotowari.restful.exception.DecisionGraphException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/**
 * A branching node in the decision graph.
 *
 * <p>Evaluates a test function against the {@link RestContext} and branches to
 * {@code thenNode} (truthy) or {@code elseNode} (falsy). The test function is
 * resolved in priority order:
 * <ol>
 *   <li>The resource-level function registered via {@code @Decision}</li>
 *   <li>The inline test passed at graph construction time</li>
 * </ol>
 *
 * <p>Return value interpretation:
 * <ul>
 *   <li>{@code null} → falsy</li>
 *   <li>{@link Boolean} → used directly</li>
 *   <li>{@link Problem} → truthy, and the Problem is set as the context message</li>
 *   <li>{@link String} → truthy, wrapped in {@link SimpleMessage} as the context message</li>
 *   <li>Any other non-null value → truthy (message unchanged)</li>
 * </ul>
 *
 * @author kawasima
 */
public final class Decision implements Node<Node<?>> {
    private static final Logger LOG = LoggerFactory.getLogger("kotowari.restful.decision");

    private final DecisionPoint point;
    private final Function<RestContext, ?> test;
    private final Node<?> thenNode;
    private final Node<?> elseNode;

    /**
     * @param name     the decision point this node represents
     * @param test     the inline test function, used when the resource has no override (may be {@code null})
     * @param thenNode the node to follow when the test is truthy
     * @param elseNode the node to follow when the test is falsy
     */
    public Decision(DecisionPoint name,
                    Function<RestContext, ?> test,
                    Node<?> thenNode,
                    Node<?> elseNode) {
        this.point = name;
        this.test = test;
        this.thenNode = thenNode;
        this.elseNode = elseNode;
    }

    public Node<?> execute(RestContext context) {
        LOG.debug("{}", point);
        Function<RestContext, ?> ftest = context.getResourceFunction(point);
        if (ftest == null) {
            ftest = test;
        }
        if (ftest == null) throw new DecisionGraphException(point.name());
        Object fres = ftest.apply(context);
        boolean result = switch (fres) {
            case null -> false;
            case Boolean b -> b;
            case Problem p -> { context.setMessage(p); yield true; }
            case String s -> { context.setMessage(new SimpleMessage(s)); yield true; }
            default -> true;
        };
        return result ? thenNode : elseNode;
    }

    public String getName() {
        return this.point.name();
    }
}
