package com.recipedumper;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class GtRecipeRegistryBridge {
    private GtRecipeRegistryBridge() {
    }

    static Inspection inspectGtRegistry() {
        Inspection inspection = new Inspection();
        try {
            Class<?> registriesClass = Class.forName("com.gregtechceu.gtceu.api.registry.GTRegistries");
            inspection.registriesClassName = registriesClass.getName();
            Object recipeTypes = registriesClass.getField("RECIPE_TYPES").get(null);
            inspection.recipeTypesClassName = recipeTypes == null ? "null" : recipeTypes.getClass().getName();
            inspection.recipeTypesIterable = recipeTypes instanceof Iterable<?>;
            List<Object> recipeTypeList = toList(asIterable(recipeTypes));
            inspection.recipeTypeCount = recipeTypeList.size();
            inspection.recipes = collectRecipesFromTypes(recipeTypeList, inspection);
        } catch (ReflectiveOperationException e) {
            inspection.failure = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        return inspection;
    }

    static List<Object> collectRecipesFromTypes(Iterable<?> recipeTypes) {
        return collectRecipesFromTypes(recipeTypes, null);
    }

    private static List<Object> collectRecipesFromTypes(Iterable<?> recipeTypes, Inspection inspection) {
        Set<Object> dedupedRecipes = new LinkedHashSet<>();
        for (Object recipeType : recipeTypes) {
            if (inspection != null && inspection.firstRecipeTypeClassName.isEmpty() && recipeType != null) {
                inspection.firstRecipeTypeClassName = recipeType.getClass().getName();
                inspection.firstRecipeTypeSuperclassName = recipeType.getClass().getSuperclass() == null
                    ? "null"
                    : recipeType.getClass().getSuperclass().getName();
                inspection.firstRecipeTypeMethodHints = summarizeMethods(recipeType.getClass());
                inspection.firstRecipeTypeDeclaredMethodHints = summarizeDeclaredMethods(recipeType.getClass());
                inspection.firstRecipeTypeFieldHints = summarizeFields(recipeType.getClass());
            }
            RecipeSnapshot snapshot = snapshotRecipes(recipeType, inspection);
            if (snapshot.shouldAttemptInitSearch()) {
                boolean initialized = invokeAccessibleNoArg(recipeType, "initSearch");
                if (inspection != null && initialized) {
                    inspection.initSearchInvocationCount++;
                }
                if (initialized) {
                    snapshot = snapshotRecipes(recipeType, inspection);
                }
            }
            if (snapshot.addAllTo(dedupedRecipes)) {
                continue;
            }
            int beforePublicFallback = dedupedRecipes.size();
            Collection<?> categories = snapshot.categories;
            for (Object category : categories) {
                Collection<?> recipes = asCollection(invokePublic(recipeType, "getRecipesInCategory", category));
                dedupedRecipes.addAll(recipes);
            }
            if (dedupedRecipes.size() > beforePublicFallback) {
                continue;
            }
            int beforePrivateFallback = dedupedRecipes.size();
            collectFromDeclaredMapFields(recipeType, dedupedRecipes, inspection);
            if (dedupedRecipes.size() > beforePrivateFallback) {
                continue;
            }
        }
        if (inspection != null) {
            inspection.recipeCount = dedupedRecipes.size();
        }
        return new ArrayList<>(dedupedRecipes);
    }

    private static RecipeSnapshot snapshotRecipes(Object recipeType, Inspection inspection) {
        Map<?, ?> categoryMap = asMap(invokePublic(recipeType, "getCategoryMap"));
        Collection<?> categories = asCollection(invokePublic(recipeType, "getCategories"));
        Map<?, ?> proxyRecipes = asMap(invokePublic(recipeType, "getProxyRecipes"));

        if (inspection != null) {
            inspection.categoryMapCount += categoryMap.size();
            inspection.categoryCount += categories.size();
            inspection.proxyRecipeMapCount += proxyRecipes.size();
            if (inspection.firstCategoryMapValueClassName.isEmpty() && !categoryMap.isEmpty()) {
                Object firstValue = categoryMap.values().iterator().next();
                inspection.firstCategoryMapValueClassName = firstValue == null ? "null" : firstValue.getClass().getName();
            }
            if (inspection.firstCategoryClassName.isEmpty() && !categories.isEmpty()) {
                Object firstCategory = categories.iterator().next();
                inspection.firstCategoryClassName = firstCategory == null ? "null" : firstCategory.getClass().getName();
            }
        }

        return new RecipeSnapshot(categoryMap, categories, proxyRecipes, inspection);
    }

    private static Iterable<?> asIterable(Object value) {
        if (value instanceof Iterable<?> iterable) {
            return iterable;
        }
        if (value != null && value.getClass().isArray()) {
            List<Object> values = new ArrayList<>(Array.getLength(value));
            for (int i = 0; i < Array.getLength(value); i++) {
                values.add(Array.get(value, i));
            }
            return values;
        }
        return List.of();
    }

    private static String summarizeMethods(Class<?> type) {
        return java.util.Arrays.stream(type.getMethods())
            .map(Method::getName)
            .filter(name -> name.contains("recipe") || name.contains("Recipe") || name.contains("category") || name.contains("Category") || name.equals("db") || name.contains("proxy") || name.contains("Proxy") || name.contains("addition") || name.contains("Addition"))
            .distinct()
            .sorted()
            .limit(20)
            .collect(Collectors.joining("|"));
    }

    private static String summarizeDeclaredMethods(Class<?> type) {
        return java.util.Arrays.stream(type.getDeclaredMethods())
            .map(Method::getName)
            .distinct()
            .sorted()
            .limit(20)
            .collect(Collectors.joining("|"));
    }

    private static String summarizeFields(Class<?> type) {
        return java.util.Arrays.stream(type.getDeclaredFields())
            .map(field -> field.getName() + ":" + field.getType().getSimpleName())
            .sorted()
            .limit(20)
            .collect(Collectors.joining("|"));
    }

    private static Map<?, ?> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        return Map.of();
    }

    private static List<Object> toList(Iterable<?> iterable) {
        List<Object> values = new ArrayList<>();
        for (Object value : iterable) {
            values.add(value);
        }
        return values;
    }

    private static Collection<?> asCollection(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> values = new ArrayList<>();
            for (Object entry : iterable) {
                values.add(entry);
            }
            return values;
        }
        if (value != null && value.getClass().isArray()) {
            List<Object> values = new ArrayList<>(Array.getLength(value));
            for (int i = 0; i < Array.getLength(value); i++) {
                values.add(Array.get(value, i));
            }
            return values;
        }
        return List.of();
    }

    private static Object invokePublic(Object instance, String methodName, Object... args) {
        if (instance == null) {
            return List.of();
        }
        try {
            Method method = findCompatibleMethod(instance.getClass(), methodName, args);
            if (method == null) {
                return List.of();
            }
            return method.invoke(instance, args);
        } catch (ReflectiveOperationException e) {
            return List.of();
        }
    }

    private static Method findCompatibleMethod(Class<?> type, String methodName, Object[] args) {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length != args.length) {
                continue;
            }
            boolean matches = true;
            for (int i = 0; i < parameterTypes.length; i++) {
                Object arg = args[i];
                if (arg != null && !parameterTypes[i].isInstance(arg)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return method;
            }
        }
        return null;
    }

    private static boolean invokeAccessibleNoArg(Object instance, String methodName) {
        if (instance == null) {
            return false;
        }
        try {
            Method method = instance.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            method.invoke(instance);
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private static void collectFromDeclaredMapFields(Object instance, Set<Object> sink, Inspection inspection) {
        for (Class<?> type = instance.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(instance);
                    Map<?, ?> map = asMap(value);
                    if (inspection != null) {
                        inspection.privateMapFieldCount++;
                        if (!map.isEmpty()) {
                            inspection.nonEmptyPrivateMapFieldCount++;
                            if (inspection.firstNonEmptyPrivateMapFieldName.isEmpty()) {
                                inspection.firstNonEmptyPrivateMapFieldName = field.getName();
                                Object firstValue = map.values().iterator().next();
                                inspection.firstNonEmptyPrivateMapValueClassName = firstValue == null ? "null" : firstValue.getClass().getName();
                            }
                        }
                    }
                    for (Object entryValue : map.values()) {
                        addCandidateObjects(entryValue, sink);
                    }
                } catch (ReflectiveOperationException ignored) {
                    if (inspection != null) {
                        inspection.privateMapFieldAccessFailureCount++;
                    }
                }
            }
        }
    }

    private static void addCandidateObjects(Object value, Set<Object> sink) {
        if (value == null) {
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Object nestedValue : map.values()) {
                addCandidateObjects(nestedValue, sink);
            }
            return;
        }
        if (value instanceof Collection<?> collection) {
            sink.addAll(collection);
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object entry : iterable) {
                sink.add(entry);
            }
            return;
        }
        if (value.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(value); i++) {
                sink.add(Array.get(value, i));
            }
            return;
        }
        sink.add(value);
    }

    private static final class RecipeSnapshot {
        private final Map<?, ?> categoryMap;
        private final Collection<?> categories;
        private final Map<?, ?> proxyRecipes;
        private final Inspection inspection;

        private RecipeSnapshot(Map<?, ?> categoryMap, Collection<?> categories, Map<?, ?> proxyRecipes, Inspection inspection) {
            this.categoryMap = categoryMap;
            this.categories = categories;
            this.proxyRecipes = proxyRecipes;
            this.inspection = inspection;
        }

        private boolean shouldAttemptInitSearch() {
            return allGroupsEmpty(categoryMap.values()) && allGroupsEmpty(proxyRecipes.values());
        }

        private boolean addAllTo(Set<Object> sink) {
            boolean found = false;
            for (Object recipes : categoryMap.values()) {
                Collection<?> recipeCollection = asCollection(recipes);
                if (inspection != null && !recipeCollection.isEmpty()) {
                    inspection.nonEmptyCategoryMapCount++;
                }
                if (!recipeCollection.isEmpty()) {
                    found = true;
                }
                sink.addAll(recipeCollection);
            }
            for (Object recipes : proxyRecipes.values()) {
                Collection<?> recipeCollection = asCollection(recipes);
                if (inspection != null && !recipeCollection.isEmpty()) {
                    inspection.nonEmptyProxyRecipeMapCount++;
                }
                if (!recipeCollection.isEmpty()) {
                    found = true;
                }
                sink.addAll(recipeCollection);
            }
            return found;
        }

        private boolean allGroupsEmpty(Collection<?> groups) {
            for (Object recipes : groups) {
                Collection<?> recipeCollection = asCollection(recipes);
                if (!recipeCollection.isEmpty()) {
                    return false;
                }
            }
            return true;
        }
    }

    static final class Inspection {
        private List<Object> recipes = List.of();
        private String failure;
        private String registriesClassName = "";
        private String recipeTypesClassName = "";
        private boolean recipeTypesIterable;
        private int recipeTypeCount;
        private int categoryMapCount;
        private int nonEmptyCategoryMapCount;
        private int categoryCount;
        private int proxyRecipeMapCount;
        private int nonEmptyProxyRecipeMapCount;
        private int initSearchInvocationCount;
        private int privateMapFieldCount;
        private int nonEmptyPrivateMapFieldCount;
        private int privateMapFieldAccessFailureCount;
        private int recipeCount;
        private String firstRecipeTypeClassName = "";
        private String firstRecipeTypeSuperclassName = "";
        private String firstRecipeTypeMethodHints = "";
        private String firstRecipeTypeDeclaredMethodHints = "";
        private String firstRecipeTypeFieldHints = "";
        private String firstCategoryClassName = "";
        private String firstCategoryMapValueClassName = "";
        private String firstNonEmptyPrivateMapFieldName = "";
        private String firstNonEmptyPrivateMapValueClassName = "";

        String summary() {
            return "registriesClass=" + emptyToPlaceholder(registriesClassName)
                + ", recipeTypesClass=" + emptyToPlaceholder(recipeTypesClassName)
                + ", recipeTypesIterable=" + recipeTypesIterable
                + ", recipeTypeCount=" + recipeTypeCount
                + ", categoryMapCount=" + categoryMapCount
                + ", nonEmptyCategoryMapCount=" + nonEmptyCategoryMapCount
                + ", categoryCount=" + categoryCount
                + ", proxyRecipeMapCount=" + proxyRecipeMapCount
                + ", nonEmptyProxyRecipeMapCount=" + nonEmptyProxyRecipeMapCount
                + ", initSearchInvocationCount=" + initSearchInvocationCount
                + ", privateMapFieldCount=" + privateMapFieldCount
                + ", nonEmptyPrivateMapFieldCount=" + nonEmptyPrivateMapFieldCount
                + ", privateMapFieldAccessFailureCount=" + privateMapFieldAccessFailureCount
                + ", recipeCount=" + recipeCount
                + ", firstRecipeTypeClass=" + emptyToPlaceholder(firstRecipeTypeClassName)
                + ", firstRecipeTypeSuperclass=" + emptyToPlaceholder(firstRecipeTypeSuperclassName)
                + ", firstRecipeTypeMethods=" + emptyToPlaceholder(firstRecipeTypeMethodHints)
                + ", firstRecipeTypeDeclaredMethods=" + emptyToPlaceholder(firstRecipeTypeDeclaredMethodHints)
                + ", firstRecipeTypeFields=" + emptyToPlaceholder(firstRecipeTypeFieldHints)
                + ", firstCategoryClass=" + emptyToPlaceholder(firstCategoryClassName)
                + ", firstCategoryMapValueClass=" + emptyToPlaceholder(firstCategoryMapValueClassName)
                + ", firstNonEmptyPrivateMapField=" + emptyToPlaceholder(firstNonEmptyPrivateMapFieldName)
                + ", firstNonEmptyPrivateMapValueClass=" + emptyToPlaceholder(firstNonEmptyPrivateMapValueClassName)
                + ", failure=" + emptyToPlaceholder(failure);
        }

        List<Object> recipes() {
            return recipes;
        }

        private static String emptyToPlaceholder(String value) {
            return value == null || value.isEmpty() ? "<empty>" : value;
        }
    }
}
