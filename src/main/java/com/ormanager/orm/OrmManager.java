package com.ormanager.orm;

import com.ormanager.jdbc.ConnectionToDB;
import com.ormanager.orm.annotation.*;
import com.ormanager.orm.mapper.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.sql.*;
import java.util.*;
import java.util.stream.Stream;

import static com.ormanager.orm.mapper.ObjectMapper.mapperToObject;

@Slf4j(topic = "OrmManager")
public class OrmManager<T> {

    private OrmManagerUtil<T> ormManagerUtil;
    private Connection con;

    public static <T> OrmManager<T> withPropertiesFrom(String filename) throws SQLException {
        ConnectionToDB.setFileName(filename);
        return new OrmManager<T>(ConnectionToDB.getConnection());
    }

    public static <T> OrmManager<T> getConnectionWithArgmunets(String url, String username, String password) throws SQLException {
        return new OrmManager<T>(url, username, password);
    }

    public static <T> OrmManager<T> withDataSource(DataSource dataSource) throws SQLException {
        return new OrmManager<T>(dataSource.getConnection());
    }

    private OrmManager(Connection connection) {
        this.con = connection;
    }

    private OrmManager(String url, String username, String password) throws SQLException {
        this.con = DriverManager.
                getConnection(url, username, password);
    }

    public void persist(T t) throws SQLException, IllegalAccessException {
        String sqlStatement = ormManagerUtil.getInsertStatement(t);

        try (PreparedStatement preparedStatement = con.prepareStatement(sqlStatement)) {
            ormManagerUtil.mapStatement(t, preparedStatement);
        }
    }

    public boolean doesEntityExists(Class<?> clazz) throws SQLException {
        var searchedEntityName = ormManagerUtil.getTableName(clazz);

        String checkIfEntityExistsSQL = "SELECT COUNT(*) FROM information_schema.TABLES " +
                "WHERE (TABLE_SCHEMA = 'test') AND (TABLE_NAME = '" + searchedEntityName + "');";

        try (Statement statement = con.createStatement()) {
            ResultSet resultSet = statement.executeQuery(checkIfEntityExistsSQL);
            resultSet.next();

            return resultSet.getInt(1) == 1;
        }
    }

    public void createRelationships(Class<?>... entityClasses) throws SQLException {
        for (var entity : entityClasses) {
            if (ormManagerUtil.doesClassHaveAnyRelationship(entity)) {
                createRelationships(entity);
            }
        }
    }

    public void createRelationships(Class<?> clazz) throws SQLException {
        for (var field : ormManagerUtil.getRelationshipFields(clazz, ManyToOne.class)) {

            var fieldClass = field.getType();
            var fieldTableAnnotationClassName = ormManagerUtil.getTableName(fieldClass);
            var fieldBasicClassNameWithId = fieldClass.getSimpleName().toLowerCase() + "_id";
            var fieldClassIdName = ormManagerUtil.getIdField(fieldClass).getName();

            if (doesEntityExists(fieldClass) && !(doesRelationshipAlreadyExist(clazz, fieldClass))) {

                var relationshipSQL = "ALTER TABLE " + ormManagerUtil.getTableName(clazz) + " ADD COLUMN " + fieldBasicClassNameWithId + " BIGINT UNSIGNED," +
                        " ADD FOREIGN KEY (" + fieldBasicClassNameWithId + ")" +
                        " REFERENCES " + fieldTableAnnotationClassName + "(" + fieldClassIdName + ") ON DELETE CASCADE;";

                LOGGER.info("Establishing relationship between entities: {} and {} is being processed now: " + relationshipSQL, clazz.getSimpleName().toUpperCase(), fieldClass.getSimpleName().toUpperCase());

                try (PreparedStatement statement = con.prepareStatement(relationshipSQL)) {
                    statement.execute();

                    LOGGER.info("Establishing relationship processed successfully!");
                }
            } else {
                if (!doesEntityExists(fieldClass)) {
                    var missingEntityName = fieldClass.getSimpleName();

                    throw new SQLException(String.format("Relationship between %s and %s cannot be made! Missing entity %s!", clazz.getSimpleName(), missingEntityName, missingEntityName));
                }
                LOGGER.info("Relationship between entities: {} and {} already exists.", clazz.getSimpleName().toUpperCase(), fieldClass.getSimpleName().toUpperCase());
            }
        }
    }

    public boolean isRecordInDataBase(T searchedRecord) {
        boolean isInDB = false;
        String tableName = searchedRecord.getClass().getAnnotation(Table.class).name();
        String queryCheck = String.format("SELECT count(*) FROM %s WHERE id = ?", tableName);

        try (PreparedStatement preparedStatement = con.prepareStatement(queryCheck)) {
            String recordId = ormManagerUtil.getRecordId(searchedRecord);

            preparedStatement.setString(1, recordId);
            LOGGER.info("SQL CHECK STATEMENT: {}", preparedStatement);

            final ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                int count = resultSet.getInt(1);
                isInDB = count == 1;
            }
        } catch (SQLException | IllegalAccessException e) {
            LOGGER.error(e.getMessage());
        }

        LOGGER.info("This {} {} in Data Base.",
                searchedRecord.getClass().getSimpleName(),
                isInDB ? "exists" : "does not exist");

        return isInDB;
    }

    public boolean doesRelationshipAlreadyExist(Class<?> clazzToCheck, Class<?> relationToCheck) throws SQLException {
        String findRelationSQL = "SELECT REFERENCED_TABLE_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE WHERE TABLE_NAME = '" + ormManagerUtil.getTableName(clazzToCheck) + "';";

        try (Statement statement = con.createStatement()) {
            ResultSet resultSet = statement.executeQuery(findRelationSQL);
            resultSet.next();

            while (resultSet.next()) {
                if (resultSet.getString(1).equals(ormManagerUtil.getTableName(relationToCheck))) {
                    return true;
                }
            }
        }
        return false;
    }

    public T save(T t) throws SQLException, IllegalAccessException {
        if (!merge(t)) {
            String sqlStatement = ormManagerUtil.getInsertStatement(t);
            try (PreparedStatement preparedStatement = con.prepareStatement(sqlStatement, Statement.RETURN_GENERATED_KEYS)) {
                ormManagerUtil.mapStatement(t, preparedStatement);
                ResultSet generatedKeys = preparedStatement.getGeneratedKeys();
                long id = -1;
                while (generatedKeys.next()) {
                    for (Field field : ormManagerUtil.getAllDeclaredFieldsFromObject(t)) {
                        field.setAccessible(true);
                        if (field.isAnnotationPresent(Id.class)) {
                            id = generatedKeys.getLong(1);
                            field.set(t, id);
                        }
                    }
                }
            }
        }
        return t;
    }

    public boolean merge(T entity) {
        boolean isMerged = false;

        if (isRecordInDataBase(entity)) {
            String queryCheck = String.format("UPDATE %s SET %s WHERE id = ?",
                    ormManagerUtil.getTableClassName(entity), ormManagerUtil.getColumnFieldsWithValuesToString(entity));

            try (PreparedStatement preparedStatement = con.prepareStatement(queryCheck)) {
                preparedStatement.setString(1, ormManagerUtil.getRecordId(entity));
                LOGGER.info("SQL CHECK STATEMENT: {}", preparedStatement);

                isMerged = preparedStatement.executeUpdate() > 0;
            } catch (Exception e) {
                LOGGER.error(e.getMessage());
            }
        }
        return isMerged;
    }

    public <T> Optional<T> findById(Serializable id, Class<T> cls) {
        T t = null;
        String sqlStatement = "SELECT * FROM "
                .concat(cls.getDeclaredAnnotation(Table.class).name())
                .concat(" WHERE id=")
                .concat(id.toString())
                .concat(";");
        try (PreparedStatement preparedStatement = con.prepareStatement(sqlStatement)) {
            t = cls.getDeclaredConstructor().newInstance();
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                t = mapperToObject(resultSet, t).orElseThrow();
                var oneToManyValues = Arrays.stream(t.getClass().getDeclaredFields())
                        .filter(v -> v.isAnnotationPresent(OneToMany.class))
                        .toList();

            }
        } catch (SQLException | InvocationTargetException | InstantiationException | IllegalAccessException |
                 NoSuchMethodException e) {
            LOGGER.info(String.valueOf(e));
        }
        return Optional.ofNullable(t);
    }

    public List<T> findAll(Class<T> cls) throws SQLException {
        List<T> allEntities = new ArrayList<>();
        String sqlStatement = "SELECT * FROM " + cls.getAnnotation(Table.class).name();
        LOGGER.info("sqlStatement {}", sqlStatement);
        try (PreparedStatement preparedStatement = con.prepareStatement(sqlStatement)) {
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                T t = cls.getConstructor().newInstance();
                ObjectMapper.mapperToObject(resultSet, t);
                allEntities.add(t);
            }
        } catch (IllegalAccessException | InvocationTargetException | InstantiationException |
                 NoSuchMethodException e) {
            LOGGER.info(String.valueOf(e));
        }
        return allEntities;
    }

    public Stream<T> findAllAsStream(Class<T> cls) {
        try {
            return findAll(cls).stream();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Iterable<T> findAllAsIterable(Class<T> cls) {
        String sqlStatement = "SELECT * FROM " + cls.getAnnotation(Table.class).name();
        LOGGER.info("sqlStatement {}", sqlStatement);
        try (PreparedStatement preparedStatement = con.prepareStatement(sqlStatement)) {
            ResultSet resultSet = preparedStatement.executeQuery();
            return () -> new Iterator<T>() {
                @Override
                public boolean hasNext() {
                    try {
                        return resultSet.next();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public T next() {
                    if (!hasNext()) throw new NoSuchElementException();
                    T t = null;
                    try {
                        t = cls.getConstructor().newInstance();
                    } catch (IllegalAccessException | InvocationTargetException | InstantiationException |
                             NoSuchMethodException e) {
                        LOGGER.info(String.valueOf(e));
                    }
                    ObjectMapper.mapperToObject(resultSet, t);
                    return t;
                }
            };
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void register(Class<?>... entityClasses) throws SQLException {
        for (var clazz : entityClasses) {
            register(clazz);
        }
    }

    private void register(Class<?> clazz) throws SQLException {
        if (doesEntityExists(clazz)) {
            LOGGER.info("{} already exists in database!", clazz.getSimpleName());
            return;
        }

        var tableName = ormManagerUtil.getTableName(clazz);

        var id = ormManagerUtil.getIdField(clazz);

        var basicFields = ormManagerUtil.getBasicFieldsFromClass(clazz);

        var fieldsAndTypes = new StringBuilder();

        for (var basicField : basicFields) {
            var sqlTypeForField = ormManagerUtil.getSqlTypeForField(basicField);

            if (basicField.isAnnotationPresent(Column.class)) {
                if (basicField.getAnnotation(Column.class).name().equals("")) {
                    fieldsAndTypes.append(" ").append(basicField.getName());
                } else {
                    fieldsAndTypes.append(" ").append(basicField.getAnnotation(Column.class).name());
                }
            } else {
                fieldsAndTypes.append(" ").append(basicField.getName());
            }
            fieldsAndTypes.append(sqlTypeForField);
        }

        StringBuilder registerSQL = new StringBuilder("CREATE TABLE IF NOT EXISTS " + tableName + " (" + id.getName() + " BIGINT UNSIGNED AUTO_INCREMENT,"
                + fieldsAndTypes + " PRIMARY KEY (" + id.getName() + "))");

        LOGGER.info("CREATE TABLE SQL statement is being prepared now: " + registerSQL);

        try (PreparedStatement preparedStatement = con.prepareStatement(String.valueOf(registerSQL))) {
            preparedStatement.execute();

            LOGGER.info("CREATE TABLE SQL completed successfully! {} entity has been created in DB.", tableName.toUpperCase());
        }
    }

    public boolean delete(T recordToDelete) {
        boolean isDeleted = false;
        if (isRecordInDataBase(recordToDelete)) {
            String tableName = recordToDelete.getClass().getAnnotation(Table.class).name();
            String queryCheck = String.format("DELETE FROM %s WHERE id = ?", tableName);

            try (PreparedStatement preparedStatement = con.prepareStatement(queryCheck)) {
                String recordId = ormManagerUtil.getRecordId(recordToDelete);
                preparedStatement.setString(1, recordId);
                LOGGER.info("SQL CHECK STATEMENT: {}", preparedStatement);

                isDeleted = preparedStatement.executeUpdate() > 0;
            } catch (SQLException | IllegalAccessException e) {
                LOGGER.error(e.getMessage());
            }

            if (isDeleted) {
                ormManagerUtil.setObjectToNull(recordToDelete);
                LOGGER.info("{} has been deleted from DB.", recordToDelete.getClass().getSimpleName());
            }
        }
        return isDeleted;
    }

    public Object update(T o) throws IllegalAccessException {
        if (ormManagerUtil.getId(o) != null && isRecordInDataBase(o)) {
            LOGGER.info("This {} has been updated from Data Base.",
                    o.getClass().getSimpleName());
            return findById(ormManagerUtil.getId(o), o.getClass()).get();
        }
        LOGGER.info("There is no such object with id in database or id of element is null.");
        LOGGER.info("The object {} that was passed to the method was returned.",
                o.getClass().getSimpleName());
        return o;
    }
}
