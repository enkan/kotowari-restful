package kotowari.restful.data;

import javax.validation.ConstraintViolation;
import java.io.Serializable;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Problem implements Serializable{
    private URI type;
    private String title;
    private int status;
    private String detail;
    private String instance;
    private List<Violation> violations;

    public Problem(URI type, String title, int status, String detail, String instance) {
        this.type = type;
        this.title = title;
        this.status = status;
        this.detail = detail;
        this.instance = instance;
    }
    public static <T> Problem fromViolations(Set<ConstraintViolation<T>> violations) {
        Problem problem = new Problem(null, "", 400, "", "");
        problem.violations = violations.stream()
                .map(violation -> new Violation(violation))
                .collect(Collectors.toList());
        return problem;
    }

    public static class Violation<T> implements Serializable {
        private String field;
        private String message;
        public Violation(ConstraintViolation<T> constraintViolation) {
            this.field = constraintViolation.getPropertyPath().toString();
            this.message = constraintViolation.getMessage();
        }

        public String getField() {
            return field;
        }

        public String getMessage() {
            return message;
        }
    }
}
