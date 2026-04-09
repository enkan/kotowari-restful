package kotowari.restful.example.resource;

import kotowari.restful.example.data.Address;
import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Result;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AddressJsonDecodersTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode tree(Object body) {
        return MAPPER.valueToTree(body);
    }

    private static Map<String, Object> validBody() {
        Map<String, Object> m = new HashMap<>();
        m.put("careOf", "c/o Yamada");
        m.put("street", "1-2-3 Shibuya");
        m.put("additional", "Apt 101");
        m.put("city", "Tokyo");
        m.put("zip", "150-0001");
        m.put("countryCode", "JP");
        return m;
    }

    @Test
    void decodesValidBody() {
        Result<Address> result = AddressJsonDecoders.ADDRESS.decode(tree(validBody()));

        assertThat(result).isInstanceOf(Ok.class);
        Address address = ((Ok<Address>) result).value();
        assertThat(address.id()).isNull();
        assertThat(address.careOf()).isEqualTo("c/o Yamada");
        assertThat(address.street()).isEqualTo("1-2-3 Shibuya");
        assertThat(address.additional()).isEqualTo("Apt 101");
        assertThat(address.city()).isEqualTo("Tokyo");
        assertThat(address.zip()).isEqualTo("150-0001");
        assertThat(address.countryCode()).isEqualTo("JP");
    }

    @Test
    void missingStreetIsRejected() {
        Map<String, Object> body = validBody();
        body.remove("street");

        Result<Address> result = AddressJsonDecoders.ADDRESS.decode(tree(body));

        assertThat(result).isInstanceOf(Err.class);
        Err<Address> err = (Err<Address>) result;
        assertThat(err.issues().asList())
                .anyMatch(issue -> issue.path().toString().contains("street"));
    }

    @Test
    void blankCityIsRejected() {
        Map<String, Object> body = validBody();
        body.put("city", "   ");

        Result<Address> result = AddressJsonDecoders.ADDRESS.decode(tree(body));

        assertThat(result).isInstanceOf(Err.class);
        Err<Address> err = (Err<Address>) result;
        assertThat(err.issues().asList())
                .anyMatch(issue -> issue.path().toString().contains("city"));
    }

    @Test
    void countryCodeLongerThanTwoIsRejected() {
        Map<String, Object> body = validBody();
        body.put("countryCode", "JPN");

        Result<Address> result = AddressJsonDecoders.ADDRESS.decode(tree(body));

        assertThat(result).isInstanceOf(Err.class);
        Err<Address> err = (Err<Address>) result;
        assertThat(err.issues().asList())
                .anyMatch(issue -> issue.path().toString().contains("countryCode"));
    }

    @Test
    void countryCodeShorterThanTwoIsRejected() {
        Map<String, Object> body = validBody();
        body.put("countryCode", "J");

        Result<Address> result = AddressJsonDecoders.ADDRESS.decode(tree(body));

        assertThat(result).isInstanceOf(Err.class);
    }

    @Test
    void optionalFieldsMayBeOmitted() {
        Map<String, Object> body = new HashMap<>();
        body.put("street", "1-2-3 Shibuya");
        body.put("city", "Tokyo");
        body.put("countryCode", "JP");

        Result<Address> result = AddressJsonDecoders.ADDRESS.decode(tree(body));

        assertThat(result).isInstanceOf(Ok.class);
        Address address = ((Ok<Address>) result).value();
        assertThat(address.careOf()).isNull();
        assertThat(address.additional()).isNull();
        assertThat(address.zip()).isNull();
    }

    @Test
    void multipleViolationsAccumulated() {
        Map<String, Object> body = new HashMap<>();
        body.put("street", "");
        body.put("city", "");
        body.put("countryCode", "JPN");

        Result<Address> result = AddressJsonDecoders.ADDRESS.decode(tree(body));

        assertThat(result).isInstanceOf(Err.class);
        Err<Address> err = (Err<Address>) result;
        assertThat(err.issues().asList()).hasSizeGreaterThanOrEqualTo(2);
    }
}
