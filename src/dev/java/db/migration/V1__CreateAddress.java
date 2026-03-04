package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

public class V1__CreateAddress extends BaseJavaMigration {
    private static final String INS = "INSERT INTO address(country_code, zip, city, street) VALUES(?,?,?,?)";
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        try(Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE SEQUENCE address_id_seq START WITH 1 INCREMENT BY 50");
            stmt.execute("CREATE TABLE address(" +
                    "id BIGINT DEFAULT NEXT VALUE FOR address_id_seq NOT NULL PRIMARY KEY," +
                    "country_code VARCHAR(2)," +
                    "zip VARCHAR(20)," +
                    "city VARCHAR(100)," +
                    "street VARCHAR(100)," +
                    "additional VARCHAR(100)," +
                    "care_of VARCHAR(100)" +
                    ")");

        }

        try (PreparedStatement stmt = connection.prepareStatement(INS)) {
            stmt.setString(1, "JP");
            stmt.setString(2, "167-0051");
            stmt.setString(3, "Tokyo");
            stmt.setString(4, "Shibuya");
            stmt.executeUpdate();
            stmt.setString(1, "JP");
            stmt.setString(2, "555-5555");
            stmt.setString(3, "Osaka");
            stmt.setString(4, "Shinsaibashi");
            stmt.executeUpdate();
            connection.commit();
        }
    }
}
