package com.biometrics.data;

import org.neogroup.warp.data.DataSource;
import org.neogroup.warp.data.DataSourceComponent;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@DataSourceComponent("main")
public class MainDataSource extends DataSource {

    private final String databaseUrl;

    public MainDataSource() {
        String dbUrl = System.getenv("JDBC_DATABASE_URL");
        if (dbUrl == null) {
            databaseUrl = "jdbc:postgresql://localhost:5432/biometrics?user=postgres&password=postgres";
        } else {
            databaseUrl = dbUrl;
        }
    }

    @Override
    protected Connection requestConnection() throws SQLException {
        return DriverManager.getConnection(databaseUrl);
    }
}
