package kotowari.restful.example.resource;

import java.io.Serializable;

public class AddressSearchParams implements Serializable {
    private int limit = 20;
    private int offset = 0;
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

    public String getQ() {
        return q;
    }

    public void setQ(String q) {
        this.q = q;
    }
}
