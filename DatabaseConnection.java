package prototype.backend;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {

    public static Connection getConnection() {

        try {

            // Railway MySQL
            String host = System.getenv("MYSQLHOST");

            if (host != null && !host.isBlank()) {

                String port = System.getenv("MYSQLPORT");
                String database = System.getenv("MYSQLDATABASE");
                String user = System.getenv("MYSQLUSER");
                String password = System.getenv("MYSQLPASSWORD");

                String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

                Connection connection =
                        DriverManager.getConnection(url, user, password);

                System.out.println("Railway MySQL connected successfully!");

                return connection;
            }

            // Local MySQL fallback
            String url = "jdbc:mysql://localhost:3306/civicconnect";
            String user = "root";
            String password = System.getenv("LOCAL_DB_PASSWORD");

            Connection connection =
                    DriverManager.getConnection(url, user, password);

            System.out.println("Local MySQL connected successfully!");

            return connection;

        } catch (SQLException e) {

            System.out.println("Database connection failed!");
            e.printStackTrace();

            return null;
        }
    }
}
