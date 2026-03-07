package kotowari.restful.example.resource;

import enkan.collection.Parameters;
import kotowari.restful.Decision;
import kotowari.restful.data.ContextKey;
import kotowari.restful.data.Problem;
import kotowari.restful.data.RestContext;
import kotowari.restful.example.behavior.PromoteToPrimary;
import kotowari.restful.example.dao.CustomerRepository;
import kotowari.restful.example.data.ContactMethod;
import kotowari.restful.example.data.ContactMethodId;
import kotowari.restful.example.data.Customer;
import kotowari.restful.example.data.CustomerId;
import kotowari.restful.example.data.CustomerWithIds;
import kotowari.restful.resource.AllowedMethods;
import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import org.jooq.DSLContext;

import java.util.List;

import static kotowari.restful.DecisionPoint.*;

/**
 * Resource for the {@code /customers/:id/contact-methods/:cmId/primary} endpoint.
 *
 * <p>Promotes a secondary contact method to primary via {@code PUT}.
 *
 * <h2>Decision graph flow</h2>
 * <ol>
 *   <li>{@link #exists} — loads the customer and the target contact method;
 *       404 if either is not found.</li>
 *   <li>{@link #promote} — applies {@link PromoteToPrimary} and persists the result.</li>
 *   <li>{@link #handleOk} — returns the updated customer as 200 OK.</li>
 * </ol>
 */
@AllowedMethods({"PUT"})
public class ContactMethodPrimaryResource {

    private static final PromoteToPrimary PROMOTE = new PromoteToPrimary();

    static final ContextKey<CustomerId> CUSTOMER_ID = ContextKey.of(CustomerId.class);
    static final ContextKey<ContactMethodId> CONTACT_METHOD_ID = ContextKey.of(ContactMethodId.class);
    static final ContextKey<Customer> CUSTOMER = ContextKey.of(Customer.class);
    static final ContextKey<CustomerWithIds> CUSTOMER_WITH_IDS = ContextKey.of(CustomerWithIds.class);
    static final ContextKey<ContactMethod> CONTACT_METHOD = ContextKey.of(ContactMethod.class);

    /**
     * Loads the customer and the target contact method from the path parameters.
     *
     * @param params  the routing parameters containing {@code id} and {@code cmId}
     * @param dsl     the jOOQ DSLContext (injected)
     * @param context the current request context
     * @return {@code true} if both are found, {@code false} to trigger 404
     */
    @Decision(EXISTS)
    public boolean exists(Parameters params, DSLContext dsl, RestContext context) {
        long customerId = Long.parseLong(params.get("id"));
        long cmId = Long.parseLong(params.get("cmId"));
        CustomerRepository repo = new CustomerRepository(dsl);
        return repo.findByIdWithIds(customerId).flatMap(cwi ->
                repo.findContactMethodById(customerId, cmId).map(cm -> {
                    context.put(CUSTOMER_ID, new CustomerId(customerId));
                    context.put(CONTACT_METHOD_ID, new ContactMethodId(cmId));
                    context.put(CUSTOMER, cwi.customer());
                    context.put(CUSTOMER_WITH_IDS, cwi);
                    context.put(CONTACT_METHOD, cm);
                    return true;
                })
        ).orElse(false);
    }

    /**
     * Applies {@link PromoteToPrimary} and persists the updated customer.
     *
     * @param id       the customer ID
     * @param dsl      the jOOQ DSLContext (injected)
     * @param context  the current request context
     * @return {@code true} on success; a {@link Problem} on business rule violation
     */
    @Decision(PUT)
    public Object promote(CustomerId id, DSLContext dsl, RestContext context) {
        CustomerWithIds existing = context.get(CUSTOMER_WITH_IDS).orElseThrow();
        ContactMethod target = context.get(CONTACT_METHOD).orElseThrow();
        return switch (PROMOTE.apply(existing.customer(), target)) {
            case Ok<Customer> ok -> {
                dsl.transaction(cfg -> {
                    CustomerRepository repo = new CustomerRepository(org.jooq.impl.DSL.using(cfg));
                    CustomerWithIds updatedCwi = buildPromotedWithIds(existing, ok.value());
                    repo.replaceContactMethodsWithIds(id.value(), updatedCwi);
                    CustomerWithIds refreshed = repo.findByIdWithIds(id.value()).orElseThrow();
                    context.put(CUSTOMER_WITH_IDS, refreshed);
                });
                yield true;
            }
            case Err<Customer> err -> {
                List<Problem.Violation> violations = err.issues().asList().stream()
                        .map(issue -> new Problem.Violation(issue.path().toString(), issue.code(), issue.message()))
                        .toList();
                yield Problem.fromViolationList(violations);
            }
        };
    }

    /**
     * Builds a {@link CustomerWithIds} that reflects the promoted structure while preserving
     * the original DB ids of each contact method row.
     *
     * <p>After {@link PromoteToPrimary} runs:
     * <ul>
     *   <li>The new primary CM is the secondary whose CM equals {@code target}.</li>
     *   <li>The new secondary list starts with the old primary, followed by the remaining
     *       secondaries in their original order (excluding the promoted one).</li>
     * </ul>
     *
     * @param existing the original {@link CustomerWithIds} with stable DB ids
     * @param promoted the new {@link Customer} returned by {@link PromoteToPrimary}
     * @return a new {@link CustomerWithIds} with updated CM ordering and preserved ids
     */
    private static CustomerWithIds buildPromotedWithIds(CustomerWithIds existing, Customer promoted) {
        ContactMethod newPrimary = promoted.primaryContactMethod();
        long newPrimaryId = existing.secondaryCmIds().stream()
                .filter(e -> e.getValue().equals(newPrimary))
                .mapToLong(java.util.Map.Entry::getKey)
                .findFirst()
                .orElseThrow();

        java.util.List<java.util.Map.Entry<Long, ContactMethod>> newSecondaries = new java.util.ArrayList<>();
        newSecondaries.add(java.util.Map.entry(existing.primaryCmId(), existing.customer().primaryContactMethod()));
        existing.secondaryCmIds().stream()
                .filter(e -> e.getKey() != newPrimaryId)
                .forEach(newSecondaries::add);

        return new CustomerWithIds(promoted, newPrimaryId, java.util.List.copyOf(newSecondaries));
    }

    /**
     * Disallows PUT to a missing resource — a non-existent contact method cannot be
     * promoted to primary, so the graph routes to {@code HANDLE_NOT_FOUND} (404).
     *
     * @return always {@code false}
     */
    @Decision(CAN_PUT_TO_MISSING)
    public boolean canPutToMissing() {
        return false;
    }

    /**
     * Indicates that this PUT operation updates an existing resource (not creates a new one),
     * so the graph routes to {@code RESPOND_WITH_ENTITY} rather than {@code HANDLE_CREATED} (201).
     *
     * @return always {@code false}
     */
    @Decision(NEW)
    public boolean isNew() {
        return false;
    }

    /**
     * Indicates that the PUT response should include the updated resource in the body,
     * routing to {@code HANDLE_OK} (200).
     *
     * @return always {@code true}
     */
    @Decision(RESPOND_WITH_ENTITY)
    public boolean respondWithEntity() {
        return true;
    }

    /**
     * Returns the updated customer as a 200 OK response.
     *
     * @param id  the customer ID
     * @param cwi the updated customer with IDs from the context
     * @return the response body for the 200 response
     */
    @Decision(HANDLE_OK)
    public CustomerResponse handleOk(CustomerId id, CustomerWithIds cwi) {
        return CustomerResponse.from(id, cwi);
    }
}
