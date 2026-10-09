# P4 の共有空白・コメント定義

P4 の grammar default と `Formula`、`ImportDeclaration`、`VariableDeclaration` は、固定した `std/layout@1.0.0` の `layout.SPACES_AND_COMMENTS` を参照する。rule の明示指定は `@whitespace(layout.SPACES_AND_COMMENTS)` に置換した。従来の `@interleave(profile=javaStyle)` と同じ場所で同じ集合を使い、子 rule に独自の指定が追加された場合も rule の優先順位を保てるため、冗長に見える指定も残した。

`grammar/ubnf.json` は builtin の正確な版を指定する。`ubnf.lock.json` の identity は `std/layout` / `1.0.0` / SHA-256 `2701f6884ea6c712d28c76ec3d340c3b27fca4575846239bd0854933205f4b67`。この小さな標準 artifact 自体を grammar の `.ubnf-cache/packages` に同梱する。生成は lock と検証済み bytes のみを読み、ネットワーク・認証・個人設定を必要としない。上流 generator は Maven `3.3.0-SNAPSHOT` と `.github/unlaxer-source-pin` の exact commit で固定する。CI の専用 Maven repository へその commit を install して検証する。

標準定義は ASCII space と TAB..CR、CR/LF 手前までの `//`、最初の `*/` までの block comment。閉じていない block comment は読み飛ばさない。文字列と fenced Java code の token 内部へ自動 skip は入らない。AST の文字列正規化、raw CST、source-preserving model の既存の comment mask と位置契約を維持する。

## backend の範囲

| 経路 | 実 P4 | 名前付き空白と固定 package |
|---|---|---|
| unlaxer Java 生成 parser | 対応。旧 grammar と全 AST field/node span・cursor・CST 原文を比較 | 直接対応 |
| unlaxer Java 経由 Rust 生成 / native Rust 生成 | P4 の token/layout projection で同じ入力・独立期待値・生成物 bytes を比較。実 P4 全体には従来の portability 制限がある（native は `literalBoundary: none` を拒否） | 直接対応。局所 `none` も共通 fixture で比較 |
| TinyExpression 本番 ubnfc Java / Rust | 固定生成物を使う。共有実 P4 fixture と source positions、memo OFF/ON を検証 | **std/layout@1.0.0 の限定互換処理**。任意 package/named policy の直接対応ではない |

`scripts/prepare-ubnfc-layout.py` は manifest・lock graph・artifact hash・全定義・global 1箇所/rule 3箇所を確認し、この標準定義だけを旧 javaStyle/interleave へ戻す。混在設定、追加 package/comment、未知の定義、改ざんを変換前に拒否する。導出 grammar は旧 SHA-256 と一致し、Java/Rust ubnfc の grammar→IR→生成物も byte 一致する。再生成スクリプトの両経路から同じ互換処理を呼び、文法・manifest・lock・artifact・互換処理の hash を両 pin に含めた。汎用 ubnfc 対応は [#249](https://github.com/opaopa6969/tinyexpression/issues/249) で追跡する。

## 検証と定義表示

`actual-p4.json` は実 P4 の空白/TAB/LF/CRLF/コメント/未閉鎖/文字列/Java fence/import/declaration の14入力。Java の旧/新 parser と Tiny 本番 Java/Rust が同じ cursor 期待値を使う。Java の旧/新 AST 全 field/node span と retained CST 原文、owned source の位置を比較する。`corpus.json` は実 P4 の lexical modules を用いる29入力で、旧/新 layout と局所 `none` を Java・Java経由Rust・native Rust へ入力し、位置・診断・AST の独立期待値を比較する。

```sh
python3 scripts/test_ubnfc_layout.py
MAVEN_REPO=/tmp/tiny-layout-m2 scripts/ci/install-unlaxer-if-unpublished.sh
mvn test -Dmaven.repo.local=/tmp/tiny-layout-m2 -Dtinyexpression.skipRailroad=true \
  -Dtinyexpression.layout.conformance=true -Dtinyexpression.unlaxer.source=/path/to/pinned/unlaxer-parser \
  -Dtest=P4LayoutMigrationTest,P4LayoutGeneratedConformanceTest
cargo test --locked --manifest-path rust/Cargo.toml -p tinyexpression-rs shared_imported_layout
```

文法作者は UBNF authoring extension の `@whitespace` 補完から `layout.SPACES_AND_COMMENTS` を選び、hover で定義と ID/version/hash、定義への移動で読み取り専用の標準 module を確認できる。CLI `deps inspect --grammar tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf` も同じ定義・出典・code-point 位置を返す。`P4LayoutMigrationTest` は実 grammar のこの snapshot と4 policy scopeを確認する。TinyExpression の runtime LSP/DAP と文法作者向け UBNF LSP は別の役割を持つ。
