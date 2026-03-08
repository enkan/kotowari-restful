package kotowari.restful.devel;

import enkan.Endpoint;
import enkan.data.HttpRequest;
import enkan.data.HttpResponse;

import static enkan.util.HttpResponseUtils.contentType;
import static enkan.util.HttpResponseUtils.response;

/**
 * An enkan {@link Endpoint} that serves the decision graph SVG file.
 *
 * <p>Mount this endpoint alongside {@link TraceViewerEndpoint}:
 *
 * <pre>{@code
 * app.use(PathPredicate.GET("^/_dev/trace\\.svg$"),
 *         new TraceSvgEndpoint());
 * }</pre>
 *
 * <p>The SVG is loaded from the classpath ({@code kotowari/restful/trace/decision-graph.svg})
 * bundled inside {@code kotowari-restful-devel}.
 */
public class TraceSvgEndpoint implements Endpoint<HttpRequest, HttpResponse> {
    private final String svg = new DecisionGraphRenderer().readSvg();

    @Override
    public HttpResponse handle(HttpRequest request) {
        return contentType(response(svg), "image/svg+xml; charset=UTF-8");
    }
}
