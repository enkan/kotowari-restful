package kotowari.restful.example.data;

public sealed interface ContactMethod permits ContactMethod.Email, ContactMethod.PostalAddress {

    record Email(EmailContactInfo info) implements ContactMethod {}

    record PostalAddress(PostalContactInfo info) implements ContactMethod {}
}
