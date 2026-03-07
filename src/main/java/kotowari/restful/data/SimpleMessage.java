package kotowari.restful.data;

import java.io.Serializable;

/**
 * A simple text message used as the default response body by handler nodes
 * when no resource function provides a richer object.
 *
 * <p>Serialized to JSON as {@code {"message": "..."}}.
 *
 * @param message the message text
 * @author kawasima
 */
public record SimpleMessage(String message) implements Serializable {
    @Override
    public String toString() {
        return message;
    }
}
