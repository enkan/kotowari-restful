package kotowari.restful.example.data;

import java.util.Optional;

public record PostalContactInfo(
        String50 label,
        String100 address1,
        Optional<String100> address2,
        String50 city,
        String50 state,
        ZipCode zipCode
) {
}
