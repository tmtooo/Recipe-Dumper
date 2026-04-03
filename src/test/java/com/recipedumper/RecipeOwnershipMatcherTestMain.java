package com.recipedumper;

import java.util.Arrays;
import java.util.Collections;

public class RecipeOwnershipMatcherTestMain {
    public static void main(String[] args) {
        shouldMatchExactNamespace();
        shouldMatchGtceuMachineRecipeOwnedByAddonResources();
        shouldMatchGtceuMachineRecipeOwnedByAddonIdHint();
        shouldRejectPlainGtceuMachineRecipeForAddonDump();
    }

    private static void shouldMatchExactNamespace() {
        assertMatch(
            true,
            RecipeOwnershipMatcher.matchesRequestedMod(
                "gtceu",
                "gtceu",
                "gtceu:cutter/oak_slab",
                Collections.emptyList()
            ),
            "exact namespace should match"
        );
    }

    private static void shouldMatchGtceuMachineRecipeOwnedByAddonResources() {
        assertMatch(
            true,
            RecipeOwnershipMatcher.matchesRequestedMod(
                "gtocore",
                "gtceu",
                "gtceu:assembler/some_machine_recipe",
                Arrays.asList("gtocore:stellar_alloy_dust", "minecraft:water")
            ),
            "gtceu machine recipe with gtocore resources should match gtocore dump"
        );
    }

    private static void shouldMatchGtceuMachineRecipeOwnedByAddonIdHint() {
        assertMatch(
            true,
            RecipeOwnershipMatcher.matchesRequestedMod(
                "gtocore",
                "gtceu",
                "gtceu:assembler/gtocore_custom_plate",
                Collections.emptyList()
            ),
            "gtceu machine recipe with addon hint in id should match"
        );
    }

    private static void shouldRejectPlainGtceuMachineRecipeForAddonDump() {
        assertMatch(
            false,
            RecipeOwnershipMatcher.matchesRequestedMod(
                "gtocore",
                "gtceu",
                "gtceu:assembler/cover_gold_wire_gt_double_styrene_butadiene",
                Arrays.asList("gtceu:gold_double_wire", "forge:styrene_butadiene_rubber")
            ),
            "plain gtceu machine recipe should not match gtocore dump"
        );
    }

    private static void assertMatch(boolean expected, boolean actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }
}
