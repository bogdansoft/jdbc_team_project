package com.ormanager.orm;

import com.ormanager.jdbc.ConnectionToDB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

public class ConnectionToDBTest {

    @BeforeAll
    public static void setUp() {
        ConnectionToDB.setFileName("src/test/resources/application_test.properties");
    }

    @Test
    @DisplayName("1. Connection should not be null")
    void whenConnectionEstablished_Expect_connectionNotNull() throws Exception {
        Connection connection = ConnectionToDB.getConnection();
        assertNotNull(connection);
    }

    @Test
    @DisplayName("2. Connection should be closed")
    void whenConnectionEstablishedAndClosed_Expect_connectionIsClosed() throws Exception {
        Connection connection = ConnectionToDB.getConnection();
        connection.close();
        assertTrue(connection.isClosed());
    }

    @Test
    @DisplayName("3. Connection should not be null")
    void whenConnectionsEstablished_Expect_connectionsAreNotNullAndNotEquals() throws Exception {
        Connection connection = ConnectionToDB.getConnection();
        Connection connection2 = ConnectionToDB.getConnection();
        assertNotNull(connection);
        assertNotNull(connection2);
        assertNotEquals(connection, connection2);
    }

    @Test
    @DisplayName("4. Connection should be open return true")
    void whenConnectionEstablishedAndQueryExecuted_Expect_resultIsTrue() throws Exception {
        Connection connection = ConnectionToDB.getConnection();
        boolean result = connection.prepareStatement("SELECT 1").execute();
        assertTrue(result);
    }
}
