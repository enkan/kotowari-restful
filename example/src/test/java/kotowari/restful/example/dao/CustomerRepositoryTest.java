package kotowari.restful.example.dao;

import kotowari.restful.example.data.*;
import org.h2.jdbcx.JdbcDataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerRepositoryTest {

    private DSLContext dsl;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:customertest;DB_CLOSE_DELAY=-1");
        dsl = DSL.using(ds, SQLDialect.H2);

        dsl.execute("CREATE SEQUENCE IF NOT EXISTS customer_id_seq START WITH 1 INCREMENT BY 50");
        dsl.execute("CREATE TABLE IF NOT EXISTS customer(" +
                "id BIGINT DEFAULT NEXT VALUE FOR customer_id_seq NOT NULL PRIMARY KEY," +
                "first_name VARCHAR(50) NOT NULL," +
                "middle_name VARCHAR(50)," +
                "last_name VARCHAR(100) NOT NULL)");
        dsl.execute("CREATE SEQUENCE IF NOT EXISTS contact_method_id_seq START WITH 1 INCREMENT BY 50");
        dsl.execute("CREATE TABLE IF NOT EXISTS contact_method(" +
                "id BIGINT DEFAULT NEXT VALUE FOR contact_method_id_seq NOT NULL PRIMARY KEY," +
                "customer_id BIGINT NOT NULL REFERENCES customer(id)," +
                "is_primary BOOLEAN NOT NULL DEFAULT FALSE," +
                "type VARCHAR(20) NOT NULL," +
                "label VARCHAR(50) NOT NULL," +
                "email_address VARCHAR(100)," +
                "address1 VARCHAR(100)," +
                "address2 VARCHAR(100)," +
                "city VARCHAR(50)," +
                "state VARCHAR(50)," +
                "zip_code VARCHAR(10))");
    }

    @AfterEach
    void tearDown() {
        dsl.execute("DROP TABLE IF EXISTS contact_method");
        dsl.execute("DROP TABLE IF EXISTS customer");
        dsl.execute("DROP SEQUENCE IF EXISTS customer_id_seq");
        dsl.execute("DROP SEQUENCE IF EXISTS contact_method_id_seq");
    }

    private Customer emailOnlyCustomer() {
        return new Customer(
                new PersonalName(
                        new String50("Taro"),
                        Optional.empty(),
                        new String100("Yamada")),
                new ContactMethod.Email(new EmailContactInfo(
                        new String50("work"),
                        new EmailAddress("taro@example.com"))),
                List.of()
        );
    }

    private Customer fullCustomer() {
        return new Customer(
                new PersonalName(
                        new String50("Hanako"),
                        Optional.of(new String50("M")),
                        new String100("Suzuki")),
                new ContactMethod.Email(new EmailContactInfo(
                        new String50("personal"),
                        new EmailAddress("hanako@example.com"))),
                List.of(
                        new ContactMethod.PostalAddress(new PostalContactInfo(
                                new String50("home"),
                                new String100("1-2-3 Shibuya"),
                                Optional.of(new String100("Apt 101")),
                                new String50("Tokyo"),
                                new String50("Tokyo"),
                                new ZipCode("150-0001")))
                )
        );
    }

    @Test
    void insertAndFindEmailOnlyCustomer() {
        CustomerRepository repo = new CustomerRepository(dsl);

        CustomerId id = repo.insert(emailOnlyCustomer());
        assertThat(id.value()).isPositive();

        Optional<Customer> found = repo.findById(id.value());
        assertThat(found).isPresent();

        Customer c = found.get();
        assertThat(c.name().firstName().value()).isEqualTo("Taro");
        assertThat(c.name().middleName()).isEmpty();
        assertThat(c.name().lastName().value()).isEqualTo("Yamada");
        assertThat(c.primaryContactMethod()).isInstanceOf(ContactMethod.Email.class);
        ContactMethod.Email email = (ContactMethod.Email) c.primaryContactMethod();
        assertThat(email.info().emailAddress().value()).isEqualTo("taro@example.com");
        assertThat(c.secondaryContactMethods()).isEmpty();
    }

    @Test
    void insertAndFindCustomerWithSecondaryContacts() {
        CustomerRepository repo = new CustomerRepository(dsl);

        CustomerId id = repo.insert(fullCustomer());
        Optional<Customer> found = repo.findById(id.value());
        assertThat(found).isPresent();

        Customer c = found.get();
        assertThat(c.name().firstName().value()).isEqualTo("Hanako");
        assertThat(c.name().middleName()).isPresent();
        assertThat(c.name().middleName().get().value()).isEqualTo("M");
        assertThat(c.secondaryContactMethods()).hasSize(1);

        ContactMethod.PostalAddress postal = (ContactMethod.PostalAddress) c.secondaryContactMethods().getFirst();
        assertThat(postal.info().address1().value()).isEqualTo("1-2-3 Shibuya");
        assertThat(postal.info().address2()).isPresent();
        assertThat(postal.info().city().value()).isEqualTo("Tokyo");
        assertThat(postal.info().zipCode().value()).isEqualTo("150-0001");
    }

    @Test
    void findByIdReturnsEmptyForNonExistent() {
        CustomerRepository repo = new CustomerRepository(dsl);
        assertThat(repo.findById(999)).isEmpty();
    }
}
