package kotowari.restful.decision;

import kotowari.restful.data.RestContext;

/**
 * A node in the decision graph.
 *
 * <p>Each node receives a {@link RestContext} and produces a typed response:
 * <ul>
 *   <li>{@link Decision} returns the next {@code Node} to traverse.</li>
 *   <li>{@link Handler} returns an {@link kotowari.restful.data.ApiResponse} (terminal).</li>
 * </ul>
 *
 * @param <RESPONSE> the type returned by {@link #execute(RestContext)}
 * @author kawasima
 */
public interface Node<RESPONSE> {

    /**
     * Executes this node against the given context.
     *
     * @param context the per-request context
     * @return the result — either the next node or a final response
     */
    RESPONSE execute(RestContext context);
}
