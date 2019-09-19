package kotowari.restful.data;

import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemTest {
    @Test
    void valueOf() {
        assertThat(Problem.valueOf(404))
                .is(new Condition<>(p -> p.getStatus() == 404, "status is 404"))
                .is(new Condition<>(p -> p.getDetail() == null, "detail is null"));
    }
}