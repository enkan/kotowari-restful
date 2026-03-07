package kotowari.restful.example;


import enkan.collection.OptionMap;
import enkan.component.ApplicationComponent;
import enkan.component.flyway.FlywayMigration;
import enkan.component.hikaricp.HikariCPComponent;
import enkan.component.jackson.JacksonBeansConverter;
import enkan.component.jooq.JooqProvider;
import enkan.component.undertow.UndertowComponent;
import enkan.config.EnkanSystemFactory;
import enkan.system.EnkanSystem;
import kotowari.restful.component.BeansValidator;
import org.jooq.SQLDialect;

import static enkan.component.ComponentRelationship.component;
import static enkan.util.BeanBuilder.builder;

/**
 * Wires all infrastructure components together into an EnkanSystem.
 *
 * <p>Component dependency graph:
 * <pre>
 *   datasource ← flyway   (runs Flyway migrations on startup)
 *   datasource ← jooq     (jOOQ DSLContext backed by HikariCP)
 *   jooq, beans, validator, flyway ← app   (the web application)
 *   app ← http            (Undertow HTTP server on port 3000)
 * </pre>
 */
public class ExampleSystemFactory implements EnkanSystemFactory {
    @Override
    public EnkanSystem create() {
        return EnkanSystem.of(
                "jooq", builder(new JooqProvider())
                        .set(JooqProvider::setDialect, SQLDialect.H2)
                        .build(),
                "validator", new BeansValidator(),
                "datasource", builder(new HikariCPComponent(OptionMap.of(
                            "uri", "jdbc:h2:mem:test"
                    )))
                            .build(),
                "flyway", new FlywayMigration(),
                "beans", new JacksonBeansConverter(),
                "app", new ApplicationComponent<>("kotowari.restful.example.ExampleApplicationFactory"),
                "http", builder(new UndertowComponent())
                        .set(UndertowComponent::setPort, 3000)
                        .build()
        ).relationships(
                component("flyway").using("datasource"),
                component("jooq").using("datasource"),
                component("app").using("jooq", "beans", "validator", "flyway"),
                component("http").using("app")
        );
    }
}
