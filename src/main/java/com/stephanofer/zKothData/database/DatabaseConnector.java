package com.stephanofer.zKothData.database;

import java.sql.Connection;
import java.sql.SQLException;

public interface DatabaseConnector {

    void closeConnection();

    void connect (ConnectionCallback callback);

    void connect(ConnectionCallback callback, boolean useTransaction);

    Connection connect() throws SQLException;

    Object getLock();
    boolean isFinished();
    void cleanup();

    interface ConnectionCallback {
        void accept(Connection connection) throws SQLException;
    }
}


