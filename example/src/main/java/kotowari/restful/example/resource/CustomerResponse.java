package kotowari.restful.example.resource;

import kotowari.restful.example.data.*;

import java.util.List;

public record CustomerResponse(
        long id,
        PersonalNameResponse name,
        ContactMethodResponse primaryContactMethod,
        List<ContactMethodResponse> secondaryContactMethods
) {
    static CustomerResponse from(CustomerId id, Customer cmd) {
        return new CustomerResponse(
                id.value(),
                PersonalNameResponse.from(cmd.name()),
                ContactMethodResponse.from(cmd.primaryContactMethod()),
                cmd.secondaryContactMethods().stream()
                        .map(ContactMethodResponse::from)
                        .toList()
        );
    }

    public record PersonalNameResponse(String firstName, String middleName, String lastName) {
        static PersonalNameResponse from(PersonalName name) {
            return new PersonalNameResponse(
                    name.firstName().value(),
                    name.middleName().map(String50::value).orElse(null),
                    name.lastName().value()
            );
        }
    }

    public sealed interface ContactMethodResponse
            permits ContactMethodResponse.EmailResponse, ContactMethodResponse.PostalAddressResponse {

        static ContactMethodResponse from(ContactMethod cm) {
            return switch (cm) {
                case ContactMethod.Email e -> new EmailResponse(
                        e.info().label().value(),
                        e.info().emailAddress().value()
                );
                case ContactMethod.PostalAddress p -> new PostalAddressResponse(
                        p.info().label().value(),
                        p.info().address1().value(),
                        p.info().address2().map(String100::value).orElse(null),
                        p.info().city().value(),
                        p.info().state().value(),
                        p.info().zipCode().value()
                );
            };
        }

        record EmailResponse(String label, String emailAddress) implements ContactMethodResponse {}

        record PostalAddressResponse(
                String label,
                String address1,
                String address2,
                String city,
                String state,
                String zipCode
        ) implements ContactMethodResponse {}
    }
}
