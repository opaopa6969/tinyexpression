# P4 single-root recognition (#256)

Normal recognition in the Java Classic and ubnfc facades, Rust `parse`, and Rust contextual
evaluator now invokes `Formula` once. They do not retry Boolean/String/Object entries after root failure, or invoke
another entry after a successful parse to interpret explicit result hints. Caller-selected
subtree entries remain available. Classic's detailed-diagnostic retry uses the same root after
a failed parse, with its existing remaining-budget policy; it is not result-family dispatch.

`Formula` recognizes code blocks, imports, declarations and annotations once. Its tail is an
ordinary ordered choice of `(family expression, method declarations, EOF)`. A short numeric
prefix fails the enclosing branch and rolls back before trying the boolean/string branch.
A complete numeric or common `if` expression commits the first branch. Nested `Expression`
and the combinators' normal first-success Choice semantics are unchanged. The family wrappers
all map to the existing `ExpressionExpr`, preserving the public AST schema.

An initial experiment with `@longestChoice` on every Expression, and a second restricted to
root dispatch, were rejected after substantial parsing regressions. Repeating the prologue in
each complete branch also re-executed declarations, changing state epochs and losing failure
memo reuse for truncated inputs. The factored grammar avoids these repeated effects. No
handwritten edits to generated parsers substitute for the pinned regeneration.

## AST and position compatibility

The facades preserve the historical semantic subtree for bare expressions that previously
needed an alternate root: numeric comparisons, boolean operations starting with a variable,
and string concatenations starting with a variable. This is a structural AST projection,
without recognizing source text again. Documents retain Formula/Expression wrappers.

Only existing direct result hints select a typed family. A number-shaped variable expression
can be projected into StringConcat/BooleanOr/Object wrappers, and compatible match cases into
the corresponding typed case records. An incompatible literal/operator or mixed hints still
fail. These projections retain identity-based owned code-point spans; new wrappers refer to
origin nodes from the committed parse. String `as` versus no-`as` spelling retains the existing
StringTypedVariableRef versus VariableRef shape. Layout between `$`, the identifier and the
hint is permitted. Identifiers remain the lexical grammar's ASCII alphabet; this work does
not add non-ASCII identifiers.

The spelling check uses one operation-owned CP/UTF-16 (Java) or CP/UTF-8 (Rust) cursor.
String operands and match values are visited in source order, so a list of typed variables
no longer scans the source prefix from the beginning for each value. Reverse, repeated and
empty EOF spans remain valid; the reader moves backwards when necessary rather than assuming
all callers are monotonic. This does not change the general public random-access span API,
allocate a full source-index array, or change grapheme coordinates.

Every accepted input in `root-family-projection.tsv` is compared with the explicit family
entry in tests only, including every record and every node span. That entry is an independent
structural oracle, not a production fallback. Java checks both engines and retained snapshots;
Rust checks its owned public tree. `root-expression.tsv` fixes the existing public root
contracts, including comments containing a family emoji. `root-document-dispatch.tsv` fixes
values, declarations, methods and expression CP boundaries. Capture ranges may include trailing
trivia before a method; these are not grapheme or UTF-16 indexes.

Complete document tails also repair previously rejected inputs such as
`var $s as string set '𝄞😀' description='音符'; $s + '!'` and
`var $n as number set 1;$n<2`. Both Java engines and Rust now recognize and evaluate the entire
document. Losing branches must not duplicate declarations/references or leave method metadata
behind; the failure regression checks this before closing the context.

## Verification and limits

Use Java 21, Rust 1.85, the generator revision recorded in UBNFC_PIN/ubnfc-pin.txt, and an
isolated Maven repository containing the chosen unlaxer source build. For this change the
fixed parser dependency is source `faccb2c9`, version `3.3.0` (not a new Central publication).

```sh
mvn -Dmaven.repo.local=/tmp/unlaxer-tiny-root-choice-m2 -Dunlaxer.version=3.3.0 \
  -Dtinyexpression.skipRailroad=true -Dgpg.skip=true -Dtinyexpression.rust.shared=true \
  -Dtest=P4SingleRecognitionProjectionTest,P4ProjectionTextReaderTest,P4RustRootExpressionAcceptanceTest test
RUSTUP_TOOLCHAIN=1.85.0 cargo test --manifest-path rust/Cargo.toml -p tinyexpression-rs \
  --test root_family_projection --test java_differential --test parser_cli
UBNFC_DIR=/path/to/pinned/ubnfc scripts/regenerate-ubnfc-parser.sh --check --require-full
UBNFC_DIR=/path/to/pinned/ubnfc rust/check-generated.sh
python3 scripts/generate-ubnfc-converter.py --check
```

The fixed 953-formula / 12,609-row Java golden is compared without rewriting expectations.
A golden result compares value/type/bits or failure stage/class; diagnostic prose is not its
cross-language contract. The new tests additionally check EOF rejection, Unicode CP failure
positions, scope rollback, retained sources and typed evaluator use without a fallback.

On the shared development host, the same 350-case Java engine comparison completed in
11.166 s before this change and 8.627 s with the factored grammar. These are reference runs,
not eligible dedicated-host throughput measurements or a claim of general speed improvement.
The existing Classic mapper resource rejections for the x64 complex/comparison inputs are
tracked independently by [Tiny #259](https://github.com/opaopa6969/tinyexpression/issues/259)
and [unlaxer-parser #680](https://github.com/opaopa6969/unlaxer-parser/issues/680);
neither resource limits nor their test expectations are relaxed here.
Five fraud formulas and the complex fixture must be parsed and evaluated successfully when
recording timing. A fast caught exception does not count as acceptance. Parent production
latency and memory requirements remain subject to the dedicated evidence and open issues.
