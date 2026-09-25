use super::ast::tree::AstTree;
use super::ast::Ast;
pub use super::rt::cursor::State;
pub use super::rt::scope::{Decl, Diagnostic as ScopeDiagnostic, Reference, Severity};
pub type Span = [usize; 2];
#[derive(Clone, Copy, Debug)]
pub struct ParseOptions {
    /// Build the owned typed AST (`ParseResult::ast`, `node_spans`).
    pub build_ast: bool,
    pub require_eof: bool,
    pub lexical: bool,
    /// Maximum active rule calls; native stack budget may stop earlier.
    pub max_depth: usize,
    pub memo: bool,
    /// Predictive exclusion of ordered-choice alternatives that provably fail at
    /// their first token (design v1 §3.4). false is the reference path that tries
    /// every alternative; both produce the same result and diagnostics.
    pub predict: bool,
    /// Left factoring of ordered-choice alternatives that share a common prefix
    /// (design v1 §3.4): the shared prefix is evaluated once and the continuations
    /// are tried in declaration order. false is the reference path that evaluates
    /// every alternative from its start; both produce the same result and diagnostics.
    pub factor: bool,
    /// Bulk scan of terminal-only regions (design v1 §3.4, D-072): a region the front
    /// proved to be built from terminals alone and to leave no observation behind
    /// (`plan.scans`) is consumed by one generated scanning function instead of walking
    /// the grammar expression by expression. It runs only in fast mode (`diagnostics:
    /// false`) and only when the occurrence tables are not requested, so the recorded
    /// diagnostics and occurrences are those of the reference path by construction;
    /// `false` is the reference path. Both produce the same result and diagnostics.
    pub scan: bool,
    /// Reuse the session's large buffers (memo table, diagnostics arena, event arena)
    /// across parses on the same thread instead of allocating them per parse. The
    /// retained set is bounded (`POOL_RETAIN_BYTES`) and only affects allocation
    /// behaviour, never results.
    pub reuse_buffers: bool,
    /// Per-position lexical cache: a combined DFA over the grammar's literal terminals is
    /// run once per input position and its result (the set of literals that match there)
    /// is kept for that position, together with the trivia policies' `next_non_trivia`
    /// table. Every terminal is a pure predicate on (input, position), so the cache cannot
    /// change any parse or diagnostic; false is the reference path that scans characters at
    /// every attempt. Both produce the same result and diagnostics.
    pub lexical_cache: bool,
    /// Record diagnostics during this parse (design v1 §3.9, D-014: the main
    /// diagnostic DAG and the public farthest observation).
    ///
    /// `false` (the default) is *fast mode*: nothing is recorded, so a fully
    /// successful parse pays nothing for observations it does not return. The
    /// entry points re-run the same input with `diagnostics: true` whenever the
    /// result needs them (failure, trailing / partial input, a recovery, a scope
    /// diagnostic, a mapping or resource diagnostic). Parsing is deterministic and
    /// free of observable side effects, so the re-run yields the same result.
    ///
    /// Set it to `true` when the speculative failures of a *successful* parse are
    /// wanted (`ParseResult::hints`); they are the only observation fast mode
    /// drops. The generated driver sets it for that reason.
    ///
    /// `limits.diagnostics` bounds the recording, so in fast mode it is never
    /// consumed and never fails the parse.
    pub diagnostics: bool,
    /// Retain the occurrence tables (`ParseResult::captures`, `lexical`, `tokens`;
    /// design v1 §3.6 calls them optional outputs) (D-035).
    ///
    /// `false` (the default) does not build the tables, so a consumer that only wants
    /// the AST does not pay for rows it never reads. Building an AST still walks the
    /// event arena once (it needs each rule's immediate captures), but that walk is
    /// independent of these tables and nothing else in the result changes. With
    /// neither an AST nor a request the walk is skipped entirely. `lexical: true`
    /// implies this flag, because the rule / token occurrence tables are the entire
    /// point of lexical mode.
    ///
    /// Set it to `true` for occurrence-consuming frontends (LSP-style projection,
    /// the self-host bootstrap, the harness driver).
    pub occurrences: bool,
    /// Retain `ParseResult::value_spans` (the source span of every mapped AST value,
    /// addressed by canonical AST JSON pointer) (D-035).
    ///
    /// `false` (the default) skips the third walk over the built AST. The AST,
    /// `node_spans` and every diagnostic are unaffected; only `value_spans` is empty.
    /// Requires `build_ast` or `ast_tree` — without an AST there is nothing to address.
    pub value_spans: bool,
    /// Return the AST in arena form, `ParseResult::tree` (D-077).
    ///
    /// The mapping always builds this arena (`Vec` of 24-byte nodes plus `u32` child
    /// slots; text values are byte ranges of the input, never copied). `build_ast`
    /// projects the owned `Ast` from it; `ast_tree` hands the arena itself over,
    /// together with one copy of the input so that text values can be borrowed from it.
    /// With `build_ast: false, ast_tree: true` nothing per node is allocated: the owned
    /// `Ast` and `node_spans` are not built (`AstTree::node_spans` / `AstTree::to_ast`
    /// derive them on demand). Both may be set; the owned AST is then the projection of
    /// the returned tree. `false` (the default) keeps the result as before.
    pub ast_tree: bool,
    pub limits: ResourceLimits,
}
/// `ResourceLimits::default()` の `arena_entries`（issue #35 / D-071）。`Session::new` は
/// `arena_entries` がこの値と**一致するとき**だけ入力長に応じて実効上限を引き上げる
/// （下の `scaled_default_arena_entries`）。既定と異なる値（`1` や `1 << 30` など）を
/// 明示した呼出しは常にその値をそのまま使う — 小さい budget をわざと設定して
/// `arena budget exceeded` を確実に踏ませるテスト（`resource_limits_fail_without_partial_success`）
/// はこの分岐に入らない。既定と全く同じ値をあえて明示した場合は既定と区別できないので
/// スケーリングされる（`arena_entries` を固定の 2,000,000 に留めたいだけの目的なら
/// 別の値、例えば `2_000_001` を使うこと）。
pub const DEFAULT_ARENA_ENTRIES: usize = 2_000_000;
/// Per-session hard limits, checked before indexing input or retaining data.
#[derive(Clone, Copy, Debug)]
pub struct ResourceLimits {
    pub input_bytes: usize,
    pub arena_entries: usize,
    pub diagnostics: usize,
    pub memo_entries: usize,
    pub effect_entries: usize,
    pub effect_bytes: usize,
    pub mapping_depth: usize,
}
impl Default for ResourceLimits {
    fn default() -> Self {
        Self {
            input_bytes: 16 * 1024 * 1024,
            arena_entries: DEFAULT_ARENA_ENTRIES,
            diagnostics: 2_000_000,
            memo_entries: 1_000_000,
            effect_entries: 1_000_000,
            effect_bytes: 64 * 1024 * 1024,
            mapping_depth: 512,
        }
    }
}
impl ParseOptions {
    /// AST をどちらかの形で作るか（event の記録と AST 構築の要否）。
    pub fn wants_ast(&self) -> bool {
        self.build_ast || self.ast_tree
    }
}
impl Default for ParseOptions {
    fn default() -> Self {
        Self {
            build_ast: true,
            require_eof: true,
            lexical: false,
            max_depth: 10_000,
            memo: true,
            predict: true,
            factor: true,
            scan: true,
            reuse_buffers: true,
            lexical_cache: false,
            diagnostics: false,
            occurrences: false,
            value_spans: false,
            ast_tree: false,
            limits: ResourceLimits::default(),
        }
    }
}
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Capture {
    pub rule_completion_order: usize,
    pub occurrence_id: usize,
    pub site_id: &'static str,
    pub name: &'static str,
    pub span: Span,
    pub completion_order: usize,
}
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct LexicalOccurrence {
    pub occurrence_id: usize,
    pub parent_occurrence_id: Option<usize>,
    pub rule_id: &'static str,
    pub expr_id: &'static str,
    pub span: Span,
    pub completion_order: usize,
}
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct NodeSpan {
    pub node_id: usize,
    pub rule_id: &'static str,
    pub node_type: &'static str,
    pub span: Span,
}
/// Retained mapped value span, addressed by a canonical AST JSON pointer.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ValueSpan {
    pub path: String,
    pub span: Span,
    pub text: Option<String>,
}
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum DiagnosticKind {
    Syntax,
    TrailingInput,
    Mapping,
    Resource,
    Recovery,
}
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Diagnostic {
    pub kind: DiagnosticKind,
    pub offset_cp: usize,
    pub farthest_cp: usize,
    pub expected: Vec<&'static str>,
    pub farthest_expected: Vec<&'static str>,
    pub deepest_rule: Option<&'static str>,
    pub rule_path: Vec<&'static str>,
    pub message: Option<String>,
    pub recovery_id: Option<usize>,
    pub length_cp: usize,
}
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct Statistics {
    pub rule_evaluations: usize,
    pub memo_hits: usize,
    pub memo_entries: usize,
    pub recipes: usize,
    pub ast_nodes: usize,
    pub memo_max_probe: usize,
    /// Number of combined-DFA runs (lexical cache misses; one per newly touched position).
    pub lexical_runs: u64,
    /// Number of terminal matches answered from the lexical cache (hits + misses).
    pub lexical_probes: u64,
    /// `next_non_trivia` table hits / misses (diagnostic-free trivia skipping).
    pub trivia_skip_hits: u64,
    pub trivia_skip_misses: u64,
}
#[derive(Clone, Debug, PartialEq)]
pub struct ParseResult {
    pub ok: bool,
    pub consumed_cp: usize,
    pub matched_cp: usize,
    pub ast: Option<Ast>,
    /// The AST in arena form (`ParseOptions::ast_tree`); `Some` exactly when an AST was
    /// built and the option was set.
    pub tree: Option<AstTree>,
    pub captures: Vec<Capture>,
    /// Rule occurrences, retained for frontend projection compatibility.
    pub lexical: Vec<LexicalOccurrence>,
    /// Token occurrences; parents address the rule table, IDs follow rule IDs.
    pub tokens: Vec<LexicalOccurrence>,
    pub node_spans: Vec<NodeSpan>,
    pub value_spans: Vec<ValueSpan>,
    pub diagnostics: Vec<Diagnostic>,
    /// Local farthest failures, including speculative failures on successful parses.
    pub hints: Vec<Diagnostic>,
    pub recoveries: Vec<Recovery>,
    pub declarations: Vec<Decl>,
    pub references: Vec<Reference>,
    pub scope_diagnostics: Vec<ScopeDiagnostic>,
    pub statistics: Statistics,
    pub scope: ScopeResult,
}
#[derive(Clone, Debug)]
pub enum ScanEffect {
    Diagnostic {
        message: String,
        offset_cp: usize,
        len_cp: usize,
        severity: Severity,
    },
    Enter,
    Leave,
    Declare {
        name: String,
        offset_cp: usize,
    },
    Use {
        name: String,
        offset_cp: usize,
        len_cp: usize,
    },
    Clear,
}
#[derive(Clone, Debug)]
pub struct ScanDiagnostic {
    pub offset: usize,
    pub expected: &'static str,
}
/// offsets と value_span は byte。scanner は UTF-8 境界の単調な位置を返す。
#[derive(Clone, Debug)]
pub struct ScanResult {
    pub ok: bool,
    pub consumed_end: usize,
    pub matched_end: usize,
    pub value_span: Span,
    pub diagnostics: Vec<ScanDiagnostic>,
    pub effects: Vec<ScanEffect>,
}
pub trait TokenScanner {
    fn scan(
        &mut self,
        logical_id: &str,
        input: &str,
        state: State,
        match_only: bool,
        scope: &super::rt::scope::ScopeStore,
    ) -> ScanResult;
}
pub struct RejectExtern;
impl TokenScanner for RejectExtern {
    fn scan(
        &mut self,
        _: &str,
        _: &str,
        state: State,
        _: bool,
        _: &super::rt::scope::ScopeStore,
    ) -> ScanResult {
        ScanResult {
            ok: false,
            consumed_end: state.consumed,
            matched_end: state.matched,
            value_span: [state.consumed, state.consumed],
            diagnostics: vec![],
            effects: vec![],
        }
    }
}

/// Events retained by the selected parse, in execution order. Offsets are code points.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ScopeEvent {
    pub order: usize,
    pub action: &'static str,
    pub mode: &'static str,
    pub name: Option<String>,
    pub offset_cp: usize,
    pub length_cp: usize,
}
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ScopeResult {
    pub depth: usize,
    /// Grammar modes in declaration order (a grammar may mix lexical and dynamic effects).
    pub mode: Vec<&'static str>,
    pub events: Vec<ScopeEvent>,
}

/// Adopted recovery regions. The associated typed node is omitted, never fabricated.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Recovery {
    pub rule_id: &'static str,
    pub mode: &'static str,
    pub span: Span,
    pub sync_span: Option<Span>,
    pub capture_occurrence_ids: Vec<usize>,
}
