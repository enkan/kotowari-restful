package kotowari.restful.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A thread-safe, bounded LRU store for {@link RequestTrace} objects.
 *
 * <p>At most {@value #MAX_SIZE} traces are retained. When the limit is exceeded the
 * oldest entry is evicted automatically. This store is intended for development use only
 * and should not be enabled in production environments.
 */
public class TraceStore {
    private static final int MAX_SIZE = 100;

    private final LinkedHashMap<String, RequestTrace> store = new LinkedHashMap<>();

    /**
     * Stores a trace under the given identifier.
     *
     * @param id    the trace identifier
     * @param trace the request trace
     */
    public synchronized void put(String id, RequestTrace trace) {
        if (store.size() >= MAX_SIZE) {
            store.remove(store.firstEntry().getKey());
        }
        store.put(id, trace);
    }

    /**
     * Retrieves the trace for the given identifier.
     *
     * @param id the trace identifier
     * @return an {@link Optional} containing the trace, or empty if not found
     */
    public synchronized Optional<RequestTrace> get(String id) {
        return Optional.ofNullable(store.get(id));
    }

    /**
     * Returns all stored trace entries in reverse insertion order (newest first).
     *
     * @return list of id-to-trace entries, newest first
     */
    public List<Map.Entry<String, RequestTrace>> entries() {
        List<Map.Entry<String, RequestTrace>> list;
        synchronized (store) {
            list = new ArrayList<>(store.entrySet());
        }
        Collections.reverse(list);
        return list;
    }
}
