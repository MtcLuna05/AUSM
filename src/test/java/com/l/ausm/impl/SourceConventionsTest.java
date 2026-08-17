package com.l.ausm.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SourceConventionsTest {
    private static final Path MAIN_JAVA = Path.of("src/main/java");
    private static final int CLASS_LINE_LIMIT = 1_000;
    private static final Pattern WILDCARD_IMPORT = Pattern.compile("(?m)^import (?:static )?[^;]+\\.\\*;");
    private static final Pattern INLINE_QUALIFIED_TYPE = Pattern.compile(
            "(?m)^(?!package |import ).*\\b(?:com|org|net|java|javax)(?:\\.[a-z_]\\w*)+\\.[A-Z]\\w*");
    private static final Pattern SIZE_COMPARED_WITH_ZERO = Pattern.compile(
            "\\.size\\(\\)\\s*(?:==|!=|>|<=)\\s*0|0\\s*(?:==|!=|<|>=)\\s*[^;\\n]+\\.size\\(\\)");
    private static final Pattern NUMBERED_SPLIT_NAME = Pattern.compile(".*Part\\d+\\.java");
    private static final Pattern DOUBLE_SEMICOLON = Pattern.compile("\\);;");

    @Test
    void preventsJavaMonoliths() throws IOException {
        try (Stream<Path> files = javaSources()) {
            files.forEach(file -> {
                String relative = normalizedRelativePath(file);
                long lineCount = lineCount(file);
                assertTrue(lineCount <= CLASS_LINE_LIMIT,
                        () -> relative + " has " + lineCount + " lines; maximum is " + CLASS_LINE_LIMIT);
            });
        }
    }

    @Test
    void requiresExplicitImports() throws IOException {
        try (Stream<Path> files = javaSources()) {
            files.forEach(file -> assertFalse(WILDCARD_IMPORT.matcher(read(file)).find(),
                    () -> normalizedRelativePath(file) + " contains a wildcard import"));
        }
    }

    @Test
    void requiresImportsInsteadOfInlineQualifiedTypes() throws IOException {
        try (Stream<Path> files = javaSources()) {
            files.forEach(file -> assertFalse(INLINE_QUALIFIED_TYPE.matcher(withoutCommentsAndLiterals(read(file))).find(),
                    () -> normalizedRelativePath(file) + " contains an inline fully-qualified type"));
        }
    }

    @Test
    void usesIsEmptyForEmptinessChecks() throws IOException {
        try (Stream<Path> files = javaSources()) {
            files.forEach(file -> assertFalse(SIZE_COMPARED_WITH_ZERO.matcher(read(file)).find(),
                    () -> normalizedRelativePath(file) + " compares size() with zero instead of using isEmpty()"));
        }
    }

    @Test
    void requiresResponsibilityBasedSplitNames() throws IOException {
        try (Stream<Path> files = javaSources()) {
            files.forEach(file -> assertFalse(NUMBERED_SPLIT_NAME.matcher(file.getFileName().toString()).matches(),
                    () -> normalizedRelativePath(file) + " uses a numbered split-class name"));
        }
    }

    @Test
    void rejectsRedundantDoubleSemicolons() throws IOException {
        try (Stream<Path> files = javaSources()) {
            files.forEach(file -> assertFalse(DOUBLE_SEMICOLON.matcher(withoutCommentsAndLiterals(read(file))).find(),
                    () -> normalizedRelativePath(file) + " contains a redundant double semicolon"));
        }
    }

    @Test
    void keepsMixinImplementationTypesOutOfRuntimeHelpers() {
        Stream.of(
                        "com/l/ausm/impl/compat/nothirium/NothiriumRenderChunkTaskCompileHooks.java",
                        "com/l/ausm/impl/compat/nothirium/NothiriumBloomCompileHooks.java",
                        "com/l/ausm/impl/compat/nothirium/NothiriumLayerCompileHooks.java")
                .map(MAIN_JAVA::resolve)
                .forEach(file -> assertFalse(
                        withoutCommentsAndLiterals(read(file)).contains("NothiriumRenderChunkTaskCompileMixin"),
                        () -> normalizedRelativePath(file) + " leaks a non-loadable mixin type into runtime bytecode"));
    }

    @Test
    void keepsOrdinarySupportClassesOutOfMixinPackages() throws IOException {
        Path mixinRoot = MAIN_JAVA.resolve("com/l/ausm/impl/mixin");
        try (Stream<Path> files = Files.walk(mixinRoot).filter(path -> path.toString().endsWith(".java"))) {
            files.forEach(file -> assertTrue(withoutCommentsAndLiterals(read(file)).contains("@Mixin"),
                    () -> normalizedRelativePath(file) + " is an ordinary class inside a registered Mixin package"));
        }
    }

    private static Stream<Path> javaSources() throws IOException {
        return Files.walk(MAIN_JAVA).filter(path -> path.toString().endsWith(".java"));
    }

    private static String normalizedRelativePath(Path file) {
        return MAIN_JAVA.relativize(file).toString().replace('\\', '/');
    }

    private static long lineCount(Path file) {
        try (Stream<String> lines = Files.lines(file)) {
            return lines.count();
        } catch (IOException exception) {
            throw new AssertionError("Could not count lines in " + file, exception);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException exception) {
            throw new AssertionError("Could not read " + file, exception);
        }
    }

    private static String withoutCommentsAndLiterals(String source) {
        StringBuilder sanitized = new StringBuilder(source.length());
        boolean blockComment = false;
        boolean lineComment = false;
        char quote = 0;
        for (int index = 0; index < source.length(); index++) {
            char character = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : 0;
            if (lineComment) {
                if (character == '\n') {
                    lineComment = false;
                    sanitized.append(character);
                } else {
                    sanitized.append(' ');
                }
            } else if (blockComment) {
                if (character == '*' && next == '/') {
                    sanitized.append("  ");
                    index++;
                    blockComment = false;
                } else {
                    sanitized.append(character == '\n' ? '\n' : ' ');
                }
            } else if (quote != 0) {
                if (character == '\\' && next != 0) {
                    sanitized.append("  ");
                    index++;
                } else {
                    sanitized.append(character == '\n' ? '\n' : ' ');
                    if (character == quote) {
                        quote = 0;
                    }
                }
            } else if (character == '/' && next == '/') {
                sanitized.append("  ");
                index++;
                lineComment = true;
            } else if (character == '/' && next == '*') {
                sanitized.append("  ");
                index++;
                blockComment = true;
            } else if (character == '"' || character == '\'') {
                sanitized.append(' ');
                quote = character;
            } else {
                sanitized.append(character);
            }
        }
        return sanitized.toString();
    }
}
