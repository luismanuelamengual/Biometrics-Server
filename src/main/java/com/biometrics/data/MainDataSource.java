package com.biometrics.data;

import org.neogroup.warp.data.DataSource;
import org.neogroup.warp.data.DataSourceComponent;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@DataSourceComponent("main")
public class MainDataSource extends DataSource {

    private final String databaseUrl;

    public MainDataSource() {
        String jdbDatabaseUrl = System.getenv("JDBC_DATABASE_URL");
        String databaseUrl = System.getenv("DATABASE_URL");
        if (jdbDatabaseUrl != null) {
            this.databaseUrl = jdbDatabaseUrl;
        } else if (databaseUrl != null) {
            try {
                URI dbUri = new URI(databaseUrl);
                String username = dbUri.getUserInfo().split(":")[0];
                String password = dbUri.getUserInfo().split(":")[1];
                this.databaseUrl = "jdbc:postgresql://" + dbUri.getHost() + ':' + dbUri.getPort() + dbUri.getPath() + "?sslmode=require&user=" + username + "&password=" + password;
            } catch (Exception ex) {
                throw new RuntimeException("Error in database initialization");
            }
        } else {
            this.databaseUrl = "jdbc:postgresql://localhost:5432/biometrics?user=postgres&password=postgres";
        }
    }

    @Override
    protected Connection requestConnection() throws SQLException {
        return DriverManager.getConnection(databaseUrl);
    }
}
