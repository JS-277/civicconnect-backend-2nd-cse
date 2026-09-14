package prototype.backend;

import java.sql.Connection;
import java.sql.DriverManager;

public class DatabaseConnection {

    private static final String URL =
            "jdbc:mysql://localhost:3306/civicconnect";

    private static final String USER = "root";

    // Put YOUR MySQL password here
    private static final String PASSWORD = "iamjs0002";

    public static Connection getConnection() {

        try {

            Connection connection =
                    DriverManager.getConnection(URL, USER, PASSWORD);

            System.out.println("Database connected successfully!");

            return connection;

        } catch (Exception e) {

            System.out.println("Database connection failed!");

            e.printStackTrace();

            return null;
        }
    }
}