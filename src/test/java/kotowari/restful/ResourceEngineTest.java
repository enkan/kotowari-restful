package kotowari.restful;

import enkan.collection.Headers;
import enkan.data.DefaultHttpRequest;
import enkan.data.HttpRequest;
import kotowari.restful.ResourceEngine;
import kotowari.restful.data.DefaultResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static enkan.util.BeanBuilder.builder;

public class ResourceEngineTest {
    ResourceEngine resourceEngine;

    @BeforeEach
    public void setup() {
        resourceEngine = new ResourceEngine();
    }

    @Test
    public void http200Ok() {
        DefaultResource resource = new DefaultResource();
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "GET")
                .set(HttpRequest::setContentType, "application/json")
                .set(HttpRequest::setHeaders, Headers.empty())
                .build();

        resourceEngine.run(resource, request);
    }

    @Test
    public void httpPost() {
        DefaultResource resource = new DefaultResource();
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "POST")
                .set(HttpRequest::setContentType, "application/json")
                .set(HttpRequest::setHeaders, Headers.empty())
                .build();

        resourceEngine.run(resource, request);
    }

}