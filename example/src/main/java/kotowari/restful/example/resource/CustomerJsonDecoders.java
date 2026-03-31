package kotowari.restful.example.resource;

import kotowari.restful.example.data.*;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.json.JsonDecoder;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.regex.Pattern;

import static net.unit8.raoh.json.JsonDecoders.*;

/**
 * JSON decoders for the Customer domain types.
 *
 * <p>Centralizes the JSON ↔ domain boundary so that domain types
 * ({@link Customer}, {@link PersonalName}, etc.) remain free of
 * serialization concerns.
 *
 * <p>All decoders are stateless constants and safe to share across threads.
 *
 * <h2>Decoder structure</h2>
 * <pre>{@code
 * CUSTOMER
 *   ├─ PERSONAL_NAME
 *   │    ├─ firstName  → STRING50
 *   │    ├─ middleName → Optional<STRING50>
 *   │    └─ lastName   → STRING100
 *   ├─ primaryContactMethod → CONTACT_METHOD
 *   │    ├─ "email"         → EMAIL_CONTACT_INFO  → ContactMethod.Email
 *   │    └─ "postalAddress" → POSTAL_CONTACT_INFO  → ContactMethod.PostalAddress
 *   └─ secondaryContactMethods → list(CONTACT_METHOD)
 * }</pre>
 */
public final class CustomerJsonDecoders {

    private CustomerJsonDecoders() {}

    // ========================================================================
    // Primitive value object decoders
    // ========================================================================

    static final JsonDecoder<String50> STRING50 =
            string().trim().nonBlank().maxLength(50).map(String50::new)::decode;

    static final JsonDecoder<String100> STRING100 =
            string().trim().nonBlank().maxLength(100).map(String100::new)::decode;

    static final JsonDecoder<EmailAddress> EMAIL_ADDRESS =
            string().trim().email().map(EmailAddress::new)::decode;

    private static final Pattern ZIP_PATTERN = Pattern.compile("^[0-9]{3}-?[0-9]{4}$");

    static final JsonDecoder<ZipCode> ZIP_CODE =
            string().trim().nonBlank().pattern(ZIP_PATTERN).map(ZipCode::new)::decode;

    static final JsonDecoder<Age> AGE =
            int_().range(0, 150).map(Age::new)::decode;

    // ========================================================================
    // Composite decoders
    // ========================================================================

    static final JsonDecoder<PersonalName> PERSONAL_NAME = combine(
            field("firstName", STRING50),
            optionalField("middleName", STRING50),
            field("lastName", STRING100)
    ).map(PersonalName::new)::decode;

    static final JsonDecoder<EmailContactInfo> EMAIL_CONTACT_INFO = combine(
            field("label", STRING50),
            field("emailAddress", EMAIL_ADDRESS)
    ).map(EmailContactInfo::new)::decode;

    static final JsonDecoder<PostalContactInfo> POSTAL_CONTACT_INFO = combine(
            field("label", STRING50),
            field("address1", STRING100),
            optionalField("address2", STRING100),
            field("city", STRING50),
            field("state", STRING50),
            field("zipCode", ZIP_CODE)
    ).map(PostalContactInfo::new)::decode;

    // ========================================================================
    // Discriminating decoder for ContactMethod
    // ========================================================================

    static final JsonDecoder<ContactMethod> CONTACT_METHOD;
    static {
        Decoder<JsonNode, ? extends ContactMethod> emailDec = EMAIL_CONTACT_INFO.map(ContactMethod.Email::new);
        Decoder<JsonNode, ? extends ContactMethod> postalDec = POSTAL_CONTACT_INFO.map(ContactMethod.PostalAddress::new);
        CONTACT_METHOD = discriminate("type", Map.of(
                "email", emailDec,
                "postalAddress", postalDec
        ))::decode;
    }

    // ========================================================================
    // Top-level Customer decoder
    // ========================================================================

    /** Decodes a JSON object into a {@link Customer}. */
    public static final JsonDecoder<Customer> CUSTOMER = combine(
            field("name", PERSONAL_NAME),
            field("primaryContactMethod", CONTACT_METHOD),
            field("secondaryContactMethods", list(CONTACT_METHOD))
    ).map(Customer::new)::decode;
}
