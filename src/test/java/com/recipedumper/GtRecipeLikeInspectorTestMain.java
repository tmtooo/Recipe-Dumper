package com.recipedumper;

import java.util.HashMap;
import java.util.Map;

public class GtRecipeLikeInspectorTestMain {
    public static void main(String[] args) {
        shouldDetectClassNamedGtRecipe();
        shouldDetectGtStyleRecipeWithDifferentSimpleName();
        shouldRejectNonGtRecipeLikeObject();
    }

    private static void shouldDetectClassNamedGtRecipe() {
        assertEquals(true, GtRecipeLikeInspector.isGtRecipeLike(new GTRecipe()), "GTRecipe should be recognized");
    }

    private static void shouldDetectGtStyleRecipeWithDifferentSimpleName() {
        assertEquals(true, GtRecipeLikeInspector.isGtRecipeLike(new Recipe()), "gt-style Recipe should be recognized");
    }

    private static void shouldRejectNonGtRecipeLikeObject() {
        assertEquals(false, GtRecipeLikeInspector.isGtRecipeLike(new PlainRecipe()), "plain recipe should not be recognized");
    }

    private static void assertEquals(boolean expected, boolean actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    public static class GTRecipe {
    }

    public static class Recipe {
        public int duration = 20;
        public Map<String, Object> inputs = new HashMap<>();
        public Map<String, Object> outputs = new HashMap<>();
        public Map<String, Object> tickInputs = new HashMap<>();
        public Map<String, Object> tickOutputs = new HashMap<>();
    }

    public static class PlainRecipe {
        public int duration = 20;
    }
}
