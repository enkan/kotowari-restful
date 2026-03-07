package kotowari.restful.example.dao;

import kotowari.restful.example.data.*;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;

import java.util.List;
import java.util.Optional;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

/**
 * Repository for persisting and retrieving {@link Customer} aggregates.
 *
 * <p>Uses jOOQ's {@link DSLContext} to access the {@code customer} and
 * {@code contact_method} tables.
 *
 * <ul>
 *   <li><b>Writes</b> — {@link #insert(Customer)} inserts a customer row together
 *       with its contact_method rows. The columns written depend on the
 *       {@link ContactMethod} subtype: {@link ContactMethod.Email} stores
 *       {@code email_address}, while {@link ContactMethod.PostalAddress} stores
 *       {@code address1} through {@code zip_code}.</li>
 *   <li><b>Reads</b> — {@link #findById(long)} fetches the customer row and its
 *       associated contact_method rows, then delegates to {@link CustomerMapper}
 *       to decode them into a domain object.</li>
 * </ul>
 *
 * <p>This class does not manage transactions. Callers (middleware or resource
 * classes) should set the transaction boundary via {@code @Transactional}.
 *
 * @see CustomerMapper
 */
public class CustomerRepository {

    // ========================================================================
    // Column references — customer table
    // ========================================================================

    private static final Field<Long> ID = field("id", Long.class);
    private static final Field<String> FIRST_NAME = field("first_name", String.class);
    private static final Field<String> MIDDLE_NAME = field("middle_name", String.class);
    private static final Field<String> LAST_NAME = field("last_name", String.class);

    // ========================================================================
    // Column references — contact_method table
    // ========================================================================

    private static final Field<Long> CUSTOMER_ID = field("customer_id", Long.class);
    private static final Field<Boolean> IS_PRIMARY = field("is_primary", Boolean.class);
    private static final Field<String> TYPE = field("type", String.class);
    private static final Field<String> LABEL = field("label", String.class);
    private static final Field<String> EMAIL_ADDRESS = field("email_address", String.class);
    private static final Field<String> ADDRESS1 = field("address1", String.class);
    private static final Field<String> ADDRESS2 = field("address2", String.class);
    private static final Field<String> CITY = field("city", String.class);
    private static final Field<String> STATE = field("state", String.class);
    private static final Field<String> ZIP_CODE = field("zip_code", String.class);

    private final DSLContext dsl;
    private final CustomerMapper mapper = new CustomerMapper();

    /**
     * @param dsl the jOOQ DSLContext, expected to be used within a transaction
     */
    public CustomerRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Inserts a new customer together with all of its contact methods.
     *
     * <p>First inserts a row into the {@code customer} table, then inserts the
     * {@link Customer#primaryContactMethod()} (with {@code is_primary = true})
     * and each of the {@link Customer#secondaryContactMethods()} (with
     * {@code is_primary = false}) into the {@code contact_method} table.
     *
     * @param customer the customer to insert
     * @return the generated customer ID
     */
    public CustomerId insert(Customer customer) {
        Record rec = dsl.insertInto(table("customer"),
                        FIRST_NAME, MIDDLE_NAME, LAST_NAME)
                .values(
                        customer.name().firstName().value(),
                        customer.name().middleName().map(String50::value).orElse(null),
                        customer.name().lastName().value())
                .returningResult(ID)
                .fetchOne();
        long customerId = rec.get(ID);

        insertContactMethod(customerId, customer.primaryContactMethod(), true);
        for (ContactMethod cm : customer.secondaryContactMethods()) {
            insertContactMethod(customerId, cm, false);
        }

        return new CustomerId(customerId);
    }

    /**
     * Inserts a single row into the {@code contact_method} table.
     *
     * <p>The columns written depend on the sealed subtype of {@link ContactMethod}:
     * <ul>
     *   <li>{@link ContactMethod.Email} — {@code type}, {@code label},
     *       {@code email_address}</li>
     *   <li>{@link ContactMethod.PostalAddress} — {@code type}, {@code label},
     *       {@code address1} through {@code zip_code}</li>
     * </ul>
     *
     * @param customerId the owning customer's ID
     * @param cm         the contact method to insert
     * @param isPrimary  {@code true} if this is the customer's primary contact method
     */
    private void insertContactMethod(long customerId, ContactMethod cm, boolean isPrimary) {
        switch (cm) {
            case ContactMethod.Email e -> dsl.insertInto(table("contact_method"),
                            CUSTOMER_ID, IS_PRIMARY, TYPE, LABEL, EMAIL_ADDRESS)
                    .values(customerId, isPrimary, "email",
                            e.info().label().value(),
                            e.info().emailAddress().value())
                    .execute();
            case ContactMethod.PostalAddress p -> dsl.insertInto(table("contact_method"),
                            CUSTOMER_ID, IS_PRIMARY, TYPE, LABEL,
                            ADDRESS1, ADDRESS2, CITY, STATE, ZIP_CODE)
                    .values(customerId, isPrimary, "postalAddress",
                            p.info().label().value(),
                            p.info().address1().value(),
                            p.info().address2().map(String100::value).orElse(null),
                            p.info().city().value(),
                            p.info().state().value(),
                            p.info().zipCode().value())
                    .execute();
        }
    }

    /**
     * Finds a customer by ID, returning the fully hydrated aggregate.
     *
     * <p>Queries the {@code customer} table for the given ID, then fetches all
     * associated {@code contact_method} rows ordered by their ID. The raw jOOQ
     * records are decoded into a {@link Customer} domain object by
     * {@link CustomerMapper#customer(Record, List)}.
     *
     * <p>Contact methods are partitioned by the {@code is_primary} column:
     * the row with {@code is_primary = true} becomes
     * {@link Customer#primaryContactMethod()}, and the rest become
     * {@link Customer#secondaryContactMethods()}.
     *
     * @param id the customer ID to look up
     * @return the customer wrapped in an {@link Optional}, or {@link Optional#empty()}
     *         if no customer exists with the given ID
     * @throws IllegalStateException if the contact_method records cannot be decoded
     *         (data integrity issue such as an unknown type value or a missing
     *         primary contact method)
     */
    public Optional<Customer> findById(long id) {
        Record customerRec = dsl.select(ID, FIRST_NAME, MIDDLE_NAME, LAST_NAME)
                .from(table("customer"))
                .where(ID.eq(id))
                .fetchOne();

        if (customerRec == null) return Optional.empty();

        List<? extends Record> cmRecords = dsl.select(
                        ID, CUSTOMER_ID, IS_PRIMARY, TYPE, LABEL,
                        EMAIL_ADDRESS, ADDRESS1, ADDRESS2, CITY, STATE, ZIP_CODE)
                .from(table("contact_method"))
                .where(CUSTOMER_ID.eq(id))
                .orderBy(ID)
                .fetch();

        return Optional.of(mapper.customer(customerRec, cmRecords).getOrThrow());
    }
}
