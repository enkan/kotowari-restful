package kotowari.restful.devel;

import kotowari.restful.trace.RequestTrace;
import kotowari.restful.trace.TraceEntry;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Renders an HTML page that embeds the decision graph SVG and highlights the nodes
 * and edges visited during a specific request trace.
 *
 * <p>The SVG is loaded from the classpath ({@code kotowari/restful/trace/decision-graph.svg})
 * and served from {@code /_dev/trace.svg}. An inline JavaScript array of visited nodes
 * is generated from the {@link RequestTrace} and used by the browser to color the graph.
 *
 * <p>Color scheme:
 * <ul>
 *   <li>Green ({@code hl-true}) — decision node evaluated to {@code true}</li>
 *   <li>Red ({@code hl-false}) — decision node evaluated to {@code false}</li>
 *   <li>Blue ({@code hl-visited}) — action or handler node (no boolean result)</li>
 *   <li>Orange edge ({@code hl-edge}) — traversed edge between consecutive nodes</li>
 * </ul>
 */
public class DecisionGraphRenderer {
    private static final String SVG_PATH = "/kotowari/restful/trace/decision-graph.svg";

    /**
     * Renders the HTML trace viewer page for the given trace.
     *
     * @param traceId the trace identifier (shown in the page title)
     * @param trace   the request trace to visualize
     * @return the HTML page as a string
     */
    static String escapeHtml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public String render(String traceId, RequestTrace trace) {
        List<TraceEntry> entries = trace.getEntries();
        StringBuilder js = buildJsArray(entries);
        return buildHtml(escapeHtml(traceId), js.toString());
    }

    private StringBuilder buildJsArray(List<TraceEntry> entries) {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < entries.size(); i++) {
            TraceEntry e = entries.get(i);
            sb.append("  {point:\"").append(e.point().name()).append("\"")
              .append(",kind:\"").append(e.kind()).append("\"")
              .append(",result:").append(e.result() == null ? "null" : e.result())
              .append("}");
            if (i < entries.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]");
        return sb;
    }

    private String buildHtml(String traceId, String visitedJs) {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="UTF-8">
                  <title>Decision Graph Trace #%1$s</title>
                  <style>
                    body { font-family: sans-serif; margin: 1em; }
                    #graph { width: 100%%; border: 1px solid #ccc; }
                  </style>
                </head>
                <body>
                  <h1>Decision Graph Trace <code>%1$s</code></h1>
                  <object id="graph" type="image/svg+xml" data="/_dev/trace.svg"
                          width="100%%" style="min-height:600px;"></object>
                  <script>
                  const visited = %2$s;
                  function colorGraph() {
                    const svgDoc = document.getElementById('graph').contentDocument;
                    if (!svgDoc) return;
                    const style = svgDoc.createElementNS('http://www.w3.org/2000/svg', 'style');
                    style.textContent = [
                      '.hl-true  polygon, .hl-true  rect  { fill: #90ee90 !important; }',
                      '.hl-false polygon, .hl-false rect  { fill: #ff9999 !important; }',
                      '.hl-visited ellipse                 { fill: #aaddff !important; }',
                      '.hl-edge path, .hl-edge polygon    { stroke: #e67e00 !important; stroke-width: 2px !important; }'
                    ].join(' ');
                    const root = svgDoc.querySelector('svg');
                    root.appendChild(style);
                    const nodeMap = {}, edgeMap = {};
                    svgDoc.querySelectorAll('g.node').forEach(n => {
                      const t = n.querySelector('title');
                      if (t) nodeMap[t.textContent] = n;
                    });
                    svgDoc.querySelectorAll('g.edge').forEach(e => {
                      const t = e.querySelector('title');
                      if (t) edgeMap[t.textContent] = e;
                    });
                    for (let i = 0; i < visited.length; i++) {
                      const {point, result} = visited[i];
                      const node = nodeMap[point];
                      if (node) {
                        if (result === true)       node.classList.add('hl-true');
                        else if (result === false) node.classList.add('hl-false');
                        else                       node.classList.add('hl-visited');
                      }
                      if (i > 0) {
                        const edgeKey = visited[i-1].point + '->' + point;
                        const edge = edgeMap[edgeKey];
                        if (edge) edge.classList.add('hl-edge');
                      }
                    }
                  }
                  const obj = document.getElementById('graph');
                  obj.addEventListener('load', colorGraph);
                  // Retry in case the SVG loads before the listener is registered
                  setTimeout(colorGraph, 500);
                  setTimeout(colorGraph, 1500);
                  </script>
                </body>
                </html>
                """.formatted(traceId, visitedJs);
    }

    /**
     * Renders an HTML page listing all stored traces with links to their detail pages.
     *
     * @param entries the id-to-trace entries from {@link kotowari.restful.trace.TraceStore}
     * @return the HTML page as a string
     */
    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public String renderList(Collection<Map.Entry<String, RequestTrace>> entries) {
        StringBuilder rows = new StringBuilder();
        for (Map.Entry<String, RequestTrace> e : entries) {
            String id = e.getKey();
            RequestTrace t = e.getValue();
            String timestamp = t.getTimestamp() != null ? TIMESTAMP_FMT.format(t.getTimestamp()) : "-";
            String method = t.getMethod() != null ? escapeHtml(t.getMethod()) : "-";
            String uri = t.getUri() != null ? escapeHtml(t.getUri()) : "-";
            String safeId = escapeHtml(id);
            rows.append("<tr>")
                .append("<td>").append(timestamp).append("</td>")
                .append("<td><a href=\"/_dev/trace/").append(safeId).append("\">").append(safeId).append("</a></td>")
                .append("<td>").append(method).append("</td>")
                .append("<td>").append(uri).append("</td>")
                .append("</tr>\n");
        }
        return """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="UTF-8">
                  <title>Decision Graph Traces</title>
                  <style>
                    body { font-family: sans-serif; margin: 1em; }
                    table { border-collapse: collapse; width: 100%%; }
                    th, td { border: 1px solid #ccc; padding: 0.4em 0.8em; text-align: left; }
                    th { background: #f0f0f0; }
                  </style>
                </head>
                <body>
                  <h1>Decision Graph Traces</h1>
                  <table>
                    <thead><tr><th>Time</th><th>ID</th><th>Method</th><th>URI</th></tr></thead>
                    <tbody>%s</tbody>
                  </table>
                </body>
                </html>
                """.formatted(rows);
    }

    /**
     * Reads the decision graph SVG from the classpath.
     *
     * @return the SVG content as a string
     * @throws UncheckedIOException if the SVG resource cannot be read
     */
    public String readSvg() {
        try (InputStream in = getClass().getResourceAsStream(SVG_PATH)) {
            if (in == null) {
                throw new IllegalStateException("SVG resource not found on classpath: " + SVG_PATH);
            }
            return new String(in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
