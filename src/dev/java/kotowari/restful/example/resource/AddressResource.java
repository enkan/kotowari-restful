package kotowari.restful.example.resource;

import enkan.collection.Parameters;
import enkan.component.BeansConverter;
import kotowari.restful.Decision;
import kotowari.restful.component.BeansValidator;
import kotowari.restful.data.Problem;
import kotowari.restful.data.RestContext;
import kotowari.restful.example.entity.Address;
import kotowari.restful.resource.AllowedMethods;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolation;
import java.util.Set;

import static kotowari.restful.DecisionPoint.*;

/**
 * Resource class for the {@code /addresses/:id} single-item endpoint.
 *
 * <p>Demonstrates:
 * <ul>
 *   <li>{@code EXISTS} decision point for 404 short-circuiting and entity caching</li>
 *   <li>Per-HTTP-method {@code @Decision} dispatch (MALFORMED only for PUT)</li>
 *   <li>Retrieving a context-stored value by type via {@code context.getValue()}</li>
 * </ul>
 *
 * <p>Supported operations:
 * <ul>
 *   <li>GET    /addresses/:id — return the address or 404</li>
 *   <li>PUT    /addresses/:id — validate and replace the address body</li>
 *   <li>DELETE /addresses/:id — remove the address</li>
 * </ul>
 */
@AllowedMethods({"GET", "PUT", "DELETE"})
@Transactional
public class AddressResource {

    @Inject
    private BeansValidator validator;

    @Inject
    private BeansConverter beansConverter;

    /**
     * Looks up the address by {@code :id} path variable and short-circuits to 404 if not found.
     *
     * <p>The route path variable {@code :id} is merged into {@link Parameters} by
     * {@code RoutingMiddleware}, so it is accessible via {@code params.get("id")}.
     * {@link EntityManager} is injected by {@code EntityManagerInjector}.
     * When the entity is found it is stored in the context via {@code putValue()} so that
     * {@link #show}, {@link #update}, and {@link #destroy} can receive it as a typed parameter
     * without querying the database again.
     */
    @Decision(EXISTS)
    public boolean exists(Parameters params, EntityManager em, RestContext context) {
        Long id = Long.valueOf(params.get("id").toString());
        Address address = em.find(Address.class, id);
        if (address == null) return false;
        context.putValue(address);
        return true;
    }

    /**
     * Validates the deserialized request body for PUT only.
     *
     * <p>Returning a non-null {@link Problem} short-circuits to 400 before the {@code PUT}
     * action is reached. GET and DELETE requests skip this method entirely because of the
     * {@code method = {"PUT"}} filter.
     */
    @Decision(value = MALFORMED, method = {"PUT"})
    public Problem validatePut(Address body) {
        Set<ConstraintViolation<Address>> violations = validator.validate(body);
        return violations.isEmpty() ? null : Problem.fromViolations(violations);
    }

    /**
     * Returns the address entity for a successful GET (200 OK).
     *
     * <p>{@link Address} is injected from the context store populated by {@link #exists}.
     */
    @Decision(HANDLE_OK)
    public Address show(Address address) {
        return address;
    }

    /**
     * Applies the request body to the existing address entity.
     *
     * <p>{@code body} is the deserialized JSON request body; {@code address} is the
     * existing entity retrieved by {@link #exists}. {@code beansConverter.copy()} performs
     * a shallow property copy, and EclipseLink flushes the dirty entity at transaction commit.
     * The updated entity is stored in context so {@link #show} can return it as the response body.
     */
    @Decision(PUT)
    public void update(Address body, Address address, EntityManager em, RestContext context) {
        beansConverter.copy(body, address);
        context.putValue(address);
    }

    /**
     * PUT updates an existing resource, so it is never "new" — routes through
     * RESPOND_WITH_ENTITY rather than HANDLE_CREATED (201).
     */
    @Decision(value = NEW, method = {"PUT"})
    public boolean isNew() {
        return false;
    }

    /**
     * Return the updated entity in the PUT response body (200 OK with body).
     * DELETE does not respond with a body (204 No Content), so this is PUT-only.
     */
    @Decision(value = RESPOND_WITH_ENTITY, method = {"PUT"})
    public boolean respondWithEntity() {
        return true;
    }

    /**
     * Removes the address entity (204 No Content).
     *
     * <p>{@link Address} is injected from the context store; no separate lookup is needed.
     * The transaction is managed by {@code NonJtaTransactionMiddleware}.
     */
    @Decision(DELETE)
    public void destroy(Address address, EntityManager em) {
        em.remove(address);
    }
}
