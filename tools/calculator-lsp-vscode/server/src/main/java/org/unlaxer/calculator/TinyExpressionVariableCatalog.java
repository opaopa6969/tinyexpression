package org.unlaxer.calculator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class TinyExpressionVariableCatalog {

    static final String CATALOG_PATH_PROPERTY = "tinyexpression.catalog.path";
    static final String CATALOG_PATH_ENV = "TINYEXPRESSION_CATALOG_PATH";
    static final String LEGACY_ALLOWED_VARIABLES_PROPERTY = "tinyexpr.allowedVariablesFile";
    static final String LEGACY_ALLOWED_VARIABLES_ENV = "TINYEXPR_ALLOWED_VARIABLES_FILE";
    static final String LEGACY_ALLOWED_CHECK_KIND_PROPERTY = "tinyexpr.allowedCheckKindFile";
    static final String LEGACY_ALLOWED_CHECK_KIND_ENV = "TINYEXPR_ALLOWED_CHECK_KIND_FILE";
    static final String CATALOG_PROVIDER_CLASS_PROPERTY = "tinyexpression.catalog.provider.class";
    static final String CATALOG_PROVIDER_CLASS_ENV = "TINYEXPRESSION_CATALOG_PROVIDER_CLASS";
    static final String CANONICAL_FORMAT_MARKER = "tinyexpression-catalog-v1";

    private TinyExpressionVariableCatalog() {
    }

    static Rules loadFromRuntimeConfiguration() {
        RuntimeCatalogProvider runtimeProvider = tryCreateRuntimeProvider();
        if (runtimeProvider != null) {
            try {
                Rules provided = runtimeProvider.load(defaultProvider());
                if (provided.isEmpty() == false) {
                    return provided;
                }
            } catch (RuntimeException providerError) {
                System.err.println("[tinyExpressionLsp] runtime catalog provider error: " + providerError);
            }
        }

        String catalogPaths = firstNonBlank(
                System.getProperty(CATALOG_PATH_PROPERTY),
                System.getenv(CATALOG_PATH_ENV)
        );
        if (catalogPaths != null) {
            return loadFromPathList(catalogPaths, CATALOG_PATH_PROPERTY);
        }

        List<String> legacyPathParts = new ArrayList<>();
        appendPathPart(legacyPathParts,
                firstNonBlank(
                        System.getProperty(LEGACY_ALLOWED_VARIABLES_PROPERTY),
                        System.getenv(LEGACY_ALLOWED_VARIABLES_ENV)));
        appendPathPart(legacyPathParts,
                firstNonBlank(
                        System.getProperty(LEGACY_ALLOWED_CHECK_KIND_PROPERTY),
                        System.getenv(LEGACY_ALLOWED_CHECK_KIND_ENV)));
        if (legacyPathParts.isEmpty()) {
            return Rules.empty();
        }
        return loadFromPathList(String.join(",", legacyPathParts), "legacy-properties");
    }

    public static Rules loadFromPathList(String rawPathList, String sourceLabel) {
        return loadFromPathList(rawPathList, sourceLabel, defaultProvider());
    }

    static Rules loadFromPathList(String rawPathList, String sourceLabel, CatalogProvider provider) {
        if (rawPathList == null || rawPathList.isBlank()) {
            return Rules.empty();
        }
        Set<String> exactNames = new LinkedHashSet<>();
        Set<String> partialPrefixes = new LinkedHashSet<>();
        List<String> loadedSources = new ArrayList<>();
        for (String segment : rawPathList.split(",")) {
            String trimmed = segment.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Path path = Path.of(trimmed);
            if (Files.exists(path) == false) {
                continue;
            }
            Rules loaded = provider.load(path);
            if (loaded.isEmpty()) {
                continue;
            }
            exactNames.addAll(loaded.exactNames());
            partialPrefixes.addAll(loaded.partialPrefixes());
            loadedSources.add(path.toAbsolutePath().toString());
        }
        if (loadedSources.isEmpty()) {
            return Rules.empty();
        }
        return new Rules(Set.copyOf(exactNames), Set.copyOf(partialPrefixes), sourceLabel + ":" + String.join(",", loadedSources));
    }

    public static CatalogProvider defaultProvider() {
        return new CompositeCatalogProvider(
                List.of(new CanonicalCatalogAdapter(), new LegacyCatalogAdapter()));
    }

    private static String normalizeVariableName(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("$")) {
            return trimmed.substring(1);
        }
        return trimmed;
    }

    private static String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (candidate != null && candidate.isBlank() == false) {
                return candidate;
            }
        }
        return null;
    }

    private static void appendPathPart(List<String> target, String part) {
        if (part == null || part.isBlank()) {
            return;
        }
        target.add(part.trim());
    }

    private static RuntimeCatalogProvider tryCreateRuntimeProvider() {
        String className = firstNonBlank(
                System.getProperty(CATALOG_PROVIDER_CLASS_PROPERTY),
                System.getenv(CATALOG_PROVIDER_CLASS_ENV));
        if (className == null || className.isBlank()) {
            return null;
        }
        try {
            Class<?> rawClass = Class.forName(className.trim());
            if (RuntimeCatalogProvider.class.isAssignableFrom(rawClass) == false) {
                System.err.println("[tinyExpressionLsp] catalog provider class does not implement RuntimeCatalogProvider: " + className);
                return null;
            }
            @SuppressWarnings("unchecked")
            Class<? extends RuntimeCatalogProvider> providerClass =
                    (Class<? extends RuntimeCatalogProvider>) rawClass;
            return providerClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException reflectionError) {
            System.err.println("[tinyExpressionLsp] failed to create catalog provider: " + className + " (" + reflectionError + ")");
            return null;
        }
    }

    public interface CatalogProvider {
        Rules load(Path path);
    }

    interface CatalogAdapter {
        boolean supports(Path path, List<String> lines);
        Rules parse(Path path, List<String> lines);
        default int priority() {
            return 100;
        }
    }

    static final class CompositeCatalogProvider implements CatalogProvider {
        private final List<CatalogAdapter> adapters;

        CompositeCatalogProvider(List<CatalogAdapter> adapters) {
            this.adapters = adapters == null ? List.of() : adapters.stream()
                    .sorted(Comparator.comparingInt(CatalogAdapter::priority))
                    .toList();
        }

        @Override
        public Rules load(Path path) {
            if (path == null || Files.exists(path) == false) {
                return Rules.empty();
            }
            List<String> lines;
            try {
                lines = Files.readAllLines(path);
            } catch (IOException ioException) {
                System.err.println("[tinyExpressionLsp] failed to read catalog: " + path + " (" + ioException + ")");
                return Rules.empty();
            }
            for (CatalogAdapter adapter : adapters) {
                if (adapter.supports(path, lines) == false) {
                    continue;
                }
                try {
                    return adapter.parse(path, lines);
                } catch (RuntimeException parseException) {
                    System.err.println("[tinyExpressionLsp] failed to parse catalog with "
                            + adapter.getClass().getSimpleName() + ": " + path + " (" + parseException + ")");
                    return Rules.empty();
                }
            }
            return Rules.empty();
        }
    }

    static final class LegacyCatalogAdapter implements CatalogAdapter {
        @Override
        public boolean supports(Path path, List<String> lines) {
            if (lines == null) {
                return false;
            }
            for (String rawLine : lines) {
                String line = rawLine == null ? "" : rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String head = line.split("\\|", 2)[0].trim();
                if ("exact".equalsIgnoreCase(head)
                        || "prefixWithSuffix".equalsIgnoreCase(head)
                        || "prefix_with_suffix".equalsIgnoreCase(head)
                        || CANONICAL_FORMAT_MARKER.equalsIgnoreCase(head)) {
                    return false;
                }
                return true;
            }
            return false;
        }

        @Override
        public Rules parse(Path path, List<String> lines) {
            Set<String> exactNames = new LinkedHashSet<>();
            Set<String> partialPrefixes = new LinkedHashSet<>();
            for (String rawLine : lines) {
                String line = rawLine == null ? "" : rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\|", 2);
                String variableName = normalizeVariableName(parts[0]);
                if (variableName.isEmpty()) {
                    continue;
                }
                if (variableName.endsWith("_*") && variableName.length() > 2) {
                    partialPrefixes.add(variableName.substring(0, variableName.length() - 2));
                    continue;
                }
                exactNames.add(variableName);
            }
            return new Rules(Set.copyOf(exactNames), Set.copyOf(partialPrefixes), "legacy:" + path.toAbsolutePath());
        }

        @Override
        public int priority() {
            return 200;
        }
    }

    static final class CanonicalCatalogAdapter implements CatalogAdapter {
        @Override
        public boolean supports(Path path, List<String> lines) {
            if (path != null && path.getFileName() != null
                    && path.getFileName().toString().endsWith(".tecatalog")) {
                return true;
            }
            if (lines == null) {
                return false;
            }
            for (String rawLine : lines) {
                String line = rawLine == null ? "" : rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String head = line.split("\\|", 2)[0].trim();
                if (CANONICAL_FORMAT_MARKER.equalsIgnoreCase(head)
                        || "exact".equalsIgnoreCase(head)
                        || "prefixWithSuffix".equalsIgnoreCase(head)
                        || "prefix_with_suffix".equalsIgnoreCase(head)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Rules parse(Path path, List<String> lines) {
            Set<String> exactNames = new LinkedHashSet<>();
            Set<String> partialPrefixes = new LinkedHashSet<>();
            for (String rawLine : lines) {
                String line = rawLine == null ? "" : rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\|");
                String kind = parts[0].trim();
                if (CANONICAL_FORMAT_MARKER.equalsIgnoreCase(kind)) {
                    continue;
                }
                if ("exact".equalsIgnoreCase(kind)) {
                    if (parts.length < 2) {
                        continue;
                    }
                    String name = normalizeVariableName(parts[1]);
                    if (name.isEmpty()) {
                        continue;
                    }
                    exactNames.add(name);
                    continue;
                }
                if ("prefixWithSuffix".equalsIgnoreCase(kind)
                        || "prefix_with_suffix".equalsIgnoreCase(kind)) {
                    if (parts.length < 2) {
                        continue;
                    }
                    String prefix = normalizeVariableName(parts[1]);
                    if (prefix.isEmpty()) {
                        continue;
                    }
                    String delimiter = parts.length >= 3 ? parts[2].trim() : "_";
                    if ("_".equals(delimiter) == false) {
                        // Current TE024 rule is `_`-joined suffix. Keep unsupported delimiter lines ignored for now.
                        continue;
                    }
                    partialPrefixes.add(prefix);
                }
            }
            return new Rules(Set.copyOf(exactNames), Set.copyOf(partialPrefixes), "canonical:" + path.toAbsolutePath());
        }

        @Override
        public int priority() {
            return 100;
        }
    }

    public static record Rules(Set<String> exactNames, Set<String> partialPrefixes, String source) {
        public static Rules empty() {
            return new Rules(Set.of(), Set.of(), "none");
        }

        boolean isMissingPartialSuffix(String variableName) {
            return partialPrefixes.contains(variableName);
        }

        public boolean isEmpty() {
            return exactNames.isEmpty() && partialPrefixes.isEmpty();
        }
    }
}
