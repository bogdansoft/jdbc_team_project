package com.ormanager.orm;

import com.ormanager.client.entity.Publisher;
import org.junit.jupiter.api.Test;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UnitTests {
    String url = "jdbc:h2:./src/test/resources/h2-test-db/test";
    String login = "sa";
    String password = "";
    @Test
    void save_ShouldReturnAutoGeneratedIdOfPublisherFromDatabase() throws SQLException, IllegalAccessException {
        //GIVEN
        Publisher publisher = new Publisher("saveTestPublisher");
        Long expectedId;
        OrmManager ormManager = OrmManager.getConnectionWithArgmunets(url, login, password);
        //WHEN
        ormManager.save(publisher);
        try (Connection con = DriverManager.getConnection(url, login, password);
             PreparedStatement preparedStatement = con.prepareStatement("SELECT * FROM Publishers WHERE id = (SELECT MAX(id) from Publishers);")) {
            preparedStatement.executeQuery();
            ResultSet resultSet = preparedStatement.getResultSet();
            resultSet.next();
            expectedId = resultSet.getLong(1);
        }
        //THEN
        assertEquals(expectedId, publisher.getId());

    }
}