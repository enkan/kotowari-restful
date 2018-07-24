package kotowari.restful;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Decision {
    DecisionPoint value();
    String[] method() default {};
}
