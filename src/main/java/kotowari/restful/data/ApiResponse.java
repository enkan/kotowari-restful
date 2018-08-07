package kotowari.restful.data;

import enkan.collection.Headers;
import enkan.data.HasBody;
import enkan.data.HasHeaders;
import enkan.data.HasStatus;

import java.util.Objects;

public class ApiResponse implements HasHeaders, HasStatus, HasBody {
    private int status;
    private Headers headers;
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
    public String toString() {
        return "ApiResponse{" +
            "status=" + status +
            ", headers=" + headers +
            ", body=" + Objects.toString(body) +
            '}';
    }

    @Override
    public Object getBody() {
        return body;
    }

    public void setBody(Object body) {
        this.body = body;
    }
}
