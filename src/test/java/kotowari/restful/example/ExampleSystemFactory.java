package kotowari.restful.example;


import enkan.collection.OptionMap;
import enkan.component.ApplicationComponent;
import enkan.component.eclipselink.EclipseLinkEntityManagerProvider;
import enkan.component.flyway.FlywayMigration;
import enkan.component.hikaricp.HikariCPComponent;
import enkan.component.jackson.JacksonBeansConverter;
import enkan.component.undertow.UndertowComponent;
import enkan.config.EnkanSystemFactory;
import enkan.system.EnkanSystem;
import kotowari.restful.component.BeansValidator;
import kotowari.restful.example.entity.Address;

import static enkan.component.ComponentRelationship.component;
import static enkan.util.BeanBuilder.builder;

public class ExampleSystemFactory implements EnkanSystemFactory {
    @Override
    public EnkanSystem create() {
        return EnkanSystem.of(
                "jpa", builder(new EclipseLinkEntityManagerProvider())
                        .set(EclipseLinkEntityManagerProvider::registerClass, Address.class)
                        .build(),
                "validator", new BeansValidator(),
                "datasource", builder(new HikariCPComponent(OptionMap.of(
                            "uri", "jdbc:h2:mem:test"
                    )))
                            .build(),
                "flyway", new FlywayMigration(),
                "beans", new JacksonBeansConverter(),
                "app", new ApplicationComponent("kotowari.restful.example.ExampleApplicationFactory"),
                "http", builder(new UndertowComponent())
                        .set(UndertowComponent::setPort, 3000)
                        .build()
        ).relationships(
                component("flyway").using("datasource"),
                component("jpa").using("datasource"),
                component("app").using("jpa", "beans", "validator", "flyway"),
                component("http").using("app")
        );
    }
}
