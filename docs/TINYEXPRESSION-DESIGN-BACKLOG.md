# TinyExpression Design Backlog

Last updated: 2026-02-26

## 1. Parser Delimiter Coupling

Status: planned

Context:
- `if (...)` / `if/*comment*/(...)` / `match { ... }` head判定は、runtime側でdelimiter知識を持つとdriftしやすい。
- `TinyExpressionParserCapabilities` で共通化は入ったが、`XXXDelimitedChain` の実装変更と完全連動ではない。

Target:
1. `JavaStyleDelimitedLazyChain` 系のdelimiter仕様を parser-owned contract として公開する。
2. evaluator/runtime 側は contract API のみ参照する。
3. delimiter仕様の変更時に unit test が先に落ちる構造にする。

## 2. Parser Keyword Coupling

Status: planned

Context:
- `var` / `variable` / 将来の別名（例: ローカライズ）を runtime 側が文字列で前提化すると追従コストが高い。
- `TinyExpressionKeywords` 導入済みだが、head判定APIはまだ parser capability として十分高レベルではない。

Target:
1. parser側に「宣言ヘッド/式ヘッド」を問い合わせる高レベル capability API を追加する。
2. evaluator 側の ad-hoc textual probe を順次置き換える。
3. alias 追加時に evaluator 側修正ゼロを目指す。

## 3. Evaluator Type Safety

Status: planned

Context:
- generated AST evaluation と embedded bridge の切替時に `Object` ベースの分岐が多く、型境界が緩い。

Target:
1. evaluator内部の結果表現を typed wrapper に寄せる（number/string/boolean/object + runtime mode）。
2. bridge混在時でも result-type contract を compile-time で追える設計へ寄せる。
3. DAP probe も typed metadata 由来へ統一する。

## 4. Error Message Design

Status: planned

Context:
- 現在の parse/eval エラーは文脈情報が不足し、運用時の原因追跡が重い。
- 単純な `Choice(rightExpression, errorMessage)` だけでは情報量が足りない可能性がある。

Target:
1. parser段階で期待トークン/位置/候補 rule を構造化して保持する。
2. evaluator段階で runtime mode（generated/token/bridge）を添えて報告する。
3. LSP diagnostics と DAP variables の双方で同じ error envelope を使う。

## 5. Priority (next design sessions)

1. parser delimiter contract formalization
2. keyword/head capability API formalization
3. error envelope schema for LSP/DAP
4. evaluator typed-result internal model
