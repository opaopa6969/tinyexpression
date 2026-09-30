# CodeBlock のソース保持と AOT 準備契約

tinyexpression #228。Rust full-spec のうち、信頼された `rustcodeblock` を明示許可して
ネイティブバイナリに組み込むための準備段階である。**この段階では Rust の本文を
コンパイル・実行できない。** パーサが本文を失わず取り出せることと、許可・対象言語を
副作用なしで検査できることを Java / Rust の両方で固定する。

> **Warning**: Java code blocks compile and execute arbitrary code on the JVM. Only use this feature when formula authors are fully trusted. Do not expose this capability to untrusted users.

既存 Java 実行の信頼境界は [ADR-003](decisions/ADR-003-java-codeblock-safety.md) のまま。
将来の Rust ビルド・実行にも同様の信頼が必要であり、AOT 化は sandbox 化ではない。

## ソースを失わない API

Java は `org.unlaxer.tinyexpression.codeblock.CodeBlockSource`、Rust は
`tinyexpression_rs::code_blocks` を公開する。どちらも既存の生成 P4 parser が成功した
occurrence を使い、文字列やコメントの中にある fence をコードブロックとして拾わない。
生のソースに正規表現を適用する方式ではない。

~~~text
```rust:sample.v1.Demo
pub fn answer() -> f32 { 42.0 }
```
1
~~~

```java
var blocks = CodeBlockSource.parse(source);
var diagnostics = CodeBlockSource.preflight(blocks, CodeBlockSource.Target.RUST, false);
// CB004: 明示許可がない。コンパイラは呼ばれない。
```

```rust
let blocks = tinyexpression_rs::code_blocks::parse(source)?;
let diagnostics = tinyexpression_rs::code_blocks::preflight(
    &blocks, tinyexpression_rs::code_blocks::Target::Rust, false,
);
// 同じ CB004 と name_span。コンパイラは呼ばれない。
```

返す順序はソース順。各 block は次を持つ。

| Java / Rust | 内容 |
|---|---|
| `scheme` | 元の表記の `java` / `rust` など。未知 scheme も構文としては受理する |
| `identifier` | 元の binding label。ファイル名でも、生成コードへそのまま挿入してよい識別子でもない |
| `body` | opening fence の改行の直後から closing fence の直前まで。trim・改行正規化しない |
| `span` | opening fence の先頭から closing fence とその直後の改行まで |
| `bodySpan` / `body_span` | `body` の元ソース上の範囲 |
| `nameSpan` / `name_span` | header の `identifier` 部分の元ソース上の範囲 |

範囲はすべて Unicode code point 単位の半開区間 `[start, end)`。
Java の UTF-16 offset でも Rust の UTF-8 byte offset でもない。LF / CRLF / CR と
非 BMP 文字を含め、同じソースから同じ本文・範囲を返す。
コンパイラ診断を戻す段階では、byte / UTF-16 と code point の変換が別途必要になる。

`parse` は入力全文の構文を検査し、不正なら失敗する。公開 facade と同じ
Formula → BooleanExpression → StringExpression → ObjectExpression の受理順であり、
部分的に受理しただけのブロックを返さない。後三者には CodeBlock 規則はないが、
文字列やコメントに fence を含む裸の比較式などを正しく扱うために必要である。

## 副作用のない preflight

`preflight` は block 列、target、ホストが決めた `allowCode` / `allow_code` だけを受け取る。
環境変数、ソース内の指定、Java のグローバル実行許可から Rust の許可を推測しない。
コンパイラ起動、class loading、ファイル作成、依存取得は行わない。

| Code | 条件 | 位置 |
|---|---|---|
| `CB001` | `java` / `rust` 以外の scheme | name span |
| `CB002` | 同一 identifier が先のブロックにある | 後の name span |
| `CB003` | scheme と build target が異なる | name span |
| `CB004` | ホストから明示的な許可がない | name span |
| `CB005` | 未コンパイルの Rust ブロックを通常の source-aware evaluator に渡した | 下記参照 |

preflight は `CB001` → `CB002` → `CB003` → `CB004` の優先順位で、各 block 最大一件を
ソース順に返す。scheme は大小文字を区別せず、identifier は区別する。異なる scheme 間でも
同名は重複し、未知 scheme の identifier も後続の重複判定に含める。
空の block 列は target / 許可にかかわらず成功する。

`allow_code = true` で診断が空でも、本文の Rust 構文・型・関数の存在は未検査である。
Rust として不正な本文もパーサはそのまま保存する。これはビルド済みの証拠でも、通常の
evaluator へ渡す許可証でもない。

## 通常評価での扱いと互換性

- `parse` は実行しない。コードブロックを持つソースの AST も生成できる。
- Rust の `Program::new` と source-aware `evaluate` は Rust ブロックを `CB005` で拒否する。
  CLI / JSON API の通常評価もこの経路を使う。Java ブロックの既存 ExternalHost / stub 契約は
  変えない。構築できた `Program` からは `code_blocks()` で本文・位置を参照できる。
- Java の source を受け取る calculator バックエンドも `CB005` で拒否する。
  AST 系は `UnsupportedOperationException`、JavaCode 系は従来のエラーラップ契約に従い
  `CompileError` の cause に同例外を持つ。後者は code のみ、前者と Rust は name span を
  メッセージにも含める。preflight の code / span は両言語で一致する。
- `EvalContextService` は Java 実行が許可されていても、Rust が混在すれば Java ブロックの
  コンパイル前に拒否する。クラス名でまとめた Map ではなく元ソースを検査するため、同名の
  Java ブロックで Rust の存在を隠せない。既存の応答 `codeBlocks.executed` / audit のフラグは
  ポリシーによる実行可否であり、実際のコンパイル・実行の完了証明には使わない。
- 既存 typed AST の `CodeBlockExpr` は本文を持たず、Formula の mapping も block を保持しない。
  公開 AST 形を破壊せず独立した projection API として追加した。**元ソースを渡さない
  `evaluate_ast` 等は、捨てられた block を検査できない**。AOT ではこの API が返す本文と位置を
  ソースと一緒に保持する必要がある。
- 既存の Java codeblock の opt-in と Rust の Java stub 挙動は変更しない。未コンパイル Rust を
  通常評価で黙って無視していた点だけは、明示的な拒否へ変わる。

source-aware 評価は fence がある場合に metadata 用の parse を追加で行う。
今回は生成 parser や AST の形を変更していない。将来、同じ parse 結果から AST と metadata を
一度に作る場合も、受理・位置・診断の共通契約を維持すること。

## 構文上の限界

既存 UBNF の `CODE_BODY` は `UNTIL("```")` であり、Rust lexer ではない。
Rust の文字列やコメントの内部でも三連 backtick は本文の終端と衝突する。
不正な終端位置なら式全体を拒否する。任意の Rust ソースを完全に埋め込めるとは主張しない。
header は既存の英数字・underscore の識別子 / dotted name 規則に従う。
`../escape` のような label は拒否するが、それを生成先 path の安全性保証の代用にしてはいけない。

## 検証

Java `CodeBlockSourceTest` と Rust `tests/code_blocks.rs` は次の同一 oracle を読む。

- `src/test/resources/code-block-source.tsv`: 元本文・全 span、LF / CRLF / CR、Unicode、
  空本文、qualified name、文字列 / コメント内の偽 fence、未閉鎖、不正 label、終端衝突。
- `src/test/resources/code-block-preflight.tsv`: 許可、target、未知 scheme、重複、複数診断の順序。

加えて Java は全 source calculator バックエンドと `EvalContextService` の混在拒否を検査する。
Rust は不正な Rust 本文で parse / preflight の成功、通常評価の拒否を検査し、native CLI の
`parse` / `eval` を空の `PATH` で動かしてコンパイラ探索が不要であることを確認する。

```sh
./mvnw test -Dtinyexpression.skipRailroad=true \
  -Dtest=CodeBlockSourceTest,EvalContextServiceTest,JavaCodeBlockPolicyTest
cargo test --locked --manifest-path rust/Cargo.toml -p tinyexpression-rs --test code_blocks
```

## full-spec までの後続

この段階だけで Rust full-spec や rustcodeblock の実行対応を完了扱いにしない。
後続の受け入れ条件は [tinyexpression #229](https://github.com/opaopa6969/tinyexpression/issues/229) に固定する。
次に実装・検証するのは、既存 `ExternalHost` に結ぶ型付き Rust binding、明示許可付きの
AOT 生成・build、生成物と toolchain / dependency の固定、元ソースへのコンパイラ診断写像、
生成バイナリの実行である。build と execute は別の操作とし、parse / IDE は今後も実行しない。
number / string / boolean、変数 context、外部呼び出しの失敗条件を Java と共通入力で照合する。
