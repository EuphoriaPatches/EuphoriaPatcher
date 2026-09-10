package com.euphoriapatches.euphoria_patcher.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;

public class ReflectionUtils {

    private static void debugLog(String message) {
		EuphoriaLogger.debugLog("[ReflectionUtils] " + message);
	}

    /**
     * Checks if a class exists in the current classpath.
     *
     * @param className The fully qualified class name to check
     * @return true if the class exists, false otherwise
     */
    public static boolean checkClassExists(String className) {
        try {
            String resourceName = className.replace('.', '/') + ".class";
            boolean exists = ReflectionUtils.class.getClassLoader().getResource(resourceName) != null;;
            debugLog("Class check for " + className + ": " + (exists ? "found" : "not found"));
            return exists;
        } catch (Exception e) {
            debugLog("Exception checking class " + className + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Returns the first declared constructor of {@code clazz} with {@code parameterCount} parameters,
     * made accessible, or null. Useful when a constructor's parameter types aren't known ahead of time.
     */
    public static Constructor<?> findConstructor(Class<?> clazz, int parameterCount) {
        for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
            if (constructor.getParameterCount() == parameterCount) {
                constructor.setAccessible(true);
                return constructor;
            }
        }
        return null;
    }

    /**
     * Invokes a method by matching return and parameter types instead of name, bypassing deobfuscation differences.
     * Returns {@code null} and logs a debug message on invocation failure or signature mismatch.
     */
    public static Object invokeBySignature(Object target, Class<?> returnType, Class<?>[] parameterTypes, Object... args) {
        for (Method method : target.getClass().getDeclaredMethods()) {
            if (method.getReturnType() != returnType || !parametersMatch(method.getParameterTypes(), parameterTypes)) {
                continue;
            }
            try {
                method.setAccessible(true);
                return method.invoke(target, args);
            } catch (Exception e) {
                debugLog("Error invoking " + method.getName() + ": " + e.getMessage());
                return null;
            }
        }
        debugLog("No " + returnType.getSimpleName() + " method " + Arrays.toString(parameterTypes)
                + " on " + target.getClass().getName());
        return null;
    }

    private static boolean parametersMatch(Class<?>[] actual, Class<?>[] wanted) {
        if (actual.length != wanted.length) {
            return false;
        }
        for (int i = 0; i < wanted.length; i++) {
            if (actual[i] == wanted[i]) {
                continue;
            }
            if (actual[i].isPrimitive() || wanted[i].isPrimitive() || !actual[i].isAssignableFrom(wanted[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the first of the given class names that can be loaded, or null if none can.
     * Handy for a type that is named differently across mapping sets.
     */
    public static Class<?> firstClass(String... classNames) {
        for (String className : classNames) {
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException ignored) {
            }
        }
        return null;
    }

    /**
     * Finds a method in the given class that matches the specified name and has a single parameter
     * that is assignable from the provided argument instance's class.
     * @param declaringClass
     * @param methodName
     * @param argInstance
     * @return
     * @throws NoSuchMethodException
     */
    public static Method findMethodForInstance(Class<?> declaringClass, String methodName, Object argInstance) throws NoSuchMethodException {
        for (Method method : declaringClass.getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isAssignableFrom(argInstance.getClass())) {
                return method;
            }
        }
        throw new NoSuchMethodException(declaringClass.getName() + "." + methodName + "(" + argInstance.getClass().getName() + ")");
    }

    /**
     * Tries each candidate no-arg method name in order on {@code declaringClass}, returning the
     * first one that resolves. Useful when the same method can appear under different names
     * depending on mapping/obfuscation state (e.g. named vs. intermediary vs. SRG), when you
     * already know the exact declaring class and just don't know which name it's using here.
     *
     * @param declaringClass the class to search
     * @param methodNames    candidate no-arg method names to try, in priority order
     * @return the first resolvable method
     * @throws NoSuchMethodException if none of the candidates resolve
     */
    public static Method tryMethods(Class<?> declaringClass, String... methodNames) throws NoSuchMethodException {
        return tryMethods(declaringClass, new Class<?>[0], methodNames);
    }

    /**
     * Same as {@link #tryMethods(Class, String...)} but for methods that take arguments -
     * tries each candidate name with the given parameter types, in priority order.
     *
     * @param declaringClass the class to search
     * @param parameterTypes the parameter types shared by every candidate
     * @param methodNames    candidate method names to try, in priority order
     * @return the first resolvable method
     * @throws NoSuchMethodException if none of the candidates resolve
     */
    public static Method tryMethods(Class<?> declaringClass, Class<?>[] parameterTypes, String... methodNames) throws NoSuchMethodException {
        NoSuchMethodException lastFailure = null;
        for (String methodName : methodNames) {
            try {
                return declaringClass.getMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException e) {
                lastFailure = e;
            }
        }
        throw lastFailure != null ? lastFailure : new NoSuchMethodException(declaringClass.getName() + ": no candidate method names provided");
    }

    /**
     * Same as {@link #tryMethods(Class, Class[], String...)}, but also finds non-public methods
     * (e.g. an internal accessor that was never meant to be called from outside the class),
     * forcing each one accessible via reflection before returning it. Useful when the only
     * implementation available under a given name isn't public.
     *
     * @param declaringClass the class to search
     * @param parameterTypes the parameter types shared by every candidate
     * @param methodNames    candidate method names to try, in priority order
     * @return the first resolvable method, already made accessible
     * @throws NoSuchMethodException if none of the candidates resolve
     */
    public static Method tryDeclaredMethods(Class<?> declaringClass, Class<?>[] parameterTypes, String... methodNames) throws NoSuchMethodException {
        NoSuchMethodException lastFailure = null;
        for (String methodName : methodNames) {
            try {
                Method method = declaringClass.getDeclaredMethod(methodName, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException e) {
                lastFailure = e;
            }
        }
        throw lastFailure != null ? lastFailure : new NoSuchMethodException(declaringClass.getName() + ": no candidate method names provided");
    }

    /**
     * Retrieves the value of a field from an object.
     *
     * @param target    The object from which to retrieve the field value
     * @param fieldName The name of the field to retrieve
     * @return The value of the field, or null if not found
     */
    public static Object getFieldValue(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        Class<?> clazz;
        Object instance = target;

        try {
            if (target instanceof String) {
                clazz = Class.forName((String) target);
                instance = null;

            } else if (target instanceof Class<?>) {
                clazz = (Class<?>) target;
                instance = null;

            } else {
                clazz = target.getClass();
            }

            while (clazz != null) {
                try {
                    Field field = clazz.getDeclaredField(fieldName);
                    field.setAccessible(true);

                    return field.get(instance);

                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }

        } catch (ClassNotFoundException e) {
            debugLog("Class not found: " + target);
            return null;
        } catch (Exception e) {
            debugLog("Error accessing field " + fieldName + ": " + e.getMessage());
            return null;
        }

        debugLog("Field " + fieldName + " not found in class hierarchy");
        return null;
    }

    /**
     * Sets the value of a field on an object (or a static field when {@code target} is a
     * {@link Class} or class-name String), walking the class hierarchy and overriding access
     * checks.
     *
     * @param target    Target instance, or class reference.
     * @param fieldName Target field identifier.
     * @param value     Value to assign to the field.
     * @return boolean indicating success or failure
     */
    public static boolean setFieldValue(Object target, String fieldName, Object value) {
        if (target == null) {
            return false;
        }
        Class<?> clazz;
        Object instance = target;

        try {
            if (target instanceof String) {
                clazz = Class.forName((String) target);
                instance = null;
            } else if (target instanceof Class<?>) {
                clazz = (Class<?>) target;
                instance = null;
            } else {
                clazz = target.getClass();
            }

            while (clazz != null) {
                try {
                    Field field = clazz.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    field.set(instance, value);
                    return true;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
        } catch (ClassNotFoundException e) {
            debugLog("Class not found: " + target);
            return false;
        } catch (Exception e) {
            debugLog("Error setting field " + fieldName + ": " + e.getMessage());
            return false;
        }

        debugLog("Field " + fieldName + " not found in class hierarchy");
        return false;
    }

    /**
     * Tries each candidate field name in order and returns the first non-null value found.
     *
     * @param target     Target instance, {@link Class}, or class-name String for static fields.
     * @param fieldNames Candidate field names in priority order.
     * @return First non-null resolved value, or {@code null} if all fail.
     */
    public static Object getFieldValue(Object target, String... fieldNames) {
        for (String fieldName : fieldNames) {
            Object value = getFieldValue(target, fieldName);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * Invokes a method by matching exact parameter types, walking class hierarchies and overriding access checks.
     * <p>
     * Swallows all reflection exceptions and returns {@code null} on failure or for {@code void} methods.
     *
     * @param target         Instance object, or {@link Class} / fully qualified class-name String for static methods.
     * @param methodName     Target method name.
     * @param parameterTypes Exact parameter types (pass {@code new Class<?>[0]} for no-arg methods).
     * @param args           Invocation arguments matching {@code parameterTypes}.
     * @return Method return value, or {@code null} on failure / {@code void} methods.
     */
    public static Object invokeMethod(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        if (target == null) {
            return null;
        }
        Class<?> clazz;
        Object instance = target;

        try {
            if (target instanceof String) {
                clazz = Class.forName((String) target);
                instance = null;
            } else if (target instanceof Class<?>) {
                clazz = (Class<?>) target;
                instance = null;
            } else {
                clazz = target.getClass();
            }
        } catch (ClassNotFoundException e) {
            debugLog("Class not found: " + target);
            return null;
        }

        while (clazz != null) {
            try {
                Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
                method.setAccessible(true);
                return method.invoke(instance, args);
            } catch (NoSuchMethodException e) {
                clazz = clazz.getSuperclass();
            } catch (Exception e) {
                debugLog("Error invoking " + methodName + ": " + e.getMessage());
                return null;
            }
        }

        debugLog("Method " + methodName + " not found in class hierarchy");
        return null;
    }

    public static Object invokeMethod(Object target, String methodName) {
        return invokeMethod(target, methodName, new Class<?>[0]);
    }

    /**
     * Like {@link #invokeMethod(Object, String, Class[], Object...)} but tries each candidate name
     * in order (for a method named differently across mapping sets), returning the first non-null
     * result. Not suitable for {@code void} methods.
     */
    public static Object invokeMethod(Object target, String[] methodNames, Class<?>[] parameterTypes, Object... args) {
        for (String methodName : methodNames) {
            Object result = invokeMethod(target, methodName, parameterTypes, args);
            if (result != null) {
                return result;
            }
        }
        return null;
    }
}
