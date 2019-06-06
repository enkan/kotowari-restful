package kotowari.restful.component;

import org.hibernate.validator.constraints.Length;
import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolation;
import java.io.Serializable;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class BeansValidatorTest {
    private static class TestBean implements Serializable {
        @Length(max = 3)
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
    @Test
    void validate() {
        BeansValidator validator = new BeansValidator();
        validator.lifecycle().start(validator);
        try {
            final TestBean testBean = new TestBean();
            testBean.setName("1234");
            final Set<ConstraintViolation<TestBean>> violations = validator.validate(testBean);
            assertThat(violations).isNotEmpty();
        } finally {
            validator.lifecycle().stop(validator);
        }
    }
}