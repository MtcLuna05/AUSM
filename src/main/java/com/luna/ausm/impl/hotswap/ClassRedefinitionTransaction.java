package com.luna.ausm.impl.hotswap;

import java.io.IOException;
import java.lang.instrument.ClassDefinition;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies a set of loaded-class replacements as one JVM transaction and keeps
 * the exact pre-transaction definitions available for one explicit rollback.
 */
final class ClassRedefinitionTransaction {
    @FunctionalInterface
    interface RedefinitionEngine {
        void redefine(ClassDefinition[] definitions) throws Throwable;
    }

    @FunctionalInterface
    interface BaselineReader {
        byte[] read(Class<?> target) throws IOException;
    }

    private final RedefinitionEngine engine;
    private final BaselineReader baselineReader;
    private final Map<Class<?>, byte[]> currentDefinitions = new IdentityHashMap<>();
    private List<ClassDefinition> rollbackDefinitions = List.of();

    ClassRedefinitionTransaction(RedefinitionEngine engine, BaselineReader baselineReader) {
        this.engine = engine;
        this.baselineReader = baselineReader;
    }

    synchronized List<Class<?>> apply(Map<Class<?>, byte[]> requestedDefinitions) throws Throwable {
        if (requestedDefinitions.isEmpty()) {
            return List.of();
        }

        Map<Class<?>, byte[]> replacements = copyDefinitions(requestedDefinitions);
        List<ClassDefinition> snapshots = new ArrayList<>(replacements.size());
        List<ClassDefinition> definitions = new ArrayList<>(replacements.size());
        for (Map.Entry<Class<?>, byte[]> replacement : replacements.entrySet()) {
            Class<?> target = replacement.getKey();
            byte[] current = currentDefinitions.get(target);
            if (current == null) {
                current = baselineReader.read(target);
            }
            if (current == null || current.length == 0) {
                throw new IOException("No pre-swap bytecode available for " + target.getName());
            }
            snapshots.add(new ClassDefinition(target, current.clone()));
            definitions.add(new ClassDefinition(target, replacement.getValue()));
        }

        // Instrumentation guarantees that an exception from this one call
        // leaves every class in the batch unchanged.
        engine.redefine(definitions.toArray(ClassDefinition[]::new));
        rollbackDefinitions = List.copyOf(snapshots);
        replacements.forEach((target, bytecode) -> currentDefinitions.put(target, bytecode.clone()));
        return List.copyOf(replacements.keySet());
    }

    synchronized List<Class<?>> rollback() throws Throwable {
        if (rollbackDefinitions.isEmpty()) {
            return List.of();
        }

        List<ClassDefinition> restoring = rollbackDefinitions;
        engine.redefine(restoring.toArray(ClassDefinition[]::new));
        for (ClassDefinition definition : restoring) {
            currentDefinitions.put(
                    definition.getDefinitionClass(),
                    definition.getDefinitionClassFile().clone());
        }
        rollbackDefinitions = List.of();
        return restoring.stream().map(ClassDefinition::getDefinitionClass).toList();
    }

    synchronized boolean canRollback() {
        return !rollbackDefinitions.isEmpty();
    }

    private static Map<Class<?>, byte[]> copyDefinitions(Map<Class<?>, byte[]> requestedDefinitions) {
        Map<Class<?>, byte[]> copied = new LinkedHashMap<>();
        requestedDefinitions.forEach((target, bytecode) -> copied.put(target, bytecode.clone()));
        return copied;
    }
}
