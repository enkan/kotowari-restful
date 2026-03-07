package kotowari.restful.example.data;

import org.jooq.Field;
import org.jooq.Record;

import static org.jooq.impl.DSL.field;

public record Address(
        Long id,
        String careOf,
        String street,
        String additional,
        String city,
        String zip,
        String countryCode
) {
    public static final Field<Long> ID = field("id", Long.class);
    public static final Field<String> CARE_OF = field("care_of", String.class);
    public static final Field<String> STREET = field("street", String.class);
    public static final Field<String> ADDITIONAL = field("additional", String.class);
    public static final Field<String> CITY = field("city", String.class);
    public static final Field<String> ZIP = field("zip", String.class);
    public static final Field<String> COUNTRY_CODE = field("country_code", String.class);

    public static Address fromRecord(Record rec) {
        return new Address(
                rec.get(ID),
                rec.get(CARE_OF),
                rec.get(STREET),
                rec.get(ADDITIONAL),
                rec.get(CITY),
                rec.get(ZIP),
                rec.get(COUNTRY_CODE)
        );
    }
}
