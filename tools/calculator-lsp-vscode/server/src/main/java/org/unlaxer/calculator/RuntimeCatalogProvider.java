package org.unlaxer.calculator;

/**
 * Extension point for loading TinyExpression variable catalog rules from
 * arbitrary sources (e.g. API/DB).
 *
 * <p>Implementations are selected via
 * `-Dtinyexpression.catalog.provider.class=...` or
 * `TINYEXPRESSION_CATALOG_PROVIDER_CLASS`.</p>
 */
public interface RuntimeCatalogProvider {

    /**
     * Load rules from an external source.
     *
     * <p>Implementations may use the provided fallback to parse file-based
     * catalog sources if needed.</p>
     */
    TinyExpressionVariableCatalog.Rules load(TinyExpressionVariableCatalog.CatalogProvider fallbackFileProvider);
}
