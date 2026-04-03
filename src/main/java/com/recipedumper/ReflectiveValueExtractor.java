package com.recipedumper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class ReflectiveValueExtractor {
    private ReflectiveValueExtractor() {
    }

    static Object readFieldOrGetter(Object instance, String propertyName) {
        if (instance == null || propertyName == null || propertyName.isEmpty()) {
            return null;
        }

        Field field = findField(instance.getClass(), propertyName);
        if (field != null) {
            try {
                return field.get(instance);
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }

        String suffix = Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        Object getterValue = invokeNoArg(instance, "get" + suffix);
        if (getterValue != null) {
            return getterValue;
        }

        getterValue = invokeNoArg(instance, propertyName);
        if (getterValue != null) {
            return getterValue;
        }

        return invokeNoArg(instance, "is" + suffix);
    }

    static Object invokeNoArg(Object instance, String methodName) {
        if (instance == null || methodName == null || methodName.isEmpty()) {
            return null;
        }

        Method method = findNoArgMethod(instance.getClass(), methodName);
        if (method == null) {
            return null;
        }

        try {
            return method.invoke(instance);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    static Object unwrapContentCandidate(Object content) {
        if (content == null) {
            return null;
        }

        Object current = content;
        for (int depth = 0; depth < 4; depth++) {
            Object next = unwrapSingleLevel(current);
            if (next == null || next == current) {
                return current;
            }
            current = next;
        }
        return current;
    }

    static Object convertWithCapability(Object capability, Object content) {
        if (capability == null) {
            return unwrapContentCandidate(content);
        }

        Object unwrapped = unwrapContentCandidate(content);
        Object converted = invokeSingleArg(capability, "of", unwrapped);
        return converted != null ? converted : unwrapped;
    }

    static String formatEnergyStackContent(Object energy, Number fallbackVoltage) {
        Object current = unwrapContentCandidate(energy);
        if (current != null) {
            Object voltage = readFieldOrGetter(current, "voltage");
            Object amperage = readFieldOrGetter(current, "amperage");
            if (voltage instanceof Number && amperage instanceof Number) {
                return buildEnergyStackString((Number) voltage, (Number) amperage);
            }

            String rendered = current.toString();
            if (rendered.startsWith("EnergyStack[")) {
                return rendered;
            }

            if (current instanceof Number) {
                return buildEnergyStackString((Number) current, 1);
            }
        }

        if (fallbackVoltage != null) {
            return buildEnergyStackString(fallbackVoltage, 1);
        }

        return null;
    }

    static Integer resolveItemCount(int stackCount, Object primary, Object secondary, Object tertiary) {
        Integer wrappedCount = resolveWrappedItemCount(primary);
        if (wrappedCount != null) {
            return wrappedCount;
        }

        wrappedCount = resolveWrappedItemCount(secondary);
        if (wrappedCount != null) {
            return wrappedCount;
        }

        wrappedCount = resolveWrappedItemCount(tertiary);
        if (wrappedCount != null) {
            return wrappedCount;
        }

        return stackCount > 0 ? stackCount : null;
    }

    static Object unwrapNestedProperty(Object value, String propertyName) {
        if (value == null || propertyName == null || propertyName.isEmpty()) {
            return value;
        }

        Object current = value;
        for (int depth = 0; depth < 4; depth++) {
            Object next = readFieldOrGetter(current, propertyName);
            if (next == null || next == current) {
                return current;
            }
            current = next;
        }
        return current;
    }

    private static Field findField(Class<?> type, String fieldName) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Keep walking the hierarchy.
            }
        }
        return null;
    }

    private static Method findNoArgMethod(Class<?> type, String methodName) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(methodName);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                // Keep walking the hierarchy.
            }
        }
        return null;
    }

    static Object invokeSingleArg(Object instance, String methodName, Object arg) {
        if (instance == null || methodName == null || methodName.isEmpty()) {
            return null;
        }

        Method method = findSingleArgMethod(instance.getClass(), methodName, arg);
        if (method == null) {
            return null;
        }

        try {
            return method.invoke(instance, arg);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Method findSingleArgMethod(Class<?> type, String methodName, Object arg) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != 1) {
                    continue;
                }
                Class<?> parameterType = method.getParameterTypes()[0];
                if (arg != null && !parameterType.isInstance(arg) && parameterType != Object.class) {
                    continue;
                }
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private static Object unwrapSingleLevel(Object value) {
        Object direct = readFieldOrGetter(value, "content");
        if (direct != null && direct != value) {
            return direct;
        }

        Object inner = readFieldOrGetter(value, "inner");
        if (inner != null && inner != value) {
            return inner;
        }

        Object ingredient = readFieldOrGetter(value, "ingredient");
        if (ingredient != null && ingredient != value) {
            return ingredient;
        }

        return value;
    }

    private static String buildEnergyStackString(Number voltage, Number amperage) {
        return "EnergyStack[voltage=" + voltage.longValue() + ", amperage=" + amperage.longValue() + "]";
    }

    private static Integer readPositiveInteger(Object instance, String propertyName) {
        Object value = readFieldOrGetter(instance, propertyName);
        if (value instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        return null;
    }

    private static Integer resolveWrappedItemCount(Object candidate) {
        Integer count = readPositiveInteger(candidate, "amount");
        if (count != null) {
            return count;
        }

        count = readPositiveInteger(candidate, "count");
        if (count != null) {
            return count;
        }

        Object unwrapped = unwrapContentCandidate(candidate);
        if (unwrapped != candidate) {
            count = readPositiveInteger(unwrapped, "amount");
            if (count != null) {
                return count;
            }

            count = readPositiveInteger(unwrapped, "count");
            if (count != null) {
                return count;
            }
        }

        Object nestedInner = unwrapNestedProperty(candidate, "inner");
        if (nestedInner != candidate && nestedInner != unwrapped) {
            count = readPositiveInteger(nestedInner, "amount");
            if (count != null) {
                return count;
            }

            count = readPositiveInteger(nestedInner, "count");
            if (count != null) {
                return count;
            }
        }

        return null;
    }
}
