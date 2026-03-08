package kotowari.restful;

import enkan.collection.Headers;
import enkan.data.DefaultHttpRequest;
import enkan.data.HttpRequest;
import kotowari.restful.data.ApiResponse;
import kotowari.restful.data.DefaultResource;
import kotowari.restful.trace.RequestTrace;
import kotowari.restful.trace.TraceEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static enkan.util.BeanBuilder.builder;
import static org.assertj.core.api.Assertions.assertThat;

class ResourceEngineTraceTest {
    private ResourceEngine resourceEngine;

    @BeforeEach
    void setup() {
        resourceEngine = new ResourceEngine();
        resourceEngine.setTracingEnabled(true);
    }

    @Test
    void traceRecordsVisitedNodes() {
        DefaultResource resource = new DefaultResource();
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "GET")
                .set(HttpRequest::setContentType, "application/json")
                .set(HttpRequest::setHeaders, Headers.empty())
                .build();

        resourceEngine.run(resource, request);

        assertThat(resourceEngine.getTraceStore().entries()).isNotEmpty();
    }

    @Test
    void traceContainsServiceAvailableDecision() {
        DefaultResource resource = new DefaultResource();
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "GET")
                .set(HttpRequest::setContentType, "application/json")
                .set(HttpRequest::setHeaders, Headers.empty())
                .build();

        resourceEngine.run(resource, request);

        Optional<RequestTrace> trace = resourceEngine.getTraceStore().entries().stream()
                .findFirst()
                .map(e -> e.getValue());

        assertThat(trace).isPresent();

        List<TraceEntry> entries = trace.get().getEntries();
        assertThat(entries).isNotEmpty();

        TraceEntry serviceAvailable = entries.stream()
                .filter(e -> e.point() == DecisionPoint.SERVICE_AVAILABLE)
                .findFirst()
                .orElse(null);

        assertThat(serviceAvailable).isNotNull();
        assertThat(serviceAvailable.kind()).isEqualTo("DECISION");
        assertThat(serviceAvailable.result()).isTrue();
    }

    @Test
    void traceContainsHandleOkHandler() {
        DefaultResource resource = new DefaultResource();
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "GET")
                .set(HttpRequest::setContentType, "application/json")
                .set(HttpRequest::setHeaders, Headers.empty())
                .build();

        ApiResponse response = resourceEngine.run(resource, request);
        assertThat(response.getStatus()).isEqualTo(200);

        Optional<RequestTrace> trace = resourceEngine.getTraceStore().entries().stream()
                .findFirst()
                .map(e -> e.getValue());

        assertThat(trace).isPresent();

        TraceEntry handleOk = trace.get().getEntries().stream()
                .filter(e -> e.point() == DecisionPoint.HANDLE_OK)
                .findFirst()
                .orElse(null);

        assertThat(handleOk).isNotNull();
        assertThat(handleOk.kind()).isEqualTo("HANDLER");
        assertThat(handleOk.result()).isNull();
    }

    @Test
    void noTraceStoredWhenTracingDisabled() {
        ResourceEngine engineWithoutTracing = new ResourceEngine();
        DefaultResource resource = new DefaultResource();
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "GET")
                .set(HttpRequest::setContentType, "application/json")
                .set(HttpRequest::setHeaders, Headers.empty())
                .build();

        engineWithoutTracing.run(resource, request);

        assertThat(engineWithoutTracing.getTraceStore().entries()).isEmpty();
    }
}
