# Embedded Java diagnostics in the existing P4 server

This is an explicit migration profile. The default dependency remains unlaxer 3.1.0; default P4/ubnfc and DAP behavior are unchanged. The profile uses the new source-map/provider API from unlaxer-parser 3.3.0-SNAPSHOT (validated at a3d1b131) and JDK 21.0.9. It never evaluates a Tiny formula or Java body; the external javac adapter disables annotation processing.

Build unlaxer-parser's `unlaxer-common` and `unlaxer-dsl` modules with `mvn -pl unlaxer-common,unlaxer-dsl -am install -DskipTests -Dgpg.skip=true`. Install this repository's root artifact using its default dependencies with `mvn install -DskipTests -Dtinyexpression.skipRailroad=true -Dgpg.skip=true`. Then, from this directory:

```sh
mvn clean package -Pembedded-languages -Dgpg.skip=true
```

Point `tinyExpressionP4Lsp.server.jarPath` at the resulting server jar, use JDK 21.0.9, and enable `tinyExpressionP4Lsp.embeddedLanguageDiagnostics`. Other LSP clients can set `initializationOptions.embeddedLanguageDiagnostics: true`. Enabling it with a server that lacks the profile fails initialization explicitly. Merely including the profile does not enable analysis.

The existing `TinyExpressionP4LanguageServerExt` now sends untouched, versioned full documents to an optional diagnostics provider. The production FormulaInfo and P4 parsers construct all section regions; javac diagnostics from each Java body are mapped to original UTF-16 positions. Updating later sections does not reuse first-section cache results. Stale updates are rejected, save revalidates, and close clears diagnostics. Invalid or incomplete region extraction and unavailable providers emit `EMBEDDED_UNAVAILABLE`, not empty success. Inside an owned Java region, completion now uses the same production editor binding and returns source-mapped javac edits, including at unfinished host EOF. Outside Java, existing first-section Tiny completion, formatting and other methods retain their prior behavior.

The bridge is a namespaced port of unlaxer-parser production editor bridge commit f2fc6faa (the strict bridge originated in PR #470). Its Java/Rust contract is tested there using the same production fixture, independent code-point/UTF-16 coordinates and rejection cases. This repository's existing LSP consumer is Java; no Rust LSP consumer is claimed here. Foreign dependency diagnostics need a later consumer extension. Workspace ownership is rejected explicitly by this single-document adapter.

Run `mvn test -Pembedded-languages -Dtest=ProductionEmbeddedDiagnosticsTest,TinyExpressionP4LanguageServerExtTest,TinyExpressionP4DebugAdapterExtTest` here. The integration test runs real javac on production fixture 69 (ten formulas, two Java bodies), checks both error positions, a second-section-only update, Unicode/CRLF, stale updates, save, close and unavailable extraction. Run the 39 existing LSP/DAP tests without the profile as a separate clean build to verify the released dependency path. Use an isolated Maven repository if other checkouts install Tiny artifacts with different generated AST versions.

The explicit editor entry certifies leading CodeBlock prefixes using the real production parser. Recognition-only synthetic closing text never enters the snapshot or edits. Incomplete Java diagnostics and EOF completion stay inside the original body; valid siblings remain usable when another formula fails. The strict parser is unchanged and still reports unfinished formulas as FAILED. The packaged stdio smoke additionally verifies the Unicode/CRLF unfinished-body completion edit at line 3, characters 41–44.
The new completion method is optional on the dependency-neutral SPI. The default released build does not load a provider or alter its Tiny completion behavior. Both diagnostics and completion use the same immutable full snapshot under the existing server's lifecycle lock, so an intervening update cannot relabel an old result with a new version.
