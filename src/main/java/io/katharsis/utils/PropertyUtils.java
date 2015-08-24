package io.katharsis.utils;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * <p>
 * A lighter version of Apache Commons PropertyUtils without additional dependencies and with support for fluent
 * setters and {@link JsonProperty} annotation.
 * </p>
 */
public class PropertyUtils {

    private static final PropertyUtils INSTANCE = new PropertyUtils();

    private PropertyUtils() {
    }

    /**
     * Get bean's property value. The sequence of searches for getting a value is as follows:
     * <ol>
     *    <li>All class fields are found using {@link ClassUtils#getClassFields(Class)}</li>
     *    <li>Search for a field annotated with {@link JsonProperty} and value of the desired one is made</li>
     *    <li>Search for a field with the name of the desired one is made</li>
     *    <li>If a field is found and it's a public field, the value is returned using the public field</li>
     *    <li>If a field is found and it's a non-public field, the value is returned using the accompanying getter</li>
     *    <li>If a field is not found, a search for a getter is made - all class getters are found using
     *    {@link ClassUtils#getClassFields(Class)}</li>
     *    <li>From class getters, an appropriate getter with {@link JsonProperty} and having value of the desired one
     *    is used</li>
     *    <li>From class getters, an appropriate getter with name of the desired one is used</li>
     * </ol>
     *
     * @param bean  bean to be accessed
     * @param field bean's fieldName
     * @return bean's property value
     */
    public static Object getProperty(Object bean, String field) {
        INSTANCE.checkParameters(bean, field);

        try {
            return INSTANCE.getPropertyValue(bean, field);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private void checkParameters(Object bean, String field) {
        if (bean == null) {
            throw new IllegalArgumentException("No bean specified");
        }
        if (field == null) {
            throw new IllegalArgumentException(String.format("No field specified for bean: %s", bean.getClass()));
        }
    }

    private Object getPropertyValue(Object bean, String fieldName)
        throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {

        Field foundField = findField(bean, fieldName);
        if (foundField != null) {
            if (Modifier.isPrivate(foundField.getModifiers())) {
                Method getter = getGetter(bean, foundField.getName());
                return getter.invoke(bean);
            } else if (Modifier.isPublic(foundField.getModifiers())) {
                return foundField.get(bean);
            } else {
                throw new RuntimeException(
                    String.format("Couldn't access a field %s.%s", bean.getClass().getCanonicalName(), fieldName));
            }
        } else {
            Method getter = findGetter(bean, fieldName);
            if (getter == null) {
                throw new RuntimeException(
                    String.format("Cannot find an getter for %s.%s", bean.getClass().getCanonicalName(), fieldName));
            }
            return getter.invoke(bean);
        }
    }

    private Method findGetter(Object bean, String fieldName) {
        List<Method> classGetters = ClassUtils.getClassGetters(bean.getClass());

        for (Method getter : classGetters) { // The first loop tries to get name from annotation
            if (getter.isAnnotationPresent(JsonProperty.class)
                && fieldName.equals(getter.getAnnotation(JsonProperty.class).value())) {
                return getter;
            }
        }
        for (Method getter : classGetters) { // The second just tries to get by internal name
            String getterFieldName = getGetterFieldName(getter);
            if (getterFieldName.equals(fieldName)) {
                return getter;
            }
        }
        return null;
    }

    private String getGetterFieldName(Method getter) {
        if (isBoolean(getter.getReturnType())) {
            return getter.getName().substring(2, 3).toLowerCase() + getter.getName().substring(3);
        } else {
            return getter.getName().substring(3, 4).toLowerCase() + getter.getName().substring(4);
        }
    }

    private boolean isBoolean(Class<?> returnType) {
        return boolean.class.equals(returnType) || Boolean.class.equals(returnType);
    }

    private Field findField(Object bean, String fieldName) {
        List<Field> classFields = ClassUtils.getClassFields(bean.getClass());
        for (Field field : classFields) { // The first loop tries to get name from annotation
            if (field.isAnnotationPresent(JsonProperty.class)
                && fieldName.equals(field.getAnnotation(JsonProperty.class).value())) {
                return field;
            }
        }
        for (Field field : classFields) { // The second just tries to get by internal name
            if (field.getName().equals(fieldName)) {
                return field;
            }
        }
        return null;
    }

    private Method getGetter(Object bean, String fieldName) throws NoSuchMethodException {
        Class<?> beanClass = bean.getClass();

        String upperCaseName = fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);

        try {
            return beanClass.getMethod("get" + upperCaseName);
        } catch (NoSuchMethodException e) {
            return beanClass.getMethod("is" + upperCaseName);
        }
    }

    public static void setProperty(Object bean, String field, Object value) {
        INSTANCE.checkParameters(bean, field);

        try {
            INSTANCE.setPropertyValue(bean, field, value);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private void setPropertyValue(Object bean, String fieldName, Object value)
        throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Field foundField = findField(bean, fieldName);

        if (foundField != null) {
            if (Modifier.isPrivate(foundField.getModifiers())) {
                Method setter = getSetter(bean, foundField.getName(), foundField.getType());
                setter.invoke(bean, value);
            } else if (Modifier.isPublic(foundField.getModifiers())) {
                foundField.set(bean, value);
            } else {
                throw new RuntimeException(
                    String.format("Couldn't access a field %s.%s", bean.getClass().getCanonicalName(), fieldName));
            }
        } else {
            Method getter = findGetter(bean, fieldName);
            if (getter == null) {
                throw new RuntimeException(
                    String.format("Cannot find an getter for %s.%s", bean.getClass().getCanonicalName(), fieldName));
            }
            String getterFieldName = getGetterFieldName(getter);
            Method setter = getSetter(bean, getterFieldName, getter.getReturnType());
            setter.invoke(bean, value);
        }
    }

    private Method getSetter(Object bean, String fieldName, Class<?> fieldType) throws NoSuchMethodException {
        Class<?> beanClass = bean.getClass();

        String upperCaseName = fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);

        return beanClass.getMethod("set" + upperCaseName, fieldType);
    }
}
