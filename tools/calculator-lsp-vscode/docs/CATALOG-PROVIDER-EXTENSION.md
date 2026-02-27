# TinyExpression Catalog Provider Extension

This document describes how to plug a custom TE024 catalog loader (API/DB etc.)
into the TinyExpression LSP server.

## 1. Interface

Implement:
- `org.unlaxer.calculator.RuntimeCatalogProvider`

Method:
- `TinyExpressionVariableCatalog.Rules load(TinyExpressionVariableCatalog.CatalogProvider fallbackFileProvider)`

## 2. Activation

Provide your class on the server classpath and set one of:
- JVM property:
  - `-Dtinyexpression.catalog.provider.class=your.package.YourRuntimeCatalogProvider`
- env var:
  - `TINYEXPRESSION_CATALOG_PROVIDER_CLASS=your.package.YourRuntimeCatalogProvider`

If the provider returns non-empty rules, those rules are used first.
If empty, default file/env catalog loading continues.

## 3. Minimal example

```java
package your.package;

import java.nio.file.Path;
import org.unlaxer.calculator.RuntimeCatalogProvider;
import org.unlaxer.calculator.TinyExpressionVariableCatalog;

public class DbBackedCatalogProvider implements RuntimeCatalogProvider {
    @Override
    public TinyExpressionVariableCatalog.Rules load(
            TinyExpressionVariableCatalog.CatalogProvider fallbackFileProvider) {
        // Example: fallback to a local canonical file if DB is unavailable
        return fallbackFileProvider.load(Path.of("/etc/tinyexpression/catalog.tecatalog"));
    }
}
```

## 4. Notes

- Keep provider constructor no-args.
- Current TE024 rule checks `$prefix` as invalid and expects `$prefix_<suffix>`.
- Canonical format delimiter support is currently `_` only.

## 5. Built-in sample provider

You can use the built-in sample class without adding extra jars:

- class: `org.unlaxer.calculator.SampleInMemoryRuntimeCatalogProvider`
- set:
  - `tinyexpression.catalog.provider.class=org.unlaxer.calculator.SampleInMemoryRuntimeCatalogProvider`
  - optional:
    - `tinyexpression.catalog.sample.exactNames=age,score`
    - `tinyexpression.catalog.sample.partialPrefixes=kind,segment`
    - `tinyexpression.catalog.sample.fallbackPaths=/path/a.tecatalog,/path/b.txt`
