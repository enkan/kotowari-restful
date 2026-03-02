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
import org.eclipse.persistence.config.PersistenceUnitProperties;

import java.util.HashMap;
import java.util.Map;

import static enkan.component.ComponentRelationship.component;
import static enkan.util.BeanBuilder.builder;

/**
 * Wires all infrastructure components together into an EnkanSystem.
 *
 * <p>Component dependency graph:
 * <pre>
 *   datasource ← flyway   (runs Flyway migrations on startup)
 *   datasource ← jpa      (EclipseLink JPA backed by HikariCP)
 *   jpa, beans, validator, flyway ← app   (the web application)
 *   app ← http            (Undertow HTTP server on port 3000)
 * </pre>
 *
 * <p>The in-memory H2 database is seeded by {@code V1__CreateAddress} via Flyway,
 * so the app is ready to accept requests immediately after startup with no
 * external database required.
 */
public class ExampleSystemFactory implements EnkanSystemFactory {
    @Override
    public EnkanSystem create() {
        return EnkanSystem.of(
                // JPA provider backed by EclipseLink; registers Address as a managed entity.
                // TARGET_DATABASE=H2 is required so EclipseLink uses the correct sequence SQL for H2 2.x.
                "jpa", builder(new EclipseLinkEntityManagerProvider())
                        .set(EclipseLinkEntityManagerProvider::registerClass, Address.class)
                        .set(EclipseLinkEntityManagerProvider::setJpaProperties,
                                new HashMap<>(Map.of(PersistenceUnitProperties.TARGET_DATABASE,
                                        "org.eclipse.persistence.platform.database.H2Platform")))
                        .build(),
                // Bean validator powered by Hibernate Validator (jakarta.validation)
                "validator", new BeansValidator(),
                // In-memory H2 connection pool — no external database needed
                "datasource", builder(new HikariCPComponent(OptionMap.of(
                            "uri", "jdbc:h2:mem:test"
                    )))
                            .build(),
                // Flyway runs Java-based migrations in db.migration on startup
                "flyway", new FlywayMigration(),
                // Jackson-based BeansConverter: serializes/deserializes JSON and copies bean properties
                "beans", new JacksonBeansConverter(),
                "app", new ApplicationComponent("kotowari.restful.example.ExampleApplicationFactory"),
                // Undertow embedded HTTP server
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
