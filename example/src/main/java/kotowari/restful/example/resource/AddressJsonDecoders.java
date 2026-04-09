package kotowari.restful.example.resource;

import kotowari.restful.example.data.Address;
import net.unit8.raoh.json.JsonDecoder;

import static net.unit8.raoh.json.JsonDecoders.*;

/**
 * JSON decoders for the {@link Address} domain type.
 *
 * <p>Centralizes the JSON → {@link Address} boundary so validation rules
 * (required fields, max lengths, ISO country code format) are declared
 * once and reused by both the collection and single-item resources.
 */
public final class AddressJsonDecoders {

    private AddressJsonDecoders() {}

    private static final JsonDecoder<String> REQUIRED_STRING =
            string().trim().nonBlank()::decode;

    private static final JsonDecoder<String> OPTIONAL_STRING =
            string().trim()::decode;

    private static final JsonDecoder<String> COUNTRY_CODE =
            string().trim().minLength(2).maxLength(2)::decode;

    private static Address build(java.util.Optional<String> careOf,
                                 String street,
                                 java.util.Optional<String> additional,
                                 String city,
                                 java.util.Optional<String> zip,
                                 String countryCode) {
        return new Address(null,
                careOf.orElse(null),
                street,
                additional.orElse(null),
                city,
                zip.orElse(null),
                countryCode);
    }

    /** Decodes a JSON object into an {@link Address}. The {@code id} field is always {@code null}. */
    public static final JsonDecoder<Address> ADDRESS = combine(
            optionalField("careOf", OPTIONAL_STRING),
            field("street", REQUIRED_STRING),
            optionalField("additional", OPTIONAL_STRING),
            field("city", REQUIRED_STRING),
            optionalField("zip", OPTIONAL_STRING),
            field("countryCode", COUNTRY_CODE)
    ).map(AddressJsonDecoders::build)::decode;
}
