package kotowari.restful.example.resource;

import enkan.collection.Parameters;
import kotowari.restful.Decision;
import kotowari.restful.data.ContextKey;
import kotowari.restful.data.Problem;
import kotowari.restful.data.RestContext;
import kotowari.restful.example.behavior.AddSecondaryContactMethod;
import kotowari.restful.example.dao.CustomerRepository;
import kotowari.restful.example.data.ContactMethod;
import kotowari.restful.example.data.Customer;
import kotowari.restful.example.data.CustomerId;
import kotowari.restful.example.data.CustomerWithIds;
import kotowari.restful.resource.AllowedMethods;
import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import org.jooq.DSLContext;
import tools.jackson.databind.JsonNode;

import java.util.List;

import static kotowari.restful.DecisionPoint.*;

/**
 * Resource for the {@code /customers/:id/contact-methods} collection endpoint.
 *
 * <p>Handles addition of a secondary contact method via {@code POST}.
 *
 * <h2>Decision graph flow</h2>
 * <ol>
 *   <li>{@link #exists} — loads the customer; 404 if not found.</li>
 *   <li>{@link #isMalformed} — decodes the request body into a {@link ContactMethod}.</li>
 *   <li>{@link #add} — applies {@link AddSecondaryContactMethod} and persists the result.</li>
 *   <li>{@link #handleCreated} — returns the updated customer as 201.</li>
 * </ol>
 */
@AllowedMethods({"POST"})
public class ContactMethodsResource {

    private static final AddSecondaryContactMethod ADD_SECONDARY = new AddSecondaryContactMethod();

    static final ContextKey<CustomerId> CUSTOMER_ID = ContextKey.of(CustomerId.class);
    static final ContextKey<Customer> CUSTOMER = ContextKey.of(Customer.class);
    static final ContextKey<ContactMethod> CONTACT_METHOD = ContextKey.of(ContactMethod.class);
    static final ContextKey<CustomerWithIds> CUSTOMER_WITH_IDS = ContextKey.of(CustomerWithIds.class);

    /**
     * Loads the customer identified by the {@code id} path parameter.
     *
     * @param params  the routing parameters containing the {@code id} path variable
     * @param dsl     the jOOQ DSLContext (injected)
     * @param context the current request context
     * @return {@code true} if found, {@code false} to trigger 404
     */
    @Decision(EXISTS)
    public boolean exists(Parameters params, DSLContext dsl, RestContext context) {
        long id = Long.parseLong(params.get("id"));
        CustomerRepository repo = new CustomerRepository(dsl);
        return repo.findByIdWithIds(id).map(cwi -> {
            context.put(CUSTOMER_ID, new CustomerId(id));
            context.put(CUSTOMER, cwi.customer());
            return true;
        }).orElse(false);
    }

    /**
     * Disallows POST to a non-existent customer — secondary contact methods can only be added
     * to customers that exist, so the graph routes to {@code HANDLE_NOT_FOUND} (404).
     *
     * @return always {@code false}
     */
    @Decision(CAN_POST_TO_MISSING)
    public boolean canPostToMissing() {
        return false;
    }

    /**
     * Decodes the request body into a {@link ContactMethod}.
     *
     * @param body    the deserialized JSON request body
     * @param context the current request context
     * @return {@code null} if valid; a {@link Problem} with violations if invalid
     */
    @Decision(value = MALFORMED, method = {"POST"})
    public Problem isMalformed(JsonNode body, RestContext context) {
        return switch (CustomerJsonDecoders.CONTACT_METHOD.decode(body)) {
            case Ok<ContactMethod> ok -> {
                context.put(CONTACT_METHOD, ok.value());
                yield null;
            }
            case Err<ContactMethod> err -> {
                List<Problem.Violation> violations = err.issues().asList().stream()
                        .map(issue -> new Problem.Violation(issue.path().toString(), issue.code(), issue.message()))
                        .toList();
                yield Problem.fromViolationList(violations);
            }
        };
    }

    /**
     * Applies {@link AddSecondaryContactMethod} and persists the updated customer.
     *
     * <p>Returns {@code true} on success, or a {@link Problem} on business rule violation.
     * When a {@code Problem} is returned, the {@code Action} node automatically routes
     * to the error handler — no manual {@code context.setMessage()} needed.
     *
     * @param id       the customer ID
     * @param dsl      the jOOQ DSLContext (injected)
     * @param context  the current request context
     * @return {@code true} on success; a {@link Problem} on business rule violation
     */
    @Decision(POST)
    public Object add(CustomerId id, DSLContext dsl, RestContext context) {
        Customer customer = context.get(CUSTOMER).orElseThrow();
        ContactMethod toAdd = context.get(CONTACT_METHOD).orElseThrow();
        return switch (ADD_SECONDARY.apply(customer, toAdd)) {
            case Ok<Customer> ok -> {
                dsl.transaction(cfg -> {
                    CustomerRepository repo = new CustomerRepository(org.jooq.impl.DSL.using(cfg));
                    repo.replaceContactMethods(id.value(), ok.value());
                    CustomerWithIds cwi = repo.findByIdWithIds(id.value()).orElseThrow();
                    context.put(CUSTOMER_WITH_IDS, cwi);
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
     * Returns the updated customer as a 201 Created response.
     *
     * @param id  the customer ID
     * @param cwi the customer with IDs from the context
     * @return the response body for the 201 response
     */
    @Decision(HANDLE_CREATED)
    public CustomerResponse handleCreated(CustomerId id, CustomerWithIds cwi) {
        return CustomerResponse.from(id, cwi);
    }
}
