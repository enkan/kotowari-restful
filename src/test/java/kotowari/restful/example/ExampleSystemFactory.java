package kotowari.restful.example;


import enkan.component.ApplicationComponent;
import enkan.component.doma2.DomaProvider;
import enkan.component.hikaricp.HikariCPComponent;
import enkan.component.jackson.JacksonBeansConverter;
import enkan.config.EnkanSystemFactory;
import enkan.system.EnkanSystem;
import org.seasar.doma.jdbc.Naming;
import org.seasar.doma.jdbc.dialect.H2Dialect;

import static enkan.component.ComponentRelationship.*;
import static enkan.util.BeanBuilder.*;

public class ExampleSystemFactory implements EnkanSystemFactory {
    @Override
    public EnkanSystem create() {
        return EnkanSystem.of(
            "doma", builder(new DomaProvider())
                        .set(DomaProvider::setDialect, new H2Dialect())
                        .set(DomaProvider::setNaming, Naming.SNAKE_LOWER_CASE)
                        .build(),
            "datasource", builder(new HikariCPComponent())
                .build(),
            "beans", new JacksonBeansConverter(),
            "app", new ApplicationComponent("kotowari.restful.example.ExampleApplicationFactory")
        ).relationships(
            component("doma").using("datasource"),
            component("app").using("doma", "beans")
        );
    }
}
