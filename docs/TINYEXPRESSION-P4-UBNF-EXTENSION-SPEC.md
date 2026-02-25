# TinyExpression P4 UBNF Extension Design Merge Spec

## Purpose

P4 の目的である以下 3 点を実施した記録と合意用仕様。

1. TinyExpression 要求を BNF-level と annotation-level に分解
2. tinyexpression-codex 共同作業の共有仕様を定義
3. `interleave` / `backreference` / `scopeTree` を実ケースで検証

## Scope

- This spec is design+validation for Stage 2 merge preparation.
- No change is made to `unlaxer-common` / `unlaxer-dsl` source in this step.

---

## 1. Responsibility Split (BNF vs Annotation)

### 1.1 Rule

- Parse-time truth -> BNF-level
- Post-parse meaning/tooling policy -> annotation-level

### 1.2 TinyExpression mapping table

| Concern | TinyExpression current implementation | Stage-2 target layer |
|---|---|---|
| Java-style whitespace/comment tolerant parsing | `JavaStyleDelimitedLazyChain` family | BNF-level (`@whitespace`, `@comment`, `@interleave`) |
| Variable/method name reference consistency | `VariableDeclarationMatchedTokenParser` + method lookup in `OperatorOperandTreeCreator.extracteMethodInvocation` | BNF-level intent + annotation metadata (`@backref`) |
| Scope-aware meaning (declaration/use/resolve) | `VariableDeclarationParser` transaction store + `VariableTypeResolver` | Annotation-level contract (`@scopeTree`) + semantic phase |
| Operator precedence/associativity | parser chain + `OperatorOperandTreeCreator` rebuild | BNF-level + assoc/precedence annotation contract |
| AST model mapping | manual token extraction + annotation bridge (`TinyAst*`) | annotation-level (`@mapping`) + generated mapper |
| Diagnostics policy | explicit `IllegalArgumentException` messages + roadmap tests | annotation-level diagnostics policy + validator |

---

## 2. Shared Collaboration Spec (tinyexpression-codex)

### 2.1 Deliverables for each incremental slice

For each migrated slice (number/boolean/string/object/method/variable):

1. UBNF grammar fragment
2. parser-ir export artifact (or command trace)
3. TinyExpression runtime parity tests (success + failure)
4. Known gap list (behavior intentionally different)

### 2.2 Compatibility contract

- Existing TinyExpression runtime behavior is baseline truth during migration.
- If generated pipeline differs, record explicitly in “Known Gaps”.
- Do not silently change behavior in parser/codegen without roadmap test update.

### 2.3 Gate checks

- `unlaxer-dsl` `--validate-only` must pass for draft grammar.
- TinyExpression roadmap tests for semantic snapshots must pass.

---

## 3. UBNF Draft Artifact

Draft file:

- `docs/ubnf/tinyexpression-p4-draft.ubnf`
- assoc/precedence repro file:
  - `docs/ubnf/tinyexpression-p4-assoc-repro.ubnf`

Notes:

- Includes `@interleave(profile=javaStyle)`, `@backref(name=methodName)`, `@scopeTree(mode=lexical)`
- Intentionally keeps precedence/assoc minimal for this P4 checkpoint

Validation command and result:

```bash
cd /mnt/c/var/unlaxer-temp/unlaxer-dsl
mvn -q -DskipTests compile
mvn -q -DskipTests exec:java -Dexec.mainClass=org.unlaxer.dsl.CodegenMain \
  -Dexec.args="--grammar /mnt/c/var/unlaxer-temp/tinyexpression/docs/ubnf/tinyexpression-p4-draft.ubnf --validate-only --report-format json"
```

Result snapshot:

- `ok=true`
- `grammarCount=1`
- `warningsCount=0`

Assoc/precedence repro validation:

- group-choice 形式 (`{ ( '+' @op | '-' @op ) Term @right }`) は validator 不一致を再現
- operator rule reference 形式 (`{ AddOp @op Term @right }`) へ変更すると validate pass
- dependency note reference:
  - `docs/TINYEXPRESSION-DEPENDENCY-EXTENSION-NOTES.md`

---

## 4. Real-Case Semantic Validation (TinyExpression)

Roadmap test file:

- `src/test/java/org/unlaxer/tinyexpression/roadmap/UbnfExtensionRoadmapTest.java`

### 4.1 interleave semantics snapshot

Case:

- variable declaration + trailing comment + newline-separated expression

Expectation:

- parse/execute succeeds with Java-style delimiter behavior

### 4.2 backreference semantics snapshot

Case:

- `call missing()` with no method declaration

Expectation:

- parse phase fails with message containing `missing is not declared`

### 4.3 scope tree semantics snapshot

Case:

- global number variable `$amount`
- method parameter `$amount`
- method body returns `$amount`

Result:

- method parameter shadowing is respected on this path (returns argument value `1.0f`)

---

## 5. Known Gaps after this P4 step

1. Full TinyExpression grammar is not yet fully represented in UBNF.

---

## 6. Next P4 Implementation Steps

1. Extend lexical-scope conformance tests across string/object method paths as well.
2. Expand UBNF draft from core subset to full TinyExpression syntax (match/if/setter/method/annotation).
