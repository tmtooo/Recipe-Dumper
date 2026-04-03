package com.recipedumper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Mod("rdump001")
public class RecipeDumperMod {
    public static final String MOD_ID = "rdump001";
    public static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static MinecraftServer serverInstance;
    private static final SuggestionProvider<CommandSourceStack> MODID_SUGGESTIONS = (context, builder) -> {
        List<String> modIds = new ArrayList<>();
        ModList.get().forEachModContainer((id, container) -> modIds.add(id));
        return SharedSuggestionProvider.suggest(modIds, builder);
    };

    public RecipeDumperMod() {
        LOGGER.info("Recipe Dumper mod initialized");
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        serverInstance = event.getServer();
        LOGGER.info("Server started, RecipeManager available");
    }

    @SubscribeEvent
    public void onCommandsRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("dumprecipes")
            .requires(source -> source.hasPermission(2))
            .then(Commands.argument("modid", StringArgumentType.word())
                .suggests(MODID_SUGGESTIONS)
                .executes(context -> {
                    String modid = StringArgumentType.getString(context, "modid");
                    context.getSource().sendSuccess(() -> Component.literal("Dumping recipes for mod: " + modid + "..."), true);
                    dumpRecipes(context, modid);
                    return 1;
                })
            )
        );
    }

    private void dumpRecipes(CommandContext<CommandSourceStack> context, String modid) {
        try {
            if (serverInstance == null) {
                context.getSource().sendFailure(Component.literal("Server not fully started yet"));
                return;
            }

            RecipeManager recipeManager = serverInstance.getRecipeManager();
            Collection<Recipe<?>> recipeManagerRecipes = recipeManager.getRecipes();
            GtRecipeRegistryBridge.Inspection gtInspection = GtRecipeRegistryBridge.inspectGtRegistry();
            List<Recipe<?>> gtRegistryRecipes = toMinecraftRecipes(gtInspection.recipes());
            Collection<Recipe<?>> allRecipes = mergeRecipes(recipeManagerRecipes, gtRegistryRecipes);

            LOGGER.info("Total recipes in RecipeManager: {}", recipeManagerRecipes.size());
            LOGGER.info("Total recipes in GT registry: {}", gtRegistryRecipes.size());
            LOGGER.info("Total recipes after merge: {}", allRecipes.size());
            LOGGER.debug("GT registry inspection: {}", gtInspection.summary());

            if (LOGGER.isDebugEnabled()) {
                Map<String, AtomicInteger> namespaceCount = new HashMap<>();
                for (Recipe<?> recipe : allRecipes) {
                    String namespace = getRecipeNamespace(recipe);
                    namespaceCount.computeIfAbsent(namespace, ignored -> new AtomicInteger()).incrementAndGet();
                }
                LOGGER.debug("Namespace distribution:");
                namespaceCount.forEach((namespace, value) -> LOGGER.debug("  {}: {}", namespace, value.get()));
            }

            boolean dumpAll = "all".equalsIgnoreCase(modid);
            List<Map<String, Object>> recipesList = new ArrayList<>();
            AtomicInteger count = new AtomicInteger(0);

            for (Recipe<?> recipe : allRecipes) {
                try {
                    ResourceLocation id = getRecipeId(recipe);
                    if (id == null) {
                        continue;
                    }

                    String namespace = id.getNamespace();
                    ExtractedRecipe extracted = isGtRecipe(recipe) ? extractGTRecipe(recipe) : ExtractedRecipe.empty();

                    if (!dumpAll && !RecipeOwnershipMatcher.matchesRequestedMod(modid, namespace, id.toString(), extracted.ownershipHints)) {
                        continue;
                    }

                    Map<String, Object> recipeData = new LinkedHashMap<>();
                    recipeData.put("namespace", namespace);
                    recipeData.put("id", id.toString());
                    recipeData.put("type", recipe.getClass().getSimpleName());
                    recipeData.putAll(extracted.fields);

                    recipesList.add(recipeData);
                    count.incrementAndGet();

                    if (count.get() <= 5) {
                        LOGGER.info("Found recipe: {}", id);
                    }
                } catch (Exception e) {
                    LOGGER.debug("Error processing recipe {}", recipe, e);
                }
            }

            LOGGER.info("Found {} recipes for mod: {}", count.get(), modid);

            Path outputDir = Paths.get("dumped_recipes", modid);
            Files.createDirectories(outputDir);
            Path outputFile = outputDir.resolve("recipes.json");
            Files.writeString(outputFile, GSON.toJson(recipesList));

            context.getSource().sendSuccess(() -> Component.literal("Dumped " + count.get() + " recipes to " + outputFile), true);
            LOGGER.info("Dumped {} recipes for mod {} to {}", count.get(), modid, outputFile);
        } catch (Exception e) {
            LOGGER.error("Failed to dump recipes", e);
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
        }
    }

    private String getRecipeNamespace(Recipe<?> recipe) {
        ResourceLocation id = getRecipeId(recipe);
        return id != null ? id.getNamespace() : "unknown";
    }

    private Collection<Recipe<?>> mergeRecipes(Collection<Recipe<?>> recipeManagerRecipes, Collection<Recipe<?>> gtRegistryRecipes) {
        Map<String, Recipe<?>> merged = new LinkedHashMap<>();

        for (Recipe<?> recipe : recipeManagerRecipes) {
            ResourceLocation id = getRecipeId(recipe);
            if (id != null) {
                merged.put(id.toString(), recipe);
            }
        }

        for (Recipe<?> recipe : gtRegistryRecipes) {
            ResourceLocation id = getRecipeId(recipe);
            if (id != null) {
                merged.putIfAbsent(id.toString(), recipe);
            }
        }

        return merged.values();
    }

    private List<Recipe<?>> toMinecraftRecipes(Collection<?> rawRecipes) {
        List<Recipe<?>> converted = new ArrayList<>();
        for (Object rawRecipe : rawRecipes) {
            if (rawRecipe instanceof Recipe<?> recipe) {
                converted.add(recipe);
            }
        }
        return converted;
    }

    private ResourceLocation getRecipeId(Recipe<?> recipe) {
        return recipe.getId();
    }

    private boolean isGtRecipe(Recipe<?> recipe) {
        return GtRecipeLikeInspector.isGtRecipeLike(recipe);
    }

    private ExtractedRecipe extractGTRecipe(Recipe<?> recipe) {
        ExtractedRecipe extracted = new ExtractedRecipe();

        Object duration = readRecipeValue(recipe, "duration");
        if (duration instanceof Number) {
            extracted.fields.put("duration", ((Number) duration).intValue());
        }

        Object inputEnergy = readRecipeValue(recipe, "inputEUt");
        Integer eut = extractInputEUt(inputEnergy);
        String energyStackContent = ReflectiveValueExtractor.formatEnergyStackContent(inputEnergy, eut);
        if (eut != null) {
            extracted.fields.put("EUt", eut);
        }

        String recipeId = recipe.getId().toString();

        List<Map<String, Object>> inputs = extractCapabilityEntries(recipeId, readRecipeValue(recipe, "inputs"), extracted.ownershipHints, extracted.fields, energyStackContent);
        if (!inputs.isEmpty()) {
            extracted.fields.put("inputs", inputs);
        }

        List<Map<String, Object>> outputs = extractCapabilityEntries(recipeId, readRecipeValue(recipe, "outputs"), extracted.ownershipHints, extracted.fields, energyStackContent);
        if (!outputs.isEmpty()) {
            extracted.fields.put("outputs", outputs);
        }

        List<Map<String, Object>> tickInputs = extractCapabilityEntries(recipeId, readRecipeValue(recipe, "tickInputs"), extracted.ownershipHints, extracted.fields, energyStackContent);
        if (!tickInputs.isEmpty()) {
            extracted.fields.put("fluidInputs", tickInputs);
        }

        List<Map<String, Object>> tickOutputs = extractCapabilityEntries(recipeId, readRecipeValue(recipe, "tickOutputs"), extracted.ownershipHints, extracted.fields, energyStackContent);
        if (!tickOutputs.isEmpty()) {
            extracted.fields.put("fluidOutputs", tickOutputs);
        }

        return extracted;
    }

    private Integer extractInputEUt(Object energy) {
        if (energy == null) {
            return null;
        }

        Object voltage = readRecipeValue(energy, "voltage");
        Object amperage = readRecipeValue(energy, "amperage");
        if (voltage instanceof Number && amperage instanceof Number) {
            long eut = ((Number) voltage).longValue() * ((Number) amperage).longValue();
            return (int) eut;
        }
        return null;
    }

    private List<Map<String, Object>> extractCapabilityEntries(String recipeId, Object contents, Set<String> ownershipHints, Map<String, Object> recipeFields, String energyStackContent) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!(contents instanceof Map<?, ?>)) {
            return result;
        }

        for (Map.Entry<?, ?> entry : ((Map<?, ?>) contents).entrySet()) {
            Object capabilityKey = entry.getKey();
            String capability = capabilityKey.getClass().getSimpleName().replace("Capability", "");
            if (!(entry.getValue() instanceof List<?>)) {
                continue;
            }

            for (Object content : (List<?>) entry.getValue()) {
                Map<String, Object> extracted = extractContent(recipeId, capabilityKey, capability, content, ownershipHints, recipeFields, energyStackContent);
                if (extracted.isEmpty()) {
                    continue;
                }
                extracted.put("capability", capability);
                result.add(extracted);
            }
        }

        return result;
    }

    private Map<String, Object> extractContent(String recipeId, Object capabilityKey, String capabilityName, Object content, Set<String> ownershipHints, Map<String, Object> recipeFields, String energyStackContent) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (content == null) {
            return result;
        }

        Object chance = readRecipeValue(content, "chance");
        Object maxChance = readRecipeValue(content, "maxChance");
        if (chance instanceof Number) {
            int chanceValue = ((Number) chance).intValue();
            int maxChanceValue = maxChance instanceof Number ? ((Number) maxChance).intValue() : 10000;
            if (chanceValue != maxChanceValue) {
                result.put("chance", chanceValue);
            }
        }

        Object directInner = readRecipeValue(content, "inner");
        Object inner = ReflectiveValueExtractor.convertWithCapability(capabilityKey, content);
        if (inner == null) {
            inner = ReflectiveValueExtractor.unwrapContentCandidate(content);
        }

        if ("EURecipe".equals(capabilityName)) {
            if (inner instanceof Number && !recipeFields.containsKey("EUt")) {
                recipeFields.put("EUt", ((Number) inner).intValue());
            }
            result.put("type", "energystack");
            Number fallbackVoltage = recipeFields.get("EUt") instanceof Number ? (Number) recipeFields.get("EUt") : null;
            String formatted = energyStackContent != null ? energyStackContent : ReflectiveValueExtractor.formatEnergyStackContent(inner, fallbackVoltage);
            result.put("content", formatted != null ? formatted : inner.toString());
            return result;
        }

        if (inner instanceof Ingredient) {
            extractItemIngredient(content, directInner, (Ingredient) inner, result, ownershipHints);
            return result;
        }

        String className = inner.getClass().getSimpleName();
        if (className.contains("FluidIngredient")) {
            extractFluidIngredient(inner, result, ownershipHints);
        } else if ("EnergyStack".equals(className)) {
            result.put("type", "energystack");
            result.put("content", inner.toString());
        } else {
            result.put("type", className.toLowerCase(Locale.ROOT));
            result.put("content", inner.toString());
        }

        return result;
    }

    private void extractItemIngredient(Object content, Object directInner, Ingredient ingredient, Map<String, Object> result, Set<String> ownershipHints) {
        ItemStack[] items = ingredient.getItems();
        if (items.length == 0) {
            return;
        }

        ItemStack stack = items[0];
        Object baseIngredient = ReflectiveValueExtractor.unwrapNestedProperty(ingredient, "inner");
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId != null) {
            result.put("item", itemId.toString());
            ownershipHints.add(itemId.toString());
        }

        Integer count = ReflectiveValueExtractor.resolveItemCount(stack.getCount(), directInner, content, baseIngredient);
        if (count != null) {
            result.put("count", count);
        }

        if ("IntCircuitIngredient".equals(baseIngredient.getClass().getSimpleName()) || "gtceu:programmed_circuit".equals(itemId == null ? null : itemId.toString())) {
            result.put("type", "circuit");
            Object configuration = readRecipeValue(baseIngredient, "configuration");
            if (configuration instanceof Number) {
                result.put("configuration", ((Number) configuration).intValue());
            } else if (stack.getTag() != null) {
                if (stack.getTag().contains("Configuration")) {
                    result.put("configuration", stack.getTag().getInt("Configuration"));
                } else if (stack.getTag().contains("configuration")) {
                    result.put("configuration", stack.getTag().getInt("configuration"));
                }
            }
        } else {
            result.put("type", "item");
        }
    }

    private void extractFluidIngredient(Object ingredient, Map<String, Object> result, Set<String> ownershipHints) {
        Object stacks = ReflectiveValueExtractor.invokeNoArg(ingredient, "getStacks");
        if (stacks == null || stacks.getClass().isArray() && Array.getLength(stacks) == 0) {
            stacks = readRecipeValue(ingredient, "stacks");
        }
        if (stacks != null && stacks.getClass().isArray() && Array.getLength(stacks) > 0) {
            Object stackObj = Array.get(stacks, 0);
            if (stackObj instanceof FluidStack fluidStack) {
                ResourceLocation fluidId = ForgeRegistries.FLUIDS.getKey(fluidStack.getFluid());
                if (fluidId != null) {
                    result.put("fluid", fluidId.toString());
                    ownershipHints.add(fluidId.toString());
                }
                result.put("type", "fluid");
                if (fluidStack.getAmount() > 0) {
                    result.put("amount", (long) fluidStack.getAmount());
                }
                return;
            }
        }

        Object values = readRecipeValue(ingredient, "values");
        if (values != null && values.getClass().isArray() && Array.getLength(values) > 0) {
            Object value = Array.get(values, 0);
            if (value != null) {
                if ("TagValue".equals(value.getClass().getSimpleName())) {
                    Object tag = readRecipeValue(value, "tag");
                    if (tag != null) {
                        String tagName = extractTagName(tag.toString());
                        if (!tagName.isEmpty()) {
                            result.put("fluid", tagName);
                            ownershipHints.add(tagName);
                        }
                    }
                } else if ("FluidValue".equals(value.getClass().getSimpleName())) {
                    Object fluid = readRecipeValue(value, "fluid");
                    if (fluid instanceof Fluid gtFluid) {
                        ResourceLocation fluidId = ForgeRegistries.FLUIDS.getKey(gtFluid);
                        if (fluidId != null) {
                            result.put("fluid", fluidId.toString());
                            ownershipHints.add(fluidId.toString());
                        }
                    }
                }
            }
        }

        Object amount = ReflectiveValueExtractor.invokeNoArg(ingredient, "getAmount");
        if (!(amount instanceof Number)) {
            amount = readRecipeValue(ingredient, "amount");
        }
        if (amount instanceof Number) {
            result.put("amount", ((Number) amount).longValue());
        }
        result.put("type", "fluid");
    }

    private String extractTagName(String tagString) {
        if (tagString == null || tagString.isEmpty()) {
            return "";
        }

        int slashIndex = tagString.indexOf(" / ");
        if (slashIndex >= 0) {
            int endBracket = tagString.lastIndexOf(']');
            if (endBracket > slashIndex) {
                return tagString.substring(slashIndex + 3, endBracket).trim();
            }
            return tagString.substring(slashIndex + 3).trim();
        }

        return tagString.trim();
    }

    private Object readRecipeValue(Object instance, String propertyName) {
        return ReflectiveValueExtractor.readFieldOrGetter(instance, propertyName);
    }

    private static final class ExtractedRecipe {
        private final Map<String, Object> fields = new LinkedHashMap<>();
        private final Set<String> ownershipHints = new LinkedHashSet<>();

        private static ExtractedRecipe empty() {
            return new ExtractedRecipe();
        }
    }
}
