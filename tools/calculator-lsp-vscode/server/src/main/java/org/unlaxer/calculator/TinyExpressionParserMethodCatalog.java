package org.unlaxer.calculator;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.SuggestableParser;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.FormulaParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleNamedParenthesesParser;

final class TinyExpressionParserMethodCatalog {

    private static final String PARSER_PACKAGE_PREFIX = "org.unlaxer.tinyexpression.parser.";
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)");
    private static final Set<String> EXCLUDED = Set.of(
            "if", "match", "var", "variable", "import", "as", "set", "description",
            "call", "external", "internal", "default", "returning", "not");
    private static final Set<String> FALLBACK = Set.of(
            "sin", "cos", "tan", "sqrt", "min", "max", "random",
            "isPresent", "startsWith", "endsWith", "contains", "in", "toNum");
    private static final Set<String> METHODS = Collections.unmodifiableSet(loadMethods());

    private TinyExpressionParserMethodCatalog() {}

    static Set<String> methodNames() {
        return METHODS;
    }

    private static Set<String> loadMethods() {
        Set<String> methods = new HashSet<>();
        for (String className : discoverClassNames()) {
            collectFromClass(className, methods);
        }
        methods.removeIf(name -> EXCLUDED.contains(name));
        methods.removeIf(String::isBlank);
        if (methods.isEmpty()) {
            methods.addAll(FALLBACK);
            return methods;
        }
        methods.addAll(FALLBACK);
        return methods;
    }

    private static Set<String> discoverClassNames() {
        Set<String> classNames = new HashSet<>();
        CodeSource codeSource = FormulaParser.class.getProtectionDomain().getCodeSource();
        if (codeSource == null) {
            return classNames;
        }
        URL location = codeSource.getLocation();
        if (location == null) {
            return classNames;
        }
        try {
            Path path = Path.of(location.toURI());
            if (Files.isDirectory(path)) {
                discoverFromDirectory(path, classNames);
            } else if (path.toString().endsWith(".jar")) {
                discoverFromJar(path, classNames);
            }
        } catch (URISyntaxException | IOException ignored) {
            // fallback set will still be used
        }
        return classNames;
    }

    private static void discoverFromDirectory(Path root, Set<String> classNames) throws IOException {
        Path parserRoot = root.resolve("org/unlaxer/tinyexpression/parser");
        if (Files.exists(parserRoot) == false) {
            return;
        }
        Files.walk(parserRoot)
                .filter(p -> p.toString().endsWith(".class"))
                .forEach(p -> {
                    String relative = root.relativize(p).toString().replace('\\', '/');
                    String className = relative.substring(0, relative.length() - ".class".length())
                            .replace('/', '.');
                    classNames.add(className);
                });
    }

    private static void discoverFromJar(Path jarPath, Set<String> classNames) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith("org/unlaxer/tinyexpression/parser/") == false
                        || name.endsWith(".class") == false) {
                    continue;
                }
                String className = name.substring(0, name.length() - ".class".length())
                        .replace('/', '.');
                classNames.add(className);
            }
        }
    }

    private static void collectFromClass(String className, Set<String> methods) {
        if (className.startsWith(PARSER_PACKAGE_PREFIX) == false) {
            return;
        }
        try {
            Class<?> clazz = Class.forName(className, false, FormulaParser.class.getClassLoader());
            if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
                return;
            }
            if (SuggestableParser.class.isAssignableFrom(clazz)) {
                collectFromSuggestableClass(clazz, methods);
            }
            if (JavaStyleNamedParenthesesParser.class.isAssignableFrom(clazz)) {
                collectFromNamedParenthesesClass(clazz, methods);
            }
        } catch (Throwable ignored) {
            // keep best-effort extraction
        }
    }

    private static void collectFromSuggestableClass(Class<?> clazz, Set<String> methods) {
        Object parser = instantiateNoArg(clazz);
        if (parser == null) {
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            List<Object> targetStrings = (List<Object>) SuggestableParser.class
                    .getField("targetStrings")
                    .get(parser);
            if (targetStrings == null) {
                return;
            }
            for (Object target : targetStrings) {
                String token = normalizeMethodToken(String.valueOf(target));
                if (token.isBlank() == false) {
                    methods.add(token);
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // best effort
        }
    }

    private static void collectFromNamedParenthesesClass(Class<?> clazz, Set<String> methods) {
        Object parser = instantiateNoArg(clazz);
        if (parser == null) {
            return;
        }
        try {
            Method nameParser = clazz.getMethod("nameParser");
            Object nameParserInstance = nameParser.invoke(parser);
            String token = readWordToken(nameParserInstance);
            token = normalizeMethodToken(token);
            if (token.isBlank() == false) {
                methods.add(token);
            }
        } catch (ReflectiveOperationException ignored) {
            // best effort
        }
    }

    private static Object instantiateNoArg(Class<?> clazz) {
        try {
            Constructor<?> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String readWordToken(Object parser) {
        if (parser == null) {
            return "";
        }
        if (parser instanceof WordParser wordParser) {
            return String.valueOf(wordParser.word);
        }
        try {
            Object word = parser.getClass().getField("word").get(parser);
            if (word != null) {
                return String.valueOf(word);
            }
        } catch (ReflectiveOperationException ignored) {
            // fallback below
        }
        if (parser instanceof Parser) {
            return String.valueOf(parser);
        }
        return "";
    }

    private static String normalizeMethodToken(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        String normalized = token.strip();
        while (normalized.startsWith("'") || normalized.startsWith("\"")) {
            normalized = normalized.substring(1).stripLeading();
        }
        while (normalized.endsWith("'") || normalized.endsWith("\"")) {
            normalized = normalized.substring(0, normalized.length() - 1).stripTrailing();
        }
        if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        Matcher matcher = IDENTIFIER_PATTERN.matcher(normalized);
        if (matcher.find() == false) {
            return "";
        }
        return matcher.group(1);
    }
}
