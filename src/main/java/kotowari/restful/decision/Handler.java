package kotowari.restful.decision;

import kotowari.restful.DecisionPoint;
import kotowari.restful.data.ApiResponse;
import kotowari.restful.data.RestContext;
import kotowari.restful.data.SimpleMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

public class Handler implements Node<ApiResponse> {
    private static final Logger LOG = LoggerFactory.getLogger("kotowari.restful.decision");

    private final DecisionPoint point;
    private final int statusCode;
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
        LOG.info("{}", point.name());
        Function<RestContext, ?> ftest = context.getResourceFunction(point);
        if (ftest != null) {
            Object fres = ftest.apply(context);
            if (fres instanceof ApiResponse) {
                return (ApiResponse) fres;
            } else if (fres == null) {
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

    @Override
    public String toString() {
        return "Handler{" +
                "point=" + point +
                ", statusCode=" + statusCode +
                ", message=" + message +
                '}';
    }
}
