package com.ormanager.orm;

import com.ormanager.client.entity.Book;
import com.ormanager.client.entity.Publisher;
import com.ormanager.jdbc.ConnectionToDB;
import com.ormanager.orm.exception.IdAlreadySetException;
import com.ormanager.orm.test_entities.TestClassIdString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class persistAndSaveTests {
    private IOrmManager ormManager;

    @BeforeEach
    void setUp() throws SQLException, NoSuchFieldException {
        ormManager = OrmManager.withPropertiesFrom("src/test/resources/application_test.properties");
        ormManager.dropEntity(Book.class);
        ormManager.dropEntity(Publisher.class);

        var entityClassesAsSet = ClassScanner.getClassesMarkedAsEntity();
        var entityClassesAsArray = new Class<?>[entityClassesAsSet.size()];
        entityClassesAsSet.toArray(entityClassesAsArray);
        ormManager.register(entityClassesAsArray);
        ormManager.createRelationships(entityClassesAsArray);
    }

    @Test
    void givenObjectWithIdAlreadySetWhenPersistingThenThrowsIdAlreadySetException() {
        //GIVEN
        Publisher publisher = new Publisher("test");

        //THEN
        assertThrows(IdAlreadySetException.class, () -> ormManager.persist(ormManager.save(publisher)));
    }

    @Test
    void givenObjectWithIdOfTypeStringAlreadySetWhenPersistingThenDoesntThrowIdAlreadySetException() throws SQLException, NoSuchFieldException {
        //GIVEN
        TestClassIdString testClassIdString = new TestClassIdString("test");

        //WHEN
        ormManager.register(TestClassIdString.class);

        //THEN
        assertDoesNotThrow(() -> ormManager.persist(testClassIdString));
    }

    @Test
    void givenPublisherWhenSavingThenReturnsObjectWithAutoGeneratedIdFromDB() throws SQLException {
        //GIVEN
        Publisher publisher = new Publisher("test");
        Long expectedId;

        //WHEN
        ormManager.save(publisher);
        try (Connection connection = ConnectionToDB.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM Publishers WHERE id = (SELECT MAX(id) from Publishers);")) {
            preparedStatement.executeQuery();
            ResultSet resultSet = preparedStatement.getResultSet();
            resultSet.next();
            expectedId = resultSet.getLong(1);
        }

        //THEN
        assertEquals(expectedId, publisher.getId());
    }

    @Test
    void givenBookWhenSavingThenReturnsObjectWithAutoGeneratedIdFromDB() throws SQLException {
        //GIVEN
        Book book = new Book("test", LocalDate.now());

        var pub = new Publisher("test");
        ormManager.save(pub);
        book.setPublisher(pub);
        Long expectedId;

        //WHEN
        ormManager.save(book);
        try (Connection connection = ConnectionToDB.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM Books WHERE id = (SELECT MAX(id) from Books);")) {
            preparedStatement.executeQuery();
            ResultSet resultSet = preparedStatement.getResultSet();
            resultSet.next();
            expectedId = resultSet.getLong(1);
        }

        //THEN
        assertEquals(expectedId, book.getId());
    }
    @Test
    void givenObjectWhenSavingThenDatabaseShouldHaveOneMoreRecord() throws SQLException {
        //GIVEN
        Book bookToSave = new Book("Example", LocalDate.of(1999, 10, 20));
        int recordsBeforeSave;
        int recordsAfterSave;
        //WHEN
        try (Connection connection = ConnectionToDB.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement("SELECT COUNT(*) FROM Books")) {
            preparedStatement.executeQuery();
            ResultSet resultSet = preparedStatement.getResultSet();
            resultSet.next();
            recordsBeforeSave = resultSet.getInt(1);
        }
        ormManager.save(bookToSave);
        try (Connection connection = ConnectionToDB.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement("SELECT COUNT(*) FROM Books")) {
            preparedStatement.executeQuery();
            ResultSet resultSet = preparedStatement.getResultSet();
            resultSet.next();
            recordsAfterSave = resultSet.getInt(1);
        }
        //THEN
        assertEquals(recordsBeforeSave+1, recordsAfterSave);
    }
    @Test
    void givenObjectWhenPersistingThenDatabaseShouldHaveOneMoreRecord() throws SQLException, IllegalAccessException {
        //GIVEN
        Publisher publisherToPersist = new Publisher("Best publisher");
        int recordsBeforeSave;
        int recordsAfterSave;
        //WHEN
        try (Connection connection = ConnectionToDB.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement("SELECT COUNT(*) FROM publishers")) {
            preparedStatement.executeQuery();
            ResultSet resultSet = preparedStatement.getResultSet();
            resultSet.next();
            recordsBeforeSave = resultSet.getInt(1);
        }
        ormManager.persist(publisherToPersist);
        try (Connection connection = ConnectionToDB.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement("SELECT COUNT(*) FROM publishers")) {
            preparedStatement.executeQuery();
            ResultSet resultSet = preparedStatement.getResultSet();
            resultSet.next();
            recordsAfterSave = resultSet.getInt(1);
        }
        //THEN
        assertEquals(recordsBeforeSave+1, recordsAfterSave);
    }

}
