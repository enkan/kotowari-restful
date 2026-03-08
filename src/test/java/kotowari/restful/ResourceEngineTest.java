package kotowari.restful;

import enkan.collection.Headers;
import enkan.data.DefaultHttpRequest;
import enkan.data.HttpRequest;
import kotowari.restful.data.ApiResponse;
import kotowari.restful.data.DefaultResource;
import kotowari.restful.data.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static enkan.util.BeanBuilder.builder;
import static org.assertj.core.api.Assertions.assertThat;

class ResourceEngineTest {
    private ResourceEngine resourceEngine;

    @BeforeEach
    void setup() {
        resourceEngine = new ResourceEngine();
    }

    @Test
    void http200Ok() {
        DefaultResource resource = new DefaultResource();
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "GET")
                .set(HttpRequest::setContentType, "application/json")
                .set(HttpRequest::setHeaders, Headers.empty())
                .build();

        resourceEngine.run(resource, request);
    }

    @Test
    void httpPost() {
        DefaultResource resource = new DefaultResource();
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "POST")
                .set(HttpRequest::setContentType, "application/json")
                .set(HttpRequest::setHeaders, Headers.empty())
                .build();

        resourceEngine.run(resource, request);
    }

    @Test
    void methodNotAllowedReturnsAllowHeader() {
        // DefaultResource allows GET and HEAD only
        DefaultResource resource = new DefaultResource();
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "DELETE")
                .set(HttpRequest::setContentType, "application/json")
                .set(HttpRequest::setHeaders, Headers.empty())
                .build();

        ApiResponse response = resourceEngine.run(resource, request);

        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeaders().get("Allow")).isNotNull();
        assertThat(response.getHeaders().get("Allow").toString())
                .contains("GET")
                .contains("HEAD");
    }

    @Test
    void optionsReturnsAllowHeader() {
        // Resource that advertises GET, HEAD, POST and allows OPTIONS through METHOD_ALLOWED
        Resource resource = new DefaultResource() {
            @Override
            public Set<String> getAllowedMethods() {
                return Set.of("GET", "HEAD", "POST");
            }

            @Override
            public java.util.function.Function<kotowari.restful.data.RestContext, ?> getFunction(kotowari.restful.DecisionPoint point) {
                if (point == kotowari.restful.DecisionPoint.METHOD_ALLOWED) {
                    return DefaultResource.testRequestMethod("GET", "HEAD", "POST", "OPTIONS");
                }
                return super.getFunction(point);
            }
        };
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "OPTIONS")
                .set(HttpRequest::setContentType, "application/json")
                .set(HttpRequest::setHeaders, Headers.empty())
                .build();

        ApiResponse response = resourceEngine.run(resource, request);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeaders().get("Allow")).isNotNull();
        assertThat(response.getHeaders().get("Allow").toString())
                .contains("GET")
                .contains("HEAD")
                .contains("POST");
    }
}
