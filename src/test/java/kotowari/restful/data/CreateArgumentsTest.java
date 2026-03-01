package kotowari.restful.data;

import enkan.collection.Parameters;
import enkan.component.BeansConverter;
import enkan.component.jackson.JacksonBeansConverterFactory;
import enkan.data.DefaultHttpRequest;
import enkan.data.HttpRequest;
import enkan.system.inject.ComponentInjector;
import enkan.util.MixinUtils;
import kotowari.data.BodyDeserializable;
import kotowari.inject.ParameterInjector;
import kotowari.inject.parameter.ParametersInjector;
import kotowari.restful.Decision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import static kotowari.restful.DecisionPoint.HANDLE_OK;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ClassResource#createArguments covering all injection paths.
 */
class CreateArgumentsTest {

    // --- Resource classes for each scenario ---

    public static class RestContextArgResource implements Serializable {
        @Decision(HANDLE_OK)
        public RestContext handleOk(RestContext context) {
            return context;
        }
    }

    public static class ContextValueArgResource implements Serializable {
        @Decision(HANDLE_OK)
        public SearchParams handleOk(SearchParams params) {
            return params;
        }
    }

    public static class ParametersArgResource implements Serializable {
        @Decision(HANDLE_OK)
        public Parameters handleOk(Parameters params) {
            return params;
        }
    }

    public static class BodySameTypeArgResource implements Serializable {
        @Decision(HANDLE_OK)
        public Address handleOk(Address address) {
            return address;
        }
    }

    public static class BodyConverterArgResource implements Serializable {
        @Decision(HANDLE_OK)
        public Address handleOk(Address address) {
            return address;
        }
    }

    public static class NullBodyArgResource implements Serializable {
        @Decision(HANDLE_OK)
        public Address handleOk(Address address) {
            return address;
        }
    }

    // --- Supporting data classes ---

    public static class SearchParams {
        private String query;
        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
    }

    public static class Address implements Serializable {
        private String street;
        public String getStreet() { return street; }
        public void setStreet(String street) { this.street = street; }
    }

    // --- Test setup ---

    private final List<ParameterInjector<?>> parameterInjectors = List.of(new ParametersInjector());
    private BeansConverter converter;
    private DefaultResource parent;

    @BeforeEach
    void setup() {
        converter = JacksonBeansConverterFactory.create();
        parent = new DefaultResource();
    }

    private ClassResource classResource(Class<?> resourceClass) {
        return new ClassResource(resourceClass, parent,
                new ComponentInjector(Map.of()), parameterInjectors, converter);
    }

    private HttpRequest baseRequest() {
        DefaultHttpRequest req = new DefaultHttpRequest();
        req.setRequestMethod("GET");
        return MixinUtils.mixin(req, BodyDeserializable.class);
    }

    private HttpRequest requestWithBody(Object body) {
        HttpRequest req = baseRequest();
        ((BodyDeserializable) req).setDeserializedBody(body);
        return req;
    }

    // --- Tests ---

    @Test
    void injectRestContextItself() {
        ClassResource resource = classResource(RestContextArgResource.class);
        HttpRequest req = baseRequest();
        RestContext context = new RestContext(parent, req);

        Object result = resource.getFunction(HANDLE_OK).apply(context);

        assertThat(result).isSameAs(context);
    }

    @Test
    void injectContextStoredValue() {
        ClassResource resource = classResource(ContextValueArgResource.class);
        HttpRequest req = baseRequest();
        RestContext context = new RestContext(parent, req);
        SearchParams params = new SearchParams();
        params.setQuery("Tokyo");
        context.putValue(params);

        Object result = resource.getFunction(HANDLE_OK).apply(context);

        assertThat(result).isSameAs(params);
    }

    @Test
    void injectViaParameterInjector() {
        ClassResource resource = classResource(ParametersArgResource.class);
        DefaultHttpRequest rawReq = new DefaultHttpRequest();
        rawReq.setRequestMethod("GET");
        Parameters params = Parameters.of("city", "Osaka");
        rawReq.setParams(params);
        HttpRequest req = MixinUtils.mixin(rawReq, BodyDeserializable.class);
        RestContext context = new RestContext(parent, req);

        Object result = resource.getFunction(HANDLE_OK).apply(context);

        assertThat(result).isSameAs(params);
    }

    @Test
    void injectDeserializedBodyDirectly() {
        ClassResource resource = classResource(BodySameTypeArgResource.class);
        Address address = new Address();
        address.setStreet("Main St");
        HttpRequest req = requestWithBody(address);
        RestContext context = new RestContext(parent, req);

        Object result = resource.getFunction(HANDLE_OK).apply(context);

        assertThat(result).isSameAs(address);
    }

    @Test
    void injectDeserializedBodyViaConverter() {
        ClassResource resource = classResource(BodyConverterArgResource.class);
        // body is a Map (e.g. parsed JSON), not Address
        Map<String, Object> rawBody = Map.of("street", "Second Ave");
        HttpRequest req = requestWithBody(rawBody);
        RestContext context = new RestContext(parent, req);

        Object result = resource.getFunction(HANDLE_OK).apply(context);

        assertThat(result).isInstanceOf(Address.class);
        assertThat(((Address) result).getStreet()).isEqualTo("Second Ave");
    }

    @Test
    void nullWhenDeserializedBodyIsNull() {
        ClassResource resource = classResource(NullBodyArgResource.class);
        HttpRequest req = baseRequest(); // no body set
        RestContext context = new RestContext(parent, req);

        Object result = resource.getFunction(HANDLE_OK).apply(context);

        assertThat(result).isNull();
    }
}
