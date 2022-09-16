package com.ormanager.orm;

import com.ormanager.orm.test_entities.Book;
import com.ormanager.orm.test_entities.Publisher;
import com.ormanager.jdbc.ConnectionToDB;
import com.ormanager.orm.exception.OrmFieldTypeException;
import com.ormanager.orm.test_entities.AllFieldsClass;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.NoSuchElementException;

import static com.ormanager.orm.OrmManagerUtil.getSqlTypeForField;
import static com.ormanager.orm.mapper.ObjectMapper.mapperToList;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class UnitTests {

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
    void getSqlTypeForField_ShouldReturnOrmFieldTypeException() throws NoSuchFieldException {
        //GIVEN
        Field field = Character.class.getField("MIN_VALUE");

        //THEN
        assertThrows(OrmFieldTypeException.class, () -> getSqlTypeForField(field));
    }

    @Test
    void whenUsingFindAllAsIterable_ShouldBeLazyLoading() throws Exception {
        //GIVEN
        Publisher publisher1 = new Publisher("saveTestPublisher1");
        Publisher publisher2 = new Publisher("saveTestPublisher2");
        Publisher publisher3 = new Publisher("saveTestPublisher3");

        //WHEN
        ormManager.getOrmCache().clearCache();
        ormManager.save(publisher1);
        ormManager.save(publisher2);
        ormManager.save(publisher3);
        ormManager.getOrmCache().deleteFromCache(publisher1);
        ormManager.getOrmCache().deleteFromCache(publisher2);
        ormManager.getOrmCache().deleteFromCache(publisher3);
        var iterator = ormManager.findAllAsIterable(Publisher.class);
        int counter = 0;
        try(iterator){
            while(iterator.hasNext() && counter<1){
                counter++;
                iterator.next();
            }
        }

        //THEN
        assertEquals(ormManager.getOrmCache().count(Publisher.class), counter);
    }

    @Test
    void whenUsingFindAllAsIterable_ShouldReturnOnePublisher() throws Exception {
        //GIVEN
        Publisher publisher1 = new Publisher("saveTestPublisher1");
        Publisher publisherFromDb=null;
        //WHEN
        ormManager.save(publisher1);
        var iterator = ormManager.findAllAsIterable(Publisher.class);
        int counter = 0;
        try(iterator){
            while(iterator.hasNext() && counter<1){
                counter++;
                publisherFromDb = iterator.next();
            }
        }
        //THEN
        assertEquals(publisher1, publisherFromDb);
    }

    @Test
    void whenUsingFindAllAsStream_ShouldBeLazyLoading() throws Exception {
        //GIVEN
        Publisher publisher1 = new Publisher("saveTestPublisher1");
        Publisher publisher2 = new Publisher("saveTestPublisher2");
        Publisher publisher3 = new Publisher("saveTestPublisher3");

        //WHEN
        ormManager.getOrmCache().clearCache();
        ormManager.save(publisher1);
        ormManager.save(publisher2);
        ormManager.save(publisher3);
        ormManager.getOrmCache().deleteFromCache(publisher1);
        ormManager.getOrmCache().deleteFromCache(publisher2);
        ormManager.getOrmCache().deleteFromCache(publisher3);
        var stream = ormManager.findAllAsStream(Publisher.class);
        var list= stream.limit(2).toList();

        //THEN
        assertEquals(ormManager.getOrmCache().count(Publisher.class), list.size());
    }

    @Test
    void whenUsingFindAllAsStream_ShouldReturnOnePublisher() throws Exception {
        //GIVEN
        Publisher publisher1 = new Publisher("saveTestPublisher1");

        //WHEN
        ormManager.getOrmCache().clearCache();
        ormManager.save(publisher1);
        var stream = ormManager.findAllAsStream(Publisher.class);
        var list= stream.limit(1).toList();

        //THEN
        assertEquals(publisher1, list.get(0));
        assertEquals(1, list.size());
    }

    @Test
    void findById_ShouldReturnPublisherFromDatabaseByGivenId() {
        //GIVEN
        Publisher publisher = new Publisher("test");
        Long expectedId = 0L;

        //WHEN
        ormManager.save(publisher);
        var id = publisher.getId();
        try (Connection connection = ConnectionToDB.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM Publishers WHERE id = " + id + ";")) {
            preparedStatement.executeQuery();
            ResultSet resultSet = preparedStatement.getResultSet();
            resultSet.next();
            expectedId = resultSet.getLong(1);
        } catch (SQLException e) {
            LOGGER.info(e.toString());
        }

        //THEN
        assertEquals(publisher, ormManager.findById(publisher.getId(), Publisher.class).orElseThrow());
        assertEquals(expectedId, publisher.getId());
    }

    @Test
    void findById_ShouldReturnBookFromDatabaseByGivenId() {
        //GIVEN
        Book book = new Book("test", LocalDate.now());
        Long expectedId = 0L;

        //WHEN
        var pub = new Publisher("test");
        ormManager.save(pub);
        book.setPublisher(pub);
        ormManager.save(book);
        var id = book.getId();
        try (Connection connection = ConnectionToDB.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM Books WHERE id = " + id + ";")) {
            preparedStatement.executeQuery();
            ResultSet resultSet = preparedStatement.getResultSet();
            resultSet.next();
            expectedId = resultSet.getLong(1);
        } catch (SQLException e) {
            LOGGER.info(e.toString());
        }

        //THEN
        assertEquals(book, ormManager.findById(book.getId(), Book.class).orElseThrow());
        assertEquals(expectedId, book.getId());
    }

    @Test
    void findByIdPublisherNullValue_ShouldReturnNoSuchElementException() {
        //GIVEN
        Publisher publisher = new Publisher();

        //THEN
        assertThrows(NoSuchElementException.class, () -> ormManager.findById(publisher.getId(), Publisher.class));
    }

    @Test
    void findByIdBookNullValue_ShouldReturnNoSuchElementException() {
        //GIVEN
        Book book = new Book();

        //THEN
        assertThrows(NoSuchElementException.class, () -> ormManager.findById(book.getId(), Book.class));
    }

    @Test
    void findAllPublishersTest() throws SQLException {
        //GIVEN
        Publisher publisher = new Publisher("saveTestPublisher");
        List<Publisher> publishers;

        //WHEN
        ormManager.save(publisher);
        try (Connection connection = ConnectionToDB.getConnection()) {
            ResultSet resultSet = connection.createStatement().executeQuery("SELECT * FROM Publishers;");
            publishers = mapperToList(resultSet, Publisher.class);
        }
        var findAllList = ormManager.findAll(Publisher.class);

        //THEN
        assertTrue(publishers.size() > 0);
        assertEquals(findAllList.size(), publishers.size());
    }

    @Test
    void findAllBooksTest() throws SQLException {
        //GIVEN
        Book book = new Book("test", LocalDate.now());
        List<Book> books;

        //WHEN
        var pub = new Publisher("test");
        ormManager.save(pub);
        book.setPublisher(pub);
        ormManager.save(book);
        try (Connection connection = ConnectionToDB.getConnection()) {
            ResultSet resultSet = connection.createStatement().executeQuery("SELECT * FROM Books;");
            books = mapperToList(resultSet, Book.class);
        }
        var findAllList = ormManager.findAll(Book.class);

        //THEN
        assertTrue(books.size() > 0);
        assertEquals(findAllList.size(), books.size());
    }

    @Test
    void givenPublisherIsUpdated_thenAssertId() {
        //GIVEN
        Publisher publisher = new Publisher("Test1");

        //WHEN
        ormManager.save(publisher);
        var id = publisher.getId();
        Publisher publisher1 = ormManager.findById(id, Publisher.class).get();

        //THEN
        assertEquals(id, ((Publisher) ormManager.update(publisher1)).getId());
    }

    @Test
    void givenBookIsUpdated_thenAssertId() {
        //GIVEN
        Publisher publisher = new Publisher("Test1");
        ormManager.save(publisher);
        Book book = new Book("Harry Potter", LocalDate.now());
        book.setPublisher(publisher);

        //WHEN
        ormManager.save(book);
        var id = book.getId();
        Book book1 = ormManager.findById(id, Book.class).get();

        //THEN
        assertEquals(id, ((Book) ormManager.update(book1)).getId());
    }

    @Test
    void givenPublisherSetNewName_whenUpdatePublisher_thenAssertName() {
        //GIVEN
        Publisher publisher = new Publisher("Test1");

        //WHEN
        ormManager.save(publisher);
        var id = publisher.getId();
        Publisher publisher1 = ormManager.findById(id, Publisher.class).get();

        publisher1.setName("Test2");
        var name = ((Publisher) ormManager.update(publisher1)).getName();

        //THEN
        assertEquals("Test1", name);
    }

    @Test
    void givenBookSetNewTitle_whenUpdatePublisher_thenAssertTitle() {
        //GIVEN
        Publisher publisher = new Publisher("Test2");
        ormManager.save(publisher);
        Book book = new Book("Lord of the rings", LocalDate.now());
        book.setPublisher(publisher);

        //WHEN
        ormManager.save(book);
        var id = book.getId();
        Book book1 = ormManager.findById(id, Book.class).get();

        book1.setTitle("Alice in the wonderland");
        var title = ((Book) ormManager.update(book1)).getTitle();

        //THEN
        assertEquals("Lord of the rings", title);
    }

    @Test
    void givenPublisherAddBook_whenUpdatePublisher_thenAssertBooks() {
        //GIVEN
        Publisher publisher = new Publisher("Test2");
        ormManager.save(publisher);
        Book book = new Book("Lord of the rings", LocalDate.now());
        book.setPublisher(publisher);
        ormManager.save(book);
        publisher.getBooks().add(book);

        //WHEN
        Book book1 = new Book("Alice in the wonderland", LocalDate.now());
        publisher.getBooks().add(book1);
        Publisher publisher1 = (Publisher) ormManager.update(publisher);

        List<Book> lists = ormManager.findById(publisher1.getId(), Publisher.class).get().getBooks();

        //THEN
        System.out.println("Test dane    " + ormManager.findById(1L, Book.class).get());
        assertEquals(1, lists.size());
    }

    @Test
    void givenBookSetPublisherAndSaved_whenAddNewAndUpdate_thenAssertIdOfPublisher() {
        //GIVEN
        Publisher publisher = new Publisher("Test");
        ormManager.save(publisher);
        Book book = new Book("Lord of the rings", LocalDate.now());
        book.setPublisher(publisher);
        ormManager.save(book);

        //WHEN
        Publisher publisher1 = new Publisher("Test2");
        ormManager.save(publisher1);
        book.setPublisher(publisher1);
        Book book1 = (Book) ormManager.update(book);

        //THEN
        assertEquals(publisher.getId(), book1.getPublisher().getId());
    }

    @Test
    void givenPublisherIsMerged_thenAssertResultAndName() {
        //GIVEN
        Publisher publisher = new Publisher("testPub");
        ormManager.save(publisher);

        //WHEN
        var name = "Test123";
        publisher.setName(name);
        var expectedResult = ormManager.merge(publisher);
        var mergedName = ormManager.findById(publisher.getId(), Publisher.class).get().getName();

        //THEN
        assertTrue(expectedResult);
        assertEquals(name, mergedName);
    }

    @Test
    void givenBookIsMerged_thenAssertResultAndTitle() {
        //GIVEN
        Publisher publisher = new Publisher("Test");
        ormManager.save(publisher);
        Book book = new Book("Lord of the rings", LocalDate.now());
        book.setPublisher(publisher);
        ormManager.save(book);

        //WHEN
        var title = "Alice in the wonderland";
        book.setTitle(title);
        var expectedResult = ormManager.merge(book);
        var mergedTitle = ormManager.findById(book.getId(), Book.class).get().getTitle();

        //THEN
        assertTrue(expectedResult);
        assertEquals(title, mergedTitle);
    }

    @Test
    void givenPublisherGetBook_whenPublisherIsMerged_thenBookShouldBeSaved() {
        //GIVEN
        Publisher publisher = new Publisher("testPub21");
        ormManager.save(publisher);
        Book book1 = new Book("Book11", LocalDate.now());
        publisher.getBooks().add(book1);

        //WHEN
        var expectedResult = ormManager.merge(publisher);
        List<Book> lists = ormManager.findById(publisher.getId(), Publisher.class).get().getBooks();


        //THEN
        assertTrue(expectedResult);
        assertEquals(1, lists.size());
        assertEquals(lists.get(0).getPublisher(), publisher);
    }

    @Test
    void mapStatementShouldBeAbleToMapDifferentTypes() throws SQLException, NoSuchFieldException {
        //GIVEN
        ormManager.register(AllFieldsClass.class);
        AllFieldsClass afc = new AllFieldsClass();
        long longTest = 123L;
        int intTest = 10;
        Integer wrapperIntegerTest = 15;
        double doubleTest = 3.14d;
        Double wrapperDoubleTest = 2.71d;
        boolean booleanTest = true;
        Boolean wrapperBooleanTest = false;
        String stringTest = "Super text here";
        LocalDate localDateTest = LocalDate.of(1999, 03, 01);
        LocalTime localTimeTest = LocalTime.of(21, 37, 59);
        LocalDateTime localDateTimeTest = LocalDateTime.of(2000, 06, 02, 12, 45);

        //WHEN
        afc.setLongTest(longTest);
        afc.setIntTest(intTest);
        afc.setWrapperIntegerTest(wrapperIntegerTest);
        afc.setDoubleTest(doubleTest);
        afc.setWrapperDoubleTest(wrapperDoubleTest);
        afc.setBooleanTest(booleanTest);
        afc.setWrapperBooleanTest(wrapperBooleanTest);
        afc.setStringTest(stringTest);
        afc.setLocalDateTest(localDateTest);
        afc.setLocalTimeTest(localTimeTest);
        afc.setLocalDateTimeTest(localDateTimeTest);
        AllFieldsClass allFieldsClass = (AllFieldsClass) ormManager.save(afc);
        AllFieldsClass afcFromDb = ormManager.findById(allFieldsClass.getId(), AllFieldsClass.class).get();

        //THEN
        assertAll(
                () -> assertEquals(intTest, afcFromDb.getIntTest()),
                () -> assertEquals(wrapperIntegerTest, afcFromDb.getWrapperIntegerTest()),
                () -> assertEquals(longTest, afcFromDb.getLongTest()),
                () -> assertEquals(doubleTest, afcFromDb.getDoubleTest()),
                () -> assertEquals(wrapperDoubleTest, afcFromDb.getWrapperDoubleTest()),
                () -> assertEquals(booleanTest, afcFromDb.getBooleanTest()),
                () -> assertEquals(wrapperBooleanTest, afcFromDb.getWrapperBooleanTest()),
                () -> assertEquals(stringTest, afcFromDb.getStringTest()),
                () -> assertEquals(localDateTest, afcFromDb.getLocalDateTest()),
                () -> assertEquals(localTimeTest, afcFromDb.getLocalTimeTest()),
                () -> assertEquals(localDateTimeTest, afcFromDb.getLocalDateTimeTest())
        );
    }
}
