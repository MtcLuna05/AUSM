package com.luna.ausm.impl.hotswap;

import java.lang.instrument.ClassDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ClassRedefinitionTransactionTest {
    private static final byte[] FIRST_BASELINE = {1};
    private static final byte[] SECOND_BASELINE = {2};

    @Test
    void appliesAndRollsBackTheWholeBatchInSingleCalls() throws Throwable {
        List<ClassDefinition[]> calls = new ArrayList<>();
        ClassRedefinitionTransaction transaction = new ClassRedefinitionTransaction(
                calls::add,
                target -> target == FirstFixture.class ? FIRST_BASELINE : SECOND_BASELINE);

        List<Class<?>> applied = transaction.apply(Map.of(
                FirstFixture.class, new byte[]{11},
                SecondFixture.class, new byte[]{22}));

        assertEquals(2, applied.size());
        assertEquals(1, calls.size());
        assertEquals(2, calls.getFirst().length);
        assertTrue(transaction.canRollback());

        List<Class<?>> restored = transaction.rollback();

        assertEquals(2, restored.size());
        assertEquals(2, calls.size());
        assertDefinition(calls.get(1), FirstFixture.class, FIRST_BASELINE);
        assertDefinition(calls.get(1), SecondFixture.class, SECOND_BASELINE);
        assertFalse(transaction.canRollback());
    }

    @Test
    void rejectedBatchDoesNotReplaceThePreviousRollbackPoint() throws Throwable {
        List<ClassDefinition[]> calls = new ArrayList<>();
        boolean[] reject = {false};
        ClassRedefinitionTransaction transaction = new ClassRedefinitionTransaction(
                definitions -> {
                    calls.add(definitions);
                    if (reject[0]) {
                        throw new UnsupportedOperationException("rejected");
                    }
                },
                target -> FIRST_BASELINE);
        transaction.apply(Map.of(FirstFixture.class, new byte[]{11}));
        reject[0] = true;

        assertThrows(UnsupportedOperationException.class,
                () -> transaction.apply(Map.of(FirstFixture.class, new byte[]{12})));

        reject[0] = false;
        transaction.rollback();
        assertEquals(3, calls.size());
        assertDefinition(calls.get(2), FirstFixture.class, FIRST_BASELINE);
    }

    private static void assertDefinition(ClassDefinition[] definitions,
                                         Class<?> target,
                                         byte[] expectedBytecode) {
        for (ClassDefinition definition : definitions) {
            if (definition.getDefinitionClass() == target) {
                assertArrayEquals(expectedBytecode, definition.getDefinitionClassFile());
                return;
            }
        }
        throw new AssertionError("Missing definition for " + target.getName());
    }

    private static final class FirstFixture {
    }

    private static final class SecondFixture {
    }
}
