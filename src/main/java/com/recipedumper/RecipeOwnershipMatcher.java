package com.recipedumper;

import java.util.Collection;
import java.util.Locale;

final class RecipeOwnershipMatcher {
    private RecipeOwnershipMatcher() {
    }

    static boolean matchesRequestedMod(
        String requestedModId,
        String recipeNamespace,
        String recipeId,
        Collection<String> relatedIds
    ) {
        String requested = normalize(requestedModId);
        String namespace = normalize(recipeNamespace);
        String normalizedRecipeId = normalize(recipeId);

        if (requested.isEmpty()) {
            return false;
        }
        if (requested.equals(namespace)) {
            return true;
        }
        if (!"gtceu".equals(namespace)) {
            return false;
        }
        if (containsModHint(normalizedRecipeId, requested)) {
            return true;
        }
        if (relatedIds == null) {
            return false;
        }
        for (String relatedId : relatedIds) {
            String normalizedRelatedId = normalize(relatedId);
            if (containsModHint(normalizedRelatedId, requested)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsModHint(String value, String requestedModId) {
        if (value.isEmpty()) {
            return false;
        }
        if (value.startsWith(requestedModId + ":")) {
            return true;
        }
        int index = value.indexOf(requestedModId);
        while (index >= 0) {
            boolean leftBoundary = index == 0 || !Character.isLetterOrDigit(value.charAt(index - 1));
            int end = index + requestedModId.length();
            boolean rightBoundary = end >= value.length() || !Character.isLetterOrDigit(value.charAt(end));
            if (leftBoundary && rightBoundary) {
                return true;
            }
            index = value.indexOf(requestedModId, index + 1);
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
