package kotowari.restful.trace;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mutable container that accumulates {@link TraceEntry} records for a single HTTP request.
 *
 * <p>An instance is created per request when tracing is enabled and stored on the
 * {@link kotowari.restful.data.RestContext}. After the request completes the trace is
 * transferred to the {@link TraceStore} for later retrieval.
 */
public class RequestTrace {
    private final List<TraceEntry> entries = new ArrayList<>();
    private final Instant timestamp = Instant.now();
    private String method;
    private String uri;

    /**
     * Appends a trace entry.
     *
     * @param entry the entry to append
     */
    public void record(TraceEntry entry) {
        entries.add(entry);
    }

    /**
     * Returns an unmodifiable view of all recorded entries in traversal order.
     *
     * @return unmodifiable list of trace entries
     */
    public List<TraceEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public Instant getTimestamp() { return timestamp; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getUri() { return uri; }
    public void setUri(String uri) { this.uri = uri; }
}
