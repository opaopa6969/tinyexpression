package org.unlaxer.calculator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
        Map<String, CatalogEntryInfo> exactEntries = new LinkedHashMap<>();
        Map<String, CatalogEntryInfo> partialEntries = new LinkedHashMap<>();
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
            loaded.exactEntries().forEach(exactEntries::putIfAbsent);
            loaded.partialEntries().forEach(partialEntries::putIfAbsent);
            loadedSources.add(path.toAbsolutePath().toString());
        }
        if (loadedSources.isEmpty()) {
            return Rules.empty();
        }
        return new Rules(
                Set.copyOf(exactNames),
                Set.copyOf(partialPrefixes),
                Map.copyOf(exactEntries),
                Map.copyOf(partialEntries),
                sourceLabel + ":" + String.join(",", loadedSources));
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

    private static String normalizeField(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim();
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

    private static CatalogEntryInfo buildEntryInfo(Path path, String rawDescription, String rawContext) {
        String description = normalizeField(rawDescription);
        String context = normalizeContextLabel(normalizeField(rawContext));
        if (context.isEmpty()) {
            context = inferContextFromPath(path);
        }
        String sourcePath = path == null ? "" : path.toAbsolutePath().toString();
        return new CatalogEntryInfo(context, description, sourcePath);
    }

    private static String deriveLegacyDescription(String[] parts) {
        if (parts == null || parts.length == 0) {
            return "";
        }
        if (parts.length >= 4) {
            return normalizeField(parts[3]);
        }
        if (parts.length == 3) {
            return normalizeField(parts[2]);
        }
        if (parts.length == 2) {
            String second = normalizeField(parts[1]);
            if (second.equalsIgnoreCase("string")
                    || second.equalsIgnoreCase("boolean")
                    || second.equalsIgnoreCase("number")
                    || second.equalsIgnoreCase("float")
                    || second.equalsIgnoreCase("double")
                    || second.equalsIgnoreCase("long")
                    || second.equalsIgnoreCase("int")
                    || second.equalsIgnoreCase("short")
                    || second.equalsIgnoreCase("byte")
                    || second.equalsIgnoreCase("object")) {
                return "type=" + second;
            }
            return second;
        }
        return "";
    }

    private static String deriveLegacyContext(String[] parts) {
        if (parts == null) {
            return "";
        }
        if (parts.length >= 5) {
            return normalizeField(parts[4]);
        }
        return "";
    }

    private static String normalizeContextLabel(String rawContext) {
        if (rawContext == null || rawContext.isBlank()) {
            return "";
        }
        String lowered = rawContext.trim().toLowerCase();
        if (lowered.startsWith("nim")) {
            return "NIM";
        }
        if (lowered.equals("fa") || lowered.startsWith("fa_") || lowered.startsWith("fa-")) {
            return "FA";
        }
        return rawContext.trim();
    }

    private static String inferContextFromPath(Path path) {
        if (path == null) {
            return "";
        }
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase();
        String context = inferContextFromNameToken(fileName);
        if (context.isEmpty() == false) {
            return context;
        }
        Path parent = path.getParent();
        if (parent != null && parent.getFileName() != null) {
            context = inferContextFromNameToken(parent.getFileName().toString().toLowerCase());
            if (context.isEmpty() == false) {
                return context;
            }
        }
        return "";
    }

    private static String inferContextFromNameToken(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return "";
        }
        String[] tokens = rawName.split("[^a-z0-9]+");
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            if (token.startsWith("nim")) {
                return "NIM";
            }
            if (token.startsWith("fa")) {
                return "FA";
            }
        }
        return "";
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
            Map<String, CatalogEntryInfo> exactEntries = new LinkedHashMap<>();
            Map<String, CatalogEntryInfo> partialEntries = new LinkedHashMap<>();
            for (String rawLine : lines) {
                String line = rawLine == null ? "" : rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\|", -1);
                String variableName = normalizeVariableName(parts[0]);
                if (variableName.isEmpty()) {
                    continue;
                }
                String description = deriveLegacyDescription(parts);
                String context = deriveLegacyContext(parts);
                CatalogEntryInfo info = buildEntryInfo(path, description, context);
                if (variableName.endsWith("_*") && variableName.length() > 2) {
                    String prefix = variableName.substring(0, variableName.length() - 2);
                    partialPrefixes.add(prefix);
                    partialEntries.putIfAbsent(prefix, info);
                    continue;
                }
                exactNames.add(variableName);
                exactEntries.putIfAbsent(variableName, info);
            }
            return new Rules(
                    Set.copyOf(exactNames),
                    Set.copyOf(partialPrefixes),
                    Map.copyOf(exactEntries),
                    Map.copyOf(partialEntries),
                    "legacy:" + path.toAbsolutePath());
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
            Map<String, CatalogEntryInfo> exactEntries = new LinkedHashMap<>();
            Map<String, CatalogEntryInfo> partialEntries = new LinkedHashMap<>();
            for (String rawLine : lines) {
                String line = rawLine == null ? "" : rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\|", -1);
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
                    CatalogEntryInfo info = buildEntryInfo(
                            path,
                            parts.length >= 3 ? parts[2] : "",
                            parts.length >= 4 ? parts[3] : "");
                    exactEntries.putIfAbsent(name, info);
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
                    CatalogEntryInfo info = buildEntryInfo(
                            path,
                            parts.length >= 5 ? parts[4] : "",
                            parts.length >= 6 ? parts[5] : "");
                    partialEntries.putIfAbsent(prefix, info);
                }
            }
            return new Rules(
                    Set.copyOf(exactNames),
                    Set.copyOf(partialPrefixes),
                    Map.copyOf(exactEntries),
                    Map.copyOf(partialEntries),
                    "canonical:" + path.toAbsolutePath());
        }

        @Override
        public int priority() {
            return 100;
        }
    }

    public static record CatalogEntryInfo(String context, String description, String sourcePath) {
        public CatalogEntryInfo {
            context = context == null ? "" : context;
            description = description == null ? "" : description;
            sourcePath = sourcePath == null ? "" : sourcePath;
        }

        public boolean hasDescription() {
            return description.isBlank() == false;
        }

        public boolean hasContext() {
            return context.isBlank() == false;
        }
    }

    public static record Rules(
            Set<String> exactNames,
            Set<String> partialPrefixes,
            Map<String, CatalogEntryInfo> exactEntries,
            Map<String, CatalogEntryInfo> partialEntries,
            String source) {

        public Rules {
            exactNames = exactNames == null ? Set.of() : Set.copyOf(exactNames);
            partialPrefixes = partialPrefixes == null ? Set.of() : Set.copyOf(partialPrefixes);
            exactEntries = exactEntries == null ? Map.of() : Map.copyOf(exactEntries);
            partialEntries = partialEntries == null ? Map.of() : Map.copyOf(partialEntries);
            source = source == null ? "none" : source;
        }

        public static Rules empty() {
            return new Rules(Set.of(), Set.of(), Map.of(), Map.of(), "none");
        }

        public CatalogEntryInfo exactEntry(String variableName) {
            if (variableName == null) {
                return null;
            }
            return exactEntries.get(variableName);
        }

        public CatalogEntryInfo partialEntry(String prefix) {
            if (prefix == null) {
                return null;
            }
            return partialEntries.get(prefix);
        }

        public boolean isMissingPartialSuffix(String variableName) {
            return partialPrefixes.contains(variableName);
        }

        public boolean isAllowed(String variableName) {
            if (variableName == null || variableName.isBlank()) {
                return false;
            }
            if (exactNames.contains(variableName)) {
                return true;
            }
            for (String prefix : partialPrefixes) {
                if (variableName.startsWith(prefix + "_")) {
                    return true;
                }
            }
            return false;
        }

        public boolean isEmpty() {
            return exactNames.isEmpty() && partialPrefixes.isEmpty();
        }
    }
}
