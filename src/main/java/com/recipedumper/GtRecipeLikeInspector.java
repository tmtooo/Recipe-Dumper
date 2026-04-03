package com.recipedumper;

import java.lang.reflect.Field;
import java.util.Map;

final class GtRecipeLikeInspector {
    private GtRecipeLikeInspector() {
    }

    static boolean isGtRecipeLike(Object recipe) {
        if (recipe == null) {
            return false;
        }
        if (hasClassInHierarchy(recipe.getClass(), "GTRecipe")) {
            return true;
        }
        return hasField(recipe, "duration")
            && hasField(recipe, "inputs")
            && hasField(recipe, "outputs")
            && hasField(recipe, "tickInputs")
            && hasField(recipe, "tickOutputs")
            && hasCompatibleMapField(recipe, "inputs")
            && hasCompatibleMapField(recipe, "outputs");
    }

    private static boolean hasClassInHierarchy(Class<?> type, String simpleName) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (simpleName.equals(current.getSimpleName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasField(Object instance, String fieldName) {
        try {
            instance.getClass().getField(fieldName);
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    private static boolean hasCompatibleMapField(Object instance, String fieldName) {
        try {
            Field field = instance.getClass().getField(fieldName);
            return Map.class.isAssignableFrom(field.getType());
        } catch (NoSuchFieldException e) {
            return false;
        }
    }
}
