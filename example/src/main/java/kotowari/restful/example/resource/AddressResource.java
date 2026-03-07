package kotowari.restful.example.resource;

import enkan.collection.Parameters;
import kotowari.restful.Decision;
import kotowari.restful.data.Problem;
import kotowari.restful.data.RestContext;
import kotowari.restful.example.data.Address;
import jakarta.transaction.Transactional;
import kotowari.restful.resource.AllowedMethods;

import org.jooq.DSLContext;
import org.jooq.Record;

import static kotowari.restful.DecisionPoint.*;
import static kotowari.restful.example.data.Address.*;
import static org.jooq.impl.DSL.table;

/**
 * Resource class for the {@code /addresses/:id} single-item endpoint.
 *
 * <p>Supported operations:
 * <ul>
 *   <li>GET    /addresses/:id — return the address or 404</li>
 *   <li>PUT    /addresses/:id — validate and replace the address body</li>
 *   <li>DELETE /addresses/:id — remove the address</li>
 * </ul>
 */
@AllowedMethods({"GET", "PUT", "DELETE"})
public class AddressResource {

    @Decision(EXISTS)
    public boolean exists(Parameters params, DSLContext dsl, RestContext context) {
        Long id = Long.valueOf(params.get("id").toString());
        Record rec = dsl.select(ID, CARE_OF, STREET, ADDITIONAL, CITY, ZIP, COUNTRY_CODE)
                .from(table("address"))
                .where(ID.eq(id))
                .fetchOne();
        if (rec == null) return false;
        context.putValue(Address.fromRecord(rec));
        return true;
    }

    @Decision(value = MALFORMED, method = {"PUT"})
    public Problem validatePut(Address body) {
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
    public Address show(Address address) {
        return address;
    }

    @Transactional
    @Decision(PUT)
    public void update(Address body, Address address, DSLContext dsl, RestContext context) {
        dsl.update(table("address"))
                .set(CARE_OF, body.careOf())
                .set(STREET, body.street())
                .set(ADDITIONAL, body.additional())
                .set(CITY, body.city())
                .set(ZIP, body.zip())
                .set(COUNTRY_CODE, body.countryCode())
                .where(ID.eq(address.id()))
                .execute();

        Address updated = new Address(
                address.id(), body.careOf(), body.street(), body.additional(),
                body.city(), body.zip(), body.countryCode()
        );
        context.putValue(updated);
    }

    @Decision(value = NEW, method = {"PUT"})
    public boolean isNew() {
        return false;
    }

    @Decision(value = RESPOND_WITH_ENTITY, method = {"PUT"})
    public boolean respondWithEntity() {
        return true;
    }

    @Transactional
    @Decision(DELETE)
    public void destroy(Address address, DSLContext dsl) {
        dsl.deleteFrom(table("address"))
                .where(ID.eq(address.id()))
                .execute();
    }
}
