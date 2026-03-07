package kotowari.restful.example.resource;

import enkan.collection.Parameters;
import kotowari.restful.Decision;
import kotowari.restful.data.ContextKey;
import kotowari.restful.data.RestContext;
import kotowari.restful.example.dao.CustomerRepository;
import kotowari.restful.example.data.CustomerId;
import kotowari.restful.example.data.CustomerWithIds;
import kotowari.restful.resource.AllowedMethods;
import org.jooq.DSLContext;

import static kotowari.restful.DecisionPoint.*;

/**
 * Resource for the {@code /customers/:id} single-item endpoint.
 *
 * <p>Handles retrieval of an individual customer via {@code GET /customers/:id}.
 * The customer is looked up by the {@code id} path parameter and, if found,
 * returned as a {@link CustomerResponse}.
 *
 * <h2>Decision graph flow</h2>
 * <ol>
 *   <li>{@link #exists} — parses the {@code id} path parameter, queries the
 *       database via {@link CustomerRepository#findByIdWithIds(long)}, and stores the
 *       {@link CustomerWithIds} and {@link CustomerId} in the {@link RestContext} if found.
 *       Returns {@code false} to trigger a 404 if the customer does not exist.</li>
 *   <li>{@link #show} — builds the 200 response body from the customer data
 *       stored in the context by {@link #exists}.</li>
 * </ol>
 *
 * @see CustomerRepository
 * @see CustomerResponse
 */
@AllowedMethods({"GET"})
public class CustomerResource {

    static final ContextKey<CustomerId> CUSTOMER_ID = ContextKey.of(CustomerId.class);
    static final ContextKey<CustomerWithIds> CUSTOMER_WITH_IDS = ContextKey.of(CustomerWithIds.class);

    /**
     * Checks whether the customer identified by the {@code id} path parameter exists.
     *
     * <p>Parses the {@code id} from the routing parameters, queries the database,
     * and if found, stores both the {@link CustomerId} and the {@link CustomerWithIds}
     * in the {@link RestContext} for injection into downstream decision methods.
     *
     * @param params the routing parameters containing the {@code id} path variable
     * @param dsl    the jOOQ DSLContext (injected)
     * @param context the current request context
     * @return {@code true} if the customer exists (proceeds to {@link #show}),
     *         {@code false} otherwise (triggers 404 Not Found)
     */
    @Decision(EXISTS)
    public boolean exists(Parameters params, DSLContext dsl, RestContext context) {
        long id = Long.parseLong(params.get("id"));
        CustomerRepository repo = new CustomerRepository(dsl);
        return repo.findByIdWithIds(id).map(cwi -> {
            context.put(CUSTOMER_ID, new CustomerId(id));
            context.put(CUSTOMER_WITH_IDS, cwi);
            return true;
        }).orElse(false);
    }

    /**
     * Builds the 200 OK response body for an existing customer.
     *
     * <p>Converts the {@link CustomerWithIds} and {@link CustomerId} (stored in the
     * context by {@link #exists}) into a {@link CustomerResponse} DTO suitable
     * for JSON serialization.
     *
     * @param id  the customer ID from the path parameter
     * @param cwi the customer with IDs from the context
     * @return the response body for the 200 response
     */
    @Decision(HANDLE_OK)
    public CustomerResponse show(CustomerId id, CustomerWithIds cwi) {
        return CustomerResponse.from(id, cwi);
    }
}
