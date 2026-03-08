package kotowari.restful.middleware;

import enkan.data.DefaultHttpRequest;
import enkan.data.HttpRequest;
import enkan.exception.MisconfigurationException;
import enkan.system.inject.ComponentInjector;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceInvokerMiddlewareTest {
    @Test
    void test() {
        ComponentInjector injector = new ComponentInjector(Map.of());
        final ResourceInvokerMiddleware<Object> middleware = new ResourceInvokerMiddleware<>(injector);
        HttpRequest request = new DefaultHttpRequest();
        assertThatThrownBy(() -> middleware.handle(request, null))
                .isInstanceOf(MisconfigurationException.class);
    }
}