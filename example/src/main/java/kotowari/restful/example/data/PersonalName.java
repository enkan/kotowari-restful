package kotowari.restful.example.data;

import java.util.Optional;

public record PersonalName(String50 firstName, Optional<String50> middleName, String100 lastName) {
}
