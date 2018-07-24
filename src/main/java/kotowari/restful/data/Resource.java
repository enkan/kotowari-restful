package kotowari.restful.data;

import kotowari.restful.DecisionPoint;

import java.util.function.Function;

public interface Resource {
    Function<RestContext, ?> getFunction(DecisionPoint point);
}
