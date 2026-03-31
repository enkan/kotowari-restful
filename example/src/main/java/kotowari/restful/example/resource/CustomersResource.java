package kotowari.restful.example.resource;

import kotowari.restful.Decision;
import kotowari.restful.data.ContextKey;
import kotowari.restful.data.Problem;
import kotowari.restful.data.RestContext;
import kotowari.restful.example.dao.CustomerRepository;
import kotowari.restful.example.data.*;
import kotowari.restful.example.data.CustomerWithIds;
import kotowari.restful.resource.AllowedMethods;
import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import org.jooq.DSLContext;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

import static kotowari.restful.DecisionPoint.*;

/**
 * Resource for the {@code /customers} collection endpoint.
 *
 * <p>Handles creation of new customers via {@code POST /customers}.
 * The request body is validated using {@link CustomerJsonDecoders#CUSTOMER}
 * (Raoh decoder), and validation errors are returned as an RFC 9457
 * {@link Problem} with field-level {@link Problem.Violation}s.
 *
 * <h2>Decision graph flow</h2>
 * <ol>
 *   <li>{@link #isMalformed} — decodes and validates the JSON request body.
 *       On success, stores the decoded {@link Customer} in the {@link RestContext}
 *       for downstream injection. On failure, returns a {@link Problem} (400).</li>
 *   <li>{@link #create} — persists the customer and its contact methods within
 *       a transaction, storing the generated {@link CustomerId} in the context.</li>
 *   <li>{@link #handleCreated} — builds the 201 response body from the
 *       persisted customer data.</li>
 * </ol>
 *
 * @see CustomerJsonDecoders
 * @see CustomerRepository
 */
@AllowedMethods({"POST"})
public class CustomersResource {

    static final ContextKey<Customer> CUSTOMER = ContextKey.of(Customer.class);
    static final ContextKey<CustomerId> CUSTOMER_ID = ContextKey.of(CustomerId.class);
    static final ContextKey<CustomerWithIds> CUSTOMER_WITH_IDS = ContextKey.of(CustomerWithIds.class);

    /**
     * Validates the JSON request body against the {@link Customer} decoder.
     *
     * <p>If decoding succeeds, the decoded {@link Customer} is stored in the
     * {@link RestContext} via {@link RestContext#put} so that it can be
     * injected into subsequent decision methods. Returns {@code null} (not malformed).
     *
     * <p>If decoding fails, each {@link net.unit8.raoh.Issue} is converted to a
     * {@link Problem.Violation} and wrapped in a 400 {@link Problem}.
     *
     * @param body    the deserialized JSON request body
     * @param context the current request context
     * @return {@code null} if valid; a {@link Problem} with violations if invalid
     */
    @Decision(value = MALFORMED, method = {"POST"})
    public Problem isMalformed(JsonNode body, RestContext context) {
        return switch (CustomerJsonDecoders.CUSTOMER.decode(body)) {
            case Ok<Customer> ok -> {
                context.put(CUSTOMER, ok.value());
                yield null;
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
     * Persists a new customer within a transaction.
     *
     * <p>Creates a {@link CustomerRepository} from the injected {@link DSLContext},
     * inserts the customer (including all contact methods), and stores both the
     * generated {@link CustomerId} and the {@link CustomerWithIds} in the
     * context for the response handler.
     *
     * @param customer the validated customer from the request body
     * @param dsl      the jOOQ DSLContext (injected, transaction-scoped)
     * @param context  the current request context
     * @return {@code true} to indicate the POST action succeeded
     */
    @Decision(POST)
    public boolean create(Customer customer, DSLContext dsl, RestContext context) {
        dsl.transaction(cfg -> {
            CustomerRepository repo = new CustomerRepository(org.jooq.impl.DSL.using(cfg));
            CustomerId id = repo.insert(customer);
            CustomerWithIds cwi = repo.findByIdWithIds(id.value()).orElseThrow();
            context.put(CUSTOMER_ID, id);
            context.put(CUSTOMER_WITH_IDS, cwi);
        });
        return true;
    }

    /**
     * Builds the 201 Created response body.
     *
     * <p>Encodes the persisted {@link CustomerWithIds} and its generated {@link CustomerId}
     * via {@link CustomerJsonEncoders#encodeCustomerResponse} for JSON serialization.
     *
     * @param id  the generated customer ID
     * @param cwi the persisted customer with IDs
     * @return the response body for the 201 response
     */
    @Decision(HANDLE_CREATED)
    public Map<String, Object> handleCreated(CustomerId id, CustomerWithIds cwi) {
        return CustomerJsonEncoders.encodeCustomerResponse(id, cwi);
    }
}
