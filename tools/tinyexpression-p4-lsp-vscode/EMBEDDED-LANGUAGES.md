# Embedded Java diagnostics in the existing P4 server

This is an explicit migration profile. The default dependencies follow the current root POM (unlaxer 3.3.0-SNAPSHOT and Tiny 2.1.0); the diagnostics feature remains disabled by default, and P4/ubnfc and DAP behavior are unchanged. The profile uses the new source-map/provider API from unlaxer-parser 3.3.0-SNAPSHOT (validated at a3d1b131) and JDK 21.0.9. It never evaluates a Tiny formula or Java body; the external javac adapter disables annotation processing.

Build unlaxer-parser's `unlaxer-common` and `unlaxer-dsl` modules with `mvn -pl unlaxer-common,unlaxer-dsl -am install -DskipTests -Dgpg.skip=true`. Install this repository's root artifact using the current root dependencies with `mvn install -DskipTests -Dtinyexpression.skipRailroad=true -Dgpg.skip=true`. Then, from this directory:

```sh
mvn clean package -Pembedded-languages -Dgpg.skip=true
```

Point `tinyExpressionP4Lsp.server.jarPath` at the resulting server jar, use JDK 21.0.9, and enable `tinyExpressionP4Lsp.embeddedLanguageDiagnostics`. Other LSP clients can set `initializationOptions.embeddedLanguageDiagnostics: true`. Enabling it with a server that lacks the profile fails initialization explicitly. Merely including the profile does not enable analysis.

The existing `TinyExpressionP4LanguageServerExt` now sends untouched, versioned full documents to an optional diagnostics provider. The production FormulaInfo and P4 parsers construct all section regions; javac diagnostics from each Java body are mapped to original UTF-16 positions. Updating later sections does not reuse first-section cache results. Stale updates are rejected, save revalidates, and close clears diagnostics. Invalid or incomplete region extraction and unavailable providers emit `EMBEDDED_UNAVAILABLE`, not empty success. Inside an owned Java region, completion now uses the same production editor binding and returns source-mapped javac edits, including at unfinished host EOF. Outside Java, existing first-section Tiny completion, formatting and other methods retain their prior behavior.

The bridge is a namespaced port of unlaxer-parser production editor bridge commit f2fc6faa (the strict bridge originated in PR #470), adapted to the current Tiny grammar revision 0d84f0dc. The shared Java/Rust bridge contract is independently tested in that repository at the fixed Tiny revision `f86ce8a5ab0ab7d23fdba2cd477187df8aa7607c`; this current-consumer adaptation has a separate validation scope. This repository's existing LSP consumer is Java; no Rust LSP consumer is claimed here. Foreign dependency diagnostics need a later consumer extension. Workspace ownership is rejected explicitly by this single-document adapter.

The validation targets are distinct:

| Tiny source revision | Hosts / consumer | Independent evidence |
| --- | --- | --- |
| `f86ce8a5ab0ab7d23fdba2cd477187df8aa7607c` | Strict Java / Rust production bridge in unlaxer-parser [#470](https://github.com/opaopa6969/unlaxer-parser/pull/470) | 16 shared inputs / 177 independent observations per host, including original code-point / UTF-16 positions and query edits. |
| `f86ce8a5ab0ab7d23fdba2cd477187df8aa7607c` | Editor Java / Rust production bridge in unlaxer-parser [#479](https://github.com/opaopa6969/unlaxer-parser/pull/479) | Nine shared inputs / 29 independent observations per host; edited unfinished inputs still fail strict parsing. These fixed-grammar fixtures do not certify current long-fence consumer parity. |
| `0d84f0dc5c0331c09f46350cceb21cb57ee25d79` | Existing **Java** Tiny P4 LSP consumer, [#252](https://github.com/opaopa6969/tinyexpression/pull/252) / [#254](https://github.com/opaopa6969/tinyexpression/pull/254) | The paired PRs add opt-in javac diagnostics and EOF completion with current retained-source long fences and original UTF-16 positions. No current Rust Tiny LSP consumer or current-long-fence Java/Rust protocol parity is implemented or verified by this profile. |

Run `mvn test -Pembedded-languages -Dtest=ProductionEmbeddedDiagnosticsTest,TinyExpressionP4LanguageServerExtTest,TinyExpressionP4DebugAdapterExtTest` here. The integration test runs real javac on production fixture 69 (ten formulas, two Java bodies), checks both error positions, a second-section-only update, Unicode/CRLF, stale updates, save, close and unavailable extraction. Run the 40 existing LSP/DAP tests without the profile as a separate clean build to verify the default dependency path. Use an isolated Maven repository if other checkouts install Tiny artifacts with different generated AST versions.

The explicit editor entry certifies leading CodeBlock prefixes using the real production parser. Recognition-only synthetic closing text never enters the snapshot or edits. Incomplete Java diagnostics and EOF completion stay inside the original body; valid siblings remain usable when another formula fails. The strict parser is unchanged and still reports unfinished formulas as FAILED. The packaged stdio smoke additionally verifies the Unicode/CRLF unfinished-body completion edit at line 3, characters 41–44.
The new completion method is optional on the dependency-neutral SPI. The default build does not load a provider or alter its Tiny completion behavior. Both diagnostics and completion use the same immutable full snapshot under the existing server's lifecycle lock, so an intervening update cannot relabel an old result with a new version.


Current Tiny grammar support includes the retained-source long CodeBlock form: exact-width standalone fences keep shorter triple backticks opaque inside Java text. The adapter uses the production LongCodeFence layout after the production parser certifies the block. FormulaInfo hash-comment masking never rewrites an opaque Java body. The current Tiny grammar revision is carried in FormulaInfo/Tiny region identities; the Java profile identity remains `lang/java@0.1.0`.
