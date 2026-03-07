package kotowari.restful.example.data;

import java.util.List;
import java.util.Map;

/**
 * A {@link Customer} enriched with the database IDs of its contact methods.
 *
 * <p>Used at the persistence boundary to carry {@code contact_method.id} values
 * back to the API layer without leaking DB concerns into the domain model.
 *
 * @param customer         the domain customer
 * @param primaryCmId      the DB id of the primary contact method
 * @param secondaryCmIds   pairs of (DB id → secondary ContactMethod), in insertion order
 */
public record CustomerWithIds(
        Customer customer,
        long primaryCmId,
        List<Map.Entry<Long, ContactMethod>> secondaryCmIds
) {
}
