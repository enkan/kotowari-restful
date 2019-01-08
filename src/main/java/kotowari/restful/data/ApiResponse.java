package kotowari.restful.data;

import enkan.collection.Headers;
import enkan.data.HasBody;
import enkan.data.HasHeaders;
import enkan.data.HasStatus;

import java.util.Objects;

/**
 * The response object representing for the result of API.
 *
 * @author kawasima
 */
public class ApiResponse implements HasHeaders, HasStatus, HasBody {
    /** The status of the response */
    private int status;

    /** The response headers */
    private Headers headers;

    /** The response body */
    private Object body;

    public ApiResponse() {
        headers = Headers.empty();
        status = 200;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public Headers getHeaders() {
        return headers;
    }

    public void setHeaders(Headers headers) {
        this.headers = headers;
    }

    @Override
    public Object getBody() {
        return body;
    }

    public void setBody(Object body) {
        this.body = body;
    }

    @Override
    public String toString() {
        return "ApiResponse{" +
                "status=" + status +
                ", headers=" + headers +
                ", body=" + Objects.toString(body) +
                '}';
    }
}
