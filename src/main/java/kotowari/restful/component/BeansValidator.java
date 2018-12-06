package kotowari.restful.component;

import enkan.component.ComponentLifecycle;
import enkan.component.SystemComponent;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.Set;

public class BeansValidator extends SystemComponent<BeansValidator> {
    private ValidatorFactory validatorFactory;
    private Validator validator;

    public <T> Set<ConstraintViolation<T>> validate(T bean) {
         return validator.validate(bean);
    }

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
