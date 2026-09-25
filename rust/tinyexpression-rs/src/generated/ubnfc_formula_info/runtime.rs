use super::api::*;
use super::ast::tree::AstTree;
use super::rt::{
    diag::{Diag, Diagnostics, DisplayDiagnostics, DisplaySnapshot},
    input::Input,
    lexcache::{LexCache, SkipCache},
    memo::{Key, Memo},
    scope::{Checkpoint, ScopeStore},
};

// issue #35 / D-070: ネイティブスタック番兵と、それに当たったときのエスカレーション先
// thread のサイズをここへ集める。Rust cannot recover from native stack overflow, so a
// proactive check must fire before the real stack is exhausted — but the check cannot
// know the calling thread's actual stack size. 既定呼出し（呼出し元 thread で直接走る）は
// 保守的な小さい値のままにし（呼出し元が明示的に小さい stack で呼んでも安全）、
// `max_depth` にまだ達していないのに番兵へ当たったときだけ、`max_depth` から見積もった
// 十分な stack を持つ専用 thread へ 1 度だけエスカレーションする（`parse_with_options` /
// `parse_entry_with_options` 側。外部 `TokenScanner` を受け取る `parse_with_scanner` は
// 任意の状態を持つ scanner を thread 境界へ渡せないので対象外 — 既知の制約として
// docs/reports/2026-09-24-runtime-limits.md に記録）。
//
// 実測（同報告）: JSON 入れ子（typed AST）は深さ単位（`enter_rule` 呼出し）あたり約 568 byte、
// P4 の括弧入れ子は約 620–750 byte。3 倍前後の安全率を見て 1 深さ単位 2048 byte とする。
/// 既定呼出し（呼出し元 thread）の番兵。旧来の固定値のまま（呼出し元が 1 MiB 未満の
/// 明示的に小さい stack で呼んでも安全）。
pub(crate) const NATIVE_STACK_SENTINEL_BYTES: usize = 256 * 1024;
/// エスカレーション先 thread が使ってよい native stack の目安（byte）。
pub(crate) fn escalated_stack_budget(max_depth: usize) -> usize {
    const BYTES_PER_DEPTH: usize = 2048;
    max_depth.saturating_mul(BYTES_PER_DEPTH)
}
/// エスカレーション先 thread に実際に確保する stack size。番兵の閾値より確実に大きくし
/// （番兵が実枯渇より先に必ず発火するように）、下限 8 MiB・上限 512 MiB に丸める。
pub(crate) fn escalated_thread_stack_bytes(max_depth: usize) -> usize {
    escalated_stack_budget(max_depth)
        .saturating_add(2 * 1024 * 1024)
        .clamp(8 * 1024 * 1024, 512 * 1024 * 1024)
}

// issue #35 / D-071: 既定の `arena_entries`（2,000,000）は 1 MB の入力を typed AST 付きで
// 解析すると足りない（実測 4.29 entries/byte、docs/reports/2026-09-24-runtime-limits.md）。
// 明示的に小さい値を設定したテスト・呼出し（`resource_limits_fail_without_partial_success` 等）
// を壊さないよう、既定値と厳密に一致するときだけ入力長に比例して引き上げる。
/// 実測の最大（Operators-512 の 9.00 entries/byte）に約 1.8 倍の安全率を見た値。
const ARENA_ENTRIES_PER_BYTE: usize = 16;
/// 既定スケーリングの上限（約 2,000,000 byte 入力相当）。それを超える入力は
/// `arena_entries` を明示するか、`ResourceLimits::default()` 以外の値を選ぶ。
const ARENA_ENTRIES_CEILING: usize = 32_000_000;
pub(crate) fn scaled_default_arena_entries(input_bytes: usize) -> usize {
    DEFAULT_ARENA_ENTRIES
        .max(input_bytes.saturating_mul(ARENA_ENTRIES_PER_BYTE))
        .min(ARENA_ENTRIES_CEILING)
}

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub(crate) struct EventId(pub usize);
#[derive(Clone, Copy, Debug)]
pub(crate) enum Event {
    Empty,
    /// 回復した領域。残りの観測（規則・mode・同期範囲・診断・公開候補）は
    /// `Session::recovery_events[detail]` にある（event arena の要素を小さく保つため）。
    Recovery {
        span: Span,
        detail: u32,
    },
    Join(EventId, EventId),
    Values {
        span: Span,
        child: EventId,
        wrap: bool,
    },
    Capture {
        token_extent: bool,
        site: usize,
        span: Span,
        child: EventId,
    },
    Rule {
        rule: usize,
        span: Span,
        child: EventId,
        /// この rule の直下 capture が `caps_flat` のどこにあるか（開始, 個数）。
        /// 出現収集が書き、AST 構築が読む。event を読むときに同じ cache line に載るので、
        /// event id で索く別表（x64 で 640 KB の疎な書き込み）を持たない。
        caps: (u32, u32),
    },
    Token {
        text_span: Option<Span>,
        content_span: Option<Span>,
        expr: usize,
        rule: usize,
        span: Span,
    },
}
/// 回復 event の本体（`Event::Recovery::detail` が索く）。
#[derive(Clone, Copy, Debug)]
pub(crate) struct RecoveryEvent {
    rule: usize,
    mode: &'static str,
    sync_span: Option<Span>,
    diag: Diag,
    /// `Session::recovery_hints` の添字。回復した規則の frame に属する公開候補
    /// （D-027 の表示語彙）を回復時に取っておく（診断は後段の走査で組み立てる）。
    hints: u32,
}
/// event arena の 1 要素（D-077、32 byte。`Event` のままだと 88 byte で、JSON 1 MB の
/// typed AST では 156 万 event × 88 byte の arena とその伸長が確保の 7 割を占めていた）。
/// 位置・event id・site / expr の番号は u32 に収まる（入力は `u32::MAX` byte 以下、arena は
/// `u32::MAX` 要素以下で打ち切る）。Recovery の本体は別表（`RecoveryEvent`）。
/// 読むときは `Session::ev` で `Event` に戻す（値の複製。書き換えは `set_ev`）。
/// `unpack` の結果は scalar だけなので、`match self.ev(id)` は memory へ実体化されない。
#[derive(Clone, Copy, Debug)]
pub(crate) struct PackedEvent {
    tag: u8,
    flags: u8,
    /// Token の規則（`u16::MAX` は無し）。
    rule: u16,
    w: [u32; 7],
}
const EV_EMPTY: u8 = 0;
const EV_RECOVERY: u8 = 1;
const EV_JOIN: u8 = 2;
const EV_VALUES: u8 = 3;
const EV_CAPTURE: u8 = 4;
const EV_RULE: u8 = 5;
const EV_TOKEN: u8 = 6;
const NO_RULE: u16 = u16::MAX;
#[inline(always)]
fn w32(value: usize) -> u32 {
    debug_assert!(value <= u32::MAX as usize);
    value as u32
}
#[inline(always)]
fn wide(value: u32) -> usize {
    if value == u32::MAX {
        usize::MAX
    } else {
        value as usize
    }
}
impl PackedEvent {
    const EMPTY: Self = Self {
        tag: EV_EMPTY,
        flags: 0,
        rule: 0,
        w: [0; 7],
    };
    #[inline(always)]
    fn pack(event: Event) -> Self {
        let mut p = Self::EMPTY;
        match event {
            Event::Empty => {}
            Event::Recovery { span, detail } => {
                p.tag = EV_RECOVERY;
                p.w[0] = w32(span[0]);
                p.w[1] = w32(span[1]);
                p.w[2] = detail;
            }
            Event::Join(a, b) => {
                p.tag = EV_JOIN;
                p.w[0] = w32(a.0);
                p.w[1] = w32(b.0);
            }
            Event::Values { span, child, wrap } => {
                p.tag = EV_VALUES;
                p.flags = wrap as u8;
                p.w[..3].copy_from_slice(&[w32(span[0]), w32(span[1]), w32(child.0)]);
            }
            Event::Capture {
                token_extent,
                site,
                span,
                child,
            } => {
                p.tag = EV_CAPTURE;
                p.flags = token_extent as u8;
                p.w[..4].copy_from_slice(&[w32(site), w32(span[0]), w32(span[1]), w32(child.0)]);
            }
            Event::Rule {
                rule,
                span,
                child,
                caps,
            } => {
                p.tag = EV_RULE;
                p.w[..6].copy_from_slice(&[
                    w32(rule),
                    w32(span[0]),
                    w32(span[1]),
                    w32(child.0),
                    caps.0,
                    caps.1,
                ]);
            }
            Event::Token {
                text_span,
                content_span,
                expr,
                rule,
                span,
            } => {
                p.tag = EV_TOKEN;
                p.rule = if rule == usize::MAX {
                    NO_RULE
                } else {
                    debug_assert!(rule < NO_RULE as usize);
                    rule as u16
                };
                p.w[0] = w32(span[0]);
                p.w[1] = w32(span[1]);
                if let Some(t) = text_span {
                    p.flags |= 1;
                    p.w[2] = w32(t[0]);
                    p.w[3] = w32(t[1]);
                }
                if let Some(c) = content_span {
                    p.flags |= 2;
                    p.w[4] = w32(c[0]);
                    p.w[5] = w32(c[1]);
                }
                p.w[6] = if expr == usize::MAX {
                    u32::MAX
                } else {
                    w32(expr)
                };
            }
        }
        p
    }
    #[inline(always)]
    fn unpack(self) -> Event {
        let w = self.w;
        let span = |i: usize| [w[i] as usize, w[i + 1] as usize];
        match self.tag {
            EV_JOIN => Event::Join(EventId(w[0] as usize), EventId(w[1] as usize)),
            EV_VALUES => Event::Values {
                span: span(0),
                child: EventId(w[2] as usize),
                wrap: self.flags != 0,
            },
            EV_CAPTURE => Event::Capture {
                token_extent: self.flags != 0,
                site: w[0] as usize,
                span: span(1),
                child: EventId(w[3] as usize),
            },
            EV_RULE => Event::Rule {
                rule: w[0] as usize,
                span: span(1),
                child: EventId(w[3] as usize),
                caps: (w[4], w[5]),
            },
            EV_TOKEN => Event::Token {
                text_span: (self.flags & 1 != 0).then(|| span(2)),
                content_span: (self.flags & 2 != 0).then(|| span(4)),
                expr: wide(w[6]),
                rule: if self.rule == NO_RULE {
                    usize::MAX
                } else {
                    self.rule as usize
                },
                span: span(0),
            },
            EV_RECOVERY => Event::Recovery {
                span: span(0),
                detail: w[2],
            },
            _ => Event::Empty,
        }
    }
}
#[derive(Clone, Copy, Debug)]
pub(crate) struct Step {
    pub ok: bool,
    pub state: State,
    pub events: EventId,
    pub diag: Diag,
}
impl Step {
    pub fn yes(state: State) -> Self {
        Self {
            ok: true,
            state,
            events: EventId(0),
            diag: Diag::NONE,
        }
    }
}
#[derive(Clone, Copy)]
pub(crate) struct Saved {
    consumed: u32,
    matched: u32,
    events: u32,
    diag: u32,
    diag_pos: u32,
    flags: u32,
    effects: [u32; 2],
    display: DisplaySnapshot,
}
impl Saved {
    fn new(step: Step, effects: Span, display: DisplaySnapshot) -> Self {
        Self {
            display,
            consumed: u32::try_from(step.state.consumed).expect("cursor"),
            matched: u32::try_from(step.state.matched).expect("cursor"),
            events: u32::try_from(step.events.0).expect("arena"),
            diag: step.diag.id,
            diag_pos: step.diag.pos,
            flags: step.ok as u32
                | ((step.state.invert as u32) << 1)
                | ((step.state.reset as u32) << 2),
            effects: effects.map(|p| u32::try_from(p).expect("effects")),
        }
    }
    fn step(self) -> Step {
        Step {
            ok: self.flags & 1 != 0,
            state: State {
                consumed: self.consumed as usize,
                matched: self.matched as usize,
                invert: self.flags & 2 != 0,
                reset: self.flags & 4 != 0,
            },
            events: EventId(self.events as usize),
            diag: Diag {
                id: self.diag,
                pos: self.diag_pos,
            },
        }
    }
}
/// capture の出現（site, span, 子 event）。
pub(crate) type Cap = (usize, Span, EventId);
#[derive(Clone, Copy)]
pub(crate) struct Mark {
    scope: Checkpoint,
    effects: usize,
}
// 効果は Rc で共有する。memo の save / replay は参照を複製するだけで文字列を複製しない
// （scope 側の Decl / Reference は結果 API なので所有する）。
#[derive(Clone)]
struct Effect {
    value: std::rc::Rc<ScanEffect>,
    mode: &'static str,
    offset_cp: usize,
    length_cp: usize,
    /// D-029: enter / leave が属する規則名。declare / use は効果自身が名前を持つ。
    name: Option<&'static str>,
}
/// parse をまたいで再利用する大きな buffer（`ParseOptions::reuse_buffers`）。
/// 内容は parse ごとに捨て、確保だけを thread ごとに 1 組保持する。大きな入力を扱った後は
/// `POOL_RETAIN_BYTES` を超える組を捨てて、常駐量を抑える。結果には影響しない。
pub(crate) struct Buffers {
    arena: Vec<PackedEvent>,
    diag: Diagnostics,
    display: DisplayDiagnostics,
    memo: Memo<Saved>,
    trivia_cache: Memo<(Step, DisplaySnapshot)>,
    lex: LexCache,
    trivia_skip: SkipCache,
    effects: Vec<Effect>,
    saved_effects: Vec<Effect>,
    stacks: Vec<Vec<(EventId, bool)>>,
    captures_pool: Vec<Vec<Cap>>,
    values_pool: Vec<Vec<u32>>,
    scope_pool: Vec<Vec<(usize, Span)>>,
    /// AST の構築先（D-077）。所有 `Ast` だけを返す parse では木を捨てずに再利用する。
    tree: AstTree,
    caps_flat: Vec<Cap>,
    caps_pending: Vec<Cap>,
}
const POOL_RETAIN_BYTES: usize = 64 << 20;
/// 位置ごとの字句 cache の上限（入力長に比例するため）。超える入力では cache を使わない。
const LEX_CACHE_MAX_BYTES: usize = 256 << 20;
thread_local! {
    static POOL: std::cell::RefCell<Option<Buffers>> = const { std::cell::RefCell::new(None) };
}
impl Buffers {
    fn bytes(&self) -> usize {
        self.arena.capacity() * std::mem::size_of::<PackedEvent>()
            + self.diag.capacity_bytes()
            + self.display.capacity_bytes()
            + self.memo.capacity_bytes()
            + self.trivia_cache.capacity_bytes()
            + self.lex.capacity_bytes()
            + self.trivia_skip.capacity_bytes()
            + (self.effects.capacity() + self.saved_effects.capacity())
                * std::mem::size_of::<Effect>()
            + (self.caps_flat.capacity() + self.caps_pending.capacity())
                * std::mem::size_of::<Cap>()
    }
}
/// 生成 parser の session。`DIAG` は診断を記録するかの単相化 flag で、`ParseOptions::diagnostics`
/// と 1 対 1 に対応する（D-036）。`false` は fast mode で、主診断 DAG と公開 farthest の配管が
/// 機械語から消える。
pub(crate) struct Session<'a, const DIAG: bool> {
    pub text: &'a str,
    pub input: Input<'a>,
    pub options: ParseOptions,
    pub scanner: &'a mut dyn TokenScanner,
    pub arena: Vec<PackedEvent>,
    /// `Event::Recovery` の本体（`Event::Recovery::detail` が索く）。
    recovery_events: Vec<RecoveryEvent>,
    pub diag: Diagnostics,
    pub display: DisplayDiagnostics,
    pub memo: Memo<Saved>,
    pub scope: ScopeStore,
    pub trivia_cache: Memo<(Step, DisplaySnapshot)>,
    /// 位置ごとの字句 cache（統合 DFA の結果）。key は位置だけ（rt/lexcache.rs の健全性議論）。
    pub lex: LexCache,
    /// trivia policy ごとの「次の非 trivia 位置」（診断を記録しない読み飛ばし）。
    pub trivia_skip: SkipCache,
    /// cache を引くか（`ParseOptions::lexical_cache` と、文法に cache 対象があるか）。
    pub lex_enabled: bool,
    effects: Vec<Effect>,
    saved_effects: Vec<Effect>,
    pub statistics: Statistics,
    /// AST の構築先（D-077）。節点・Text はここへ積み、所有 `Ast` はここから写す。
    pub tree: AstTree,
    /// AST 構築の走査用 stack の再利用 pool（再帰するので複数）。
    stacks: Vec<Vec<(EventId, bool)>>,
    /// AST 構築の作業 Vec の再利用 pool（capture 出現、field 値、field 文字列）。
    captures_pool: Vec<Vec<Cap>>,
    values_pool: Vec<Vec<u32>>,
    scope_pool: Vec<Vec<(usize, Span)>>,
    /// 出現収集で組み立てる「rule ごとの直下 capture」の並び。どの区間がどの rule のものかは
    /// `Event::Rule::caps`（開始, 個数）が持つ。AST 構築はその区間を写すだけで木を再走査しない。
    caps_flat: Vec<Cap>,
    caps_pending: Vec<Cap>,
    pub depth: usize,
    stack_anchor: Option<usize>,
    depth_failure: Option<usize>,
    /// D-070: `depth_failure` が native stack 番兵（`native_stack_budget`）によるものか。
    /// true なら `options.max_depth` へはまだ達していない失敗で、大きい stack を持つ
    /// thread へのエスカレーションで通る可能性がある（`enter_rule` は論理上限に先に
    /// 当たっていれば `limit` を呼ぶ前に `depth_failure` を確定させるので、両方が
    /// 同時に true になることはない）。
    depth_failure_is_stack: bool,
    /// この session が使ってよい native stack の目安（byte）。既定呼出しは
    /// `NATIVE_STACK_SENTINEL_BYTES`、エスカレーション後の thread は
    /// `escalated_stack_budget(options.max_depth)`。
    native_stack_budget: usize,
    /// D-071: 実効の arena 上限（byte 単位の `arena_entries`）。既定値のときだけ
    /// `scaled_default_arena_entries` で入力長に応じて引き上げたもの。
    arena_limit: usize,
    pub resource_failure: Option<(usize, &'static str)>,
    effect_work: usize,
    effect_bytes: usize,
    mapping_depth: usize,
    mapping_anchor: Option<usize>,
    /// 回復ごとの公開候補（最遠位置 byte, label）。`Event::Recovery::hints` が索く。
    recovery_hints: Vec<Option<(usize, Vec<&'static str>)>>,
}
impl<'a, const DIAG: bool> Session<'a, DIAG> {
    // 診断の配管は `const DIAG` で単相化する（D-036）。fast mode（`ParseOptions::diagnostics ==
    // false`）は `Session::<false>` で走り、以下の wrapper が定数 false の分岐になるので、主診断
    // DAG（`Diagnostics`）と公開 farthest（`DisplayDiagnostics`）への呼出しが機械語から消える。
    // 実行時の `enabled` 検査だけでは、呼出し・引数の準備・`Step.diag` の合流が残っていた。
    // 診断モードは `Session::<true>` で従来どおり記録する。観測は両者で変わらない。
    #[inline(always)]
    pub fn diag_fail(&mut self, pos: usize, label: &'static str) -> Diag {
        if DIAG {
            self.diag.fail(pos, label)
        } else {
            Diag::NONE
        }
    }
    #[inline(always)]
    pub fn diag_join(&mut self, a: Diag, b: Diag) -> Diag {
        if DIAG {
            self.diag.join(a, b)
        } else {
            Diag::NONE
        }
    }
    #[inline(always)]
    pub fn diag_rule(&mut self, rule: usize, child: Diag) -> Diag {
        if DIAG {
            self.diag.rule(rule, child)
        } else {
            Diag::NONE
        }
    }
    #[inline(always)]
    pub fn display_reach(&mut self, reached: usize) {
        if DIAG {
            self.display.reach(reached);
        }
    }
    #[inline(always)]
    pub fn display_failures(&mut self, pos: usize, labels: &'static [&'static str]) {
        if DIAG {
            self.display.failures(pos, labels);
        }
    }
    #[inline(always)]
    pub fn display_fail(&mut self, pos: usize, label: &'static str) {
        if DIAG {
            self.display.fail(pos, label);
        }
    }
    #[inline(always)]
    pub fn display_local_far(&self) -> Option<usize> {
        if DIAG {
            self.display.local_far()
        } else {
            None
        }
    }
    #[inline(always)]
    pub fn display_enter(&mut self, reached: usize) {
        if DIAG {
            self.display.enter(reached);
        }
    }
    #[inline(always)]
    pub fn display_leave(&mut self) {
        if DIAG {
            self.display.leave();
        }
    }
    #[inline(always)]
    pub fn display_enter_guard(&mut self, reached: usize) {
        if DIAG {
            self.display.enter_guard(reached);
        }
    }
    #[inline(always)]
    pub fn display_leave_guard(&mut self) {
        if DIAG {
            self.display.leave_guard();
        }
    }
    #[inline(always)]
    pub fn display_discard(&mut self) {
        if DIAG {
            self.display.discard();
        }
    }
    #[inline(always)]
    pub fn display_snapshot(&mut self) -> DisplaySnapshot {
        if DIAG {
            self.display.snapshot()
        } else {
            DisplaySnapshot::default()
        }
    }
    #[inline(always)]
    pub fn display_merge(&mut self, snapshot: DisplaySnapshot) {
        if DIAG {
            self.display.merge(snapshot);
        }
    }

    pub fn new(
        text: &'a str,
        options: ParseOptions,
        scanner: &'a mut dyn TokenScanner,
        native_stack_budget: usize,
    ) -> Self {
        let rejected = text.len() > options.limits.input_bytes.min(u32::MAX as usize);
        let text = if rejected { "" } else { text };
        // D-071: 既定値のときだけ入力長に応じて引き上げる（明示的に設定した値はそのまま使う）。
        let arena_limit = if options.limits.arena_entries == DEFAULT_ARENA_ENTRIES {
            scaled_default_arena_entries(text.len())
        } else {
            options.limits.arena_entries
        };
        let arena_capacity = if options.wants_ast() || options.lexical || HAS_SCOPE || HAS_RECOVERY {
            // P4 実測 約 4.4 event / byte（小さい入力はこの見積りで倍々伸長を避ける）。
            // D-077: 大きい入力は 2 event / byte を下限に見積もる（JSON は D-072 後 1.56、
            // P4 3.8、Operators 9.0）。以前は 32,768 で打ち切って倍々で伸ばしていたので、
            // pool の無い最初の parse は JSON 1 MB で 210 万 event 分を 7 回確保し直していた。
            text.len()
                .saturating_mul(5)
                .min(32768)
                .max(text.len().saturating_mul(2))
                .min(arena_limit)
                .saturating_add(8)
        } else {
            1
        };
        // fast mode（`ParseOptions::diagnostics == false`）は主診断 DAG も公開 farthest も
        // 記録しない。arena は確保せず、予算（limit 0）も消費しない。
        // 単相化（D-036）と `ParseOptions::diagnostics` は入口で 1 対 1 に対応する。
        let diag_on = DIAG;
        let diag_limit = if diag_on { options.limits.diagnostics } else { 0 };
        let diag_capacity = if diag_on {
            text.len().saturating_mul(24).saturating_add(64).min(1 << 20)
        } else {
            0
        };
        // memo は rule 本体単位（P4 実測 約 2.3 entry / byte。候補除外で失敗 entry が減った）。
        let memo_capacity = text.len().saturating_mul(3).saturating_add(64).min(1 << 20);
        // 字句 cache は位置で直接引く（位置は 0..=text.len()、EOF 位置を含む）。
        // 文法に cache 対象の literal / trivia policy が無ければ 0 を渡して確保しない。
        // 表は入力長に比例するので、割に合わない大きさになるときは cache を使わない。
        let lex_bytes = (text.len() + 1)
            .saturating_mul(LEX_WORDS * std::mem::size_of::<u64>() + std::mem::size_of::<u32>());
        let lex_enabled = options.lexical_cache
            && !rejected
            && lex_bytes <= LEX_CACHE_MAX_BYTES
            && (LEX_WORDS != 0 || TRIVIA_POLICIES != 0);
        let lex_words = if lex_enabled { LEX_WORDS } else { 0 };
        let skip_policies = if lex_enabled { TRIVIA_POLICIES } else { 0 };
        let lex_positions = if lex_enabled { text.len() + 1 } else { 0 };
        let pooled = if options.reuse_buffers {
            POOL.with(|p| p.borrow_mut().take())
        } else {
            None
        };
        let mut buffers = match pooled {
            Some(mut b) => {
                b.arena.clear();
                b.diag.reset(diag_limit);
                b.display.reset(options.limits.diagnostics);
                b.memo.reset(memo_capacity);
                // trivia cache は前回の entry 数を見積りにする（0 だと毎 parse 縮小と再拡張を繰り返す）。
                let trivia_capacity = b.trivia_cache.len();
                b.trivia_cache.reset(trivia_capacity);
                b.lex.reset(lex_words, lex_positions);
                b.trivia_skip.reset(skip_policies, lex_positions);
                b.effects.clear();
                b.saved_effects.clear();
                b.tree.clear();
                b.values_pool.iter_mut().for_each(Vec::clear);
                b
            }
            None => Buffers {
                arena: Vec::with_capacity(arena_capacity),
                diag: Diagnostics::with_limit_and_capacity(diag_limit, diag_capacity),
                display: DisplayDiagnostics::new(options.limits.diagnostics),
                memo: Memo::with_capacity(memo_capacity),
                trivia_cache: Memo::default(),
                lex: {
                    let mut lex = LexCache::default();
                    lex.reset(lex_words, lex_positions);
                    lex
                },
                trivia_skip: {
                    let mut cache = SkipCache::default();
                    cache.reset(skip_policies, lex_positions);
                    cache
                },
                effects: vec![],
                saved_effects: vec![],
                stacks: vec![],
                captures_pool: vec![],
                values_pool: vec![],
                scope_pool: vec![],
                tree: AstTree::default(),
                caps_flat: vec![],
                caps_pending: vec![],
            },
        };
        buffers.diag.enable(diag_on);
        buffers.display.enable(diag_on);
        let mut arena = buffers.arena;
        arena.push(PackedEvent::EMPTY);
        Self {
            text,
            input: Input::new(text),
            options,
            scanner,
            arena,
            diag: buffers.diag,
            display: buffers.display,
            memo: buffers.memo,
            scope: ScopeStore::new(),
            trivia_cache: buffers.trivia_cache,
            lex: buffers.lex,
            trivia_skip: buffers.trivia_skip,
            lex_enabled,
            effects: buffers.effects,
            saved_effects: buffers.saved_effects,
            statistics: Statistics::default(),
            tree: buffers.tree,
            stacks: buffers.stacks,
            captures_pool: buffers.captures_pool,
            values_pool: buffers.values_pool,
            scope_pool: buffers.scope_pool,
            caps_flat: buffers.caps_flat,
            caps_pending: buffers.caps_pending,
            depth: 0,
            stack_anchor: None,
            depth_failure: None,
            depth_failure_is_stack: false,
            native_stack_budget,
            arena_limit,
            resource_failure: rejected.then_some((0, "input byte budget exceeded")),
            effect_work: 0,
            effect_bytes: 0,
            mapping_depth: 0,
            mapping_anchor: None,
            recovery_hints: Vec::new(),
            recovery_events: Vec::new(),
        }
    }
    /// 大きな buffer を thread の pool へ返す（`finish` の最後）。
    fn recycle(&mut self) {
        if !self.options.reuse_buffers {
            return;
        }
        let mut buffers = Buffers {
            arena: std::mem::take(&mut self.arena),
            diag: std::mem::replace(&mut self.diag, Diagnostics::with_limit_and_capacity(0, 0)),
            display: std::mem::replace(&mut self.display, DisplayDiagnostics::new(0)),
            memo: std::mem::take(&mut self.memo),
            trivia_cache: std::mem::take(&mut self.trivia_cache),
            lex: std::mem::take(&mut self.lex),
            trivia_skip: std::mem::take(&mut self.trivia_skip),
            effects: std::mem::take(&mut self.effects),
            saved_effects: std::mem::take(&mut self.saved_effects),
            stacks: std::mem::take(&mut self.stacks),
            captures_pool: std::mem::take(&mut self.captures_pool),
            values_pool: std::mem::take(&mut self.values_pool),
            scope_pool: std::mem::take(&mut self.scope_pool),
            tree: std::mem::take(&mut self.tree),
            caps_flat: std::mem::take(&mut self.caps_flat),
            caps_pending: std::mem::take(&mut self.caps_pending),
        };
        if buffers.bytes() > POOL_RETAIN_BYTES {
            // 伸長の倍々で余った容量だけで上限を超えることがある（JSON 1 MB: event 156 万に
            // 容量 210 万）。使った分へ縮めて収まるなら保持する。同じ規模の次の parse は
            // 伸長しないので、縮めるのは規模が変わったときだけ。
            buffers.arena.shrink_to_fit();
            buffers.caps_flat.shrink_to_fit();
            buffers.caps_pending.shrink_to_fit();
        }
        if buffers.bytes() <= POOL_RETAIN_BYTES {
            POOL.with(|p| *p.borrow_mut() = Some(buffers));
        }
    }
    // Rust cannot recover from native stack overflow. Stop before consuming the
    // per-parse 256 KiB native stack budget, in addition to the logical rule limit.
    // Check expressions too: one rule may contain a deeply nested expression tree.
    #[inline(never)]
    pub fn limit(&mut self, state: State) -> Option<Step> {
        if self.diag.exhausted || self.display.exhausted {
            self.resource_failure
                .get_or_insert((state.consumed, "diagnostic budget exceeded"));
        }
        if let Some((offset, label)) = self.resource_failure {
            return Some(self.fail(state, offset, label));
        }
        let frame = 0u8;
        let address = std::ptr::addr_of!(frame) as usize;
        let anchor = *self.stack_anchor.get_or_insert(address);
        if self.depth_failure.is_none() && anchor.abs_diff(address) > self.native_stack_budget {
            self.depth_failure = Some(state.consumed.max(state.matched));
            self.depth_failure_is_stack = true;
        }
        self.depth_failure
            .map(|offset| self.fail(state, offset, "maximum parse depth exceeded"))
    }
    /// D-070: 直近の `depth_failure` が native stack 番兵によるものだったか
    /// （`options.max_depth` にはまだ達していない）。エスカレーション判定に使う。
    #[inline]
    pub fn stack_sentinel_tripped(&self) -> bool {
        self.depth_failure_is_stack
    }
    // 候補除外の失敗再生（§3.4、predict.rs）は rule 入口の検査を行わないので、
    // 再生中に limit / 深さ上限が発火し得る状態では本物の候補関数へ戻す。
    #[inline]
    pub fn can_replay(&self, nesting: usize) -> bool {
        self.depth + nesting <= self.options.max_depth
            && !self.diag.exhausted
            && !self.display.exhausted
            && self.resource_failure.is_none()
            && self.depth_failure.is_none()
    }
    pub fn enter_rule(&mut self, state: State) -> Option<Step> {
        if self.depth >= self.options.max_depth {
            self.depth_failure
                .get_or_insert(state.consumed.max(state.matched));
        }
        if let Some(limit) = self.limit(state) {
            return Some(limit);
        }
        self.depth += 1;
        None
    }
    pub fn cp(&self, byte: usize) -> usize {
        self.input.byte_to_cp(byte).expect("parser UTF-8 boundary")
    }
    pub fn span(&self, span: Span) -> Span {
        [self.cp(span[0]), self.cp(span[1])]
    }
    fn trimmed(&self, span: Span) -> &str {
        let [start, end] = self.trimmed_range(span);
        &self.text[start..end]
    }
    /// `trimmed` の byte 範囲（AST の Text 節点は値を複製せずこの範囲で持つ）。
    fn trimmed_range(&self, span: Span) -> [usize; 2] {
        let trim = |c: char| {
            c.is_whitespace() && !matches!(c, '\u{85}' | '\u{a0}' | '\u{2007}' | '\u{202f}')
        };
        let text = &self.text[span[0]..span[1]];
        let head = text.trim_start_matches(trim);
        let start = span[0] + (text.len() - head.len());
        [start, start + head.trim_end_matches(trim).len()]
    }
    pub fn text(&self, span: Span) -> String {
        self.trimmed(span).to_owned()
    }
    pub fn mark(&self) -> Mark {
        Mark {
            scope: self.scope.checkpoint(),
            effects: self.effects.len(),
        }
    }
    pub fn restore(&mut self, mark: Mark) {
        self.scope.restore(mark.scope);
        self.effects.truncate(mark.effects);
    }
    pub fn save_effects(&mut self, mark: Mark) -> Span {
        let start = self.saved_effects.len();
        let count = self.effects.len() - mark.effects;
        let bytes = self.effects[mark.effects..]
            .iter()
            .map(Self::effect_size)
            .sum::<usize>();
        if start.saturating_add(count) > self.options.limits.effect_entries.min(u32::MAX as usize)
            || self.effect_bytes.saturating_add(bytes) > self.options.limits.effect_bytes
        {
            self.resource_failure
                .get_or_insert((0, "saved effect budget exceeded"));
            return [0, 0];
        }
        self.effect_bytes += bytes;
        self.saved_effects
            .extend_from_slice(&self.effects[mark.effects..]);
        [start, self.saved_effects.len()]
    }
    pub fn replay_effects(&mut self, span: Span) {
        for i in span[0]..span[1] {
            let effect = self.saved_effects[i].clone();
            self.effect_shared(
                effect.value,
                effect.mode,
                effect.offset_cp,
                effect.length_cp,
                effect.name,
            );
        }
    }
    fn effect_size(effect: &Effect) -> usize {
        match &*effect.value {
            ScanEffect::Declare { name, .. } | ScanEffect::Use { name, .. } => name.len(),
            ScanEffect::Diagnostic { message, .. } => message.len(),
            _ => 0,
        }
    }
    pub fn effect(&mut self, effect: ScanEffect) {
        self.effect_at(effect, "lexical", 0, 0);
    }
    pub fn effect_at(
        &mut self,
        effect: ScanEffect,
        mode: &'static str,
        offset_cp: usize,
        length_cp: usize,
    ) {
        self.effect_shared(std::rc::Rc::new(effect), mode, offset_cp, length_cp, None);
    }
    /// D-029: scope を開閉する効果は、その scope を持つ規則名を伴う。
    pub fn effect_scope_at(
        &mut self,
        effect: ScanEffect,
        mode: &'static str,
        offset_cp: usize,
        length_cp: usize,
        name: &'static str,
    ) {
        self.effect_shared(
            std::rc::Rc::new(effect),
            mode,
            offset_cp,
            length_cp,
            Some(name),
        );
    }
    fn effect_shared(
        &mut self,
        effect: std::rc::Rc<ScanEffect>,
        mode: &'static str,
        offset_cp: usize,
        length_cp: usize,
        name: Option<&'static str>,
    ) {
        // D-029: trim 後に空になった記号名は記号ではない。宣言も参照も記録しない
        // （Java の `Session.applyEffects` と同じ規則）。
        if matches!(
            &*effect,
            ScanEffect::Declare { name, .. } | ScanEffect::Use { name, .. }
if name.is_empty()
        ) {
            return;
        }
        if self.effect_work
            >= self
                .options
                .limits
                .effect_entries
                .min(u32::MAX as usize - 1)
        {
            self.resource_failure
                .get_or_insert((0, "effect budget exceeded"));
            return;
        }
        let size = match &*effect {
            ScanEffect::Declare { name, .. } | ScanEffect::Use { name, .. } => name.len(),
            ScanEffect::Diagnostic { message, .. } => message.len(),
            _ => 0,
        };
        if self.effect_bytes.saturating_add(size) > self.options.limits.effect_bytes {
            self.resource_failure
                .get_or_insert((0, "effect byte budget exceeded"));
            return;
        }
        self.effect_work += 1;
        self.effect_bytes += size;
        match &*effect {
            ScanEffect::Diagnostic {
                message,
                offset_cp,
                len_cp,
                severity,
            } => self
                .scope
                .add_diagnostic(message, *offset_cp, *len_cp, *severity),
            ScanEffect::Enter => self.scope.enter(),
            ScanEffect::Leave => self.scope.leave(),
            ScanEffect::Clear => self.scope.clear_diagnostics(),
            ScanEffect::Declare { name, offset_cp } => self.scope.declare(name, *offset_cp),
            ScanEffect::Use {
                name,
                offset_cp,
                len_cp,
            } => {
                self.scope.add_reference(name, *offset_cp, *len_cp);
                if !self.scope.is_declared(name) {
                    self.scope.add_diagnostic(
                        &format!("未定義のシンボル: '{name}'"),
                        *offset_cp,
                        *len_cp,
                        Severity::Warning,
                    );
                }
            }
        }
        self.effects.push(Effect {
            value: effect,
            mode,
            offset_cp,
            length_cp,
            name,
        });
    }
    pub fn lookup(&mut self, key: Key) -> Option<Step> {
        if !self.options.memo {
            return None;
        }
        let saved = self.memo.get(key)?;
        self.statistics.memo_hits += 1;
        self.display_merge(saved.display);
        self.replay_effects(saved.effects.map(|p| p as usize));
        Some(saved.step())
    }
    pub fn store(&mut self, key: Key, step: Step, mark: Mark) {
        if !self.options.memo || self.depth_failure.is_some() || self.resource_failure.is_some() {
            return;
        }
        if self.memo.len() >= self.options.limits.memo_entries.min(u32::MAX as usize - 1) {
            self.resource_failure
                .get_or_insert((step.state.consumed, "memo budget exceeded"));
            return;
        }
        let effects = if step.ok {
            self.save_effects(mark)
        } else {
            [0, 0]
        };
        let snapshot = self.display_snapshot();
        self.memo.insert(key, Saved::new(step, effects, snapshot));
    }
    #[inline(always)]
    pub fn event(&mut self, event: Event) -> EventId {
        // 認識だけの parse（scope / recovery の無い文法）は記録しない。判定だけを呼出し側へ inline する。
        if !self.options.build_ast
            && !self.options.ast_tree
            && !self.options.lexical
            && !HAS_SCOPE
            && !HAS_RECOVERY
        {
            return EventId(0);
        }
        self.record_event(PackedEvent::pack(event))
    }
    #[inline]
    fn record_event(&mut self, packed: PackedEvent) -> EventId {
        if self.arena.len() >= self.arena_limit.min(u32::MAX as usize) {
            self.resource_failure
                .get_or_insert((0, "arena budget exceeded"));
            return EventId(0);
        }
        let id = EventId(self.arena.len());
        self.arena.push(packed);
        id
    }
    /// event を読む（`PackedEvent` から戻した値）。
    #[inline(always)]
    pub fn ev(&self, id: EventId) -> Event {
        self.arena[id.0].unpack()
    }
    /// literal / token 参照の結果の Token event を、expr と規則を付け替えて複製する
    /// （`Event` に戻さず packed のまま写す。Token でなければ `id` をそのまま返す）。
    #[inline(always)]
    pub fn relabel_token(&mut self, id: EventId, expr: usize, rule: usize) -> EventId {
        // 認識だけの parse では event を記録しない（`id` は 0 = Empty）ので、判定だけを inline にする。
        if self.arena[id.0].tag != EV_TOKEN {
            return id;
        }
        self.relabel_token_copy(id, expr, rule)
    }
    #[inline(never)]
    fn relabel_token_copy(&mut self, id: EventId, expr: usize, rule: usize) -> EventId {
        let mut packed = self.arena[id.0];
        debug_assert!(rule < NO_RULE as usize);
        packed.rule = rule as u16;
        packed.w[6] = w32(expr);
        self.record_event(packed)
    }
    /// Rule event の直下 capture 表の区間（出現収集が書く）。
    #[inline]
    pub fn set_rule_caps(&mut self, id: EventId, caps: (u32, u32)) {
        let packed = &mut self.arena[id.0];
        if packed.tag == EV_RULE {
            packed.w[4] = caps.0;
            packed.w[5] = caps.1;
        }
    }
    /// event を書き換える（書き換える箇所は Token / Capture だけ）。
    #[inline(always)]
    pub fn set_ev(&mut self, id: EventId, event: Event) {
        self.arena[id.0] = PackedEvent::pack(event);
    }
    pub fn join(&mut self, a: EventId, b: EventId) -> EventId {
        if a.0 == 0 {
            return b;
        }
        if b.0 == 0 {
            return a;
        }
        self.event(Event::Join(a, b))
    }
    pub fn combine(&mut self, a: Step, b: Step) -> Step {
        Step {
            ok: b.ok,
            state: b.state,
            events: if b.ok {
                self.join(a.events, b.events)
            } else {
                EventId(0)
            },
            diag: self.diag_join(a.diag, b.diag),
        }
    }
    pub fn fail(&mut self, state: State, offset: usize, label: &'static str) -> Step {
        Step {
            ok: false,
            state,
            events: EventId(0),
            diag: self.diag_fail(offset, label),
        }
    }
    pub fn primitive<const MATCH: bool>(
        &mut self,
        mut state: State,
        length: Option<usize>,
        label: &'static str,
    ) -> Step {
        let start = state.position::<MATCH>();
        let length = if state.invert {
            if length.is_some() {
                None
            } else {
                self.input.cp_at(start).map(|(_, n)| n)
            }
        } else {
            length
        };
        match length {
            Some(length) => {
                state.advance::<MATCH>(length);
                let mut step = Step::yes(state);
                step.events = self.event(Event::Token {
                    text_span: None,
                    content_span: None,
                    expr: usize::MAX,
                    rule: usize::MAX,
                    span: [start, start + length],
                });
                step
            }
            None => {
                state.advance::<MATCH>(0);
                self.fail(state, start, label)
            }
        }
    }
    /// 統合 DFA の結果を位置ごとに引く。範囲外（起こらないはず）は None を返し、
    /// 呼出し側は走査へ戻る。`lex_fill` は (入力, 位置) だけの関数なので、どの parser 状態
    /// から引いても同じ答えになる。
    #[inline]
    pub fn lex_matches(&mut self, pos: usize, id: u32) -> Option<bool> {
        if self.lex.need_fill(pos)? {
            self.lex_run(pos);
        }
        Some(self.lex.test(pos, id))
    }
    /// この位置の統合 DFA を 1 回走らせて bitset を埋める（cache miss のときだけ）。
    #[inline]
    pub fn lex_run(&mut self, pos: usize) {
        let bytes = self.text.as_bytes();
        let out = self.lex.slot(pos);
        lex_fill(bytes, pos, out);
    }
    /// `boundary` は D-040 の語境界。末尾が識別子構成文字のリテラルにだけ front が
    /// 立てる。境界違反は「一致しなかった」と同じ失敗記録（同じ位置・同じ label）になる。
    pub fn literal<const MATCH: bool>(
        &mut self,
        state: State,
        word: &'static str,
        sensitive: bool,
        lit: u32,
        boundary: bool,
        label: &'static str,
    ) -> Step {
        let start = state.position::<MATCH>();
        let length = 'length: {
            if self.lex_enabled && lit != u32::MAX {
                if let Some(hit) = self.lex_matches(start, lit) {
                    break 'length hit.then_some(word.len());
                }
            }
            if sensitive {
                self.input.starts_with(start, word).then_some(word.len())
            } else {
                let mut p = start;
                let mut ok = true;
                for expected in word.chars() {
                    let Some((actual, n)) = self.input.cp_at(p) else {
                        ok = false;
                        break;
                    };
                    if !super::rt::input::equal_ignore_case(actual, expected) {
                        ok = false;
                        break;
                    }
                    p += n;
                }
                ok.then_some(p - start)
            }
        };
        let length = match length {
            Some(n) if boundary && self.input.identifier_byte_at(start + n) => None,
            other => other,
        };
        self.primitive::<MATCH>(state, length, label)
    }
    pub fn any<const MATCH: bool>(&mut self, state: State, label: &'static str) -> Step {
        self.primitive::<MATCH>(
            state,
            self.input.cp_at(state.position::<MATCH>()).map(|(_, n)| n),
            label,
        )
    }
    pub fn empty(&mut self, state: State) -> Step {
        let mut child = state.begin();
        if let Some((_, length)) = self.input.cp_at(child.matched) {
            child.matched += length;
        }
        Step::yes(state.commit(child))
    }
    pub fn eof<const MATCH: bool>(&mut self, state: State, label: &'static str) -> Step {
        if state.begin().position::<MATCH>() == self.text.len() {
            self.display_fail(state.consumed.max(state.begin().matched),"WildCardCharacterParser");
            Step::yes(state)
        } else {
            self.fail(state, state.position::<MATCH>(), label)
        }
    }
    pub fn number<const MATCH: bool>(&mut self, state: State, label: &'static str) -> Step {
        // NumberParser.java の失敗試行を表示用に記録する。Rust の主 fail_at は
        // 仮数なしの場合のみ進め、不完全指数のロールバック位置は維持する。
        let start = state.position::<MATCH>();
        let bytes = self.text.as_bytes();
        let mut p = start;
        if matches!(bytes.get(p), Some(b'+' | b'-')) { p += 1; }
        else { self.display_fail(p, "SignParser"); }
        let digits = p;
        while bytes.get(p).is_some_and(u8::is_ascii_digit) { p += 1; }
        self.display_fail(p, "DigitParser");
        let mut count = p - digits;
        if count == 0 { self.display_fail(p, "OneOrMore"); }
        if bytes.get(p) == Some(&b'.') {
            p += 1;
            let fraction = p;
            while bytes.get(p).is_some_and(u8::is_ascii_digit) { p += 1; }
            self.display_fail(p, "DigitParser");
            if p == fraction { self.display_fail(p, "OneOrMore"); }
            count += p - fraction;
            if count == 0 { self.display_fail(start,"Chain"); self.display_fail(start,"'.'"); }
        } else {
            self.display_fail(p, "'.'");
            if count == 0 { self.display_fail(digits,"Chain"); }
        }
        if count == 0 {
            self.display_fail(start,"NumberParser");
            let mut out = self.primitive::<MATCH>(state, None, label);
            if !out.ok { out.diag = self.diag_fail(p,label); }
            return out;
        }
        if matches!(bytes.get(p), Some(b'e' | b'E')) {
            let exponent = p;
            p += 1;
            if matches!(bytes.get(p), Some(b'+' | b'-')) { p += 1; }
            else { self.display_fail(p,"SignParser"); }
            let digits = p;
            while bytes.get(p).is_some_and(u8::is_ascii_digit) { p += 1; }
            self.display_fail(p,"DigitParser");
            if digits == p { self.display_fail(p,"OneOrMore"); p = exponent; }
        } else { self.display_fail(p,"EParser"); self.display_fail(p,"ExponentParser"); }
        self.primitive::<MATCH>(state, Some(p - start), label)
    }
    pub fn identifier<const MATCH: bool>(&mut self, state: State, label: &'static str) -> Step {
        let start = state.position::<MATCH>();
        let bytes = self.text.as_bytes();
        let mut p = start;
        if bytes
            .get(p)
            .is_some_and(|b| b.is_ascii_alphabetic() || *b == b'_')
        {
            p += 1;
            while bytes
                .get(p)
                .is_some_and(|b| b.is_ascii_alphanumeric() || *b == b'_')
            {
                p += 1;
            }
        }
        if p == start { self.display_fail(start,"AlphabetUnderScoreParser"); self.display_fail(start,"IdentifierParser"); }
        else { self.display_fail(p,"AlphabetNumericUnderScoreParser"); }
        self.primitive::<MATCH>(state, (p > start).then_some(p - start), label)
    }
    pub fn quoted<const MATCH: bool>(
        &mut self,
        state: State,
        quote: char,
        label: &'static str,
    ) -> Step {
        let start = state.position::<MATCH>();
        let mut p = start;
        if self.input.cp_at(p).is_none_or(|(c, _)| c != quote) {
            return self.fail(state, start, label);
        }
        p += 1;
        while let Some((c, n)) = self.input.cp_at(p) {
            p += n;
            if c == quote {
                let step = self.primitive::<MATCH>(state, Some(p - start), label);
                let mut event = self.ev(step.events);
                if let Event::Token { text_span, content_span, .. } = &mut event {
                    *text_span = Some([start, p]);
                    *content_span = Some([start + 1, p - 1]);
                    self.set_ev(step.events, event);
                }
                return step;
            }
            if c == '\\' {
                if let Some((_, n)) = self.input.cp_at(p) {
                    p += n;
                } else {
                    break;
                }
            }
        }
        self.display_fail(p, if quote == '\'' {"SingleQuoted"} else {"Quoted"});
        self.fail(state, start, label)
    }
    pub fn until<const MATCH: bool>(&mut self, state: State, terminator: &'static str, display: &'static str) -> Step {
        let mut out = state.begin();
        loop {
            if !terminator.is_empty() && self.input.starts_with(out.matched, terminator) {
                if !MATCH {
                    out.matched += terminator.len();
                }
                return Step::yes(state.commit(out));
            }
            if !terminator.is_empty() { self.display_fail(out.consumed.max(out.matched),display); }
            let Some((_, n)) = self.input.cp_at(out.position::<MATCH>()) else {
                self.display_fail(out.consumed.max(out.matched),"ANY");
                return Step::yes(state.commit(out));
            };
            out.advance::<MATCH>(n);
        }
    }
    pub fn scan<const MATCH: bool>(
        &mut self,
        state: State,
        logical_id: &'static str,
        label: &'static str,
    ) -> Step {
        let mark = self.mark();
        let result = self
            .scanner
            .scan(logical_id, self.text, state, MATCH, &self.scope);
        let valid = result.consumed_end >= state.consumed
            && (!MATCH || result.consumed_end == state.consumed)
            && result.matched_end >= result.consumed_end
            && result.value_span[0] <= result.value_span[1]
            && [
                result.consumed_end,
                result.matched_end,
                result.value_span[0],
                result.value_span[1],
            ]
            .iter()
            .all(|&p| self.input.byte_to_cp(p).is_some())
            && result
                .diagnostics
                .iter()
                .all(|d| self.input.byte_to_cp(d.offset).is_some());
        if !valid {
            return self.fail(state, state.position::<MATCH>(), "valid Extern cursor/span");
        }
        let mut step = Step::yes(State {
            consumed: result.consumed_end,
            matched: result.matched_end,
            ..state
        });
        step.ok = result.ok;
        for d in result.diagnostics {
            self.display_fail(d.offset, d.expected);
            let diag = self.diag_fail(d.offset, d.expected);
            step.diag = self.diag_join(step.diag, diag);
        }
        if step.ok {
            for effect in result.effects {
                self.effect(effect);
            }
            step.events = self.event(Event::Token {
                expr: usize::MAX,
                rule: usize::MAX,
                span: result.value_span,
                text_span: Some(result.value_span),
                content_span: None,
            });
        } else {
            self.restore(mark);
            if step.diag.id == 0 {
                step.diag = self.diag_fail(state.position::<MATCH>(), label);
            }
        }
        step
    }
    /// 左因数分解（factor.rs）: 共有要素を先頭候補の expression 関数で評価した後、採用された
    /// 候補の capture site / token expr に付け替える。共有要素の wrapper は memo されないので
    /// この event は今回の結果からしか参照されない。
    pub fn retag(&mut self, id: EventId, site: Option<usize>, expr: Option<usize>) {
        if id.0 == 0 {
            return;
        }
        let mut target = id;
        let mut event = self.ev(id);
        if let Event::Capture {
            site: current,
            child,
            ..
        } = &mut event
        {
            if let Some(site) = site {
                *current = site;
                target = *child;
                self.set_ev(id, event);
            } else {
                target = *child;
            }
        }
        let mut event = self.ev(target);
        if let (Some(expr), Event::Token { expr: current, .. }) = (expr, &mut event) {
            *current = expr;
            self.set_ev(target, event);
        }
    }
    pub fn take_stack(&mut self) -> Vec<(EventId, bool)> {
        let mut stack = self.stacks.pop().unwrap_or_default();
        stack.clear();
        stack
    }
    pub fn give_stack(&mut self, stack: Vec<(EventId, bool)>) {
        self.stacks.push(stack);
    }
    /// rule の直下 capture 出現（完了順）を出現収集が並べた区間から写す。
    /// 以前は rule ごとに局所 event を再走査していた（`collect_captures`）。同じ木を 2 度
    /// 下るのをやめ、出現収集の 1 回の走査で rule ごとに切り出しておく。
    pub fn rule_captures(&mut self, caps: (u32, u32), out: &mut Vec<Cap>) {
        let (offset, len) = (caps.0 as usize, caps.1 as usize);
        out.extend_from_slice(&self.caps_flat[offset..offset + len]);
    }
    pub fn take_captures(&mut self) -> Vec<Cap> {
        let mut v = self.captures_pool.pop().unwrap_or_default();
        v.clear();
        v
    }
    pub fn give_captures(&mut self, v: Vec<Cap>) {
        self.captures_pool.push(v);
    }
    pub fn take_values(&mut self) -> Vec<u32> {
        let mut v = self.values_pool.pop().unwrap_or_default();
        v.clear();
        v
    }
    pub fn give_values(&mut self, v: Vec<u32>) {
        self.values_pool.push(v);
    }
    /// text 変換の field 値（recovery で直接置換された出現は除く）。
    pub fn text_values(&mut self, caps: &[Cap], sites: &[usize], out: &mut Vec<u32>) {
        for &(site, span, child) in caps {
            if sites.contains(&site) && !self.directly_recovered(child) {
                let text = self.semantic_text(child, span);
                out.push(text);
            }
        }
    }
    pub fn text_nodes(&mut self, caps: &[Cap], sites: &[usize], out: &mut Vec<u32>) {
        self.text_values(caps, sites, out);
    }
    /// node 変換の field 値。出現ごとに子の値を構築し、値が無く空でない出現は字句 Text にする。
    /// `fallback` は leaf 型（Text を leaf node に昇格する recipe）。
    pub fn node_values(
        &mut self,
        caps: &[Cap],
        sites: &[usize],
        fallback: Option<usize>,
        out: &mut Vec<u32>,
    ) -> Result<(), String> {
        for &(site, span, child) in caps {
            if !sites.contains(&site) {
                continue;
            }
            let start = out.len();
            self.build_values_into(child, out)?;
            if out.len() == start
                && span[0] != span[1]
                && !self.has_recovery(child)
                && !self.has_value_group(child)
            {
                let text = self.semantic_text(child, span);
                out.push(text);
            }
            if let Some(ty) = fallback {
                let mut i = start;
                while i < out.len() {
                    if self.tree.kind(out[i]) == super::ast::tree::KIND_TEXT {
                        out[i] = self.leaf(ty, span, out[i])?;
                    }
                    i += 1;
                }
            }
        }
        Ok(())
    }
    /// field の出現のどれかが recovery を含む（値が欠けた node は作らず省略する）。
    pub fn missing_field(&mut self, caps: &[Cap], sites: &[usize]) -> bool {
        if !HAS_RECOVERY {
            return false;
        }
        for &(site, _, child) in caps {
            if sites.contains(&site) && self.has_recovery(child) {
                return true;
            }
        }
        false
    }
    pub fn selected_extent(&mut self, root: EventId, fallback: Span) -> Span {
        let mut stack = self.take_stack();
        stack.push((root, false));
        let mut span = None;
        while let Some((id, _)) = stack.pop() {
            match self.ev(id) {
                Event::Join(a, b) => {
                    stack.push((b, false));
                    stack.push((a, false));
                }
                Event::Capture { child, .. } | Event::Values { child, .. } => {
                    stack.push((child, false))
                }
                Event::Rule { span: child, .. } | Event::Token { span: child, .. } => {
                    span = Some(span.map_or(child, |s: Span| [s[0], child[1]]));
                }
                Event::Empty => {}
                Event::Recovery { span: child, .. } => {
                    span = Some(span.map_or(child, |s: Span| [s[0], child[1]]));
                }
            }
        }
        self.give_stack(stack);
        span.unwrap_or(fallback)
    }
    pub fn significant_end(&self, root: EventId) -> Option<usize> {
        let mut stack = vec![root];
        while let Some(id) = stack.pop() {
            match self.ev(id) {
                Event::Join(a, b) => {
                    stack.push(a);
                    stack.push(b);
                }
                Event::Rule { child, .. }
                | Event::Capture { child, .. }
                | Event::Values { child, .. } => stack.push(child),
                Event::Token { span, .. } => return Some(span[1]),
                _ => {}
            }
        }
        None
    }
    pub fn has_recovery(&mut self, root: EventId) -> bool {
        // recovery を持たない文法では Recovery event が無い（走査を省く）。
        if !HAS_RECOVERY {
            return false;
        }
        let mut stack = self.take_stack();
        stack.push((root, false));
        let mut found = false;
        while let Some((id, _)) = stack.pop() {
            match self.ev(id) {
                Event::Recovery { .. } => {
                    found = true;
                    break;
                }
                Event::Join(a, b) => {
                    stack.push((b, false));
                    stack.push((a, false));
                }
                Event::Capture { child, .. }
                | Event::Rule { child, .. }
                | Event::Values { child, .. } => stack.push((child, false)),
                _ => {}
            }
        }
        self.give_stack(stack);
        found
    }
    pub fn directly_recovered(&self, mut root: EventId) -> bool {
        if !HAS_RECOVERY {
            return false;
        }
        loop {
            match self.ev(root) {
                Event::Recovery { .. } => return true,
                Event::Capture { child, .. } | Event::Values { child, .. } => root = child,
                _ => return false,
            }
        }
    }
    /// design v1 §3.10。mode / 同期集合 / `consume_sync` / `no_sync` は IR が正本で、
    /// ここでは推測しない。SYNC だけが同期文字列を消費し、AUTO の同期文字列（FOLLOW）は
    /// 外側が待っている字句なので残す。SKIP は同期文字列の手前まで進む別規則。
    #[allow(clippy::too_many_arguments)]
    pub fn recover<const MATCH: bool>(
        &mut self,
        state: State,
        failed: Step,
        rule: usize,
        mode: &'static str,
        patterns: &[&str],
        consume_sync: bool,
        no_sync: &'static str,
    ) -> Step {
        if self.depth_failure.is_some() || self.resource_failure.is_some() || self.diag.exhausted {
            return failed;
        }
        let start = state.position::<MATCH>();
        let one = |s: &Self, at: usize| s.input.cp_at(at).map_or(0, |(_, n)| n);
        let eof = self.input.bytes().len();
        // §3.10: AUTO の FOLLOW が空なら既定の `;`。既定文字列は FOLLOW ではないので消費する。
        let fallback = mode == "AUTO" && patterns.is_empty();
        let patterns = if fallback { &[";"][..] } else { patterns };
        let consume_sync = consume_sync || fallback;
        let mut sync_span = None;
        let mut at = start;
        while !patterns.is_empty() {
            let Some((_, n)) = self.input.cp_at(at) else {
                break;
            };
            if let Some(sync) = patterns
                .iter()
                .find(|s| !s.is_empty() && self.input.starts_with(at, s))
            {
                sync_span = Some([at, at + sync.len()]);
                break;
            }
            at += n;
        }
        let end = if mode == "SKIP" {
            match sync_span {
                _ if self.input.cp_at(start).is_none() => return failed,
                None if patterns.is_empty() => start + one(self, start),
                None => eof,
                Some([found, _]) if found == start => start + one(self, start),
                Some([found, _]) => found,
            }
        } else {
            match sync_span {
                Some([found, stop]) => {
                    if consume_sync {
                        stop
                    } else {
                        found
                    }
                }
                None => match no_sync {
                    "consumeToEof" => eof,
                    "succeed" => start,
                    _ => return failed,
                },
            }
        };
        if end <= start {
            return failed;
        }
        let mut child = state.begin();
        child.advance::<MATCH>(end - start);
        let diag = self.diag_rule(rule, failed.diag);
        // D-027: 公開候補は表示語彙で、回復した規則の frame に属するものだけ
        // （生成側が回復つき規則の本体を `display_enter` / `display_leave` で囲う）。
        let hints = u32::try_from(self.recovery_hints.len()).unwrap_or(u32::MAX);
        self.recovery_hints
            .push(if DIAG { self.display.local_summary() } else { None });
        let detail = u32::try_from(self.recovery_events.len()).unwrap_or(u32::MAX);
        self.recovery_events.push(RecoveryEvent {
            rule,
            mode,
            sync_span,
            diag,
            hints,
        });
        let events = self.event(Event::Recovery {
            span: [start, end],
            detail,
        });
        Step {
            ok: true,
            state: state.commit(child),
            events,
            diag: failed.diag,
        }
    }
    pub fn has_value_group(&mut self, root: EventId) -> bool {
        let mut stack = self.take_stack();
        stack.push((root, false));
        let mut found = false;
        while let Some((id, _)) = stack.pop() {
            match self.ev(id) {
                Event::Values { .. } => {
                    found = true;
                    break;
                }
                Event::Join(a, b) => {
                    stack.push((b, false));
                    stack.push((a, false));
                }
                Event::Rule { child, .. } | Event::Capture { child, .. } => {
                    stack.push((child, false))
                }
                _ => {}
            }
        }
        self.give_stack(stack);
        found
    }
    pub fn has_token_text(&self, mut root: EventId) -> bool {
        loop {
            match self.ev(root) {
                Event::Capture { child, .. }
                | Event::Rule { child, .. }
                | Event::Values { child, .. } => root = child,
                Event::Token { text_span, .. } => return text_span.is_some(),
                _ => return false,
            }
        }
    }
    pub fn text_extent(&self, mut root: EventId, fallback: Span) -> Span {
        loop {
            match self.ev(root) {
                Event::Capture { child, .. }
                | Event::Rule { child, .. }
                | Event::Values { child, .. } => root = child,
                Event::Token {
                    text_span: Some(span),
                    ..
                } => return span,
                _ => return fallback,
            }
        }
    }
    fn text_content_extent(&self, mut root: EventId, fallback: Span) -> Span {
        loop {
            match self.ev(root) {
                Event::Capture { child, .. } | Event::Rule { child, .. } | Event::Values { child, .. } => root = child,
                Event::Token { content_span, text_span, .. } => return content_span.or(text_span).unwrap_or(fallback),
                _ => return fallback,
            }
        }
    }
    /// capture の値となる Text 節点を作る（D-077: 値は複製せず入力の byte 範囲で持つ）。
    pub fn semantic_text(&mut self, root: EventId, span: Span) -> u32 {
        // Token まで下って text_span / content_span を 1 回の走査で読む
        // （text_extent / text_content_extent / has_token_text と同じ規則）。
        let mut node = root;
        let token = loop {
            match self.ev(node) {
                Event::Capture { child, .. } | Event::Rule { child, .. } | Event::Values { child, .. } => node = child,
                Event::Token { text_span, content_span, .. } => break Some((text_span, content_span)),
                _ => break None,
            }
        };
        let (extent, content, token_text) = match token {
            Some((Some(text), content)) => (text, content.unwrap_or(text), true),
            Some((None, content)) => (span, content.unwrap_or(span), false),
            None => (span, span, false),
        };
        let value = if token_text { content } else { self.trimmed_range(content) };
        let extent = self.span(extent);
        self.tree.text(extent, value)
    }
    pub fn take_scope(&mut self) -> Vec<(usize, Span)> {
        let mut v = self.scope_pool.pop().unwrap_or_default();
        v.clear();
        v
    }
    pub fn give_scope(&mut self, v: Vec<(usize, Span)>) {
        self.scope_pool.push(v);
    }
    /// scope イベントの capture 出現（trim 済み span）を完了順に集める。
    pub fn scope_captures(&mut self, root: EventId, sites: &[usize], out: &mut Vec<(usize, Span)>) {
        let mut stack = self.take_stack();
        stack.push((root, false));
        while let Some((id, done)) = stack.pop() {
            match self.ev(id) {
                Event::Join(a, b) => {
                    stack.push((b, false));
                    stack.push((a, false));
                }
                Event::Values { child, .. } => stack.push((child, false)),
                Event::Capture {
                    site, span, child, ..
                } => {
                    if done {
                        if sites.contains(&site) {
                            let text = &self.text[span[0]..span[1]];
                            let leading =
                                text.len() - text.trim_start_matches(|c: char| c <= '\u{20}').len();
                            let len = text.trim_matches(|c: char| c <= '\u{20}').len();
                            out.push((site, [span[0] + leading, span[0] + leading + len]));
                        }
                    } else {
                        stack.push((id, true));
                        stack.push((child, false));
                    }
                }
                _ => {}
            }
        }
        self.give_stack(stack);
    }
}
/// 統合 DFA の bitset 語数（literal 9 個）。0 なら cache を作らない。
const LEX_WORDS:usize=1;
/// 診断を記録しない読み飛ばし cache を持つ trivia policy 数（区切り集合で正規化済み）。
const TRIVIA_POLICIES:usize=1;
/// 全 literal 終端を 1 本にまとめた DFA（trie）。位置 `pos` から 1 回走らせ、
/// 一致した literal の id を `out` の bitset に立てる。長さは id ごとに静的。
fn lex_fill(bytes:&[u8],pos:usize,out:&mut [u64]) {
let mut p=pos;let mut state=0usize;
loop {
let Some(&c)=bytes.get(p) else {return;};
state=match state {
0=>match c {
9=>{out[0]|=1;1},
10=>{out[0]|=2;2},
13=>{out[0]|=4;3},
32=>{out[0]|=16;5},
35=>{out[0]|=32;6},
45=>7,
46=>{out[0]|=128;24},
58=>{out[0]|=256;25},
_=>return,
},
3=>match c {
10=>{out[0]|=8;4},
_=>return,
},
7=>match c {
45=>8,
_=>return,
},
8=>match c {
45=>9,
_=>return,
},
9=>match c {
69=>10,
_=>return,
},
10=>match c {
78=>11,
_=>return,
},
11=>match c {
68=>12,
_=>return,
},
12=>match c {
95=>13,
_=>return,
},
13=>match c {
79=>14,
_=>return,
},
14=>match c {
70=>15,
_=>return,
},
15=>match c {
95=>16,
_=>return,
},
16=>match c {
80=>17,
_=>return,
},
17=>match c {
65=>18,
_=>return,
},
18=>match c {
82=>19,
_=>return,
},
19=>match c {
84=>20,
_=>return,
},
20=>match c {
45=>21,
_=>return,
},
21=>match c {
45=>22,
_=>return,
},
22=>match c {
45=>{out[0]|=64;23},
_=>return,
},
_=>return,
};
p+=1;
}
}

use super::ast::*;
const HAS_RECOVERY:bool=false;
const SCOPE_MODES:&[&str]=&[];
const HAS_SCOPE:bool=false;
const RULES:&[&str]=&["FormulaInfo::Document", "FormulaInfo::Block", "FormulaInfo::Filler", "FormulaInfo::CommentLine", "FormulaInfo::BlankLine", "FormulaInfo::SpaceChar", "FormulaInfo::Entry", "FormulaInfo::Value", "FormulaInfo::ContinuationLine", "FormulaInfo::PlainLine", "FormulaInfo::AtLineEnd", "FormulaInfo::EndOfPart", "FormulaInfo::LineBreak"];
const EXPRESSIONS:&[&str]=&["expr:grammar/formula-info.ubnf:2613:2634:body/seq", "expr:grammar/formula-info.ubnf:2613:2630:body/0/repeat", "expr:grammar/formula-info.ubnf:2615:2620:body/0/0/ruleRef", "expr:grammar/formula-info.ubnf:2631:2634:body/1/tokenRef", "expr:grammar/formula-info.ubnf:3112:3255:body/seq", "expr:grammar/formula-info.ubnf:3112:3218:body/0/group", "expr:grammar/formula-info.ubnf:3114:3216:body/0/0/choice", "expr:grammar/formula-info.ubnf:3114:3168:body/0/0/0/seq", "expr:grammar/formula-info.ubnf:3114:3120:body/0/0/0/0/ruleRef", "expr:grammar/formula-info.ubnf:3130:3149:body/0/0/0/1/repeat", "expr:grammar/formula-info.ubnf:3132:3138:body/0/0/0/1/0/ruleRef", "expr:grammar/formula-info.ubnf:3150:3168:body/0/0/0/2/repeat", "expr:grammar/formula-info.ubnf:3152:3157:body/0/0/0/2/0/ruleRef", "expr:grammar/formula-info.ubnf:3183:3216:body/0/0/1/seq", "expr:grammar/formula-info.ubnf:3183:3188:body/0/0/1/0/ruleRef", "expr:grammar/formula-info.ubnf:3198:3216:body/0/0/1/1/repeat", "expr:grammar/formula-info.ubnf:3200:3205:body/0/0/1/1/0/ruleRef", "expr:grammar/formula-info.ubnf:3231:3255:body/1/group", "expr:grammar/formula-info.ubnf:3233:3253:body/1/0/choice", "expr:grammar/formula-info.ubnf:3233:3242:body/1/0/0/ruleRef", "expr:grammar/formula-info.ubnf:3250:3253:body/1/0/1/tokenRef", "expr:grammar/formula-info.ubnf:3340:3363:body/choice", "expr:grammar/formula-info.ubnf:3340:3351:body/0/ruleRef", "expr:grammar/formula-info.ubnf:3354:3363:body/1/ruleRef", "expr:grammar/formula-info.ubnf:3530:3577:body/seq", "expr:grammar/formula-info.ubnf:3530:3551:body/0/group", "expr:grammar/formula-info.ubnf:3532:3549:body/0/0/seq", "expr:grammar/formula-info.ubnf:3532:3535:body/0/0/0/literal", "expr:grammar/formula-info.ubnf:3536:3549:body/0/0/1/repeat", "expr:grammar/formula-info.ubnf:3538:3547:body/0/0/1/0/tokenRef", "expr:grammar/formula-info.ubnf:3558:3577:body/1/group", "expr:grammar/formula-info.ubnf:3560:3575:body/1/0/choice", "expr:grammar/formula-info.ubnf:3560:3569:body/1/0/0/ruleRef", "expr:grammar/formula-info.ubnf:3572:3575:body/1/0/1/tokenRef", "expr:grammar/formula-info.ubnf:3775:3862:body/choice", "expr:grammar/formula-info.ubnf:3775:3808:body/0/seq", "expr:grammar/formula-info.ubnf:3775:3792:body/0/0/group", "expr:grammar/formula-info.ubnf:3777:3790:body/0/0/0/seq", "expr:grammar/formula-info.ubnf:3777:3790:body/0/0/0/0/repeat", "expr:grammar/formula-info.ubnf:3779:3788:body/0/0/0/0/0/ruleRef", "expr:grammar/formula-info.ubnf:3799:3808:body/0/1/ruleRef", "expr:grammar/formula-info.ubnf:3825:3862:body/1/seq", "expr:grammar/formula-info.ubnf:3825:3852:body/1/0/group", "expr:grammar/formula-info.ubnf:3827:3850:body/1/0/0/seq", "expr:grammar/formula-info.ubnf:3827:3836:body/1/0/0/0/ruleRef", "expr:grammar/formula-info.ubnf:3837:3850:body/1/0/0/1/repeat", "expr:grammar/formula-info.ubnf:3839:3848:body/1/0/0/1/0/ruleRef", "expr:grammar/formula-info.ubnf:3859:3862:body/1/1/tokenRef", "expr:grammar/formula-info.ubnf:3882:3900:body/choice", "expr:grammar/formula-info.ubnf:3882:3885:body/0/literal", "expr:grammar/formula-info.ubnf:3888:3892:body/1/literal", "expr:grammar/formula-info.ubnf:3895:3900:body/2/tokenRef", "expr:grammar/formula-info.ubnf:4226:4264:body/seq", "expr:grammar/formula-info.ubnf:4226:4249:body/0/group", "expr:grammar/formula-info.ubnf:4228:4247:body/0/0/seq", "expr:grammar/formula-info.ubnf:4228:4233:body/0/0/0/tokenRef", "expr:grammar/formula-info.ubnf:4234:4247:body/0/0/1/repeat", "expr:grammar/formula-info.ubnf:4236:4245:body/0/0/1/0/seq", "expr:grammar/formula-info.ubnf:4236:4239:body/0/0/1/0/0/literal", "expr:grammar/formula-info.ubnf:4240:4245:body/0/0/1/0/1/tokenRef", "expr:grammar/formula-info.ubnf:4255:4258:body/1/literal", "expr:grammar/formula-info.ubnf:4259:4264:body/2/ruleRef", "expr:grammar/formula-info.ubnf:4685:4747:body/seq", "expr:grammar/formula-info.ubnf:4685:4747:body/0/group", "expr:grammar/formula-info.ubnf:4687:4745:body/0/0/seq", "expr:grammar/formula-info.ubnf:4687:4700:body/0/0/0/repeat", "expr:grammar/formula-info.ubnf:4689:4698:body/0/0/0/0/tokenRef", "expr:grammar/formula-info.ubnf:4701:4731:body/0/0/1/repeat", "expr:grammar/formula-info.ubnf:4703:4729:body/0/0/1/0/seq", "expr:grammar/formula-info.ubnf:4703:4712:body/0/0/1/0/0/ruleRef", "expr:grammar/formula-info.ubnf:4713:4729:body/0/0/1/0/1/ruleRef", "expr:grammar/formula-info.ubnf:4732:4745:body/0/0/2/optional", "expr:grammar/formula-info.ubnf:4734:4743:body/0/0/2/0/ruleRef", "expr:grammar/formula-info.ubnf:4921:5005:body/choice", "expr:grammar/formula-info.ubnf:4921:4964:body/0/seq", "expr:grammar/formula-info.ubnf:4921:4940:body/0/0/literal", "expr:grammar/formula-info.ubnf:4941:4950:body/0/1/tokenRef", "expr:grammar/formula-info.ubnf:4951:4964:body/0/2/repeat", "expr:grammar/formula-info.ubnf:4953:4962:body/0/2/0/tokenRef", "expr:grammar/formula-info.ubnf:4988:5005:body/1/seq", "expr:grammar/formula-info.ubnf:4988:4995:body/1/0/tokenRef", "expr:grammar/formula-info.ubnf:4996:5005:body/1/1/ruleRef", "expr:grammar/formula-info.ubnf:5025:5152:body/choice", "expr:grammar/formula-info.ubnf:5025:5050:body/0/seq", "expr:grammar/formula-info.ubnf:5025:5036:body/0/0/tokenRef", "expr:grammar/formula-info.ubnf:5037:5050:body/0/1/repeat", "expr:grammar/formula-info.ubnf:5039:5048:body/0/1/0/tokenRef", "expr:grammar/formula-info.ubnf:5067:5126:body/1/seq", "expr:grammar/formula-info.ubnf:5067:5072:body/1/0/tokenRef", "expr:grammar/formula-info.ubnf:5073:5086:body/1/1/repeat", "expr:grammar/formula-info.ubnf:5075:5084:body/1/1/0/seq", "expr:grammar/formula-info.ubnf:5075:5078:body/1/1/0/0/literal", "expr:grammar/formula-info.ubnf:5079:5084:body/1/1/0/1/tokenRef", "expr:grammar/formula-info.ubnf:5087:5126:body/1/2/group", "expr:grammar/formula-info.ubnf:5089:5124:body/1/2/0/choice", "expr:grammar/formula-info.ubnf:5089:5112:body/1/2/0/0/seq", "expr:grammar/formula-info.ubnf:5089:5098:body/1/2/0/0/0/tokenRef", "expr:grammar/formula-info.ubnf:5099:5112:body/1/2/0/0/1/repeat", "expr:grammar/formula-info.ubnf:5101:5110:body/1/2/0/0/1/0/tokenRef", "expr:grammar/formula-info.ubnf:5115:5124:body/1/2/0/1/ruleRef", "expr:grammar/formula-info.ubnf:5143:5152:body/2/ruleRef", "expr:grammar/formula-info.ubnf:5172:5191:body/choice", "expr:grammar/formula-info.ubnf:5172:5177:body/0/tokenRef", "expr:grammar/formula-info.ubnf:5180:5185:body/1/tokenRef", "expr:grammar/formula-info.ubnf:5188:5191:body/2/tokenRef", "expr:grammar/formula-info.ubnf:5375:5420:body/seq", "expr:grammar/formula-info.ubnf:5375:5394:body/0/literal", "expr:grammar/formula-info.ubnf:5401:5420:body/1/group", "expr:grammar/formula-info.ubnf:5403:5418:body/1/0/choice", "expr:grammar/formula-info.ubnf:5403:5412:body/1/0/0/ruleRef", "expr:grammar/formula-info.ubnf:5415:5418:body/1/0/1/tokenRef", "expr:grammar/formula-info.ubnf:5440:5460:body/choice", "expr:grammar/formula-info.ubnf:5440:5446:body/0/literal", "expr:grammar/formula-info.ubnf:5449:5453:body/1/literal", "expr:grammar/formula-info.ubnf:5456:5460:body/2/literal"];
const BODIES:&[&str]=&["expr:grammar/formula-info.ubnf:2613:2634:body/seq", "expr:grammar/formula-info.ubnf:3112:3255:body/seq", "expr:grammar/formula-info.ubnf:3340:3363:body/choice", "expr:grammar/formula-info.ubnf:3530:3577:body/seq", "expr:grammar/formula-info.ubnf:3775:3862:body/choice", "expr:grammar/formula-info.ubnf:3882:3900:body/choice", "expr:grammar/formula-info.ubnf:4226:4264:body/seq", "expr:grammar/formula-info.ubnf:4685:4747:body/seq", "expr:grammar/formula-info.ubnf:4921:5005:body/choice", "expr:grammar/formula-info.ubnf:5025:5152:body/choice", "expr:grammar/formula-info.ubnf:5172:5191:body/choice", "expr:grammar/formula-info.ubnf:5375:5420:body/seq", "expr:grammar/formula-info.ubnf:5440:5460:body/choice"];
const RULE_NODE:&[bool]=&[true, true, false, true, true, false, true, true, false, false, false, true, false];
const RULE_SLOTS:&[u32]=&[2, 5, 0, 1, 1, 0, 2, 1, 0, 0, 0, 1, 0];
const SITES:&[(&str,&str)]=&[("expr:grammar/formula-info.ubnf:2615:2620:body/0/0/ruleRef/capture/0", "blocks"), ("expr:grammar/formula-info.ubnf:3114:3120:body/0/0/0/0/ruleRef/capture/0", "leading"), ("expr:grammar/formula-info.ubnf:3132:3138:body/0/0/0/1/0/ruleRef/capture/0", "leading"), ("expr:grammar/formula-info.ubnf:3152:3157:body/0/0/0/2/0/ruleRef/capture/0", "entries"), ("expr:grammar/formula-info.ubnf:3183:3188:body/0/0/1/0/ruleRef/capture/0", "entries"), ("expr:grammar/formula-info.ubnf:3200:3205:body/0/0/1/1/0/ruleRef/capture/0", "entries"), ("expr:grammar/formula-info.ubnf:3233:3242:body/1/0/0/ruleRef/capture/0", "end"), ("expr:grammar/formula-info.ubnf:3530:3551:body/0/group/capture/0", "text"), ("expr:grammar/formula-info.ubnf:3775:3792:body/0/0/group/capture/0", "text"), ("expr:grammar/formula-info.ubnf:3825:3852:body/1/0/group/capture/0", "text"), ("expr:grammar/formula-info.ubnf:4226:4249:body/0/group/capture/0", "key"), ("expr:grammar/formula-info.ubnf:4259:4264:body/2/ruleRef/capture/0", "value"), ("expr:grammar/formula-info.ubnf:4685:4747:body/0/group/capture/0", "text"), ("expr:grammar/formula-info.ubnf:5375:5394:body/0/literal/capture/0", "mark")];
impl<const DIAG: bool> Session<'_, DIAG> {
fn build_values(&mut self,root:EventId)->Result<Vec<u32>,String> {let mut out=Vec::new();self.build_values_into(root,&mut out)?;Ok(out)}
#[inline(never)] fn build_values_into(&mut self,root:EventId,out:&mut Vec<u32>)->Result<(),String> {let frame=0u8;let address=std::ptr::addr_of!(frame) as usize;let anchor=*self.mapping_anchor.get_or_insert(address);if self.mapping_depth>=self.options.limits.mapping_depth || anchor.abs_diff(address)>256*1024 {return Err("maximum mapping depth exceeded".into());}self.mapping_depth+=1;let result=self.build_values_inner(root,out);self.mapping_depth-=1;result}
fn build_values_inner(&mut self,mut root:EventId,out:&mut Vec<u32>)->Result<(),String> {
// 大半の呼出しは Join を含まない 1 本の枝（P4 実測: complex-x64 で 10,883 回すべてが 1 event）。
// その場合は pool から stack を借りずに降りる。
loop {match self.ev(root) {
Event::Capture{child,..}=>root=child,
Event::Values{child,span,wrap}=>{let start=out.len();self.build_values_into(child,out)?;if wrap && ((out.len()==start+1 && self.tree.kind(out[start])==tree::KIND_TEXT) || (out.len()==start && span[0]!=span[1] && !self.has_recovery(child) && !self.has_value_group(child))) {let text=self.semantic_text(child,span);out.truncate(start);out.push(text);}return Ok(());},
Event::Rule{rule,span,child,caps}=>return self.build_rule(rule,caps,span,child,out),
Event::Join(..)=>break,
_=>return Ok(()),}}
let mut stack=self.take_stack();stack.push((root,false));while let Some((id,_))=stack.pop() {match self.ev(id) {Event::Join(a,b)=>{stack.push((b,false));stack.push((a,false));},Event::Capture{child,..}=>stack.push((child,false)),Event::Values{child,span,wrap}=>{let start=out.len();self.build_values_into(child,out)?;if wrap && ((out.len()==start+1 && self.tree.kind(out[start])==tree::KIND_TEXT) || (out.len()==start && span[0]!=span[1] && !self.has_recovery(child) && !self.has_value_group(child))) {let text=self.semantic_text(child,span);out.truncate(start);out.push(text);}},Event::Rule{rule,span,child,caps}=>self.build_rule(rule,caps,span,child,out)?,_=>{}}}self.give_stack(stack);Ok(())}
fn leaf(&mut self,ty:usize,span:Span,text:u32)->Result<u32,String> {let span=self.span(span);self.tree.set_extent(text,span);let _=(span,text);Err(format!("unknown leaf type {ty}"))}
fn build_rule(&mut self,rule:usize,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);match rule {
0=>self.map_rule_0(caps,span,child,out),
1=>self.map_rule_1(caps,span,child,out),
2=>self.map_rule_2(caps,span,child,out),
3=>self.map_rule_3(caps,span,child,out),
4=>self.map_rule_4(caps,span,child,out),
5=>self.map_rule_5(caps,span,child,out),
6=>self.map_rule_6(caps,span,child,out),
7=>self.map_rule_7(caps,span,child,out),
8=>self.map_rule_8(caps,span,child,out),
9=>self.map_rule_9(caps,span,child,out),
10=>self.map_rule_10(caps,span,child,out),
11=>self.map_rule_11(caps,span,child,out),
12=>self.map_rule_12(caps,span,child,out),
_=>Err(format!("unknown rule {rule}"))}}
fn map_rule_0(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut f0=self.take_values();self.node_values(&caps,&[0],None,&mut f0)?;
if !f0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_FormulaInfoAST_2e_FormulaInfoBlock}) {return Err("blocks: mapped value type mismatch".into());}
let f0:Vec<u32>=f0;
self.give_captures(caps);let span=self.span(span);let l0=self.tree.list(&f0);self.give_values(f0);let node=self.tree.record(tree::K_g_FormulaInfoAST_2e_FormulaInfoDocument,0,span,&[l0[0],l0[1]]);out.push(node);Ok(())}}
fn map_rule_1(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut f0=self.take_values();self.node_values(&caps,&[1, 2],None,&mut f0)?;
if !f0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_FormulaInfoAST_2e_BlankLine || k==tree::K_g_FormulaInfoAST_2e_CommentLine}) {return Err("leading: mapped value type mismatch".into());}
let f0:Vec<u32>=f0;
let mut f1=self.take_values();self.node_values(&caps,&[3, 4, 5],None,&mut f1)?;
if !f1.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_FormulaInfoAST_2e_FormulaInfoEntry}) {return Err("entries: mapped value type mismatch".into());}
let f1:Vec<u32>=f1;
let mut v2=self.take_values();self.node_values(&caps,&[6],None,&mut v2)?;
if !v2.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_FormulaInfoAST_2e_EndOfPart}) {return Err("end: mapped value type mismatch".into());}
if v2.len()>1 {return Err("end requires at most one node".into());}let f2=v2.pop();self.give_values(v2);
let f2:Option<u32>=f2;
self.give_captures(caps);let span=self.span(span);let l0=self.tree.list(&f0);self.give_values(f0);let l1=self.tree.list(&f1);self.give_values(f1);let node=self.tree.record(tree::K_g_FormulaInfoAST_2e_FormulaInfoBlock,1,span,&[l0[0],l0[1],l1[0],l1[1],f2.unwrap_or(tree::NONE)]);out.push(node);Ok(())}}
fn map_rule_2(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_3(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut t0=self.take_values();self.text_values(&caps,&[7],&mut t0);if t0.is_empty() && self.missing_field(&caps,&[7]) {return Ok(());}
if t0.len()!=1 {return Err("text requires one value".into());}let f0=t0.pop().unwrap();self.give_values(t0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_FormulaInfoAST_2e_CommentLine,3,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_4(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut t0=self.take_values();self.text_values(&caps,&[8, 9],&mut t0);if t0.is_empty() && self.missing_field(&caps,&[8, 9]) {return Ok(());}
if t0.len()!=1 {return Err("text requires one value".into());}let f0=t0.pop().unwrap();self.give_values(t0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_FormulaInfoAST_2e_BlankLine,4,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_5(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_6(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut t0=self.take_values();self.text_values(&caps,&[10],&mut t0);if t0.is_empty() && self.missing_field(&caps,&[10]) {return Ok(());}
if t0.len()!=1 {return Err("key requires one value".into());}let f0=t0.pop().unwrap();self.give_values(t0);
let f0:u32=f0;
let mut v1=self.take_values();self.node_values(&caps,&[11],None,&mut v1)?;
if !v1.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_FormulaInfoAST_2e_FormulaInfoValue}) {return Err("value: mapped value type mismatch".into());}
if v1.is_empty() && self.missing_field(&caps,&[11]) {return Ok(());}
if v1.len()!=1 {return Err("value requires one node".into());}let f1=v1.pop().unwrap();self.give_values(v1);
let f1:u32=f1;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_FormulaInfoAST_2e_FormulaInfoEntry,6,span,&[f0,f1]);out.push(node);Ok(())}}
fn map_rule_7(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut t0=self.take_values();self.text_values(&caps,&[12],&mut t0);if t0.is_empty() && self.missing_field(&caps,&[12]) {return Ok(());}
if t0.len()!=1 {return Err("text requires one value".into());}let f0=t0.pop().unwrap();self.give_values(t0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_FormulaInfoAST_2e_FormulaInfoValue,7,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_8(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_9(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_10(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_11(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut t0=self.take_values();self.text_values(&caps,&[13],&mut t0);if t0.is_empty() && self.missing_field(&caps,&[13]) {return Ok(());}
if t0.len()!=1 {return Err("mark requires one value".into());}let f0=t0.pop().unwrap();self.give_values(t0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_FormulaInfoAST_2e_EndOfPart,11,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_12(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
/// 出現（capture / rule / token）の収集と、AST 構築が rule ごとに読む直下 capture 表を
/// 1 回の走査で作る。以前は出現収集の後に AST 構築が rule ごとに `collect_captures` で
/// 同じ木をもう一度下っていた。
///
/// 直下 capture 表の作り方: capture は完了順に `pending` へ積む。走査は深さ優先なので
/// rule R に入った時点の `pending` の長さ `start` を覚えておけば、R が完了した時点の
/// `pending[start..]` はちょうど「R の直下 capture（間に別の rule を挟まないもの）を完了順に
/// 並べたもの」になる（内側の rule は自分の完了時に自分の分を抜き取って `pending` を
/// `start` まで戻すため、R の分だけが連続して残る）。これを `caps_flat` へ移して
/// `(開始, 個数)` を rule の event id に記録する。公開する出現（`captures` / `lexical` /
/// `tokens`）の並びと番号付けは従来どおり。
fn occurrences(
    &mut self,
    root: EventId,
) -> (Vec<Capture>, Vec<LexicalOccurrence>, Vec<LexicalOccurrence>) {
    // 出現数は event 数で上から見積もる（capture / rule は event の一部）。伸長の複製を避ける。
    // P4 実測は capture も rule も event の 1/8 前後（complex 155+177 / 1,308、
    // complex-x64 9,731+10,824 / 80,246）。1/4 は 2 倍以上の過大確保で、x64 では
    // `Capture` 72 byte × 20,061 を確保して半分しか使っていなかった。
    let estimate = self.arena.len() / 6 + 8;
    let build_ast = self.options.wants_ast();
    let want_lexical = self.options.lexical;
    // 公開する出現表は opt-in（D-035）。見積りで確保するのは要求されたときだけ
    // （AST だけのときに JSON 1 MB で 260,000 × 72 byte を確保して捨てていた）。
    let want_occurrences = self.options.occurrences || want_lexical;
    let mut captures = Vec::with_capacity(if want_occurrences { estimate } else { 0 });
    let mut lexical = Vec::new();
    let mut tokens = Vec::new();
    // AST の直下 capture 表（`pending` / `caps_flat`）は `build_ast` のままで、出現表の flag には
    // 依らない。走査の骨組み（rule ごとの開始位置）はどちらかが要るときに積む。
    let want_intervals = build_ast || want_occurrences;
    // 未完了の rule に属する capture の番号（完了順）。rule の完了時にその rule の
    // 完了順を直接書き込むので、所属表と後段の付け直しの走査は要らない。
    let mut pending_ids: Vec<u32> = Vec::with_capacity(if want_occurrences { estimate } else { 0 });
    // rule ごとの開始位置。`.0` は直下 capture 表（AST 用）、`.1` は公開出現の番号（出現表用）。
    let mut rule_starts: Vec<(u32, u32)> = Vec::new();
    let mut rule_occurrences: u32 = 0;
    let mut rule_order: u32 = 0;
    // 直下 capture 表（AST を作るときだけ）。event id で索く表は伸長だけする（本文の注記参照）。
    let mut caps_flat = std::mem::take(&mut self.caps_flat);
    let mut pending = std::mem::take(&mut self.caps_pending);
    if want_intervals {
        rule_starts.reserve(64);
    }
    if build_ast {
        caps_flat.clear();
        caps_flat.reserve(estimate);
        pending.clear();
    }
    // stack の要素は (event, 親 rule 出現, 出口か, 自身の rule 出現)。無しは u32::MAX。
    // event id も出現番号も入力 byte 数（u32 上限）で抑えられるので u32 に詰める（1 要素 16 byte）。
    const NONE: u32 = u32::MAX;
    let mut stack: Vec<(u32, u32, bool, u32)> = Vec::with_capacity(64);
    stack.push((root.0 as u32, NONE, false, NONE));
    let mut lexical_order = 0;
    // AST 木の見積り（D-077）: 完了した規則が作る record の slot 数の和。
    let mut rule_slots = 0usize;
    while let Some((id, parent, done, current)) = stack.pop() {
        let parent_index = (parent != NONE).then_some(parent as usize);
        let id = EventId(id as usize);
        match self.ev(id) {
            Event::Join(a, b) => {
                stack.push((b.0 as u32, parent, false, NONE));
                stack.push((a.0 as u32, parent, false, NONE));
            }
            Event::Values { child, .. } => stack.push((child.0 as u32, parent, false, NONE)),
            Event::Capture {
                site,
                span,
                child,
                token_extent,
            } => {
                if done {
                    if build_ast {
                        let extent = if token_extent {
                            self.selected_extent(child, span)
                        } else {
                            span
                        };
                        pending.push((site, extent, child));
                    }
                    if want_occurrences {
                        let order = captures.len();
                        pending_ids.push(order as u32);
                        captures.push(Capture {
                            rule_completion_order: 0,
                            occurrence_id: order,
                            site_id: SITES[site].0,
                            name: SITES[site].1,
                            span: self.span(span),
                            completion_order: order,
                        });
                    }
                } else {
                    stack.push((id.0 as u32, parent, true, NONE));
                    stack.push((child.0 as u32, parent, false, NONE));
                }
            }
            Event::Token {
                expr, rule, span, ..
            }
if want_lexical && expr != usize::MAX => {
                tokens.push(LexicalOccurrence {
                    occurrence_id: tokens.len(),
                    parent_occurrence_id: parent_index,
                    rule_id: RULES[rule],
                    expr_id: EXPRESSIONS[expr],
                    span: self.span(span),
                    completion_order: lexical_order,
                });
                lexical_order += 1;
            }
            Event::Rule {
                rule, span, child, ..
            } => {
                if done {
                    if want_intervals {
                        let (cap_start, id_start) = rule_starts.pop().unwrap_or((0, 0));
                        if build_ast {
                            rule_slots += RULE_SLOTS[rule] as usize;
                            let start = cap_start as usize;
                            let offset = caps_flat.len();
                            caps_flat.extend_from_slice(&pending[start..]);
                            pending.truncate(start);
                            self.set_rule_caps(
                                id,
                                (offset as u32, (caps_flat.len() - offset) as u32),
                            );
                        }
                        if want_occurrences {
                            let start = id_start as usize;
                            // この rule の直下 capture に rule 完了順を書く（後段の走査の代わり）。
                            for &order in &pending_ids[start..] {
                                captures[order as usize].rule_completion_order =
                                    rule_order as usize;
                            }
                            pending_ids.truncate(start);
                        }
                    }
                    if current != NONE {
                        let index = current as usize;
                        rule_order += 1;
                        // rule 出現は lexical のときだけ保持する（AST だけなら完了順だけ要る）。
                        if want_lexical {
                            let node: &mut LexicalOccurrence = &mut lexical[index];
                            node.completion_order = lexical_order;
                        }
                        lexical_order += 1;
                    }
                } else {
                    if want_intervals {
                        rule_starts.push((pending.len() as u32, pending_ids.len() as u32));
                    }
                    let index = if want_occurrences {
                        let index = rule_occurrences;
                        rule_occurrences += 1;
                        if want_lexical {
                            lexical.push(LexicalOccurrence {
                                occurrence_id: index as usize,
                                parent_occurrence_id: parent_index,
                                rule_id: RULES[rule],
                                expr_id: BODIES[rule],
                                span: self.span(span),
                                completion_order: 0,
                            });
                        }
                        index
                    } else {
                        parent
                    };
                    stack.push((id.0 as u32, parent, true, index));
                    stack.push((child.0 as u32, index, false, NONE));
                }
            }
            _ => {}
        }
    }
    for token in &mut tokens {
        token.occurrence_id += lexical.len();
    }
    if build_ast {
        // 木は所有 `Ast` だけの parse では pool に戻るので、既に足りていれば確保しない。
        // 根以外の値はどれかの capture の値なので、節点数は capture 数でほぼ決まる
        // （JSON 1 MB: capture 178,342 / 節点 178,343。P4 x64: capture 9,731 / 節点 10,434。
        // P4 は leaf 型への昇格が節点を足す）。slot は record の field 分（`RULE_SLOTS`）と
        // list の要素（capture 数が上限）。
        let captured = caps_flat.len();
        self.tree
            .reserve(captured + captured / 8 + 1, rule_slots + captured);
    }
    self.caps_flat = caps_flat;
    self.caps_pending = pending;
    (captures, lexical, tokens)
}
fn recovery_occurrences(&self, root: EventId) -> (Vec<Recovery>, Vec<Diagnostic>) {
    // D-025: `captureOccurrences` は「回復した領域を**自分の値としてそのまま持つ**
    // capture 出現」。間に AST ノードを作る（あるいは捨てる）規則が入ると、その外側の
    // capture の値はノードであって回復領域ではないので数えない。node 境界の深さを
    // 数え、capture と同じ深さにある回復だけを結び付ける。
    enum Visit {
        Enter(EventId),
        CaptureDone { start: usize, depth: u32 },
        NodeLeave,
    }
    let mut recoveries: Vec<Recovery> = Vec::new();
    // 回復ごとの node 境界の深さ（`recoveries` と同じ並び）。
    let mut recovery_depth: Vec<u32> = Vec::new();
    let mut diagnostics = Vec::new();
    let mut stack = vec![Visit::Enter(root)];
    let mut capture_order = 0;
    let mut depth = 0u32;
    while let Some(visit) = stack.pop() {
        let id = match visit {
            Visit::NodeLeave => {
                depth -= 1;
                continue;
            }
            Visit::CaptureDone { start, depth: at } => {
                // `capture_occurrence_ids` は出現表を返すかに依らない観測なので、
                // 番号付けは AST / lexical のときそのまま数える（D-035 の対象外）。
                if self.options.wants_ast() || self.options.lexical {
                    for (r, recovery) in recoveries[start..].iter_mut().enumerate() {
                        if recovery_depth[start + r] == at {
                            recovery.capture_occurrence_ids.push(capture_order);
                        }
                    }
                    capture_order += 1;
                }
                continue;
            }
            Visit::Enter(id) => id,
        };
        match self.ev(id) {
            Event::Join(a, b) => {
                stack.push(Visit::Enter(b));
                stack.push(Visit::Enter(a));
            }
            Event::Rule { rule, child, .. } => {
                if RULE_NODE[rule] {
                    depth += 1;
                    stack.push(Visit::NodeLeave);
                }
                stack.push(Visit::Enter(child));
            }
            Event::Values { child, .. } => stack.push(Visit::Enter(child)),
            Event::Capture { child, .. } => {
                stack.push(Visit::CaptureDone {
                    start: recoveries.len(),
                    depth,
                });
                stack.push(Visit::Enter(child));
            }
            Event::Recovery { span, detail } => {
                let RecoveryEvent {
                    rule,
                    mode,
                    sync_span,
                    diag,
                    hints,
                } = self.recovery_events[detail as usize];
                // D-027: 公開する候補は表示語彙（`farthestExpected` と同じ蓄積）で、
                // 範囲は回復した規則の frame。主診断 DAG の label は Rust 内部の語彙
                // なので候補には使わず、規則経路の復元にだけ使う（D-020）。
                let path = self.diag.summary(diag).map_or(vec![], |(_, _, path)| path);
                let (far, expected) = self
                    .recovery_hints
                    .get(hints as usize)
                    .and_then(Clone::clone)
                    .unwrap_or((span[0], vec![]));
                let rule_path: Vec<_> = path.into_iter().map(|r| RULES[r]).collect();
                let span = self.span(span);
                diagnostics.push(Diagnostic {
                    kind: DiagnosticKind::Recovery,
                    offset_cp: span[0],
                    length_cp: span[1] - span[0],
                    farthest_cp: self.cp(far),
                    expected: expected.clone(),
                    farthest_expected: expected,
                    deepest_rule: rule_path.last().copied(),
                    rule_path,
                    message: Some("recovered input".into()),
                    recovery_id: Some(recoveries.len()),
                });
                recovery_depth.push(depth);
                recoveries.push(Recovery {
                    rule_id: RULES[rule],
                    mode,
                    span,
                    sync_span: sync_span.map(|s| self.span(s)),
                    capture_occurrence_ids: vec![],
                });
            }
            _ => {}
        }
    }
    (recoveries, diagnostics)
}
pub(crate) fn finish(self, step: Step) -> ParseResult {
    self.finish_checked(step).0
}
/// 結果と「診断付きで解析し直す必要があるか」を返す（`ParseOptions::diagnostics` 参照）。
/// fast mode で診断が要る結果になった入口は、同じ入力を診断モードで解析し直す。
pub(crate) fn finish_checked(mut self, mut step: Step) -> (ParseResult, bool) {
    if let Some(offset) = self.depth_failure {
        step = self.fail(State::default(), offset, "maximum parse depth exceeded");
    }
    if self.diag.exhausted || self.display.exhausted {
        self.resource_failure
            .get_or_insert((0, "diagnostic budget exceeded"));
    }
    if self.resource_failure.is_some() {
        step = Step {
            ok: false,
            ..Step::yes(State::default())
        };
    }
    // require_eof を問わず「入力が残った」ことを再解析の条件にする（保守的）。
    let incomplete = step.state.consumed != self.text.len();
    let trailing = step.ok && self.options.require_eof && incomplete;
    let ok = step.ok && !trailing;
    let consumed_cp = if step.ok {
        self.cp(step.state.consumed)
    } else {
        0
    };
    let matched_cp = if step.ok {
        self.cp(step.state.matched)
    } else {
        0
    };
    let mut diagnostics = Vec::new();
    if let Some((offset, label)) = self.resource_failure {
        diagnostics.push(Diagnostic {
            kind: DiagnosticKind::Resource,
            offset_cp: self.cp(offset),
            farthest_cp: self.cp(offset),
            expected: vec![label],
            farthest_expected: vec![label],
            deepest_rule: None,
            rule_path: vec![],
            message: Some(label.into()),
            recovery_id: None,
            length_cp: 0,
        });
    }
    if let Some((pos, expected, path)) = self.diag.summary(step.diag) {
        let rule_path: Vec<_> = path.into_iter().map(|r| RULES[r]).collect();
        diagnostics.push(Diagnostic {
            kind: DiagnosticKind::Syntax,
            offset_cp: self.cp(pos),
            farthest_cp: self.cp(pos),
            farthest_expected: expected.clone(),
            expected,
            deepest_rule: rule_path.last().copied(),
            rule_path,
            message: None,
            recovery_id: None,
            length_cp: 0,
        });
    }
    // D-014: farthest は失敗 frame / 成功枝内の失敗も含む独立観測。
    let (display_pos, display_expected) = self.display.summary();
    for diagnostic in &mut diagnostics {
        if diagnostic.kind == DiagnosticKind::Syntax {
            diagnostic.farthest_cp = self.cp(display_pos);
            diagnostic.farthest_expected = display_expected.clone();
        }
    }
    let hints = diagnostics
        .iter()
        .filter(|d| d.kind == DiagnosticKind::Syntax)
        .cloned()
        .collect();
    if trailing {
        let (farthest_cp, farthest_expected, deepest_rule, rule_path) =
            diagnostics
                .pop()
                .map_or((self.cp(display_pos), display_expected, None, vec![]), |d| {
                    (
                        d.farthest_cp,
                        d.farthest_expected,
                        d.deepest_rule,
                        d.rule_path,
                    )
                });
        diagnostics.push(Diagnostic {
            kind: DiagnosticKind::TrailingInput,
            offset_cp: consumed_cp,
            farthest_cp,
            farthest_expected,
            expected: vec!["end of input"],
            deepest_rule,
            rule_path,
            message: None,
            recovery_id: None,
            length_cp: 0,
        });
    }
    if ok {
        diagnostics.clear();
    }
    // recovery を持たない文法では Recovery event が無く、走査しても空になる（走査を省く）。
    let (recoveries, recovery_diagnostics) = if step.ok && HAS_RECOVERY {
        self.recovery_occurrences(step.events)
    } else {
        (vec![], vec![])
    };
    diagnostics.splice(0..0, recovery_diagnostics);
    // 走査は AST の直下 capture 表のために `build_ast` でも要る。公開する出現表を作るかは
    // `occurrences` / `lexical` が決める（D-035）。認識だけで要求も無ければ走査ごと省く。
    let (captures, lexical, tokens) =
        if step.ok
            && (self.options.wants_ast() || self.options.occurrences || self.options.lexical)
        {
            self.occurrences(step.events)
        } else {
            (vec![], vec![], vec![])
        };
    // D-077: AST は木（`self.tree`）へ作り、所有 `Ast` はそこから写す。
    let mut root = None;
    let mut mapped = false;
    if ok && self.options.wants_ast() {
        match self.build_values(step.events).and_then(|nodes| {
            if nodes.len() == 1 {
                Ok(Some(nodes[0]))
            } else if nodes.is_empty() {
                // D-028: entry が AST 値を作らないのは mapping 失敗ではない（ast=null / mappingError=null）。
                Ok(None)
            } else {
                Err("entry produced more than one AST value".into())
            }
        }) {
            Ok(value) => {
                root = value;
                mapped = true;
            }
            Err(message) => {
                diagnostics.push(Diagnostic {
                    kind: DiagnosticKind::Mapping,
                    offset_cp: consumed_cp,
                    farthest_cp: consumed_cp,
                    expected: vec![],
                    farthest_expected: vec![],
                    deepest_rule: None,
                    rule_path: vec![],
                    message: Some(message),
                    recovery_id: None,
                    length_cp: 0,
                });
            }
        }
    }
    let ast = if self.options.build_ast {
        root.map(|root| self.tree.project(self.text, root))
    } else {
        None
    };
    let mut node_spans = Vec::new();
    if mapped && self.options.build_ast {
        node_spans.reserve_exact(self.tree.typed_node_count());
        self.tree.push_node_spans(&mut node_spans);
    }
    self.statistics.memo_entries = self.memo.len();
    self.statistics.memo_max_probe = self
        .memo
        .max_probe_len()
        .max(self.trivia_cache.max_probe_len());
    self.statistics.recipes = self.arena.len() - 1;
    self.statistics.ast_nodes = if mapped {
        self.tree.typed_node_count()
    } else {
        0
    };
    self.statistics.lexical_runs = self.lex.runs;
    self.statistics.lexical_probes = self.lex.probes;
    self.statistics.trivia_skip_hits = self.trivia_skip.hits.get();
    self.statistics.trivia_skip_misses = self.trivia_skip.misses.get();
    // value span は要求されたときだけ集める（D-035）。AST 自体は先に完成している。
    let mut value_spans = Vec::new();
    if let (true, Some(root)) = (self.options.value_spans, root) {
        value_spans.reserve(self.tree.node_count());
        let mut path = String::with_capacity(128);
        self.tree
            .collect_value_spans(self.text, root, &mut path, &mut value_spans);
    }
    // 木を返すときは結果へ移し（入力を 1 回複製する）、返さないときは pool へ戻す。
    let tree = match root {
        Some(root) if self.options.ast_tree => {
            let mut tree = std::mem::take(&mut self.tree);
            tree.set_root(root);
            tree.set_source(self.text);
            Some(tree)
        }
        _ => None,
    };
    let result = ParseResult {
        value_spans,
        ok,
        consumed_cp,
        matched_cp,
        ast,
        tree,
        captures,
        lexical,
        tokens,
        node_spans,
        diagnostics,
        hints,
        recoveries,
        declarations: if step.ok {
            self.scope.all_declarations().to_vec()
        } else {
            vec![]
        },
        references: if step.ok {
            self.scope.references().to_vec()
        } else {
            vec![]
        },
        scope_diagnostics: if step.ok {
            self.scope.diagnostics().to_vec()
        } else {
            vec![]
        },
        statistics: self.statistics,
        scope: ScopeResult {
            depth: self.scope.depth(),
            mode: SCOPE_MODES.to_vec(),
            events: if step.ok {
                self.effects
                    .iter()
                    .enumerate()
                    .map(|(order, effect)| {
                        // D-029: enter / leave / clear の name は scope を持つ規則名。
                        let owner = || effect.name.map(str::to_owned);
                        let (action, name, offset_cp, length_cp) = match &*effect.value {
                            ScanEffect::Enter => ("enter", owner(), effect.offset_cp, 0),
                            ScanEffect::Leave => ("leave", owner(), effect.offset_cp, 0),
                            ScanEffect::Clear => ("clear", owner(), effect.offset_cp, 0),
                            ScanEffect::Declare { name, offset_cp } => {
                                ("declare", Some(name.clone()), *offset_cp, effect.length_cp)
                            }
                            ScanEffect::Use {
                                name,
                                offset_cp,
                                len_cp,
                            } => ("use", Some(name.clone()), *offset_cp, *len_cp),
                            ScanEffect::Diagnostic {
                                message,
                                offset_cp,
                                len_cp,
                                ..
                            } => ("error", Some(message.clone()), *offset_cp, *len_cp),
                        };
                        ScopeEvent {
                            order,
                            action,
                            mode: effect.mode,
                            name,
                            offset_cp,
                            length_cp,
                        }
                    })
                    .collect()
            } else {
                vec![]
            },
        },
    };
    // 診断を要する結果（失敗・入力の残り・recovery / mapping / resource 診断）は fast mode では
    // 観測を持たないので、入口が診断モードで解析し直す。scope の意味診断（`scope_diagnostics`）は
    // 診断機構ではなく scope store の効果なので、fast mode でも同じものが得られる（再解析しない）。
    let escalate = !DIAG
        && (!result.ok
            || incomplete
            || !result.diagnostics.is_empty()
            || !result.recoveries.is_empty());
    self.recycle();
    (result, escalate)
}
}
