package kotowari.restful.resource;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AllowedMethods {
    String[] value() default {"GET", "HEAD"};
}
