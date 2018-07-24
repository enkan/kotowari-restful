package kotowari.restful.data;

import java.io.Serializable;

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
