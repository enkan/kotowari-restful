package kotowari.restful.resource;

import java.lang.annotation.*;

/**
 * Controls whether a resource class allows the given a HTTP method.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AllowedMethods {
    String[] value() default {"GET", "HEAD"};
}
