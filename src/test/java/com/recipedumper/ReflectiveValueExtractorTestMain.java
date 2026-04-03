package com.recipedumper;

public class ReflectiveValueExtractorTestMain {
    public static void main(String[] args) {
        shouldReadPublicField();
        shouldReadPrivateFieldFromSuperclass();
        shouldReadGetterWhenFieldIsMissing();
        shouldInvokePrivateNoArgMethod();
        shouldUnwrapContentCandidateFromContentField();
        shouldUnwrapContentCandidateFromInnerField();
        shouldUnwrapContentCandidateFromIngredientGetter();
        shouldRecursivelyUnwrapNestedWrappers();
        shouldRecursivelyUnwrapNestedInnerWrappers();
        shouldConvertUsingCapabilityOfMethod();
        shouldFormatNumericEnergyAsEnergyStack();
        shouldFormatStructuredEnergyAsEnergyStack();
        shouldPreferWrappedItemAmountOverStackCount();
        shouldReadNestedWrappedItemAmount();
        shouldReadWrappedItemCountField();
        shouldFallBackToStackCountForItems();
    }

    private static void shouldReadPublicField() {
        assertEquals("public-value", ReflectiveValueExtractor.readFieldOrGetter(new PublicFieldHolder(), "content"), "public field should be readable");
    }

    private static void shouldReadPrivateFieldFromSuperclass() {
        assertEquals(144, ReflectiveValueExtractor.readFieldOrGetter(new ChildHolder(), "amount"), "private superclass field should be readable");
    }

    private static void shouldReadGetterWhenFieldIsMissing() {
        GetterOnlyContentHolder holder = new GetterOnlyContentHolder();
        assertEquals("getter-content", ReflectiveValueExtractor.readFieldOrGetter(holder, "content"), "getter should be used when field is missing");
        assertEquals(32, ReflectiveValueExtractor.readFieldOrGetter(holder, "voltage"), "property-style getter should be resolved");
    }

    private static void shouldInvokePrivateNoArgMethod() {
        assertEquals(7, ReflectiveValueExtractor.invokeNoArg(new PrivateMethodHolder(), "amperage"), "private no-arg method should be invokable");
    }

    private static void shouldUnwrapContentCandidateFromContentField() {
        assertEquals("wrapped", ReflectiveValueExtractor.unwrapContentCandidate(new ContentFieldHolder()), "content field should unwrap first");
    }

    private static void shouldUnwrapContentCandidateFromInnerField() {
        assertEquals("inner-value", ReflectiveValueExtractor.unwrapContentCandidate(new InnerFieldHolder()), "inner field should unwrap when content is absent");
    }

    private static void shouldUnwrapContentCandidateFromIngredientGetter() {
        assertEquals("ingredient-value", ReflectiveValueExtractor.unwrapContentCandidate(new IngredientGetterHolder()), "ingredient getter should unwrap wrapper values");
    }

    private static void shouldRecursivelyUnwrapNestedWrappers() {
        assertEquals("deep-value", ReflectiveValueExtractor.unwrapContentCandidate(new NestedWrapperHolder()), "nested wrappers should unwrap recursively");
    }

    private static void shouldRecursivelyUnwrapNestedInnerWrappers() {
        assertEquals("circuit-core", ReflectiveValueExtractor.unwrapNestedProperty(new OuterInnerHolder(), "inner"), "nested getInner wrappers should unwrap recursively");
    }

    private static void shouldConvertUsingCapabilityOfMethod() {
        assertEquals("converted:deep-value", ReflectiveValueExtractor.convertWithCapability(new Capability(), new NestedWrapperHolder()), "capability.of should receive fully unwrapped content");
    }

    private static void shouldFormatNumericEnergyAsEnergyStack() {
        assertEquals("EnergyStack[voltage=7, amperage=1]", ReflectiveValueExtractor.formatEnergyStackContent(7, null), "numeric energy should fall back to single-amp EnergyStack format");
    }

    private static void shouldFormatStructuredEnergyAsEnergyStack() {
        assertEquals("EnergyStack[voltage=30, amperage=2]", ReflectiveValueExtractor.formatEnergyStackContent(new EnergyHolder(), null), "structured energy should preserve voltage and amperage");
    }

    private static void shouldPreferWrappedItemAmountOverStackCount() {
        assertEquals(9, ReflectiveValueExtractor.resolveItemCount(1, new AmountHolder(), null, null), "wrapper amount should override stack count");
    }

    private static void shouldReadWrappedItemCountField() {
        assertEquals(4, ReflectiveValueExtractor.resolveItemCount(1, new CountFieldHolder(), null, null), "wrapper count field should be readable");
    }

    private static void shouldReadNestedWrappedItemAmount() {
        assertEquals(9, ReflectiveValueExtractor.resolveItemCount(1, new NestedAmountWrapper(), null, null), "nested inner wrapper amount should be readable");
    }

    private static void shouldFallBackToStackCountForItems() {
        assertEquals(1, ReflectiveValueExtractor.resolveItemCount(1, null, null, null), "stack count should be used when wrappers have no explicit amount");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static final class PublicFieldHolder {
        public final String content = "public-value";
    }

    private static class ParentHolder {
        @SuppressWarnings("unused")
        private final int amount = 144;
    }

    private static final class ChildHolder extends ParentHolder {
    }

    private static final class GetterOnlyContentHolder {
        public String getContent() {
            return "getter-content";
        }

        public int voltage() {
            return 32;
        }
    }

    private static final class PrivateMethodHolder {
        @SuppressWarnings("unused")
        private int amperage() {
            return 7;
        }
    }

    private static final class ContentFieldHolder {
        @SuppressWarnings("unused")
        private final String content = "wrapped";
    }

    private static final class InnerFieldHolder {
        @SuppressWarnings("unused")
        private final String inner = "inner-value";
    }

    private static class IngredientGetterHolder {
        public String ingredient() {
            return "ingredient-value";
        }
    }

    private static final class NestedWrapperHolder {
        public IngredientGetterHolder getIngredient() {
            return new IngredientGetterHolder() {
                @Override
                public String ingredient() {
                    return "deep-value";
                }
            };
        }
    }

    private static final class OuterInnerHolder {
        public MiddleInnerHolder getInner() {
            return new MiddleInnerHolder();
        }
    }

    private static final class MiddleInnerHolder {
        public String getInner() {
            return "circuit-core";
        }
    }

    private static final class Capability {
        @SuppressWarnings("unused")
        public String of(Object value) {
            return "converted:" + value;
        }
    }

    private static final class EnergyHolder {
        public int getVoltage() {
            return 30;
        }

        public int getAmperage() {
            return 2;
        }
    }

    private static final class AmountHolder {
        public int getAmount() {
            return 9;
        }
    }

    private static final class CountFieldHolder {
        @SuppressWarnings("unused")
        private final int count = 4;
    }

    private static final class NestedAmountWrapper {
        public AmountHolder getInner() {
            return new AmountHolder();
        }
    }
}
