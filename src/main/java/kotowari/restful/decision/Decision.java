package kotowari.restful.decision;

import kotowari.restful.DecisionPoint;
import kotowari.restful.data.Problem;
import kotowari.restful.data.RestContext;
import kotowari.restful.data.SimpleMessage;
import kotowari.restful.exception.DecisionGraphException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

public class Decision implements Node<Node<?>> {
    private static final Logger LOG = LoggerFactory.getLogger("kotowari.restful.decision");

    private final DecisionPoint point;
    private final Function<RestContext, ?> test;
    private final Node<?> thenNode;
    private final Node<?> elseNode;

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
        LOG.debug("{}", point.name());
        Function<RestContext, ?> ftest = context.getResourceFunction(point);
        if (ftest == null) {
            ftest = test;
        }
        if (ftest == null) throw new DecisionGraphException(point.name());
        Object fres = ftest.apply(context);
        boolean result;
        if (fres == null) {
            result = false;
        } else if (fres instanceof Boolean) {
            result = (Boolean) fres;
        } else if (fres instanceof Problem) {
            context.setMessage(fres);
            result = true;
        } else if (fres instanceof String) {
            context.setMessage(new SimpleMessage((String) fres));
            result = true;
        } else {
            result = true;
        }
        return result ? thenNode : elseNode;
    }

    public String getName() {
        return this.point.name();
    }
}
