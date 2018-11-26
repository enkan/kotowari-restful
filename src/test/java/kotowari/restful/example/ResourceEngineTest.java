package kotowari.restful.example;

import enkan.collection.Headers;
import enkan.data.DefaultHttpRequest;
import enkan.data.HttpRequest;
import kotowari.restful.ResourceEngine;
import kotowari.restful.data.DefaultResouruce;
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
        DefaultResouruce resource = new DefaultResouruce();
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "GET")
                .set(HttpRequest::setContentType, "application/json")
                .set(HttpRequest::setHeaders, Headers.empty())
                .build();

        resourceEngine.run(resource, request);
    }

    @Test
    public void httpPost() {
        DefaultResouruce resource = new DefaultResouruce();
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "POST")
                .set(HttpRequest::setContentType, "application/json")
                .set(HttpRequest::setHeaders, Headers.empty())
                .build();

        resourceEngine.run(resource, request);
    }

}
