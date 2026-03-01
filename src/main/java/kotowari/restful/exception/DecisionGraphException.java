package kotowari.restful.exception;

/**
 * Thrown when a decision point has no test function registered.
 */
public class DecisionGraphException extends IllegalStateException {
    public DecisionGraphException(String decisionPoint) {
        super("No test function registered for decision point: " + decisionPoint);
    }
}
