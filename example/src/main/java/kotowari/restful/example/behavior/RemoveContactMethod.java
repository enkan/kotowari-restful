package kotowari.restful.example.behavior;

import kotowari.restful.example.data.ContactMethod;
import kotowari.restful.example.data.Customer;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;

import java.util.List;
import java.util.function.BiFunction;

/**
 * Removes a secondary {@link ContactMethod} from a {@link Customer}.
 *
 * <p>Returns {@link Result#fail} with code {@code "cannot_remove_primary"} if the given
 * contact method is the customer's primary contact method.
 * Returns {@link Result#fail} with code {@code "not_secondary"} if the given contact method
 * is not present in the secondary list.
 */
public class RemoveContactMethod implements BiFunction<Customer, ContactMethod, Result<Customer>> {

    @Override
    public Result<Customer> apply(Customer customer, ContactMethod target) {
        if (customer.primaryContactMethod().equals(target)) {
            return Result.fail(Path.ROOT, "cannot_remove_primary", "cannot remove the primary contact method");
        }
        if (!customer.secondaryContactMethods().contains(target)) {
            return Result.fail(Path.ROOT, "not_secondary", "contact method is not in the secondary list");
        }
        List<ContactMethod> newSecondary = customer.secondaryContactMethods().stream()
                .filter(cm -> !cm.equals(target))
                .toList();
        return Result.ok(new Customer(customer.name(), customer.primaryContactMethod(), newSecondary));
    }
}
