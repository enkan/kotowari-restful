package kotowari.restful.example;

import enkan.collection.Headers;
import enkan.collection.OptionMap;
import enkan.collection.Parameters;
import enkan.component.ComponentRelationship;
import enkan.component.doma2.DomaProvider;
import enkan.component.flyway.FlywayMigration;
import enkan.component.hikaricp.HikariCPComponent;
import enkan.component.jackson.JacksonBeansConverter;
import enkan.data.DefaultHttpRequest;
import enkan.data.HttpRequest;
import enkan.data.Routable;
import enkan.system.EnkanSystem;
import enkan.system.inject.ComponentInjector;
import enkan.util.MixinUtils;
import kotowari.data.BodyDeserializable;
import kotowari.inject.ParameterInjector;
import kotowari.restful.ResourceEngine;
import kotowari.restful.component.BeansValidator;
import kotowari.restful.data.ApiResponse;
import kotowari.restful.data.ClassResource;
import kotowari.restful.data.DefaultResoruce;
import kotowari.restful.example.resource.AddressesResource;
import kotowari.util.ParameterUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.seasar.doma.jdbc.Naming;
import org.seasar.doma.jdbc.dialect.H2Dialect;

import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.Map;

import static enkan.util.BeanBuilder.*;
import static enkan.util.ReflectionUtils.tryReflection;

public class ClassResourceTest {
    ResourceEngine resourceEngine;
    EnkanSystem system;

    @BeforeEach
    public void setup() {
        resourceEngine = new ResourceEngine();
        system = EnkanSystem.of(
                "beans", new JacksonBeansConverter(),
                "validator", new BeansValidator(),
                "doma", builder(new DomaProvider())
                        .set(DomaProvider::setDialect, new H2Dialect())
                        .set(DomaProvider::setNaming, Naming.SNAKE_UPPER_CASE)
                        .build(),
                "flyway", new FlywayMigration(),
                "datasource", new HikariCPComponent(
                        OptionMap.of("uri", "jdbc:h2:mem:test")
                )
        ).relationships(
                ComponentRelationship.component("doma").using("datasource"),
                ComponentRelationship.component("flyway").using("datasource")
        );
        system.start();
    }

    @Test
    public void http200Ok() {
        ComponentInjector componentInjector = new ComponentInjector(Map.of(
                "beans", system.getComponent("beans"),
                "validator", system.getComponent("validator"),
                "doma", system.getComponent("doma")));
        LinkedList<ParameterInjector<?>> parameterInjectors = ParameterUtils.getDefaultParameterInjectors();
        ClassResource resource = new ClassResource(AddressesResource.class, new DefaultResoruce(),
                componentInjector,
                parameterInjectors,
                system.getComponent("beans"));
        Method method = tryReflection(() -> AddressesResource.class.getMethod("getClass"));
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "GET")
                .set(HttpRequest::setParams, Parameters.of("A", "abc"))
                .set(HttpRequest::setContentType, "application/json")
                .set(HttpRequest::setHeaders, Headers.empty())
                .build();
        request = MixinUtils.mixin(request, Routable.class, BodyDeserializable.class);
        Routable.class.cast(request).setControllerMethod(method);
        ApiResponse response = resourceEngine.run(resource, request);
        System.out.println(response);
    }

    @AfterEach
    public void tearDown() {
        system.stop();
    }

}
