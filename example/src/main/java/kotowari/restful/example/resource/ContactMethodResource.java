package kotowari.restful.example.resource;

import enkan.collection.Parameters;
import kotowari.restful.Decision;
import kotowari.restful.data.ContextKey;
import kotowari.restful.data.Problem;
import kotowari.restful.data.RestContext;
import kotowari.restful.example.behavior.RemoveContactMethod;
import kotowari.restful.example.dao.CustomerRepository;
import kotowari.restful.example.data.ContactMethod;
import kotowari.restful.example.data.ContactMethodId;
import kotowari.restful.example.data.Customer;
import kotowari.restful.example.data.CustomerId;
import kotowari.restful.resource.AllowedMethods;
import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import org.jooq.DSLContext;

import java.util.List;

import static kotowari.restful.DecisionPoint.*;

/**
 * Resource for the {@code /customers/:id/contact-methods/:cmId} endpoint.
 *
 * <p>Handles removal of a secondary contact method via {@code DELETE}.
 *
 * <h2>Decision graph flow</h2>
 * <ol>
 *   <li>{@link #exists} — loads the customer and the target contact method;
 *       404 if either is not found.</li>
 *   <li>{@link #delete} — applies {@link RemoveContactMethod} and persists the result.</li>
 * </ol>
 */
@AllowedMethods({"DELETE"})
public class ContactMethodResource {

    private static final RemoveContactMethod REMOVE = new RemoveContactMethod();

    static final ContextKey<CustomerId> CUSTOMER_ID = ContextKey.of(CustomerId.class);
    static final ContextKey<ContactMethodId> CONTACT_METHOD_ID = ContextKey.of(ContactMethodId.class);
    static final ContextKey<Customer> CUSTOMER = ContextKey.of(Customer.class);
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
        return repo.findById(customerId).flatMap(customer ->
                repo.findContactMethodById(customerId, cmId).map(cm -> {
                    context.put(CUSTOMER_ID, new CustomerId(customerId));
                    context.put(CONTACT_METHOD_ID, new ContactMethodId(cmId));
                    context.put(CUSTOMER, customer);
                    context.put(CONTACT_METHOD, cm);
                    return true;
                })
        ).orElse(false);
    }

    /**
     * Applies {@link RemoveContactMethod} and persists the updated customer.
     *
     * <p>Returns {@code true} on success, or a {@link Problem} on business rule violation.
     * When a {@code Problem} is returned, the {@code Action} node automatically routes
     * to the error handler.
     *
     * @param id       the customer ID
     * @param dsl      the jOOQ DSLContext (injected)
     * @param context  the current request context
     * @return {@code true} on success; a {@link Problem} on business rule violation
     */
    @Decision(DELETE)
    public Object delete(CustomerId id, DSLContext dsl, RestContext context) {
        Customer customer = context.get(CUSTOMER).orElseThrow();
        ContactMethod target = context.get(CONTACT_METHOD).orElseThrow();
        return switch (REMOVE.apply(customer, target)) {
            case Ok<Customer> ok -> {
                dsl.transaction(cfg -> {
                    CustomerRepository repo = new CustomerRepository(org.jooq.impl.DSL.using(cfg));
                    repo.replaceContactMethods(id.value(), ok.value());
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
}
