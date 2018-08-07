package kotowari.restful.example;


import enkan.collection.OptionMap;
import enkan.component.ApplicationComponent;
import enkan.component.doma2.DomaProvider;
import enkan.component.eclipselink.EclipseLinkEntityManagerProvider;
import enkan.component.flyway.FlywayMigration;
import enkan.component.hikaricp.HikariCPComponent;
import enkan.component.jackson.JacksonBeansConverter;
import enkan.component.undertow.UndertowComponent;
import enkan.config.EnkanSystemFactory;
import enkan.system.EnkanSystem;
import kotowari.restful.component.BeanValidator;
import kotowari.restful.example.entity.Address;
import org.seasar.doma.jdbc.Naming;
import org.seasar.doma.jdbc.dialect.H2Dialect;

import static enkan.component.ComponentRelationship.*;
import static enkan.util.BeanBuilder.*;

public class ExampleSystemFactory implements EnkanSystemFactory {
    @Override
    public EnkanSystem create() {
        return EnkanSystem.of(
                "jpa", builder(new EclipseLinkEntityManagerProvider())
                        .set(EclipseLinkEntityManagerProvider::registerClass, Address.class)
                        .build(),
                "validator", new BeanValidator(),
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
