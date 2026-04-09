package kotowari.restful.example.data;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerWithIdsTest {

    private static ContactMethod email(String label, String address) {
        return new ContactMethod.Email(new EmailContactInfo(new String50(label), new EmailAddress(address)));
    }

    private static PersonalName name() {
        return new PersonalName(new String50("Taro"), java.util.Optional.empty(), new String100("Yamada"));
    }

    @Test
    void promotesSecondaryToPrimaryPreservingIds() {
        ContactMethod originalPrimary = email("work", "work@example.com");
        ContactMethod sec1 = email("home", "home@example.com");
        ContactMethod sec2 = email("mobile", "mobile@example.com");

        Customer original = new Customer(name(), originalPrimary, List.of(sec1, sec2));
        CustomerWithIds existing = new CustomerWithIds(
                original,
                10L,
                List.of(Map.entry(20L, sec1), Map.entry(30L, sec2))
        );

        // Simulated result of PromoteToPrimary: sec1 becomes primary, old primary goes to secondaries
        Customer promoted = new Customer(name(), sec1, List.of(originalPrimary, sec2));

        CustomerWithIds result = existing.withPromoted(promoted);

        assertThat(result.customer()).isSameAs(promoted);
        assertThat(result.primaryCmId()).isEqualTo(20L);
        assertThat(result.secondaryCmIds()).containsExactly(
                Map.entry(10L, originalPrimary),
                Map.entry(30L, sec2)
        );
    }

    @Test
    void promotesLastSecondary() {
        ContactMethod originalPrimary = email("work", "work@example.com");
        ContactMethod sec1 = email("home", "home@example.com");
        ContactMethod sec2 = email("mobile", "mobile@example.com");

        Customer original = new Customer(name(), originalPrimary, List.of(sec1, sec2));
        CustomerWithIds existing = new CustomerWithIds(
                original,
                10L,
                List.of(Map.entry(20L, sec1), Map.entry(30L, sec2))
        );

        Customer promoted = new Customer(name(), sec2, List.of(originalPrimary, sec1));

        CustomerWithIds result = existing.withPromoted(promoted);

        assertThat(result.primaryCmId()).isEqualTo(30L);
        assertThat(result.secondaryCmIds()).containsExactly(
                Map.entry(10L, originalPrimary),
                Map.entry(20L, sec1)
        );
    }

    @Test
    void throwsWhenPromotedPrimaryNotInSecondaries() {
        ContactMethod originalPrimary = email("work", "work@example.com");
        ContactMethod sec1 = email("home", "home@example.com");
        ContactMethod unknown = email("fax", "fax@example.com");

        Customer original = new Customer(name(), originalPrimary, List.of(sec1));
        CustomerWithIds existing = new CustomerWithIds(
                original,
                10L,
                List.of(Map.entry(20L, sec1))
        );

        Customer bogus = new Customer(name(), unknown, List.of(originalPrimary));

        assertThatThrownBy(() -> existing.withPromoted(bogus))
                .isInstanceOf(NoSuchElementException.class);
    }
}
