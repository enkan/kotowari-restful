package kotowari.restful.decision;

import kotowari.restful.DecisionPoint;
import kotowari.restful.data.ApiResponse;
import kotowari.restful.data.RestContext;
import kotowari.restful.data.SimpleMessage;

import java.util.function.Function;

public class Handler implements Node<ApiResponse> {
    private DecisionPoint point;
    private int statusCode;
    private Object message;

    public Handler(DecisionPoint point, int statusCode, String message) {
        this.point = point;
        this.statusCode = statusCode;
        if (message != null) {
            this.message = new SimpleMessage(message);
        }

    }

    @Override
    public ApiResponse execute(RestContext context) {
        Function<RestContext, ?> ftest = context.getResourceFunction(point);
        if (ftest != null) {
            Object fres = ftest.apply(context);
            if (fres == null) {
                message = null;
            } else if (fres instanceof String) {
                message = new SimpleMessage((String) fres);
            } else {
                message = fres;
            }
        }

        ApiResponse response = new ApiResponse();
        response.setStatus(context.getStatus().orElse(statusCode));
        response.setBody(context.getMessage().orElse(message));
        return response;
    }
}
