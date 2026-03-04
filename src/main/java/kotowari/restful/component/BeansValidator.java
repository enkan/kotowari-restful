package kotowari.restful.component;

import enkan.component.ComponentLifecycle;
import enkan.component.SystemComponent;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;

/**
 * Enkan system component that wraps a Jakarta Validation {@link Validator}.
 *
 * <p>The {@link ValidatorFactory} is created on {@code start()} and closed on
 * {@code stop()}. Inject this component into resource classes via {@code @Inject}
 * and use {@link #validate(Object)} inside {@code @Decision(MALFORMED)} methods
 * to validate request bodies. Convert the result to a {@link kotowari.restful.data.Problem}
 * with {@link kotowari.restful.data.Problem#fromViolations(Set)}.
 *
 * @author kawasima
 */
public class BeansValidator extends SystemComponent<BeansValidator> {
    private ValidatorFactory validatorFactory;
    private Validator validator;

    /**
     * Validates the given bean against its Jakarta Validation constraints.
     *
     * @param <T>  the bean type
     * @param bean the object to validate
     * @return the set of constraint violations (empty if valid)
     */
    public <T> Set<ConstraintViolation<T>> validate(T bean) {
         return validator.validate(bean);
    }

    /**
     * {@inheritDoc}
     * @return {@inheritDoc}
     */
    @Override
    protected ComponentLifecycle<BeansValidator> lifecycle() {
        return new ComponentLifecycle<>() {
            @Override
            public void start(BeansValidator component) {
                component.validatorFactory = Validation.buildDefaultValidatorFactory();
                component.validator = component.validatorFactory.getValidator();
            }

            @Override
            public void stop(BeansValidator component) {
                if (component.validatorFactory != null) {
                    component.validatorFactory.close();
                    component.validatorFactory = null;
                }
                component.validator = null;
            }
        };
    }
}
