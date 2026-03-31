package kotowari.restful.example.dao;

import kotowari.restful.example.data.*;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.Result;
import net.unit8.raoh.jooq.JooqRecordDecoder;
import org.jooq.Record;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static net.unit8.raoh.decode.ObjectDecoders.*;
import static net.unit8.raoh.jooq.JooqRecordDecoders.*;

/**
 * Maps jOOQ {@link Record}s to the {@link Customer} domain object using Raoh decoders.
 *
 * <p>Decoders are composed declaratively so that column extraction, type conversion,
 * and error accumulation are handled in a single pass. Every decoder is defined as a
 * stateless constant and is safe to share across threads.
 *
 * <h2>Decoder structure</h2>
 * <pre>{@code
 * CUSTOMER_DECODER
 *   ├─ PERSONAL_NAME_DECODER        ← builds PersonalName from the customer row
 *   │    ├─ first_name  → String50
 *   │    ├─ middle_name → Optional<String50>  (nullable)
 *   │    └─ last_name   → String100
 *   └─ CONTACT_METHODS_DECODER      ← splits contact_method rows into primary / secondary
 *        └─ TAGGED_CONTACT_DECODER   ← combines CONTACT_METHOD_DECODER + IS_PRIMARY_DECODER
 *             ├─ CONTACT_METHOD_DECODER   ← discriminates on the type column
 *             │    ├─ "email"         → EMAIL_CONTACT_DECODER   → ContactMethod.Email
 *             │    └─ "postalAddress" → POSTAL_CONTACT_DECODER  → ContactMethod.PostalAddress
 *             └─ IS_PRIMARY_DECODER       ← reads is_primary to partition rows
 * }</pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * CustomerMapper mapper = new CustomerMapper();
 * Result<Customer> result = mapper.customer(customerRec, contactMethodRecs);
 * Customer customer = result.getOrThrow();
 * }</pre>
 *
 * @see CustomerRepository
 */
public class CustomerMapper {

    /**
     * Input type for {@link #CUSTOMER_DECODER}.
     *
     * <p>A {@link Customer} spans two tables (customer + contact_method), so this
     * record bundles both sources into a single decoder input.
     *
     * @param customer       a single row from the customer table
     * @param contactMethods zero or more rows from the contact_method table
     */
    public record CustomerRecords(Record customer, List<? extends Record> contactMethods) {}

    // ========================================================================
    // Primitive decoders — convert raw column values to domain value objects
    // ========================================================================

    /** SQL string → {@link String50}. */
    private static final Decoder<Object, String50> STRING50 = string().map(String50::new);

    /** SQL string → {@link String100}. */
    private static final Decoder<Object, String100> STRING100 = string().map(String100::new);

    /** SQL string → {@link EmailAddress}. */
    private static final Decoder<Object, EmailAddress> EMAIL_ADDR = string().map(EmailAddress::new);

    /** SQL string → {@link ZipCode}. */
    private static final Decoder<Object, ZipCode> ZIP = string().map(ZipCode::new);

    // ========================================================================
    // Composite decoders — build domain objects from multiple columns
    // ========================================================================

    /**
     * Builds a {@link PersonalName} from the {@code first_name}, {@code middle_name},
     * and {@code last_name} columns of a customer row.
     *
     * <p>{@code middle_name} accepts SQL NULL and converts it to {@link Optional#empty()}.
     */
    static final JooqRecordDecoder<PersonalName> PERSONAL_NAME_DECODER = combine(
            field("first_name", STRING50),
            field("middle_name", nullable(STRING50)).map(Optional::ofNullable),
            field("last_name", STRING100)
    ).map(PersonalName::new)::decode;

    /**
     * Builds an {@link EmailContactInfo} from the {@code label} and
     * {@code email_address} columns of a contact_method row.
     */
    private static final JooqRecordDecoder<EmailContactInfo> EMAIL_CONTACT_DECODER = combine(
            field("label", STRING50),
            field("email_address", EMAIL_ADDR)
    ).map(EmailContactInfo::new)::decode;

    /**
     * Builds a {@link PostalContactInfo} from the {@code label}, {@code address1}
     * through {@code zip_code} columns of a contact_method row.
     *
     * <p>{@code address2} accepts SQL NULL and converts it to {@link Optional#empty()}.
     */
    private static final JooqRecordDecoder<PostalContactInfo> POSTAL_CONTACT_DECODER = combine(
            field("label", STRING50),
            field("address1", STRING100),
            field("address2", nullable(STRING100)).map(Optional::ofNullable),
            field("city", STRING50),
            field("state", STRING50),
            field("zip_code", ZIP)
    ).map(PostalContactInfo::new)::decode;

    // ========================================================================
    // Discriminating decoder — dispatches on the type column
    // ========================================================================

    /**
     * Lookup table from the {@code type} column value to the corresponding sub-decoder.
     *
     * <p>Each sub-decoder reads only the columns it needs from the same {@link Record}
     * and wraps the result in the appropriate {@link ContactMethod} subtype.
     */
    private static final Map<String, JooqRecordDecoder<? extends ContactMethod>> CONTACT_METHOD_DECODERS = Map.of(
            "email", (in, path) -> EMAIL_CONTACT_DECODER.decode(in, path).map(ContactMethod.Email::new),
            "postalAddress", (in, path) -> POSTAL_CONTACT_DECODER.decode(in, path).map(ContactMethod.PostalAddress::new)
    );

    /**
     * Discriminating decoder that builds a {@link ContactMethod} from a single
     * contact_method row.
     *
     * <p>Reads the {@code type} column ({@code "email"} or {@code "postalAddress"}),
     * selects the matching sub-decoder from {@link #CONTACT_METHOD_DECODERS}, and
     * returns a {@link ContactMethod.Email} or {@link ContactMethod.PostalAddress}.
     * An unknown type value produces a decode error.
     */
    static final JooqRecordDecoder<ContactMethod> CONTACT_METHOD_DECODER = (in, path) -> {
        Result<String> typeResult = field("type", string()).decode(in, path);
        return typeResult.flatMap(type -> {
            JooqRecordDecoder<? extends ContactMethod> dec = CONTACT_METHOD_DECODERS.get(type);
            if (dec == null) {
                return Result.fail(path.append("type"), "unknown_type", "unknown contact method type: " + type);
            }
            return dec.decode(in, path).map(cm -> cm);
        });
    };

    /** Decodes the {@code is_primary} column as a boolean. */
    private static final JooqRecordDecoder<Boolean> IS_PRIMARY_DECODER = field("is_primary", bool());

    // ========================================================================
    // List decoder — partitions contact_method rows into primary / secondary
    // ========================================================================

    /** A decoded contact method paired with its {@code is_primary} flag. */
    private record TaggedContact(ContactMethod cm, boolean isPrimary) {}

    /** A decoded contact method paired with its DB {@code id} and {@code is_primary} flag. */
    private record TaggedContactWithId(long id, ContactMethod cm, boolean isPrimary) {}

    /**
     * Combines {@link #CONTACT_METHOD_DECODER} and {@link #IS_PRIMARY_DECODER}
     * into a single decoder that reads both from the same {@link Record}.
     */
    private static final JooqRecordDecoder<TaggedContact> TAGGED_CONTACT_DECODER =
            combine(CONTACT_METHOD_DECODER, IS_PRIMARY_DECODER).map(TaggedContact::new)::decode;

    /** Decodes the {@code id} column as a long. */
    private static final JooqRecordDecoder<Long> ID_DECODER = field("id", long_());

    /**
     * Combines {@link #ID_DECODER}, {@link #CONTACT_METHOD_DECODER}, and
     * {@link #IS_PRIMARY_DECODER} into a single decoder that reads all three from
     * the same {@link Record}.
     */
    private static final JooqRecordDecoder<TaggedContactWithId> TAGGED_CONTACT_WITH_ID_DECODER =
            combine(ID_DECODER, CONTACT_METHOD_DECODER, IS_PRIMARY_DECODER).map(TaggedContactWithId::new)::decode;

    /**
     * Partitions a list of contact_method rows into one primary and zero or more
     * secondary {@link ContactMethod}s based on the {@code is_primary} column.
     *
     * <p>Uses {@link Result#traverse} to decode all rows via
     * {@link #TAGGED_CONTACT_DECODER}, accumulating errors across all elements.
     * The decoded list is then partitioned by the {@code isPrimary} flag.
     * If no row has {@code is_primary = true}, a {@code missing_primary} error
     * is produced.
     */
    private static final Decoder<List<? extends Record>, ContactMethods> CONTACT_METHODS_DECODER = (recs, path) ->
            Result.traverse(recs, TAGGED_CONTACT_DECODER::decode, path).flatMap(tagged -> {
                var primary = tagged.stream()
                        .filter(TaggedContact::isPrimary)
                        .map(TaggedContact::cm)
                        .findFirst();
                if (primary.isEmpty()) {
                    return Result.fail(path, "missing_primary", "no primary contact method found");
                }
                var secondary = tagged.stream()
                        .filter(t -> !t.isPrimary())
                        .map(TaggedContact::cm)
                        .toList();
                return Result.ok(new ContactMethods(primary.get(), secondary));
            });

    /**
     * Partitions a list of contact_method rows into primary and secondary contact methods,
     * preserving the DB {@code id} of each row.
     */
    private static final Decoder<List<? extends Record>, ContactMethodsWithIds> CONTACT_METHODS_WITH_IDS_DECODER = (recs, path) ->
            Result.traverse(recs, TAGGED_CONTACT_WITH_ID_DECODER::decode, path).flatMap(tagged -> {
                var primary = tagged.stream()
                        .filter(TaggedContactWithId::isPrimary)
                        .findFirst();
                if (primary.isEmpty()) {
                    return Result.fail(path, "missing_primary", "no primary contact method found");
                }
                var secondary = tagged.stream()
                        .filter(t -> !t.isPrimary())
                        .map(t -> Map.entry(t.id(), t.cm()))
                        .toList();
                return Result.ok(new ContactMethodsWithIds(primary.get().id(), primary.get().cm(), secondary));
            });

    /** Holds the result of partitioning contact methods into primary and secondary. */
    private record ContactMethods(ContactMethod primary, List<ContactMethod> secondary) {}

    /** Holds partitioned contact methods with their DB ids. */
    private record ContactMethodsWithIds(long primaryId, ContactMethod primary, List<Map.Entry<Long, ContactMethod>> secondary) {}

    // ========================================================================
    // Top-level decoder — CustomerRecords → Customer
    // ========================================================================

    /**
     * Top-level decoder that builds a {@link Customer} from a {@link CustomerRecords}
     * input (one customer row + N contact_method rows).
     *
     * <p>Decodes the customer row into a {@link PersonalName} via
     * {@link #PERSONAL_NAME_DECODER} and the contact_method rows into primary/secondary
     * {@link ContactMethod}s via {@link #CONTACT_METHODS_DECODER}, then combines both
     * results into a {@link Customer}.
     *
     * <p>Error paths are prefixed with {@code "customer"} or {@code "contactMethods"}
     * to identify the source table.
     */
    static final Decoder<CustomerRecords, Customer> CUSTOMER_DECODER = (in, path) -> {
        Result<PersonalName> nameResult = PERSONAL_NAME_DECODER.decode(in.customer(), path.append("customer"));
        Result<ContactMethods> cmResult = CONTACT_METHODS_DECODER.decode(in.contactMethods(), path.append("contactMethods"));
        return Result.map2(nameResult, cmResult,
                (name, cms) -> new Customer(name, cms.primary(), cms.secondary()));
    };

    /**
     * Top-level decoder that builds a {@link CustomerWithIds} from a {@link CustomerRecords} input,
     * preserving the DB {@code id} of each contact method row.
     */
    static final Decoder<CustomerRecords, CustomerWithIds> CUSTOMER_WITH_IDS_DECODER = (in, path) -> {
        Result<PersonalName> nameResult = PERSONAL_NAME_DECODER.decode(in.customer(), path.append("customer"));
        Result<ContactMethodsWithIds> cmResult = CONTACT_METHODS_WITH_IDS_DECODER.decode(in.contactMethods(), path.append("contactMethods"));
        return Result.map2(nameResult, cmResult, (name, cms) ->
                new CustomerWithIds(
                        new Customer(name, cms.primary(), cms.secondary().stream().map(Map.Entry::getValue).toList()),
                        cms.primaryId(),
                        cms.secondary()));
    };

    /**
     * Builds a {@link Customer} domain object from a customer table row and its
     * associated contact_method rows.
     *
     * <p>Delegates to {@link #CUSTOMER_DECODER} internally. Column value mismatches
     * or missing required fields are reported as decode errors in the returned
     * {@link Result}.
     *
     * @param customerRec       a single row from the customer table
     * @param contactMethodRecs rows from the contact_method table for this customer;
     *                          must contain at least one row with {@code is_primary = true}
     * @return a {@link Result} containing the decoded {@link Customer} on success,
     *         or accumulated errors on failure
     */
    public Result<Customer> customer(Record customerRec, List<? extends Record> contactMethodRecs) {
        return CUSTOMER_DECODER.decode(new CustomerRecords(customerRec, contactMethodRecs));
    }

    /**
     * Builds a {@link CustomerWithIds} from a customer table row and its contact_method rows,
     * preserving the DB {@code id} of each contact method.
     *
     * @param customerRec       a single row from the customer table
     * @param contactMethodRecs rows from the contact_method table for this customer
     * @return a {@link Result} containing the decoded {@link CustomerWithIds} on success,
     *         or accumulated errors on failure
     */
    public Result<CustomerWithIds> customerWithIds(Record customerRec, List<? extends Record> contactMethodRecs) {
        return CUSTOMER_WITH_IDS_DECODER.decode(new CustomerRecords(customerRec, contactMethodRecs));
    }
}
