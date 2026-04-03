package com.recipedumper;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class GtRecipeRegistryBridgeTestMain {
    public static void main(String[] args) {
        shouldCollectRecipesAcrossCategoriesWithoutDuplicates();
        shouldUseCategoryMapWhenRecipeTypeExposesIt();
        shouldInitializeSearchBeforeReadingEmptyCategoryMap();
        shouldReadRecipeCandidatesFromPrivateMapFieldAfterInitSearch();
        shouldIgnoreRecipeTypesWithoutCategories();
    }

    private static void shouldCollectRecipesAcrossCategoriesWithoutDuplicates() {
        FakeRecipe shared = new FakeRecipe("shared");
        FakeRecipe onlyA = new FakeRecipe("only-a");
        FakeRecipe onlyB = new FakeRecipe("only-b");

        List<Object> recipes = GtRecipeRegistryBridge.collectRecipesFromTypes(Arrays.asList(
            new FakeRecipeType(
                Arrays.asList(
                    new FakeCategory("a"),
                    new FakeCategory("b")
                ),
                Arrays.asList(
                    Arrays.asList(shared, onlyA),
                    Arrays.asList(shared, onlyB)
                )
            )
        ));

        assertEquals(3, recipes.size(), "expected recipes to be de-duplicated across categories");
        assertEquals("shared", ((FakeRecipe) recipes.get(0)).id, "first recipe should preserve encounter order");
        assertEquals("only-a", ((FakeRecipe) recipes.get(1)).id, "second recipe should preserve encounter order");
        assertEquals("only-b", ((FakeRecipe) recipes.get(2)).id, "third recipe should preserve encounter order");
    }

    private static void shouldIgnoreRecipeTypesWithoutCategories() {
        List<Object> recipes = GtRecipeRegistryBridge.collectRecipesFromTypes(Arrays.asList(
            new FakeRecipeType(List.of(), List.of()),
            new FakeRecipeType(null, null)
        ));

        assertEquals(0, recipes.size(), "recipe types without categories should not contribute recipes");
    }

    private static void shouldUseCategoryMapWhenRecipeTypeExposesIt() {
        FakeRecipe shared = new FakeRecipe("shared");
        FakeRecipe onlyA = new FakeRecipe("only-a");

        List<Object> recipes = GtRecipeRegistryBridge.collectRecipesFromTypes(Arrays.asList(
            new FakeRecipeTypeWithBrokenCategoryLookup(
                Arrays.asList(new FakeCategory("a")),
                Arrays.asList(Arrays.asList(shared, onlyA))
            )
        ));

        assertEquals(2, recipes.size(), "bridge should fall back to getCategoryMap when category lookup is broken");
    }

    private static void shouldInitializeSearchBeforeReadingEmptyCategoryMap() {
        FakeRecipe shared = new FakeRecipe("shared");
        FakeRecipe onlyA = new FakeRecipe("only-a");

        List<Object> recipes = GtRecipeRegistryBridge.collectRecipesFromTypes(Arrays.asList(
            new FakeLazyRecipeType(
                Arrays.asList(new FakeCategory("a")),
                Arrays.asList(Arrays.asList(shared, onlyA))
            )
        ));

        assertEquals(2, recipes.size(), "bridge should call initSearch when custom recipe type is lazily populated");
    }

    private static void shouldReadRecipeCandidatesFromPrivateMapFieldAfterInitSearch() {
        FakeRecipe shared = new FakeRecipe("shared");
        FakeRecipe onlyA = new FakeRecipe("only-a");

        List<Object> recipes = GtRecipeRegistryBridge.collectRecipesFromTypes(Arrays.asList(
            new FakeFieldBackedRecipeType(List.of(new FakeCategory("a")), Arrays.asList(Arrays.asList(shared, onlyA)))
        ));

        assertEquals(2, recipes.size(), "bridge should fall back to private map-backed recipe storage when public views stay empty");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static final class FakeRecipe {
        private final String id;

        private FakeRecipe(String id) {
            this.id = id;
        }
    }

    public static final class FakeCategory {
        private final String name;

        public FakeCategory(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static class FakeRecipeType {
        private final List<FakeCategory> categories;
        private final List<List<FakeRecipe>> categoryRecipes;

        public FakeRecipeType(List<FakeCategory> categories, List<List<FakeRecipe>> categoryRecipes) {
            this.categories = categories;
            this.categoryRecipes = categoryRecipes;
        }

        public Collection<FakeCategory> getCategories() {
            return categories;
        }

        public Collection<FakeRecipe> getRecipesInCategory(FakeCategory category) {
            if (categories == null || categoryRecipes == null) {
                return List.of();
            }
            int index = categories.indexOf(category);
            if (index < 0) {
                return List.of();
            }
            return categoryRecipes.get(index);
        }
    }

    public static final class FakeRecipeTypeWithBrokenCategoryLookup extends FakeRecipeType {
        public FakeRecipeTypeWithBrokenCategoryLookup(List<FakeCategory> categories, List<List<FakeRecipe>> categoryRecipes) {
            super(categories, categoryRecipes);
        }

        @Override
        public Collection<FakeRecipe> getRecipesInCategory(FakeCategory category) {
            throw new IllegalStateException("simulated broken category lookup");
        }

        public java.util.Map<FakeCategory, java.util.Set<FakeRecipe>> getCategoryMap() {
            java.util.Map<FakeCategory, java.util.Set<FakeRecipe>> result = new java.util.LinkedHashMap<>();
            List<FakeCategory> categories = (List<FakeCategory>) getCategories();
            for (int i = 0; i < categories.size(); i++) {
                result.put(categories.get(i), new java.util.LinkedHashSet<>((Collection<FakeRecipe>) super.getRecipesInCategory(categories.get(i))));
            }
            return result;
        }
    }

    public static final class FakeLazyRecipeType extends FakeRecipeType {
        private boolean initialized;

        public FakeLazyRecipeType(List<FakeCategory> categories, List<List<FakeRecipe>> categoryRecipes) {
            super(categories, categoryRecipes);
        }

        public java.util.Map<FakeCategory, java.util.Set<FakeRecipe>> getCategoryMap() {
            if (!initialized) {
                java.util.Map<FakeCategory, java.util.Set<FakeRecipe>> empty = new java.util.LinkedHashMap<>();
                for (FakeCategory category : getCategories()) {
                    empty.put(category, java.util.Collections.emptySet());
                }
                return empty;
            }

            java.util.Map<FakeCategory, java.util.Set<FakeRecipe>> result = new java.util.LinkedHashMap<>();
            List<FakeCategory> categories = (List<FakeCategory>) getCategories();
            for (int i = 0; i < categories.size(); i++) {
                result.put(categories.get(i), new java.util.LinkedHashSet<>((Collection<FakeRecipe>) super.getRecipesInCategory(categories.get(i))));
            }
            return result;
        }

        @SuppressWarnings("unused")
        private void initSearch() {
            initialized = true;
        }
    }

    public static final class FakeFieldBackedRecipeType extends FakeRecipeType {
        private boolean initialized;
        private final List<List<FakeRecipe>> sourceRecipes;
        private java.util.Map<String, java.util.List<FakeRecipe>> hiddenRecipes = java.util.Collections.emptyMap();

        public FakeFieldBackedRecipeType(List<FakeCategory> categories, List<List<FakeRecipe>> categoryRecipes) {
            super(categories, categoryRecipes);
            this.sourceRecipes = categoryRecipes;
        }

        public java.util.Map<FakeCategory, java.util.Set<FakeRecipe>> getCategoryMap() {
            java.util.Map<FakeCategory, java.util.Set<FakeRecipe>> empty = new java.util.LinkedHashMap<>();
            for (FakeCategory category : getCategories()) {
                empty.put(category, java.util.Collections.emptySet());
            }
            return empty;
        }

        @Override
        public Collection<FakeRecipe> getRecipesInCategory(FakeCategory category) {
            return java.util.Collections.emptyList();
        }

        @SuppressWarnings("unused")
        private void initSearch() {
            if (!initialized) {
                List<FakeCategory> categories = (List<FakeCategory>) getCategories();
                hiddenRecipes = new java.util.LinkedHashMap<>();
                hiddenRecipes.put(categories.get(0).toString(), new java.util.ArrayList<>(sourceRecipes.get(0)));
                initialized = true;
            }
        }
    }
}
