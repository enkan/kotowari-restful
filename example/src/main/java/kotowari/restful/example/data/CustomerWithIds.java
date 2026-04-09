package kotowari.restful.example.data;

import java.util.ArrayList;
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
    /**
     * Builds a new {@link CustomerWithIds} reflecting a promote-to-primary operation,
     * preserving the original DB ids of each contact method row.
     *
     * <p>The contact method in {@code promoted.primaryContactMethod()} is identified
     * among the current secondaries by value equality, its DB id becomes the new
     * primary id, and the old primary is prepended to the remaining secondaries in
     * their original order.
     *
     * @param promoted the new {@link Customer} returned by the promotion behavior
     * @return a new {@link CustomerWithIds} with updated CM ordering and preserved ids
     * @throws java.util.NoSuchElementException if the promoted primary is not among the current secondaries
     */
    public CustomerWithIds withPromoted(Customer promoted) {
        ContactMethod newPrimary = promoted.primaryContactMethod();
        long newPrimaryId = secondaryCmIds.stream()
                .filter(e -> e.getValue().equals(newPrimary))
                .mapToLong(Map.Entry::getKey)
                .findFirst()
                .orElseThrow();

        List<Map.Entry<Long, ContactMethod>> newSecondaries = new ArrayList<>(secondaryCmIds.size());
        newSecondaries.add(Map.entry(primaryCmId, customer.primaryContactMethod()));
        for (Map.Entry<Long, ContactMethod> e : secondaryCmIds) {
            if (e.getKey() != newPrimaryId) {
                newSecondaries.add(e);
            }
        }
        return new CustomerWithIds(promoted, newPrimaryId, List.copyOf(newSecondaries));
    }
}
