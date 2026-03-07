package kotowari.restful.example.resource;

import enkan.collection.Parameters;
import enkan.component.BeansConverter;
import kotowari.restful.Decision;
import kotowari.restful.component.BeansValidator;
import kotowari.restful.resource.AllowedMethods;
import kotowari.restful.data.Problem;
import kotowari.restful.data.RestContext;
import kotowari.restful.example.data.Address;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolation;
import org.jooq.DSLContext;

import java.util.List;
import java.util.Set;

import static kotowari.restful.DecisionPoint.*;
import static kotowari.restful.example.data.Address.*;
import static org.jooq.impl.DSL.table;

/**
 * Resource class for the {@code /addresses} collection endpoint.
 *
 * <p>Supported operations:
 * <ul>
 *   <li>GET  /addresses  — paginated list</li>
 *   <li>POST /addresses  — create a new address; responds 201 with the persisted entity</li>
 * </ul>
 */
@AllowedMethods({"GET", "POST"})
public class AddressesResource {
    @Inject
    private BeansValidator validator;

    @Inject
    private BeansConverter beansConverter;

    @Decision(value = MALFORMED, method={"GET"})
    public Problem isMalformed(Parameters params, RestContext context) {
        AddressSearchParams searchParams = beansConverter.createFrom(params, AddressSearchParams.class);
        context.putValue(searchParams);
        Set<ConstraintViolation<AddressSearchParams>> violations = validator.validate(searchParams);
        if (violations.isEmpty()) return null;
        List<Problem.Violation> violationList = violations.stream()
                .map(v -> new Problem.Violation(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return Problem.fromViolationList(violationList);
    }

    @Decision(value = MALFORMED, method={"POST"})
    public Problem isPostMalformed(Address body) {
        if (body.street() == null || body.street().isBlank()) {
            return Problem.valueOf(400, "Street is required");
        }
        if (body.city() == null || body.city().isBlank()) {
            return Problem.valueOf(400, "City is required");
        }
        if (body.countryCode() == null || body.countryCode().length() != 2) {
            return Problem.valueOf(400, "Country code must be 2 characters");
        }
        return null;
    }

    @Decision(HANDLE_OK)
    public List<Address> handleOk(AddressSearchParams params, DSLContext dsl) {
        return dsl.select(ID, CARE_OF, STREET, ADDITIONAL, CITY, ZIP, COUNTRY_CODE)
                .from(table("address"))
                .offset(params.getOffset())
                .limit(params.getLimit())
                .fetch()
                .map(Address::fromRecord);
    }

    @Transactional
    @Decision(POST)
    public boolean create(Address body, DSLContext dsl, RestContext context) {
        var rec = dsl.insertInto(table("address"),
                        COUNTRY_CODE, ZIP, CITY, STREET, ADDITIONAL, CARE_OF)
                .values(body.countryCode(), body.zip(), body.city(),
                        body.street(), body.additional(), body.careOf())
                .returningResult(ID)
                .fetchOne();

        Address created = new Address(
                rec.get(ID),
                body.careOf(), body.street(), body.additional(),
                body.city(), body.zip(), body.countryCode()
        );
        context.putValue(created);
        return true;
    }

    @Decision(HANDLE_CREATED)
    public Address handleCreated(Address address) {
        return address;
    }
}
