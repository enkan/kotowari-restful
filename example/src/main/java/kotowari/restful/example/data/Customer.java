package kotowari.restful.example.data;

import java.util.List;

public record Customer(
        PersonalName name,
        ContactMethod primaryContactMethod,
        List<ContactMethod> secondaryContactMethods
) {
}
