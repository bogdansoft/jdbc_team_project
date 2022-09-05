package com.ormanager.orm;

import com.ormanager.orm.annotation.*;
import com.ormanager.orm.exception.OrmFieldTypeException;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
final class OrmManagerUtil<T> {

    void setObjectToNull(T targetObject) {
        Arrays.stream(targetObject.getClass().getDeclaredFields()).forEach(field -> {
            field.setAccessible(true);
            try {
                field.set(targetObject, null);
            } catch (IllegalAccessException e) {
                LOGGER.error(e.getMessage());
            }
        });
    }

    Serializable getId(T o) throws IllegalAccessException {

        Optional<Field> optionalId = Arrays.stream(o.getClass().getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Id.class))
                .findAny();
        if (optionalId.isPresent()) {
            optionalId.get().setAccessible(true);
            return (Serializable) optionalId.get().get(o);
        }
        return null;
    }

    String getRecordId(Object recordInDb) throws IllegalAccessException {
        if (recordInDb == null) {
            return "0";
        }
        Optional<Field> optionalId = Arrays.stream(recordInDb.getClass().getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Id.class))
                .findAny();
        if (optionalId.isPresent()) {
            optionalId.get().setAccessible(true);
            Object o = optionalId.get().get(recordInDb);
            return o != null ? o.toString() : "0";
        }
        return "0";
    }

    boolean doesClassHaveAnyRelationship(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredFields())
                .anyMatch(field -> field.isAnnotationPresent(ManyToOne.class));
    }

    List<Field> getRelationshipFields(Class<?> clazz, Class<? extends Annotation> relationAnnotation) {
        return Arrays.stream(clazz.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(relationAnnotation))
                .toList();
    }

    String getTableClassName(T t) {
        return t.getClass().getAnnotation(Table.class).name();
    }

    List<Field> getAllDeclaredFieldsFromObject(T t) {
        return Arrays.asList(t.getClass().getDeclaredFields());
    }

    String getAllValuesFromListToString(T t) {
        return String.join(",", getAllValuesFromObject(t));
    }

    List<String> getAllValuesFromObject(T t) {
        List<String> strings = new ArrayList<>();
        for (Field field : getAllDeclaredFieldsFromObject(t)) {
            if (field.isAnnotationPresent(Column.class)) {
                if (!Objects.equals(field.getDeclaredAnnotation(Column.class).name(), "")) {
                    strings.add(field.getDeclaredAnnotation(Column.class).name());
                } else {
                    strings.add(field.getName());
                }
            } else if (field.isAnnotationPresent(ManyToOne.class)) {
                strings.add(field.getDeclaredAnnotation(ManyToOne.class).columnName());
            } else if (!Collection.class.isAssignableFrom(field.getType())
                    && !field.isAnnotationPresent(Id.class)) {
                strings.add(field.getName());
            }
        }
        return strings;
    }

    String getSqlTypeForField(Field field) {
        var fieldType = field.getType();

        if (fieldType == String.class) {
            return " VARCHAR(255),";
        } else if (fieldType == int.class || fieldType == Integer.class) {
            return " INT,";
        } else if (fieldType == LocalDate.class) {
            return " DATE,";
        } else if (fieldType == LocalTime.class) {
            return " DATETIME,";
        } else if (fieldType == UUID.class) {
            return " UUID,";
        } else if (fieldType == long.class || fieldType == Long.class) {
            return " BIGINT,";
        } else if (fieldType == double.class || fieldType == Double.class) {
            return " DOUBLE,";
        } else if (fieldType == boolean.class || fieldType == Boolean.class) {
            return " BOOLEAN,";
        } else if (fieldType == BigDecimal.class) {
            return " BIGINT,";
        }
        throw new OrmFieldTypeException("Could not get sql type for given field: " + fieldType);
    }

    String getTableName(Class<?> clazz) {
        var tableAnnotation = Optional.ofNullable(clazz.getAnnotation(Table.class));

        return tableAnnotation.isPresent() ? tableAnnotation.get().name() : clazz.getSimpleName().toLowerCase();
    }

    String getColumnFieldsWithValuesToString(T t) {
        try {
            return String.join(", ", getColumnFieldsWithValues(t));
        } catch (IllegalAccessException e) {
            LOGGER.error(e.getMessage());
            return "";
        }
    }

    List<Field> getBasicFieldsFromClass(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredFields())
                .filter(field -> !field.isAnnotationPresent(Id.class))
                .filter(field -> !field.isAnnotationPresent(OneToMany.class))
                .filter(field -> !field.isAnnotationPresent(ManyToOne.class))
                .filter(field -> field.getType() != Collection.class)
                .toList();
    }

    Field getIdField(Class<?> clazz) throws SQLException {
        return Arrays.stream(clazz.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Id.class))
                .findAny()
                .orElseThrow(() -> new SQLException(String.format("ID field not found in class %s !", clazz)));
    }


    List<String> getColumnFieldsWithValues(T t) throws IllegalAccessException {
        List<String> strings = new ArrayList<>();

        for (Field field : getAllDeclaredFieldsFromObject(t)) {
            field.setAccessible(true);
            //TODO CLEAN THE MESS
            if (field.isAnnotationPresent(Column.class)) {
                if (!Objects.equals(field.getDeclaredAnnotation(Column.class).name(), "")) {
                    strings.add(field.getDeclaredAnnotation(Column.class).name() + "='" + field.get(t) + "'");
                } else {
                    strings.add(field.getName() + "='" + field.get(t) + "'");
                }
            } else if (field.isAnnotationPresent(ManyToOne.class) && field.get(t) != null) {
                if (getRecordId(field.get(t)) != null) {
                    String recordId = getRecordId(field.get(t));
                    strings.add(field.getDeclaredAnnotation(ManyToOne.class).columnName() + "='" + recordId + "'");
                }
            } else if (!Collection.class.isAssignableFrom(field.getType())
                    && !field.isAnnotationPresent(Id.class)
                    && !field.isAnnotationPresent(ManyToOne.class)) {
                strings.add(field.getName() + "='" + field.get(t) + "'");
            }
        }
        return strings;
    }

    List<Field> getAllColumnsButId(T t) {
        return Arrays.stream(t.getClass().getDeclaredFields())
                .filter(v -> !v.isAnnotationPresent(Id.class))
                .collect(Collectors.toList());
    }

    Long getAllColumnsButIdAndOneToMany(T t) {
        return Arrays.stream(t.getClass().getDeclaredFields())
                .filter(v -> !v.isAnnotationPresent(Id.class))
                .filter(v -> !v.isAnnotationPresent(OneToMany.class))
                .count();
    }

    void mapStatement(T t, PreparedStatement preparedStatement) throws SQLException, IllegalAccessException {
        for (Field field : getAllColumnsButId(t)) {
            field.setAccessible(true);
            var index = getAllColumnsButId(t).indexOf(field) + 1;
            if (field.getType() == String.class) {
                preparedStatement.setString(index, (String) field.get(t));
            } else if (field.getType() == LocalDate.class) {
                Date date = Date.valueOf((LocalDate) field.get(t));
                preparedStatement.setDate(index, date);
            }
            //if we don't pass the value / don't have mapped type
            else if (!field.isAnnotationPresent(OneToMany.class)) {
                preparedStatement.setObject(index, null);
            }
        }
        LOGGER.info("PREPARED STATEMENT : {}", preparedStatement);
        preparedStatement.executeUpdate();
    }

    String getInsertStatement(T t) {
        var length = getAllColumnsButIdAndOneToMany(t);
        var questionMarks = IntStream.range(0, length.intValue())
                .mapToObj(q -> "?")
                .collect(Collectors.joining(","));

        String sqlStatement = "INSERT INTO "
                .concat(getTableClassName(t))
                .concat("(")
                .concat(getAllValuesFromListToString(t))
                .concat(") VALUES(")
                .concat(questionMarks)
                .concat(");");

        LOGGER.info("SQL STATEMENT : {}", sqlStatement);
        return sqlStatement;
    }
}
