package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.Statement;

public class V2__CreateCustomer extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE SEQUENCE customer_id_seq START WITH 1 INCREMENT BY 50");
            stmt.execute("CREATE TABLE customer(" +
                    "id BIGINT DEFAULT NEXT VALUE FOR customer_id_seq NOT NULL PRIMARY KEY," +
                    "first_name VARCHAR(50) NOT NULL," +
                    "middle_name VARCHAR(50)," +
                    "last_name VARCHAR(100) NOT NULL" +
                    ")");

            stmt.execute("CREATE SEQUENCE contact_method_id_seq START WITH 1 INCREMENT BY 50");
            stmt.execute("CREATE TABLE contact_method(" +
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
                    "zip_code VARCHAR(10)" +
                    ")");

            connection.commit();
        }
    }
}
