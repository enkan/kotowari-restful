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
                ContactMethodResponse.from(0L, cmd.primaryContactMethod()),
                cmd.secondaryContactMethods().stream()
                        .map(cm -> ContactMethodResponse.from(0L, cm))
                        .toList()
        );
    }

    static CustomerResponse from(CustomerId id, CustomerWithIds cwi) {
        return new CustomerResponse(
                id.value(),
                PersonalNameResponse.from(cwi.customer().name()),
                ContactMethodResponse.from(cwi.primaryCmId(), cwi.customer().primaryContactMethod()),
                cwi.secondaryCmIds().stream()
                        .map(e -> ContactMethodResponse.from(e.getKey(), e.getValue()))
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

        static ContactMethodResponse from(long cmId, ContactMethod cm) {
            return switch (cm) {
                case ContactMethod.Email e -> new EmailResponse(
                        cmId,
                        e.info().label().value(),
                        e.info().emailAddress().value()
                );
                case ContactMethod.PostalAddress p -> new PostalAddressResponse(
                        cmId,
                        p.info().label().value(),
                        p.info().address1().value(),
                        p.info().address2().map(String100::value).orElse(null),
                        p.info().city().value(),
                        p.info().state().value(),
                        p.info().zipCode().value()
                );
            };
        }

        record EmailResponse(long id, String label, String emailAddress) implements ContactMethodResponse {}

        record PostalAddressResponse(
                long id,
                String label,
                String address1,
                String address2,
                String city,
                String state,
                String zipCode
        ) implements ContactMethodResponse {}
    }
}
