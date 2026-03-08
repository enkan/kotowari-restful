package kotowari.restful.trace;

import kotowari.restful.DecisionPoint;

/**
 * An immutable record of a single node visited during decision graph traversal.
 *
 * @param point  the decision point corresponding to the visited node
 * @param kind   the node type: {@code "DECISION"}, {@code "ACTION"}, or {@code "HANDLER"}
 * @param result the boolean outcome of the node evaluation, or {@code null} for
 *               action and handler nodes which do not produce a boolean result
 */
public record TraceEntry(DecisionPoint point, String kind, Boolean result) {}
