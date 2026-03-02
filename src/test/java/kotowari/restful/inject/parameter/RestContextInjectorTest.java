package kotowari.restful.inject.parameter;

import enkan.data.DefaultHttpRequest;
import kotowari.restful.data.DefaultResource;
import kotowari.restful.data.RestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RestContextInjectorTest {
    private RestContextInjector sut;

    @BeforeEach
    void setup() {
         sut = new RestContextInjector();
    }
    @Test
    void applicableRestContextItself() {
        RestContext context = new RestContext(new DefaultResource(), new DefaultHttpRequest());
        assertThat(sut.isApplicable(RestContext.class, context))
                .isTrue();
    }

    @Test
    void applicableRestContextParameter() {
        RestContext context = new RestContext(new DefaultResource(), new DefaultHttpRequest());
        assertThat(sut.isApplicable(Foo.class, context))
                .isFalse();
        context.putValue(new Foo("Hello"));
        assertThat(sut.isApplicable(Foo.class, context))
                .isTrue();
        assertThat(sut.getInjectObject(context, Foo.class))
                .extracting(Foo::getMessage)
                .isEqualTo("Hello");
    }

    @Test
    void getInjectObject() {
        RestContext context = new RestContext(new DefaultResource(), new DefaultHttpRequest());
        final Foo foo = new Foo("Hello");
        context.putValue(foo);
        assertThat(sut.getInjectObject(context, Foo.class))
                .isEqualTo(foo);
    }

    private static class Foo {
        private String message;

        public Foo(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}