# Embedded Java diagnostics in the existing P4 server

This is an explicit migration profile. The default dependencies follow the current root POM (unlaxer 3.3.0-SNAPSHOT and Tiny 2.1.0); the diagnostics feature remains disabled by default, and P4/ubnfc and DAP behavior are unchanged. The profile uses the new source-map/provider API from unlaxer-parser 3.3.0-SNAPSHOT (validated at a3d1b131) and JDK 21.0.9. It never evaluates a Tiny formula or Java body; the external javac adapter disables annotation processing.

Build unlaxer-parser's `unlaxer-common` and `unlaxer-dsl` modules with `mvn -pl unlaxer-common,unlaxer-dsl -am install -DskipTests -Dgpg.skip=true`. Install this repository's root artifact using the current root dependencies with `mvn install -DskipTests -Dtinyexpression.skipRailroad=true -Dgpg.skip=true`. Then, from this directory:

```sh
mvn clean package -Pembedded-languages -Dgpg.skip=true
```

Point `tinyExpressionP4Lsp.server.jarPath` at the resulting server jar, use JDK 21.0.9, and enable `tinyExpressionP4Lsp.embeddedLanguageDiagnostics`. Other LSP clients can set `initializationOptions.embeddedLanguageDiagnostics: true`. Enabling it with a server that lacks the profile fails initialization explicitly. Merely including the profile does not enable analysis.

The existing `TinyExpressionP4LanguageServerExt` now sends untouched, versioned full documents to an optional diagnostics provider. The production FormulaInfo and P4 parsers construct all section regions; javac diagnostics from each Java body are mapped to original UTF-16 positions. Updating later sections does not reuse first-section cache results. Stale updates are rejected, save revalidates, and close clears diagnostics. Invalid or incomplete region extraction and unavailable providers emit `EMBEDDED_UNAVAILABLE`, not empty success. Existing first-section Tiny completion, formatting and other methods retain their prior behavior; this profile adds Java diagnostics only.

The bridge is a namespaced port of unlaxer-parser PR #470, commit d247f34a, adapted to the current Tiny grammar revision 0d84f0dc. Its Java/Rust contract is tested there using the same production fixture, independent code-point/UTF-16 coordinates and rejection cases. This repository's existing LSP consumer is Java; no Rust LSP consumer is claimed here. Unclosed Java region completion and foreign dependency diagnostics need a later consumer extension. Workspace ownership is rejected explicitly by this single-document adapter.

Run `mvn test -Pembedded-languages -Dtest=ProductionEmbeddedDiagnosticsTest,TinyExpressionP4LanguageServerExtTest,TinyExpressionP4DebugAdapterExtTest` here. The integration test runs real javac on production fixture 69 (ten formulas, two Java bodies), checks both error positions, a second-section-only update, Unicode/CRLF, stale updates, save, close and unavailable extraction. Run the 40 existing LSP/DAP tests without the profile as a separate clean build to verify the default dependency path. Use an isolated Maven repository if other checkouts install Tiny artifacts with different generated AST versions.

Current Tiny grammar support includes the retained-source long CodeBlock form: exact-width standalone fences keep shorter triple backticks opaque inside Java text. The adapter uses the production LongCodeFence layout after the production parser certifies the block. FormulaInfo hash-comment masking never rewrites an opaque Java body. The current Tiny grammar revision is carried in FormulaInfo/Tiny region identities; the Java profile identity remains `lang/java@0.1.0`.
