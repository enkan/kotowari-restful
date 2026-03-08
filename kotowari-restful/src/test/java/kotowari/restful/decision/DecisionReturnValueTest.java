package kotowari.restful.decision;

import enkan.data.DefaultHttpRequest;
import enkan.data.HttpRequest;
import kotowari.restful.data.Problem;
import kotowari.restful.data.Resource;
import kotowari.restful.data.RestContext;
import kotowari.restful.data.SimpleMessage;
import org.junit.jupiter.api.Test;

import static kotowari.restful.DecisionPoint.*;
import static org.assertj.core.api.Assertions.assertThat;

class DecisionReturnValueTest {
    private final Handler thenNode = DecisionFactory.handler(HANDLE_OK, 200, null);
    private final Handler elseNode = DecisionFactory.handler(HANDLE_NOT_FOUND, 404, null);

    private RestContext context(Resource resource) {
        HttpRequest request = new DefaultHttpRequest();
        return new RestContext(resource, request);
    }

    @Test
    void nullReturnValue_isFalsy() {
        Decision d = DecisionFactory.decision(EXISTS, ctx -> null, thenNode, elseNode);
        Resource resource = point -> null;
        assertThat(d.execute(context(resource))).isSameAs(elseNode);
    }

    @Test
    void booleanTrue_isTruthy() {
        Decision d = DecisionFactory.decision(EXISTS, ctx -> true, thenNode, elseNode);
        Resource resource = point -> null;
        assertThat(d.execute(context(resource))).isSameAs(thenNode);
    }

    @Test
    void booleanFalse_isFalsy() {
        Decision d = DecisionFactory.decision(EXISTS, ctx -> false, thenNode, elseNode);
        Resource resource = point -> null;
        assertThat(d.execute(context(resource))).isSameAs(elseNode);
    }

    @Test
    void problemReturn_isTruthyAndSetsMessage() {
        Problem problem = Problem.valueOf(400, "bad");
        Decision d = DecisionFactory.decision(MALFORMED, ctx -> problem, thenNode, elseNode);
        Resource resource = point -> null;
        RestContext ctx = context(resource);
        assertThat(d.execute(ctx)).isSameAs(thenNode);
        assertThat(ctx.getMessage()).hasValue(problem);
    }

    @Test
    void stringReturn_isTruthyAndSetsSimpleMessage() {
        Decision d = DecisionFactory.decision(EXISTS, ctx -> "hello", thenNode, elseNode);
        Resource resource = point -> null;
        RestContext ctx = context(resource);
        assertThat(d.execute(ctx)).isSameAs(thenNode);
        assertThat(ctx.getMessage()).hasValueSatisfying(msg ->
                assertThat(msg).isInstanceOf(SimpleMessage.class));
    }

    @Test
    void otherNonNullReturn_isTruthy() {
        Decision d = DecisionFactory.decision(EXISTS, ctx -> 42, thenNode, elseNode);
        Resource resource = point -> null;
        assertThat(d.execute(context(resource))).isSameAs(thenNode);
    }

    @Test
    void resourceFunctionOverridesInlineTest() {
        Decision d = DecisionFactory.decision(EXISTS, ctx -> false, thenNode, elseNode);
        Resource resource = point -> point == EXISTS ? ctx -> true : null;
        assertThat(d.execute(context(resource))).isSameAs(thenNode);
    }
}
