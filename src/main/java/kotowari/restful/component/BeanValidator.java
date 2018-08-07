package kotowari.restful.component;

import enkan.component.ComponentLifecycle;
import enkan.component.SystemComponent;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.Set;

public class BeanValidator extends SystemComponent<BeanValidator> {
    private ValidatorFactory validatorFactory;
    private Validator validator;

    public <T> Set<ConstraintViolation<T>> validate(T bean) {
         return validator.validate(bean);
    }

    @Override
    protected ComponentLifecycle<BeanValidator> lifecycle() {
        return new ComponentLifecycle<BeanValidator>() {
            @Override
            public void start(BeanValidator component) {
                component.validatorFactory = Validation.buildDefaultValidatorFactory();
                component.validator = component.validatorFactory.getValidator();
            }

            @Override
            public void stop(BeanValidator component) {
                if (component.validatorFactory != null) {
                    component.validatorFactory.close();
                    component.validatorFactory = null;
                }
                component.validator = null;
            }
        };
    }
}
