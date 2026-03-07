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
     * Replaces all contact methods for the given customer.
     *
     * <p>Deletes every existing {@code contact_method} row for the customer and
     * re-inserts the primary and secondary contact methods from the supplied
     * {@link Customer}. Callers must set the transaction boundary via
     * {@code @Transactional}.
     *
     * @param customerId the owning customer's ID
     * @param customer   the customer whose contact methods should be persisted
     */
    public void replaceContactMethods(long customerId, Customer customer) {
        dsl.deleteFrom(table("contact_method"))
                .where(CUSTOMER_ID.eq(customerId))
                .execute();
        insertContactMethod(customerId, customer.primaryContactMethod(), true);
        for (ContactMethod cm : customer.secondaryContactMethods()) {
            insertContactMethod(customerId, cm, false);
        }
    }

    /**
     * Replaces all contact methods for the given customer, preserving the DB id of
     * each contact method by updating in-place.
     *
     * <p>For each contact method in the supplied {@link CustomerWithIds}, the
     * existing row is updated in place so that its {@code id} does not change.
     * This avoids breaking URI references that embed a contact method id.
     * Rows that are no longer present are deleted; new rows are inserted.
     *
     * @param customerId the owning customer's ID
     * @param cwi        the updated customer aggregate with original DB ids
     */
    public void replaceContactMethodsWithIds(long customerId, CustomerWithIds cwi) {
        // Collect all current ids
        List<Long> existingIds = dsl.select(ID)
                .from(table("contact_method"))
                .where(CUSTOMER_ID.eq(customerId))
                .fetch(ID);

        // Update primary
        updateContactMethod(cwi.primaryCmId(), cwi.customer().primaryContactMethod(), true);

        // Update / insert secondaries
        java.util.Iterator<java.util.Map.Entry<Long, ContactMethod>> it = cwi.secondaryCmIds().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<Long, ContactMethod> entry = it.next();
            updateContactMethod(entry.getKey(), entry.getValue(), false);
        }

        // Delete any ids not in the updated set
        java.util.Set<Long> keepIds = new java.util.HashSet<>();
        keepIds.add(cwi.primaryCmId());
        cwi.secondaryCmIds().forEach(e -> keepIds.add(e.getKey()));
        for (long existingId : existingIds) {
            if (!keepIds.contains(existingId)) {
                dsl.deleteFrom(table("contact_method"))
                        .where(ID.eq(existingId))
                        .execute();
            }
        }
    }

    private void updateContactMethod(long cmId, ContactMethod cm, boolean isPrimary) {
        switch (cm) {
            case ContactMethod.Email e -> dsl.update(table("contact_method"))
                    .set(IS_PRIMARY, isPrimary)
                    .set(TYPE, "email")
                    .set(LABEL, e.info().label().value())
                    .set(EMAIL_ADDRESS, e.info().emailAddress().value())
                    .set(ADDRESS1, (String) null)
                    .set(ADDRESS2, (String) null)
                    .set(CITY, (String) null)
                    .set(STATE, (String) null)
                    .set(ZIP_CODE, (String) null)
                    .where(ID.eq(cmId))
                    .execute();
            case ContactMethod.PostalAddress p -> dsl.update(table("contact_method"))
                    .set(IS_PRIMARY, isPrimary)
                    .set(TYPE, "postalAddress")
                    .set(LABEL, p.info().label().value())
                    .set(EMAIL_ADDRESS, (String) null)
                    .set(ADDRESS1, p.info().address1().value())
                    .set(ADDRESS2, p.info().address2().map(String100::value).orElse(null))
                    .set(CITY, p.info().city().value())
                    .set(STATE, p.info().state().value())
                    .set(ZIP_CODE, p.info().zipCode().value())
                    .where(ID.eq(cmId))
                    .execute();
        }
    }

    /**
     * Finds a single contact method by its own ID, scoped to the given customer.
     *
     * <p>Returns {@link Optional#empty()} if no row matches both {@code customer_id}
     * and {@code id}.
     *
     * @param customerId the owning customer's ID
     * @param cmId       the contact method's own ID
     * @return the decoded {@link ContactMethod}, or empty if not found
     */
    public Optional<ContactMethod> findContactMethodById(long customerId, long cmId) {
        Record rec = dsl.select(
                        ID, CUSTOMER_ID, IS_PRIMARY, TYPE, LABEL,
                        EMAIL_ADDRESS, ADDRESS1, ADDRESS2, CITY, STATE, ZIP_CODE)
                .from(table("contact_method"))
                .where(CUSTOMER_ID.eq(customerId), ID.eq(cmId))
                .fetchOne();
        if (rec == null) return Optional.empty();
        return Optional.of(CustomerMapper.CONTACT_METHOD_DECODER.decode(rec).getOrThrow());
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

    /**
     * Finds a customer by ID, returning the fully hydrated aggregate together with
     * the DB {@code id} of each contact method row.
     *
     * <p>Identical query to {@link #findById(long)}, but delegates to
     * {@link CustomerMapper#customerWithIds} so that the DB ids of the
     * contact method rows are preserved in the returned {@link CustomerWithIds}.
     *
     * @param id the customer ID to look up
     * @return the customer with ids wrapped in an {@link Optional}, or empty if not found
     */
    public Optional<CustomerWithIds> findByIdWithIds(long id) {
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

        return Optional.of(mapper.customerWithIds(customerRec, cmRecords).getOrThrow());
    }
}
