package com.ormanager.orm;

import com.ormanager.orm.test_entities.Book;
import com.ormanager.orm.test_entities.Publisher;
import com.ormanager.jdbc.ConnectionToDB;
import com.ormanager.orm.exception.IdAlreadySetException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class PersistAndSaveTests {
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
        Publisher publisherWithId = (Publisher) ormManager.save(new Publisher("Test"));

        //THEN
        assertThrows(IdAlreadySetException.class, () -> ormManager.persist(publisherWithId));
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
        assertEquals(recordsBeforeSave + 1, recordsAfterSave);
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
        assertEquals(recordsBeforeSave + 1, recordsAfterSave);
    }

    @Test
    void givenObjectWhenSavingThenRowsInDbHaveTheSameValue() {
        //GIVEN
        Publisher publisher = (Publisher) ormManager.save(new Publisher("worst publisher"));
        Book book = new Book("Example", LocalDate.now());
        book.setPublisher(publisher);
        Book bookToSave = (Book) ormManager.save(book);
        Book bookToTest;
        Long bookId = bookToSave.getId();

        //WHEN
        try (Connection connection = ConnectionToDB.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM books WHERE ID=" + bookId + ";")) {
            preparedStatement.executeQuery();
            ResultSet resultSet = preparedStatement.getResultSet();
            bookToTest = new Book();
            resultSet.next();
            bookToTest.setId(resultSet.getLong(1));
            bookToTest.setTitle(resultSet.getString(2));
            bookToTest.setPublishedAt(resultSet.getDate(3).toLocalDate());
            bookToTest.setPublisher(ormManager.findById(resultSet.getLong(4), Publisher.class).get());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        //THEN
        assertEquals(bookToSave, bookToTest);
    }

    @Test
    void givenObjectWhenSavingThenItGoesToCache() {
        //GIVEN
        Publisher publisher = (Publisher) ormManager.save(new Publisher("New Era"));
        //THEN
        assertTrue(ormManager.getOrmCache().isRecordInCache(publisher.getId(), Publisher.class));
    }

    @Test
    void givenObjectWhenPersistingThenItGoesToCache() throws SQLException, IllegalAccessException {
        //GIVEN
        Book book = new Book("Example", LocalDate.now());

        //WHEN
        ormManager.persist(book);
        Long recordsInCache = ormManager.getOrmCache().count(Book.class);
        //THEN
        assertEquals(1, recordsInCache);
    }

    @Test
    void givenObjectWithIdAlreadySetWhenSavingThenItMerges() throws SQLException {
        //GIVEN
        Book bookWithId = (Book) ormManager.save(new Book("example", LocalDate.now()));
        //WHEN
        bookWithId.setTitle("new title");
        int recordsBeforeSave;
        int recordsAfterSave;
        //WHEN
        try (Connection connection = ConnectionToDB.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement("SELECT COUNT(*) FROM Books")) {
            preparedStatement.executeQuery();
            ResultSet resultSet = preparedStatement.getResultSet();
            resultSet.next();
            recordsBeforeSave = resultSet.getInt(1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        ormManager.save(bookWithId);
        Long recordsInCache = ormManager.getOrmCache().count(Book.class);
        try (Connection connection = ConnectionToDB.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement("SELECT COUNT(*) FROM Books")) {
            preparedStatement.executeQuery();
            ResultSet resultSet = preparedStatement.getResultSet();
            resultSet.next();
            recordsAfterSave = resultSet.getInt(1);
        }
        //THEN
        assertAll(
                () -> assertEquals(1, recordsInCache),
                () -> assertSame(recordsBeforeSave, recordsAfterSave),
                () -> assertEquals(1, ormManager.getOrmCache().count(Book.class)),
                () -> assertSame("new title", ormManager.findById(bookWithId.getId(), Book.class).get().getTitle()
                ));

    }
}
