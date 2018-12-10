package kotowari.restful.example;

import enkan.collection.Headers;
import enkan.collection.OptionMap;
import enkan.collection.Parameters;
import enkan.component.ComponentRelationship;
import enkan.component.eclipselink.EclipseLinkEntityManagerProvider;
import enkan.component.flyway.FlywayMigration;
import enkan.component.hikaricp.HikariCPComponent;
import enkan.component.jackson.JacksonBeansConverter;
import enkan.component.jpa.EntityManagerProvider;
import enkan.data.DefaultHttpRequest;
import enkan.data.HttpRequest;
import enkan.data.Routable;
import enkan.data.jpa.EntityManageable;
import enkan.system.EnkanSystem;
import enkan.system.inject.ComponentInjector;
import enkan.util.MixinUtils;
import kotowari.data.BodyDeserializable;
import kotowari.inject.ParameterInjector;
import kotowari.inject.parameter.*;
import kotowari.restful.ResourceEngine;
import kotowari.restful.component.BeansValidator;
import kotowari.restful.data.ApiResponse;
import kotowari.restful.data.ClassResource;
import kotowari.restful.data.DefaultResource;
import kotowari.restful.example.entity.Address;
import kotowari.restful.example.resource.AddressesResource;
import kotowari.util.ParameterUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.persistence.EntityManager;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static enkan.util.BeanBuilder.builder;
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
                "jpa", builder(new EclipseLinkEntityManagerProvider())
                        .set(EclipseLinkEntityManagerProvider::registerClass, Address.class)
                        .build(),
                "flyway", new FlywayMigration(),
                "datasource", new HikariCPComponent(
                        OptionMap.of("uri", "jdbc:h2:mem:test")
                )
        ).relationships(
                ComponentRelationship.component("jpa").using("datasource"),
                ComponentRelationship.component("flyway").using("datasource")
        );
        system.start();
    }

    @Test
    public void http200Ok() {
        ComponentInjector componentInjector = new ComponentInjector(Map.of(
                "beans", system.getComponent("beans"),
                "validator", system.getComponent("validator")));
        List<ParameterInjector<?>> parameterInjectors = List.of(
                new HttpRequestInjector(),
                new ParametersInjector(),
                new SessionInjector(),
                new FlashInjector<>(),
                new PrincipalInjector(),
                new ConversationInjector(),
                new ConversationStateInjector(),
                new LocaleInjector(),
                new EntityManagerInjector()
        );


        EntityManager em = system.getComponent("jpa", EntityManagerProvider.class).createEntityManager();

        ClassResource resource = new ClassResource(AddressesResource.class, new DefaultResource(),
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
        request = MixinUtils.mixin(request, Routable.class, BodyDeserializable.class, EntityManageable.class);
        ((Routable) request).setControllerMethod(method);
        ((EntityManageable) request).setEntityManager(em);
        ApiResponse response = resourceEngine.run(resource, request);

        System.out.println(response);
    }

    @AfterEach
    public void tearDown() {
        if (system != null) {
            system.stop();
        };
    }

}
