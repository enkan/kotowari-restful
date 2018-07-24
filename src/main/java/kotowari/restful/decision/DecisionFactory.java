package kotowari.restful.decision;

import kotowari.restful.DecisionPoint;
import kotowari.restful.data.RestContext;

import java.util.function.Function;

public class DecisionFactory {
    public static Decision decision(DecisionPoint point,
                                    Function<RestContext, ?> test,
                                    Node<?> thenDecision,
                                    Node<?> elseDecision) {
        return new Decision(point, test, thenDecision, elseDecision);
    }

    public static Decision decision(DecisionPoint point,
                                    Node<?> thenDecision,
                                    Node<?> elseDecision) {
        return decision(point, null, thenDecision, elseDecision);
    }

    public static Decision action(DecisionPoint point, Node<?> next) {
        return decision(point, next, next);
    }

    public static Handler handler(DecisionPoint point, int statusCode, String message) {
        return new Handler(point, statusCode, message);
    }

}
