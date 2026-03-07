package kotowari.restful.example.resource;

import enkan.component.jackson.JacksonBeansConverter;
import enkan.data.DefaultHttpRequest;
import enkan.data.HttpRequest;
import enkan.system.inject.ComponentInjector;
import enkan.util.MixinUtils;
import kotowari.data.BodyDeserializable;
import kotowari.inject.ParameterInjector;
import kotowari.inject.parameter.ParametersInjector;
import kotowari.restful.data.ClassResource;
import kotowari.restful.data.DefaultResource;
import kotowari.restful.data.Problem;
import kotowari.restful.data.RestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static kotowari.restful.DecisionPoint.MALFORMED;
import static org.assertj.core.api.Assertions.assertThat;

class CustomerResourceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DefaultResource parent;
    private ClassResource classResource;

    @BeforeEach
    void setup() {
        parent = new DefaultResource();
        classResource = new ClassResource(
                CustomersResource.class,
                parent,
                new ComponentInjector(Map.of()),
                List.of(new ParametersInjector()),
                new JacksonBeansConverter());
    }

    private RestContext contextWithJsonBody(Object body) {
        DefaultHttpRequest raw = new DefaultHttpRequest();
        raw.setRequestMethod("POST");
        HttpRequest req = MixinUtils.mixin(raw, BodyDeserializable.class);
        JsonNode node = MAPPER.valueToTree(body);
        ((BodyDeserializable) req).setDeserializedBody(node);
        return new RestContext(parent, req);
    }

    private Map<String, Object> validCustomerBody() {
        return Map.of(
                "name", Map.of(
                        "firstName", "Taro",
                        "lastName", "Yamada"
                ),
                "primaryContactMethod", Map.of(
                        "type", "email",
                        "label", "work",
                        "emailAddress", "taro@example.com"
                ),
                "secondaryContactMethods", List.of(
                        Map.of(
                                "type", "postalAddress",
                                "label", "home",
                                "address1", "1-2-3 Shibuya",
                                "city", "Tokyo",
                                "state", "Tokyo",
                                "zipCode", "150-0001"
                        )
                )
        );
    }

    @Test
    void validRequestIsNotMalformed() {
        RestContext context = contextWithJsonBody(validCustomerBody());

        Object result = classResource.getFunction(MALFORMED).apply(context);

        assertThat(result).isNull();
    }

    @Test
    void invalidEmailReturnsProblem() {
        RestContext context = contextWithJsonBody(Map.of(
                "name", Map.of("firstName", "Taro", "lastName", "Yamada"),
                "primaryContactMethod", Map.of(
                        "type", "email",
                        "label", "work",
                        "emailAddress", "not-an-email"
                ),
                "secondaryContactMethods", List.of()
        ));

        Object result = classResource.getFunction(MALFORMED).apply(context);

        assertThat(result).isInstanceOf(Problem.class);
        Problem problem = (Problem) result;
        assertThat(problem.getViolations()).isNotEmpty();
        assertThat(problem.getViolations().getFirst().field()).contains("emailAddress");
    }

    @Test
    void missingLastNameReturnsProblem() {
        RestContext context = contextWithJsonBody(Map.of(
                "name", Map.of("firstName", "Taro"),
                "primaryContactMethod", Map.of(
                        "type", "email",
                        "label", "work",
                        "emailAddress", "taro@example.com"
                ),
                "secondaryContactMethods", List.of()
        ));

        Object result = classResource.getFunction(MALFORMED).apply(context);

        assertThat(result).isInstanceOf(Problem.class);
        Problem problem = (Problem) result;
        assertThat(problem.getViolations()).isNotEmpty();
        assertThat(problem.getViolations().getFirst().field()).contains("lastName");
    }

    @Test
    void unknownContactTypeReturnsProblem() {
        RestContext context = contextWithJsonBody(Map.of(
                "name", Map.of("firstName", "Taro", "lastName", "Yamada"),
                "primaryContactMethod", Map.of(
                        "type", "fax",
                        "label", "office"
                ),
                "secondaryContactMethods", List.of()
        ));

        Object result = classResource.getFunction(MALFORMED).apply(context);

        assertThat(result).isInstanceOf(Problem.class);
    }

    @Test
    void multipleViolationsAccumulated() {
        RestContext context = contextWithJsonBody(Map.of(
                "name", Map.of("firstName", "", "lastName", ""),
                "primaryContactMethod", Map.of(
                        "type", "email",
                        "label", "",
                        "emailAddress", "bad"
                ),
                "secondaryContactMethods", List.of()
        ));

        Object result = classResource.getFunction(MALFORMED).apply(context);

        assertThat(result).isInstanceOf(Problem.class);
        Problem problem = (Problem) result;
        assertThat(problem.getViolations()).hasSizeGreaterThanOrEqualTo(2);
    }
}
