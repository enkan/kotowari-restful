package kotowari.restful.example.behavior;

import kotowari.restful.example.data.ContactMethod;
import kotowari.restful.example.data.Customer;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Promotes a secondary {@link ContactMethod} to be the primary contact method of a {@link Customer}.
 *
 * <p>The existing primary contact method is demoted to the head of the secondary list.
 * The relative order of the remaining secondary contact methods is preserved.
 *
 * <p>Returns {@link Result#fail} with code {@code "not_secondary"} if the given contact method
 * is not present in the customer's secondary list.
 */
public class PromoteToPrimary implements BiFunction<Customer, ContactMethod, Result<Customer>> {

    @Override
    public Result<Customer> apply(Customer customer, ContactMethod target) {
        if (!customer.secondaryContactMethods().contains(target)) {
            return Result.fail(Path.ROOT, "not_secondary", "contact method is not in the secondary list");
        }
        List<ContactMethod> newSecondary = new ArrayList<>();
        newSecondary.add(customer.primaryContactMethod());
        customer.secondaryContactMethods().stream()
                .filter(cm -> !cm.equals(target))
                .forEach(newSecondary::add);
        return Result.ok(new Customer(customer.name(), target, List.copyOf(newSecondary)));
    }
}
