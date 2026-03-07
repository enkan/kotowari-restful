package kotowari.restful.example.behavior;

import kotowari.restful.example.data.ContactMethod;
import kotowari.restful.example.data.Customer;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Adds a new secondary {@link ContactMethod} to a {@link Customer}.
 *
 * <p>Duplicate detection uses Java record {@code equals} (all-field equality).
 * Returns {@link Result#fail} with code {@code "duplicate"} if the given contact method
 * is already present as the primary or in the secondary list.
 */
public class AddSecondaryContactMethod implements BiFunction<Customer, ContactMethod, Result<Customer>> {

    @Override
    public Result<Customer> apply(Customer customer, ContactMethod toAdd) {
        if (customer.primaryContactMethod().equals(toAdd) ||
                customer.secondaryContactMethods().contains(toAdd)) {
            return Result.fail(Path.ROOT, "duplicate", "contact method already exists");
        }
        List<ContactMethod> newSecondary = new ArrayList<>(customer.secondaryContactMethods());
        newSecondary.add(toAdd);
        return Result.ok(new Customer(customer.name(), customer.primaryContactMethod(), List.copyOf(newSecondary)));
    }
}
