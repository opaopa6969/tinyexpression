# TinyExpression MCP 化ステータス（Phase 2）

> 更新日: 2026-08-22 | namespace: `tinyexpr` | port: 9237

## 完了項目

- [x] Phase 1 調査結果確認（docs/mcp/survey.json / SURVEY.md）
- [x] 割当表確認（#40, namespace=tinyexpr, port=9237）
- [x] DESIGN.md 作成
- [x] MCP サーバ実装（`src/main/java/org/unlaxer/tinyexpression/mcp/McpServer.java` + `McpToolDefs.java`）
  - Streamable HTTP `/mcp` + `/healthz`
  - 5 tools: evaluate / validate / execute_batch / parity_check / list_backends
  - 4 resources: tinyexpr://spec / guide / language / backends
  - 全 tool に annotations（readOnlyHint=true）
  - デフォルト AST_EVALUATOR（安全・コード生成なし）
  - JAVA_CODE 系は TINYEXPR_ALLOW_JAVA_CODE=true の時のみ
- [x] e2e テスト（15 tests, all passing）
- [x] volta.service.json / deploy/tinyexpression-mcp.service / run.sh
- [x] skill（docs/skills/write-formula-info/SKILL.md, docs/skills/choose-backend/SKILL.md）
- [x] git commit & push（chore/track-agent-tooling ブランチ）
- [x] prod (192.168.1.50) に git clone & compile & systemctl start
- [x] volta 登録（svc_add confirm:true → services.json に書き込み完了）
- [x] gateway ルート追加（gateway_routes_apply confirm:true → tinyexpr.unlaxer.org 新規 1 件）
- [x] https://tinyexpr.unlaxer.org/healthz → 200 確認
- [x] catalog__backend_status → tinyexpr namespace ready, tools=5
- [x] catalog__audit_backend → 7 ok / 0 ng / 4 skip / 1 unknown

## 協調

issue-hub への登録は不要（相手側に MCP 入口が既にあるため。municipality-stats / building-starts / establishment-census は既に volta に参加済み）。

## 未決事項

- なし（Phase 1 の open_questions は設計で解決済み）
  - Q1 external Java メソッド: 無効（セキュリティ）
  - Q2 Java コードブロック: 無効（セキュリティ）
  - Q3 namespace: tinyexpr（割当表指定）
  - Q4 execute_batch 形式: JSON 配列を優先
  - Q5 実装形態: 独立した MCP サーバ（メインプロジェクト内の mcp パッケージ）

## dry-run 結果記録

- svc_add dry-run: 既存 library エントリを上書き（type=library → type=java, mcp 追加）
- gateway_routes_diff: [新規] tinyexpr.unlaxer.org -> http://192.168.1.50:9237（1 件のみ、他は温存）
