package kotowari.restful.example.resource;

public class AddressSearchParams {
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
