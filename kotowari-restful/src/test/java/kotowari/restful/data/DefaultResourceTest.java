package kotowari.restful.data;

import kotowari.restful.DecisionPoint;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link DefaultResource} provides default functions for all
 * overridable {@link DecisionPoint} values, matching Liberator's design
 * where every decision point has a default.
 *
 * <p>Internal decisions (header existence checks, method routing) are excluded
 * because they use fixed inline tests in the graph and are not intended to be
 * overridden by resource classes.
 */
class DefaultResourceTest {

    /**
     * Internal decision points that are driven by request headers or method
     * routing in {@link kotowari.restful.ResourceEngine#createDefaultGraph()}.
     * These have fixed inline tests and are not user-overridable.
     */
    private static final Set<DecisionPoint> INTERNAL_DECISIONS = EnumSet.of(
            DecisionPoint.ACCEPT_CHARSET_EXISTS,
            DecisionPoint.ACCEPT_ENCODING_EXISTS,
            DecisionPoint.ACCEPT_LANGUAGE_EXISTS,
            DecisionPoint.ACCEPT_EXISTS,
            DecisionPoint.IF_MATCH_EXISTS,
            DecisionPoint.DOES_IF_MATCH_STAR_EXIST_FOR_MISSING,
            DecisionPoint.IF_MODIFIED_SINCE_EXISTS,
            DecisionPoint.IF_MODIFIED_SINCE_VALID_DATE,
            DecisionPoint.IF_NONE_MATCH,
            DecisionPoint.IF_NONE_MATCH_EXISTS,
            DecisionPoint.IF_NONE_MATCH_STAR,
            DecisionPoint.IF_UNMODIFIED_SINCE_EXISTS,
            DecisionPoint.IF_UNMODIFIED_SINCE_VALID_DATE,
            DecisionPoint.IS_OPTIONS,
            DecisionPoint.METHOD_DELETE,
            DecisionPoint.METHOD_POST,
            DecisionPoint.METHOD_PUT,
            DecisionPoint.METHOD_PATCH,
            DecisionPoint.POST_TO_GONE,
            DecisionPoint.POST_TO_EXISTING,
            DecisionPoint.POST_TO_MISSING,
            DecisionPoint.PUT_TO_EXISTING
    );

    @Test
    void allOverridableDecisionPointsHaveDefaults() {
        DefaultResource resource = new DefaultResource();

        Set<DecisionPoint> missing = EnumSet.allOf(DecisionPoint.class).stream()
                .filter(dp -> !INTERNAL_DECISIONS.contains(dp))
                .filter(dp -> resource.getFunction(dp) == null)
                .collect(Collectors.toSet());

        assertThat(missing)
                .as("All overridable DecisionPoints should have defaults in DefaultResource")
                .isEmpty();
    }
}
