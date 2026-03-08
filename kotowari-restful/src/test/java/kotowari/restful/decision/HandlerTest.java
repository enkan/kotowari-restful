package kotowari.restful.decision;

import enkan.data.DefaultHttpRequest;
import enkan.data.HttpRequest;
import kotowari.restful.data.ApiResponse;
import kotowari.restful.data.Resource;
import kotowari.restful.data.RestContext;
import kotowari.restful.data.SimpleMessage;
import org.junit.jupiter.api.Test;

import static kotowari.restful.DecisionPoint.*;
import static org.assertj.core.api.Assertions.assertThat;

class HandlerTest {
    private RestContext context(Resource resource) {
        HttpRequest request = new DefaultHttpRequest();
        return new RestContext(resource, request);
    }

    @Test
    void defaultMessage() {
        Handler handler = DecisionFactory.handler(HANDLE_OK, 200, "ok");
        Resource resource = point -> null;
        ApiResponse response = handler.execute(context(resource));
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getBody()).isInstanceOf(SimpleMessage.class);
        assertThat(((SimpleMessage) response.getBody()).message()).isEqualTo("ok");
    }

    @Test
    void resourceFunctionOverridesDefault() {
        Handler handler = DecisionFactory.handler(HANDLE_OK, 200, "default");
        Resource resource = point -> point == HANDLE_OK ? ctx -> "custom" : null;
        ApiResponse response = handler.execute(context(resource));
        assertThat(response.getBody()).isInstanceOf(SimpleMessage.class);
        assertThat(((SimpleMessage) response.getBody()).message()).isEqualTo("custom");
    }

    @Test
    void contextMessageTakesPriority() {
        Handler handler = DecisionFactory.handler(HANDLE_OK, 200, "default");
        Resource resource = point -> null;
        RestContext ctx = context(resource);
        ctx.setMessage("from context");
        ApiResponse response = handler.execute(ctx);
        assertThat(response.getBody()).isEqualTo("from context");
    }

    @Test
    void contextStatusOverridesDefault() {
        Handler handler = DecisionFactory.handler(HANDLE_OK, 200, null);
        Resource resource = point -> null;
        RestContext ctx = context(resource);
        ctx.setStatus(201);
        ApiResponse response = handler.execute(ctx);
        assertThat(response.getStatus()).isEqualTo(201);
    }

    @Test
    void resourceFunctionReturningNull_fallsBackToDefault() {
        Handler handler = DecisionFactory.handler(HANDLE_OK, 200, "fallback");
        Resource resource = point -> point == HANDLE_OK ? ctx -> null : null;
        ApiResponse response = handler.execute(context(resource));
        assertThat(response.getBody()).isInstanceOf(SimpleMessage.class);
        assertThat(((SimpleMessage) response.getBody()).message()).isEqualTo("fallback");
    }
}
