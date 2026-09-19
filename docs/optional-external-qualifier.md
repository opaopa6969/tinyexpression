# P4外部呼び出しの省略可能なクラス名（#109）

unlaxer-parser#177のcapture cardinality修正により、クラス名のない選択肢を持つ
`ExternalBooleanInvocationExpr` / `ExternalNumberInvocationExpr` /
`ExternalStringInvocationExpr` / `ExternalObjectInvocationExpr` の`className`は
`Optional<QualifiedNameExpr>`として生成される。
従来のnullable `QualifiedNameExpr`生成APIも、consumer側の型付きoverloadで引き続き扱う。

省略時は既存のimport解決、指定時は既存のクラス名解決へ渡すだけで、実行意味は変えない。
未importの呼び出しは従来どおり明示失敗し、手書きparser/evaluatorへのfallbackは追加しない。
parser・AST・mapper・evaluatorを一緒に再生成する必要がある。

CIのpublished/source generator行列は独立した空Maven repositoryを使い、
`P4OptionalExternalQualifierTest`で実際のfield型とgenerated backendの挙動を確認する。
source pinはcapture cardinality修正revisionで固定する。
これはJava外部呼び出しのAPI移行であり、RustからJava reflectionを実行できるという意味ではない。

同じcardinality修正により、`SliceExpr.start/end/step`も開発版では
`Optional<Object>`になる。`P4SourceText`はOptionalコンテナではなくpresent値のidentityを
source-mapで引き、emptyは従来の欠落値と同じ`null`として扱う。これにより公開版の
nullable/String・Node形状と開発版のOptional形状で、sliceの字句解釈を共通に保つ。
