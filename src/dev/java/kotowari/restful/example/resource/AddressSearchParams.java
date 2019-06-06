package kotowari.restful.example.resource;

import java.io.Serializable;

public class AddressSearchParams implements Serializable {
    private int limit;
    private int offset;
    private String q;

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }
}
