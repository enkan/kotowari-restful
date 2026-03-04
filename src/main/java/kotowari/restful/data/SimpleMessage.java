package kotowari.restful.data;

import java.io.Serializable;

/**
 * A simple text message used as the default response body by handler nodes
 * when no resource function provides a richer object.
 *
 * <p>Serialized to JSON as {@code {"message": "..."}}.
 *
 * @author kawasima
 */
public class SimpleMessage implements Serializable {
    private String message;

    public SimpleMessage(String message) {
        this.message = message;
    }
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return message;
    }
}
