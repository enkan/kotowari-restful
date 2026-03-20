package kotowari.restful.decision;

import enkan.data.DefaultHttpRequest;
import enkan.data.HttpRequest;
import kotowari.restful.data.Resource;
import kotowari.restful.data.RestContext;
import org.junit.jupiter.api.Test;

import static kotowari.restful.DecisionPoint.*;
import static org.assertj.core.api.Assertions.assertThat;

class DecisionTest {
    @Test
    void test() {
        Resource parent = point -> context -> true;
        HttpRequest request = new DefaultHttpRequest();
        RestContext context = new RestContext(parent, request);
        final Handler gone     = DecisionFactory.handler(HANDLE_GONE, 410, "");
        final Handler notFound = DecisionFactory.handler(HANDLE_NOT_FOUND, 404, "");

        final Decision decision = DecisionFactory.decision(IF_MATCH_STAR, gone, notFound);
        assertThat(decision.execute(context))
                .isEqualTo(gone);
    }
}