package kotowari.restful.component;

import enkan.component.ComponentLifecycle;
import enkan.component.SystemComponent;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;

/**
 * A component for validating beans.
 *
 * @author kawasima
 */
public class BeansValidator extends SystemComponent<BeansValidator> {
    private ValidatorFactory validatorFactory;
    private Validator validator;

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
