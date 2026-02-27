package org.unlaxer.calculator;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Built-in sample provider for runtime catalog injection.
 *
 * <p>Use with:
 * `-Dtinyexpression.catalog.provider.class=org.unlaxer.calculator.SampleInMemoryRuntimeCatalogProvider`</p>
 *
 * <p>Optional settings:
 * - `tinyexpression.catalog.sample.exactNames=age,score`
 * - `tinyexpression.catalog.sample.partialPrefixes=kind,segment`
 * - `tinyexpression.catalog.sample.fallbackPaths=/path/a.tecatalog,/path/b.txt`</p>
 */
public final class SampleInMemoryRuntimeCatalogProvider implements RuntimeCatalogProvider {

    static final String EXACT_NAMES_PROPERTY = "tinyexpression.catalog.sample.exactNames";
    static final String EXACT_NAMES_ENV = "TINYEXPRESSION_CATALOG_SAMPLE_EXACT_NAMES";
    static final String PARTIAL_PREFIXES_PROPERTY = "tinyexpression.catalog.sample.partialPrefixes";
    static final String PARTIAL_PREFIXES_ENV = "TINYEXPRESSION_CATALOG_SAMPLE_PARTIAL_PREFIXES";
    static final String FALLBACK_PATHS_PROPERTY = "tinyexpression.catalog.sample.fallbackPaths";
    static final String FALLBACK_PATHS_ENV = "TINYEXPRESSION_CATALOG_SAMPLE_FALLBACK_PATHS";

    @Override
    public TinyExpressionVariableCatalog.Rules load(TinyExpressionVariableCatalog.CatalogProvider fallbackFileProvider) {
        Set<String> exactNames = parseCommaSeparated(firstNonBlank(
                System.getProperty(EXACT_NAMES_PROPERTY),
                System.getenv(EXACT_NAMES_ENV)));
        Set<String> partialPrefixes = parseCommaSeparated(firstNonBlank(
                System.getProperty(PARTIAL_PREFIXES_PROPERTY),
                System.getenv(PARTIAL_PREFIXES_ENV)));

        String fallbackPaths = firstNonBlank(
                System.getProperty(FALLBACK_PATHS_PROPERTY),
                System.getenv(FALLBACK_PATHS_ENV));
        if (fallbackPaths != null && fallbackPaths.isBlank() == false) {
            TinyExpressionVariableCatalog.Rules fallbackRules = loadFromFallbackPaths(
                    fallbackPaths,
                    fallbackFileProvider == null ? TinyExpressionVariableCatalog.defaultProvider() : fallbackFileProvider);
            exactNames.addAll(fallbackRules.exactNames());
            partialPrefixes.addAll(fallbackRules.partialPrefixes());
        }

        if (exactNames.isEmpty() && partialPrefixes.isEmpty()) {
            return TinyExpressionVariableCatalog.Rules.empty();
        }
        return new TinyExpressionVariableCatalog.Rules(
                Set.copyOf(exactNames),
                Set.copyOf(partialPrefixes),
                "sample-in-memory");
    }

    private TinyExpressionVariableCatalog.Rules loadFromFallbackPaths(
            String rawPathList,
            TinyExpressionVariableCatalog.CatalogProvider fallbackFileProvider) {
        Set<String> exactNames = new LinkedHashSet<>();
        Set<String> partialPrefixes = new LinkedHashSet<>();
        for (String segment : rawPathList.split(",")) {
            String trimmed = segment.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            TinyExpressionVariableCatalog.Rules current = fallbackFileProvider.load(Path.of(trimmed));
            if (current.isEmpty()) {
                continue;
            }
            exactNames.addAll(current.exactNames());
            partialPrefixes.addAll(current.partialPrefixes());
        }
        if (exactNames.isEmpty() && partialPrefixes.isEmpty()) {
            return TinyExpressionVariableCatalog.Rules.empty();
        }
        return new TinyExpressionVariableCatalog.Rules(
                Set.copyOf(exactNames),
                Set.copyOf(partialPrefixes),
                "sample-fallback-paths");
    }

    private Set<String> parseCommaSeparated(String raw) {
        Set<String> values = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return values;
        }
        for (String segment : raw.split(",")) {
            String trimmed = segment.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("$")) {
                values.add(trimmed.substring(1));
            } else {
                values.add(trimmed);
            }
        }
        return values;
    }

    private String firstNonBlank(String... candidates) {
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
}
