package kotowari.restful.decision;

import kotowari.restful.DecisionPoint;
import kotowari.restful.data.RestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

public class Decision implements Node<Node<?>> {
    private static final Logger LOG = LoggerFactory.getLogger("kotowari.restful.decision");

    private DecisionPoint point;
    private Function<RestContext, ?> test;
    private Node<?> thenNode;
    private Node<?> elseNode;

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
        LOG.info("{}", point.name());
        Function<RestContext, ?> ftest = context.getResourceFunction(point);
        if (ftest == null) {
            ftest = test;
        }
        if (ftest == null) throw new NullPointerException(point.name());
        Object fres = ftest.apply(context);
        boolean result = true;
        if (fres == null) {
            result = false;
        } else if (fres instanceof Boolean) {
            result = ((Boolean) fres).booleanValue();
        } else {
            result = true;
        }
        return result ? thenNode : elseNode;
    }

    public String getName() {
        return this.point.name();
    }
}
