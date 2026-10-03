# P4 の宣言的 lexical grammar

P4 の lexical token は UBNF v2 の `token NAME ::= expression ;` から生成する。
NUMBER / IDENTIFIER / STRING / code fence / EOF を旧 Java Parser 名へ mapping しない。
既定 Java engine は空の scanner registry、Rust engine は `RejectExtern` で動く。
残してある旧 scanner は互換テスト用で、新しい P4 の認識には使用しない。

## 文法の分割

入口は `tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf`。
その隣の `lexical/` に次の grammar を置く。

| module | 定義 |
|---|---|
| `characters.ubnf` | ASCII digit / letter / underscore、CRLF / CR / LF |
| `numbers.ubnf` | 符号、整数・小数、省略可能な完全な指数部 |
| `identifiers.ubnf` | identifier と dotted class name |
| `strings.ubnf` | single/double quote、raw escape pair、改行・非 BMP 文字 |
| `fences.ubnf` | 旧 triple fence と可変長の complete fenced block |

```ubnf
@import numbers from 'lexical/numbers.ubnf'
token NUMBER ::= numbers.NUMBER;
```

alias が namespace になる。import path は宣言元の file 基準。
module の内部参照・capture は宣言元で閉じ、caller の同名定義を見ない。
独立した namespace 宣言や implicit import はない。相互再帰する expression 規則は
P4 本体に残し、独立した lexical grammar から分割する。

token 式の中には暗黙の空白を挿入しない。rule 間の javaStyle trivia は従来どおり。
ubnfc の v2 keyword boundary への切替を lexical 移行に混ぜないため、本体は
`@literalBoundary: none` を明記して従来の literal matching を維持する。

## raw 認識と値変換

文字列の token は引用符も escape も入力どおり保持する。Java の
`generate-ubnfc-converter.py` と Rust の `generate-compat.py` が作る値変換で、
公開 AST の従来どおり single quote だけを除く。double quote と escape は残し、
最終的な解釈は既存 evaluator が行う。span は元入力の code-point 半開区間のまま。

4 個以上の fence は ``CAPTURE(fence, '`'{4,})`` と `SAME_AS(fence)` で同じ幅を
要求する。閉じは独立した行であり、短い/長い fence は本文になる。
CRLF は 1 組として消費する。3 個の fence は互換のため最初の triple-backtick を
terminator 候補にする従来仕様を保つ。

新しい token は原子的に失敗し、失敗時の consumed / matched を動かさない。
診断の公開 expected token 名は維持するが、内部 `farthestExpected` から旧実装の
`DigitParser` / `SignParser` 等が消える。この実装詳細の変更を、共通 response 記録に
実行結果から反映する。失敗位置や評価結果の変更として取り扱わない。

## pin と再生成

Java の `UBNFC_PIN` と Rust の `ubnfc-pin.txt` は compiler commit / IR / 生成物だけで
なく全 lexical module の hash を記録する。module の変更だけでも drift 検査が落ちる。
Rust の再生成も `git archive` で pin commit を使い、共有 checkout の HEAD や未コミット
変更を使わない。`UBNFC_REV=<commit> ... --write` で pin を移動できる。

```sh
UBNFC_DIR=/path/to/ubnfc scripts/regenerate-ubnfc-parser.sh --write
UBNFC_DIR=/path/to/ubnfc rust/scripts/regenerate-ubnfc.sh --write
mvn compile
python3 scripts/generate-ubnfc-converter.py --write
python3 rust/scripts/generate-compat.py
UBNFC_DIR=/path/to/ubnfc scripts/regenerate-ubnfc-parser.sh --check --require-full
UBNFC_DIR=/path/to/ubnfc rust/check-generated.sh
```

Java compiler dependency は `3.2.0-SNAPSHOT` と lexical module 対応の
`.github/unlaxer-source-pin` が最低条件。公開済み `3.1.1` は新しい文法に未対応。
未公開の間、CI の declared-dependency leg もこの pin から build する。
旧公開版への fallback や同じ release version の上書きは行わず、公開版との検証と偽らない。
ローカルで新規 build する場合も、pin に記載した unlaxer-parser commit の
`mvn -pl .,unlaxer-common,unlaxer-dsl install -DskipTests -Dgpg.skip=true` を先に実行する。

Java/Rust 共通の 19 ケースは `src/test/resources/p4-lexical-conformance.json`。
受理/拒否・独立した consumed/matched・raw quote・公開 AST 値・span を比較する。
既存の engine parity / 数値・文字列・fence / 差分評価 corpus も継続する。
playground は同じ Rust engine を WASM 化し、check / parity / roundtrip / build で確認する。
