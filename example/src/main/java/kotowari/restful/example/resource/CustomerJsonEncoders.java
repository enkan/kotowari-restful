package kotowari.restful.example.resource;

import kotowari.restful.example.data.*;
import net.unit8.raoh.encode.Encoder;

import java.util.LinkedHashMap;
import java.util.Map;

import static net.unit8.raoh.encode.MapEncoders.*;
import static net.unit8.raoh.encode.ObjectEncoders.*;

/**
 * JSON encoders for the Customer domain types.
 *
 * <p>Mirrors {@link CustomerJsonDecoders} on the output side: domain objects are
 * encoded directly to {@code Map<String, Object>} (which Jackson serializes to JSON)
 * without intermediate response DTOs.
 *
 * <p>All encoders are stateless constants and safe to share across threads.
 */
public final class CustomerJsonEncoders {

    private CustomerJsonEncoders() {}

    // ========================================================================
    // Composite encoders
    // ========================================================================

    private static final Encoder<PersonalName, Map<String, Object>> PERSONAL_NAME = object(
            property("firstName",  PersonalName::firstName,  string().contramap(String50::value)),
            property("middleName", pn -> pn.middleName().map(String50::value).orElse(null), nullable(string())),
            property("lastName",   PersonalName::lastName,   string().contramap(String100::value))
    );

    private static final Encoder<EmailContactInfo, Map<String, Object>> EMAIL_CONTACT_INFO = object(
            property("label",        EmailContactInfo::label,        string().contramap(String50::value)),
            property("emailAddress", EmailContactInfo::emailAddress, string().contramap(EmailAddress::value))
    );

    private static final Encoder<PostalContactInfo, Map<String, Object>> POSTAL_CONTACT_INFO = object(
            property("label",    PostalContactInfo::label,    string().contramap(String50::value)),
            property("address1", PostalContactInfo::address1, string().contramap(String100::value)),
            property("address2", pi -> pi.address2().map(String100::value).orElse(null), nullable(string())),
            property("city",     PostalContactInfo::city,     string().contramap(String50::value)),
            property("state",    PostalContactInfo::state,    string().contramap(String50::value)),
            property("zipCode",  PostalContactInfo::zipCode,  string().contramap(ZipCode::value))
    );

    // ========================================================================
    // ContactMethod encoder (discriminated by type)
    // ========================================================================

    /**
     * Encodes a contact method with its DB id into a {@code Map<String, Object>}.
     *
     * @param id the DB id of the contact method
     * @param cm the contact method to encode
     * @return a map suitable for JSON serialization
     */
    static Map<String, Object> encodeContactMethod(long id, ContactMethod cm) {
        var result = new LinkedHashMap<String, Object>();
        result.put("id", id);
        switch (cm) {
            case ContactMethod.Email e -> {
                result.put("type", "email");
                result.putAll(EMAIL_CONTACT_INFO.encode(e.info()));
            }
            case ContactMethod.PostalAddress p -> {
                result.put("type", "postalAddress");
                result.putAll(POSTAL_CONTACT_INFO.encode(p.info()));
            }
        }
        return result;
    }

    // ========================================================================
    // Top-level Customer encoder
    // ========================================================================

    /**
     * Encodes a {@link CustomerWithIds} and its {@link CustomerId} into a
     * {@code Map<String, Object>} suitable for JSON serialization.
     *
     * @param id  the customer ID
     * @param cwi the customer with contact method IDs
     * @return a map suitable for JSON serialization
     */
    public static Map<String, Object> encodeCustomerResponse(CustomerId id, CustomerWithIds cwi) {
        Customer customer = cwi.customer();
        var result = new LinkedHashMap<String, Object>();
        result.put("id", id.value());
        result.put("name", PERSONAL_NAME.encode(customer.name()));
        result.put("primaryContactMethod", encodeContactMethod(cwi.primaryCmId(), customer.primaryContactMethod()));
        result.put("secondaryContactMethods", cwi.secondaryCmIds().stream()
                .map(e -> encodeContactMethod(e.getKey(), e.getValue()))
                .toList());
        return result;
    }
}
