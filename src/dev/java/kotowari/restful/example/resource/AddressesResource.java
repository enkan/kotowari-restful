package kotowari.restful.example.resource;

import enkan.collection.Parameters;
import enkan.component.BeansConverter;
import kotowari.restful.Decision;
import kotowari.restful.component.BeansValidator;
import kotowari.restful.resource.AllowedMethods;
import kotowari.restful.data.Problem;
import kotowari.restful.data.RestContext;
import kotowari.restful.example.entity.Address;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolation;
import java.util.List;
import java.util.Set;

import static kotowari.restful.DecisionPoint.*;

/**
 * Resource class for the {@code /addresses} collection endpoint.
 *
 * <p>Demonstrates:
 * <ul>
 *   <li>Per-HTTP-method {@code @Decision} dispatch via the {@code method} attribute</li>
 *   <li>Passing a converted object between decision points using {@code context.putValue()}</li>
 *   <li>Returning {@code Problem.fromViolations()} from {@code MALFORMED} to short-circuit with 400</li>
 * </ul>
 *
 * <p>Supported operations:
 * <ul>
 *   <li>GET  /addresses  — paginated list, filtered by optional query parameter {@code q}</li>
 *   <li>POST /addresses  — create a new address; responds 201 with the persisted entity</li>
 * </ul>
 */
@AllowedMethods({"GET", "POST"})
@Transactional
public class AddressesResource {
    @Inject
    private BeansValidator validator;

    @Inject
    private BeansConverter beansConverter;

    /**
     * Validates GET query parameters and converts them to a typed {@link AddressSearchParams}.
     *
     * <p>The converted object is stored in the context via {@code putValue()} so that
     * {@link #handleOk} can receive it as a typed parameter without repeating the conversion.
     * Returning a non-null {@link Problem} causes the decision graph to short-circuit to 400.
     */
    @Decision(value = MALFORMED, method={"GET"})
    public Problem isMalformed(Parameters params, RestContext context) {
        AddressSearchParams searchParams = beansConverter.createFrom(params, AddressSearchParams.class);
        context.putValue(searchParams);
        Set<ConstraintViolation<AddressSearchParams>> violations = validator.validate(searchParams);
        return violations.isEmpty() ? null : Problem.fromViolations(violations);
    }

    /**
     * Validates the deserialized request body for POST.
     *
     * <p>{@code SerDesMiddleware} deserializes the JSON body to {@link Address} before this
     * method is called. Returning a non-null {@link Problem} short-circuits to 400.
     */
    @Decision(value = MALFORMED, method={"POST"})
    public Problem isPostMalformed(Address address) {
        Set<ConstraintViolation<Address>> violations = validator.validate(address);
        return violations.isEmpty() ? null : Problem.fromViolations(violations);
    }

    /**
     * Returns the paginated list of addresses for a successful GET (200 OK).
     *
     * <p>{@link AddressSearchParams} is injected from the context store populated by
     * {@link #isMalformed}. {@code EntityManager} is injected by {@code EntityManagerInjector}.
     */
    @Decision(HANDLE_OK)
    public List<Address> handleOk(AddressSearchParams params, EntityManager em) {
        CriteriaBuilder builder = em.getCriteriaBuilder();
        CriteriaQuery<Address> query = builder.createQuery(Address.class);
        Root<Address> root = query.from(Address.class);
        query.select(root);

        return em.createQuery(query)
                .setFirstResult(params.getOffset())
                .setMaxResults(params.getLimit())
                .getResultList();
    }

    /**
     * Persists the new address and stores it in the context for {@link #handleCreated}.
     *
     * <p>The {@code POST} action node always proceeds to {@code HANDLE_CREATED} regardless of
     * the return value. The persisted entity is stored via {@code context.putValue()} so that
     * {@link #handleCreated} can inject it by type and return it as the 201 response body.
     */
    @Decision(POST)
    public boolean create(Address address, EntityManager em, RestContext context) {
        em.persist(address);
        context.putValue(address);
        return true;
    }

    /**
     * Returns the persisted address as the 201 Created response body.
     *
     * <p>{@link Address} is injected from the context store populated by {@link #create}.
     */
    @Decision(HANDLE_CREATED)
    public Address handleCreated(Address address) {
        return address;
    }
}
