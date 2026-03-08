package kotowari.restful.devel;

import enkan.Endpoint;
import enkan.data.HttpRequest;
import enkan.data.HttpResponse;
import kotowari.restful.trace.RequestTrace;
import kotowari.restful.trace.TraceStore;

import java.util.Optional;

import static enkan.util.HttpResponseUtils.contentType;
import static enkan.util.HttpResponseUtils.response;

/**
 * An enkan {@link Endpoint} that serves the decision graph trace list and per-request
 * trace viewer.
 *
 * <p>Mount this endpoint with a path predicate during development:
 *
 * <pre>{@code
 * app.use(GET("/_dev/trace.*").and(envIn("development")), "traceViewer",
 *         new TraceViewerEndpoint(resourceInvoker.getTraceStore()));
 * }</pre>
 *
 * <ul>
 *   <li>{@code /_dev/trace} — shows a list of all recorded traces with links</li>
 *   <li>{@code /_dev/trace/<id>} — shows the decision graph coloured for that trace</li>
 * </ul>
 */
public class TraceViewerEndpoint implements Endpoint<HttpRequest, HttpResponse> {
    private static final String LIST_PATH = "/_dev/trace";
    private static final String DETAIL_PREFIX = "/_dev/trace/";

    private final TraceStore traceStore;
    private final DecisionGraphRenderer renderer = new DecisionGraphRenderer();

    /**
     * Creates a new endpoint backed by the given trace store.
     *
     * @param traceStore the store populated by {@link kotowari.restful.ResourceEngine}
     *                   when tracing is enabled
     */
    public TraceViewerEndpoint(TraceStore traceStore) {
        this.traceStore = traceStore;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        String uri = request.getUri();

        if (uri.startsWith(DETAIL_PREFIX)) {
            return handleDetail(uri.substring(DETAIL_PREFIX.length()));
        }
        return handleList();
    }

    private HttpResponse handleList() {
        HttpResponse res = response(renderer.renderList(traceStore.entries()));
        return contentType(res, "text/html; charset=UTF-8");
    }

    private HttpResponse handleDetail(String id) {
        if (id.isEmpty()) {
            return handleList();
        }
        Optional<RequestTrace> trace = traceStore.get(id);
        if (trace.isEmpty()) {
            return contentType(response("Trace not found: " + DecisionGraphRenderer.escapeHtml(id)), "text/html; charset=UTF-8");
        }
        return contentType(response(renderer.render(id, trace.get())), "text/html; charset=UTF-8");
    }
}
