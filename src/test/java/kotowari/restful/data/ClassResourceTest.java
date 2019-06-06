package kotowari.restful.data;

import enkan.component.BeansConverter;
import enkan.component.jackson.JacksonBeansConverter;
import enkan.data.DefaultHttpRequest;
import enkan.data.HttpRequest;
import enkan.system.inject.ComponentInjector;
import kotowari.inject.ParameterInjector;
import kotowari.inject.parameter.*;
import kotowari.restful.Decision;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static kotowari.restful.DecisionPoint.HANDLE_OK;
import static org.assertj.core.api.Assertions.assertThat;

class ClassResourceTest {
    public static class TestResource implements Serializable {
        @Decision(HANDLE_OK)
        String ok() {
            return "OK";
        }
    }

    private List<ParameterInjector<?>> parameterInjectors = List.of(
            new HttpRequestInjector(),
            new ParametersInjector(),
            new SessionInjector(),
            new FlashInjector<>(),
            new PrincipalInjector(),
            new ConversationInjector(),
            new ConversationStateInjector(),
            new LocaleInjector()
    );

    @Test
    void test() {
        BeansConverter converter = new JacksonBeansConverter();
        DefaultResource parent = new DefaultResource();
        final ClassResource classResource = new ClassResource(TestResource.class,
                parent,
                new ComponentInjector(Map.of()),
                parameterInjectors,
                converter);
        HttpRequest request = new DefaultHttpRequest();
        RestContext context = new RestContext(parent, request);
        final Function<RestContext, ?> function = classResource.getFunction(HANDLE_OK);
        assertThat(function.apply(context)).isEqualTo("OK");
    }
}