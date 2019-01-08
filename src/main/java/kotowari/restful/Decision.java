package kotowari.restful;

import java.lang.annotation.*;

/**
 * Maps a method to a decision processor.
 *
 * @author kawasima
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Decision {
    /** A decision point*/
    DecisionPoint value();

    /** A target method(s) */
    String[] method() default {};
}
