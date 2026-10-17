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
        /// D-078: 意味値を持つ選択の text だけの候補。一致範囲の字句（空でも）を 1 つの値にする。
        text: bool,
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
            Event::Values {
                span,
                child,
                wrap,
                text,
            } => {
                p.tag = EV_VALUES;
                p.flags = wrap as u8 | (text as u8) << 1;
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
                wrap: self.flags & 1 != 0,
                text: self.flags & 2 != 0,
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
    /// #86: いま値を組み立てている意味値の領域（`Values{text:false}`）の入れ子の深さ。
    /// 写像される規則の本体に入るたびに 0 から数え直す。
    value_region: u32,
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
            value_region: 0,
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
    /// `text_sites` は中身が text だけの site で、出現 1 つが一致範囲の字句 1 つになる（#89、D-032）。
    /// `fallback` は leaf 型（Text を leaf node に昇格する recipe）。
    pub fn node_values(
        &mut self,
        caps: &[Cap],
        sites: &[usize],
        text_sites: &[usize],
        fallback: Option<usize>,
        out: &mut Vec<u32>,
    ) -> Result<(), String> {
        for &(site, span, child) in caps {
            if !sites.contains(&site) {
                continue;
            }
            let start = out.len();
            if text_sites.contains(&site) && span[0] != span[1] && !self.has_recovery(child) {
                let text = self.semantic_text(child, span);
                out.push(text);
            } else {
                self.build_values_into(child, out)?;
            }
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

pub mod lexical_program {
use std::collections::HashMap;

#[allow(clippy::upper_case_acronyms)]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Op {
    LITERAL,
    ANY,
    EOF,
    BOF,
    BOL,
    EOL,
    RANGE,
    EXCEPT,
    SEQUENCE,
    CHOICE,
    REPEAT,
    LOOK,
    NOT,
    CAPTURE,
    BACKREF,
    SCOPE,
}

#[derive(Clone, Debug)]
pub struct LexicalExpression {
    pub op: Op,
    pub text: &'static str,
    pub min: i32,
    pub max: i32,
    pub children: &'static [LexicalExpression],
}

impl LexicalExpression {
    pub fn match_at(&self, source: &str, start: usize) -> Option<usize> {
        self.run(source, start, &mut HashMap::new())
    }
    fn run<'a>(
        &self,
        s: &'a str,
        p: usize,
        bindings: &mut HashMap<&'static str, &'a str>,
    ) -> Option<usize> {
        let before = bindings.clone();
        let end = self.eval(s, p, bindings);
        if end.is_none() {
            *bindings = before;
        }
        end
    }
    fn eval<'a>(
        &self,
        s: &'a str,
        p: usize,
        bindings: &mut HashMap<&'static str, &'a str>,
    ) -> Option<usize> {
        match self.op {
            Op::LITERAL => s[p..].starts_with(self.text).then_some(p + self.text.len()),
            Op::ANY => s[p..].chars().next().map(|c| p + c.len_utf8()),
            Op::EOF => (p == s.len()).then_some(p),
            Op::BOF => (p == 0).then_some(p),
            Op::BOL => {
                (p == 0 || matches!(s.as_bytes().get(p - 1), Some(b'\r' | b'\n'))).then_some(p)
            }
            Op::EOL => {
                (p == s.len() || matches!(s.as_bytes().get(p), Some(b'\r' | b'\n'))).then_some(p)
            }
            Op::RANGE => s[p..]
                .chars()
                .next()
                .filter(|c| (*c as i32) >= self.min && (*c as i32) <= self.max)
                .map(|c| p + c.len_utf8()),
            Op::EXCEPT => s[p..]
                .chars()
                .next()
                .filter(|c| !self.text.contains(*c))
                .map(|c| p + c.len_utf8()),
            Op::SEQUENCE => self
                .children
                .iter()
                .try_fold(p, |p, child| child.run(s, p, bindings)),
            Op::CHOICE => self
                .children
                .iter()
                .find_map(|child| child.run(s, p, bindings)),
            Op::REPEAT => {
                let mut end = p;
                let mut count = 0;
                while self.max < 0 || count < self.max {
                    let Some(next) = self.children[0].run(s, end, bindings) else {
                        break;
                    };
                    count += 1;
                    assert!(next != end || self.max >= 0, "nullable lexical repeat");
                    end = next;
                }
                (count >= self.min).then_some(end)
            }
            Op::LOOK | Op::NOT => {
                let matched = self.children[0].run(s, p, &mut bindings.clone()).is_some();
                (matched == (self.op == Op::LOOK)).then_some(p)
            }
            Op::CAPTURE => {
                let end = self.children[0].run(s, p, bindings)?;
                bindings.insert(self.text, &s[p..end]);
                Some(end)
            }
            Op::BACKREF => bindings
                .get(self.text)
                .filter(|text| s[p..].starts_with(**text))
                .map(|text| p + text.len()),
            Op::SCOPE => self.children[0].run(s, p, &mut HashMap::new()),
        }
    }
}


}
/// 統合 DFA の bitset 語数（literal 98 個）。0 なら cache を作らない。
const LEX_WORDS:usize=2;
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
33=>1,
35=>{out[0]|=2;3},
36=>{out[0]|=4;4},
38=>{out[0]|=8;5},
40=>{out[0]|=16;6},
41=>{out[0]|=32;7},
42=>{out[0]|=64;8},
43=>{out[0]|=128;9},
44=>{out[0]|=256;10},
45=>{out[0]|=512;11},
46=>{out[0]|=2048;13},
47=>{out[0]|=1048576;71},
58=>{out[0]|=2097152;72},
59=>{out[0]|=4194304;73},
60=>{out[0]|=8388608;74},
61=>{out[0]|=33554432;76},
62=>{out[0]|=134217728;78},
63=>{out[0]|=536870912;80},
64=>{out[0]|=1073741824;81},
66=>82,
70=>89,
77=>99,
78=>105,
79=>111,
83=>117,
84=>135,
87=>149,
91=>{out[0]|=8796093022208;158},
93=>{out[0]|=17592186044416;159},
94=>{out[0]|=35184372088832;160},
97=>161,
98=>165,
99=>172,
100=>187,
101=>203,
102=>226,
105=>237,
108=>280,
109=>288,
110=>296,
111=>304,
112=>310,
114=>313,
115=>331,
116=>352,
118=>382,
123=>{out[1]|=2147483648;390},
124=>{out[1]|=4294967296;391},
125=>{out[1]|=8589934592;392},
_=>return,
},
1=>match c {
61=>{out[0]|=1;2},
_=>return,
},
11=>match c {
62=>{out[0]|=1024;12},
_=>return,
},
13=>match c {
99=>14,
101=>22,
105=>30,
108=>32,
115=>38,
116=>48,
_=>return,
},
14=>match c {
111=>15,
_=>return,
},
15=>match c {
110=>16,
_=>return,
},
16=>match c {
116=>17,
_=>return,
},
17=>match c {
97=>18,
_=>return,
},
18=>match c {
105=>19,
_=>return,
},
19=>match c {
110=>20,
_=>return,
},
20=>match c {
115=>{out[0]|=4096;21},
_=>return,
},
22=>match c {
110=>23,
_=>return,
},
23=>match c {
100=>24,
_=>return,
},
24=>match c {
115=>25,
_=>return,
},
25=>match c {
87=>26,
_=>return,
},
26=>match c {
105=>27,
_=>return,
},
27=>match c {
116=>28,
_=>return,
},
28=>match c {
104=>{out[0]|=8192;29},
_=>return,
},
30=>match c {
110=>{out[0]|=16384;31},
_=>return,
},
32=>match c {
101=>33,
_=>return,
},
33=>match c {
110=>34,
_=>return,
},
34=>match c {
103=>35,
_=>return,
},
35=>match c {
116=>36,
_=>return,
},
36=>match c {
104=>{out[0]|=32768;37},
_=>return,
},
38=>match c {
116=>39,
_=>return,
},
39=>match c {
97=>40,
_=>return,
},
40=>match c {
114=>41,
_=>return,
},
41=>match c {
116=>42,
_=>return,
},
42=>match c {
115=>43,
_=>return,
},
43=>match c {
87=>44,
_=>return,
},
44=>match c {
105=>45,
_=>return,
},
45=>match c {
116=>46,
_=>return,
},
46=>match c {
104=>{out[0]|=65536;47},
_=>return,
},
48=>match c {
111=>49,
114=>68,
_=>return,
},
49=>match c {
76=>50,
85=>59,
_=>return,
},
50=>match c {
111=>51,
_=>return,
},
51=>match c {
119=>52,
_=>return,
},
52=>match c {
101=>53,
_=>return,
},
53=>match c {
114=>54,
_=>return,
},
54=>match c {
67=>55,
_=>return,
},
55=>match c {
97=>56,
_=>return,
},
56=>match c {
115=>57,
_=>return,
},
57=>match c {
101=>{out[0]|=131072;58},
_=>return,
},
59=>match c {
112=>60,
_=>return,
},
60=>match c {
112=>61,
_=>return,
},
61=>match c {
101=>62,
_=>return,
},
62=>match c {
114=>63,
_=>return,
},
63=>match c {
67=>64,
_=>return,
},
64=>match c {
97=>65,
_=>return,
},
65=>match c {
115=>66,
_=>return,
},
66=>match c {
101=>{out[0]|=262144;67},
_=>return,
},
68=>match c {
105=>69,
_=>return,
},
69=>match c {
109=>{out[0]|=524288;70},
_=>return,
},
74=>match c {
61=>{out[0]|=16777216;75},
_=>return,
},
76=>match c {
61=>{out[0]|=67108864;77},
_=>return,
},
78=>match c {
61=>{out[0]|=268435456;79},
_=>return,
},
82=>match c {
111=>83,
_=>return,
},
83=>match c {
111=>84,
_=>return,
},
84=>match c {
108=>85,
_=>return,
},
85=>match c {
101=>86,
_=>return,
},
86=>match c {
97=>87,
_=>return,
},
87=>match c {
110=>{out[0]|=2147483648;88},
_=>return,
},
89=>match c {
82=>90,
108=>95,
_=>return,
},
90=>match c {
73=>91,
_=>return,
},
91=>match c {
68=>92,
_=>return,
},
92=>match c {
65=>93,
_=>return,
},
93=>match c {
89=>{out[0]|=4294967296;94},
_=>return,
},
95=>match c {
111=>96,
_=>return,
},
96=>match c {
97=>97,
_=>return,
},
97=>match c {
116=>{out[0]|=8589934592;98},
_=>return,
},
99=>match c {
79=>100,
_=>return,
},
100=>match c {
78=>101,
_=>return,
},
101=>match c {
68=>102,
_=>return,
},
102=>match c {
65=>103,
_=>return,
},
103=>match c {
89=>{out[0]|=17179869184;104},
_=>return,
},
105=>match c {
117=>106,
_=>return,
},
106=>match c {
109=>107,
_=>return,
},
107=>match c {
98=>108,
_=>return,
},
108=>match c {
101=>109,
_=>return,
},
109=>match c {
114=>{out[0]|=34359738368;110},
_=>return,
},
111=>match c {
98=>112,
_=>return,
},
112=>match c {
106=>113,
_=>return,
},
113=>match c {
101=>114,
_=>return,
},
114=>match c {
99=>115,
_=>return,
},
115=>match c {
116=>{out[0]|=68719476736;116},
_=>return,
},
117=>match c {
65=>118,
85=>125,
116=>130,
_=>return,
},
118=>match c {
84=>119,
_=>return,
},
119=>match c {
85=>120,
_=>return,
},
120=>match c {
82=>121,
_=>return,
},
121=>match c {
68=>122,
_=>return,
},
122=>match c {
65=>123,
_=>return,
},
123=>match c {
89=>{out[0]|=137438953472;124},
_=>return,
},
125=>match c {
78=>126,
_=>return,
},
126=>match c {
68=>127,
_=>return,
},
127=>match c {
65=>128,
_=>return,
},
128=>match c {
89=>{out[0]|=274877906944;129},
_=>return,
},
130=>match c {
114=>131,
_=>return,
},
131=>match c {
105=>132,
_=>return,
},
132=>match c {
110=>133,
_=>return,
},
133=>match c {
103=>{out[0]|=549755813888;134},
_=>return,
},
135=>match c {
72=>136,
85=>143,
_=>return,
},
136=>match c {
85=>137,
_=>return,
},
137=>match c {
82=>138,
_=>return,
},
138=>match c {
83=>139,
_=>return,
},
139=>match c {
68=>140,
_=>return,
},
140=>match c {
65=>141,
_=>return,
},
141=>match c {
89=>{out[0]|=1099511627776;142},
_=>return,
},
143=>match c {
69=>144,
_=>return,
},
144=>match c {
83=>145,
_=>return,
},
145=>match c {
68=>146,
_=>return,
},
146=>match c {
65=>147,
_=>return,
},
147=>match c {
89=>{out[0]|=2199023255552;148},
_=>return,
},
149=>match c {
69=>150,
_=>return,
},
150=>match c {
68=>151,
_=>return,
},
151=>match c {
78=>152,
_=>return,
},
152=>match c {
69=>153,
_=>return,
},
153=>match c {
83=>154,
_=>return,
},
154=>match c {
68=>155,
_=>return,
},
155=>match c {
65=>156,
_=>return,
},
156=>match c {
89=>{out[0]|=4398046511104;157},
_=>return,
},
161=>match c {
98=>162,
115=>{out[0]|=140737488355328;164},
_=>return,
},
162=>match c {
115=>{out[0]|=70368744177664;163},
_=>return,
},
165=>match c {
111=>166,
_=>return,
},
166=>match c {
111=>167,
_=>return,
},
167=>match c {
108=>168,
_=>return,
},
168=>match c {
101=>169,
_=>return,
},
169=>match c {
97=>170,
_=>return,
},
170=>match c {
110=>{out[0]|=281474976710656;171},
_=>return,
},
172=>match c {
97=>173,
101=>176,
111=>179,
_=>return,
},
173=>match c {
108=>174,
_=>return,
},
174=>match c {
108=>{out[0]|=562949953421312;175},
_=>return,
},
176=>match c {
105=>177,
_=>return,
},
177=>match c {
108=>{out[0]|=1125899906842624;178},
_=>return,
},
179=>match c {
110=>180,
115=>{out[0]|=4503599627370496;186},
_=>return,
},
180=>match c {
116=>181,
_=>return,
},
181=>match c {
97=>182,
_=>return,
},
182=>match c {
105=>183,
_=>return,
},
183=>match c {
110=>184,
_=>return,
},
184=>match c {
115=>{out[0]|=2251799813685248;185},
_=>return,
},
187=>match c {
101=>188,
_=>return,
},
188=>match c {
102=>189,
115=>194,
_=>return,
},
189=>match c {
97=>190,
_=>return,
},
190=>match c {
117=>191,
_=>return,
},
191=>match c {
108=>192,
_=>return,
},
192=>match c {
116=>{out[0]|=9007199254740992;193},
_=>return,
},
194=>match c {
99=>195,
_=>return,
},
195=>match c {
114=>196,
_=>return,
},
196=>match c {
105=>197,
_=>return,
},
197=>match c {
112=>198,
_=>return,
},
198=>match c {
116=>199,
_=>return,
},
199=>match c {
105=>200,
_=>return,
},
200=>match c {
111=>201,
_=>return,
},
201=>match c {
110=>{out[0]|=18014398509481984;202},
_=>return,
},
203=>match c {
108=>204,
110=>207,
120=>214,
_=>return,
},
204=>match c {
115=>205,
_=>return,
},
205=>match c {
101=>{out[0]|=36028797018963968;206},
_=>return,
},
207=>match c {
100=>208,
_=>return,
},
208=>match c {
115=>209,
_=>return,
},
209=>match c {
87=>210,
_=>return,
},
210=>match c {
105=>211,
_=>return,
},
211=>match c {
116=>212,
_=>return,
},
212=>match c {
104=>{out[0]|=72057594037927936;213},
_=>return,
},
214=>match c {
105=>215,
112=>{out[0]|=288230376151711744;219},
116=>220,
_=>return,
},
215=>match c {
115=>216,
_=>return,
},
216=>match c {
116=>217,
_=>return,
},
217=>match c {
115=>{out[0]|=144115188075855872;218},
_=>return,
},
220=>match c {
101=>221,
_=>return,
},
221=>match c {
114=>222,
_=>return,
},
222=>match c {
110=>223,
_=>return,
},
223=>match c {
97=>224,
_=>return,
},
224=>match c {
108=>{out[0]|=576460752303423488;225},
_=>return,
},
226=>match c {
97=>227,
108=>231,
_=>return,
},
227=>match c {
108=>228,
_=>return,
},
228=>match c {
115=>229,
_=>return,
},
229=>match c {
101=>{out[0]|=1152921504606846976;230},
_=>return,
},
231=>match c {
111=>232,
_=>return,
},
232=>match c {
97=>233,
111=>235,
_=>return,
},
233=>match c {
116=>{out[0]|=2305843009213693952;234},
_=>return,
},
235=>match c {
114=>{out[0]|=4611686018427387904;236},
_=>return,
},
237=>match c {
102=>{out[0]|=9223372036854775808;238},
109=>239,
110=>244,
115=>272,
_=>return,
},
239=>match c {
112=>240,
_=>return,
},
240=>match c {
111=>241,
_=>return,
},
241=>match c {
114=>242,
_=>return,
},
242=>match c {
116=>{out[1]|=1;243},
_=>return,
},
244=>match c {
68=>245,
84=>257,
116=>266,
_=>return,
},
245=>match c {
97=>246,
_=>return,
},
246=>match c {
121=>247,
_=>return,
},
247=>match c {
84=>248,
_=>return,
},
248=>match c {
105=>249,
_=>return,
},
249=>match c {
109=>250,
_=>return,
},
250=>match c {
101=>251,
_=>return,
},
251=>match c {
82=>252,
_=>return,
},
252=>match c {
97=>253,
_=>return,
},
253=>match c {
110=>254,
_=>return,
},
254=>match c {
103=>255,
_=>return,
},
255=>match c {
101=>{out[1]|=2;256},
_=>return,
},
257=>match c {
105=>258,
_=>return,
},
258=>match c {
109=>259,
_=>return,
},
259=>match c {
101=>260,
_=>return,
},
260=>match c {
82=>261,
_=>return,
},
261=>match c {
97=>262,
_=>return,
},
262=>match c {
110=>263,
_=>return,
},
263=>match c {
103=>264,
_=>return,
},
264=>match c {
101=>{out[1]|=4;265},
_=>return,
},
266=>match c {
101=>267,
_=>return,
},
267=>match c {
114=>268,
_=>return,
},
268=>match c {
110=>269,
_=>return,
},
269=>match c {
97=>270,
_=>return,
},
270=>match c {
108=>{out[1]|=8;271},
_=>return,
},
272=>match c {
80=>273,
_=>return,
},
273=>match c {
114=>274,
_=>return,
},
274=>match c {
101=>275,
_=>return,
},
275=>match c {
115=>276,
_=>return,
},
276=>match c {
101=>277,
_=>return,
},
277=>match c {
110=>278,
_=>return,
},
278=>match c {
116=>{out[1]|=16;279},
_=>return,
},
280=>match c {
101=>281,
111=>286,
_=>return,
},
281=>match c {
110=>{out[1]|=32;282},
_=>return,
},
282=>match c {
103=>283,
_=>return,
},
283=>match c {
116=>284,
_=>return,
},
284=>match c {
104=>{out[1]|=64;285},
_=>return,
},
286=>match c {
103=>{out[1]|=128;287},
_=>return,
},
288=>match c {
97=>289,
105=>294,
_=>return,
},
289=>match c {
116=>290,
120=>{out[1]|=512;293},
_=>return,
},
290=>match c {
99=>291,
_=>return,
},
291=>match c {
104=>{out[1]|=256;292},
_=>return,
},
294=>match c {
110=>{out[1]|=1024;295},
_=>return,
},
296=>match c {
111=>297,
117=>299,
_=>return,
},
297=>match c {
116=>{out[1]|=2048;298},
_=>return,
},
299=>match c {
109=>300,
_=>return,
},
300=>match c {
98=>301,
_=>return,
},
301=>match c {
101=>302,
_=>return,
},
302=>match c {
114=>{out[1]|=4096;303},
_=>return,
},
304=>match c {
98=>305,
_=>return,
},
305=>match c {
106=>306,
_=>return,
},
306=>match c {
101=>307,
_=>return,
},
307=>match c {
99=>308,
_=>return,
},
308=>match c {
116=>{out[1]|=8192;309},
_=>return,
},
310=>match c {
111=>311,
_=>return,
},
311=>match c {
119=>{out[1]|=16384;312},
_=>return,
},
313=>match c {
97=>314,
101=>319,
111=>327,
_=>return,
},
314=>match c {
110=>315,
_=>return,
},
315=>match c {
100=>316,
_=>return,
},
316=>match c {
111=>317,
_=>return,
},
317=>match c {
109=>{out[1]|=32768;318},
_=>return,
},
319=>match c {
116=>320,
_=>return,
},
320=>match c {
117=>321,
_=>return,
},
321=>match c {
114=>322,
_=>return,
},
322=>match c {
110=>323,
_=>return,
},
323=>match c {
105=>324,
_=>return,
},
324=>match c {
110=>325,
_=>return,
},
325=>match c {
103=>{out[1]|=65536;326},
_=>return,
},
327=>match c {
117=>328,
_=>return,
},
328=>match c {
110=>329,
_=>return,
},
329=>match c {
100=>{out[1]|=131072;330},
_=>return,
},
331=>match c {
101=>332,
105=>334,
113=>336,
116=>339,
_=>return,
},
332=>match c {
116=>{out[1]|=262144;333},
_=>return,
},
334=>match c {
110=>{out[1]|=524288;335},
_=>return,
},
336=>match c {
114=>337,
_=>return,
},
337=>match c {
116=>{out[1]|=1048576;338},
_=>return,
},
339=>match c {
97=>340,
114=>348,
_=>return,
},
340=>match c {
114=>341,
_=>return,
},
341=>match c {
116=>342,
_=>return,
},
342=>match c {
115=>343,
_=>return,
},
343=>match c {
87=>344,
_=>return,
},
344=>match c {
105=>345,
_=>return,
},
345=>match c {
116=>346,
_=>return,
},
346=>match c {
104=>{out[1]|=2097152;347},
_=>return,
},
348=>match c {
105=>349,
_=>return,
},
349=>match c {
110=>350,
_=>return,
},
350=>match c {
103=>{out[1]|=4194304;351},
_=>return,
},
352=>match c {
97=>353,
111=>355,
114=>377,
_=>return,
},
353=>match c {
110=>{out[1]|=8388608;354},
_=>return,
},
355=>match c {
76=>356,
78=>365,
85=>368,
_=>return,
},
356=>match c {
111=>357,
_=>return,
},
357=>match c {
119=>358,
_=>return,
},
358=>match c {
101=>359,
_=>return,
},
359=>match c {
114=>360,
_=>return,
},
360=>match c {
67=>361,
_=>return,
},
361=>match c {
97=>362,
_=>return,
},
362=>match c {
115=>363,
_=>return,
},
363=>match c {
101=>{out[1]|=16777216;364},
_=>return,
},
365=>match c {
117=>366,
_=>return,
},
366=>match c {
109=>{out[1]|=33554432;367},
_=>return,
},
368=>match c {
112=>369,
_=>return,
},
369=>match c {
112=>370,
_=>return,
},
370=>match c {
101=>371,
_=>return,
},
371=>match c {
114=>372,
_=>return,
},
372=>match c {
67=>373,
_=>return,
},
373=>match c {
97=>374,
_=>return,
},
374=>match c {
115=>375,
_=>return,
},
375=>match c {
101=>{out[1]|=67108864;376},
_=>return,
},
377=>match c {
105=>378,
117=>380,
_=>return,
},
378=>match c {
109=>{out[1]|=134217728;379},
_=>return,
},
380=>match c {
101=>{out[1]|=268435456;381},
_=>return,
},
382=>match c {
97=>383,
_=>return,
},
383=>match c {
114=>{out[1]|=536870912;384},
_=>return,
},
384=>match c {
105=>385,
_=>return,
},
385=>match c {
97=>386,
_=>return,
},
386=>match c {
98=>387,
_=>return,
},
387=>match c {
108=>388,
_=>return,
},
388=>match c {
101=>{out[1]|=1073741824;389},
_=>return,
},
_=>return,
};
p+=1;
}
}

use super::ast::*;
const HAS_RECOVERY:bool=false;
const SCOPE_MODES:&[&str]=&["lexical"];
const HAS_SCOPE:bool=true;
const RULES:&[&str]=&["TinyExpressionP4::Formula", "TinyExpressionP4::CodeBlock", "TinyExpressionP4::ImportDeclaration", "TinyExpressionP4::ClassName", "TinyExpressionP4::VariableDeclaration", "TinyExpressionP4::NumberVariableDeclaration", "TinyExpressionP4::StringVariableDeclaration", "TinyExpressionP4::BooleanVariableDeclaration", "TinyExpressionP4::ObjectVariableDeclaration", "TinyExpressionP4::TypeHint", "TinyExpressionP4::NumberTypeHint", "TinyExpressionP4::StringTypeHint", "TinyExpressionP4::BooleanTypeHint", "TinyExpressionP4::ObjectTypeHint", "TinyExpressionP4::OnlyIfAbsent", "TinyExpressionP4::Description", "TinyExpressionP4::Annotation", "TinyExpressionP4::AnnotationParameters", "TinyExpressionP4::AnnotationParameter", "TinyExpressionP4::MethodDeclaration", "TinyExpressionP4::NumberMethodDeclaration", "TinyExpressionP4::StringMethodDeclaration", "TinyExpressionP4::BooleanMethodDeclaration", "TinyExpressionP4::ObjectMethodDeclaration", "TinyExpressionP4::MethodParameters", "TinyExpressionP4::MethodParameter", "TinyExpressionP4::NumberReturnType", "TinyExpressionP4::StringReturnType", "TinyExpressionP4::BooleanReturnType", "TinyExpressionP4::ObjectReturnType", "TinyExpressionP4::ReturnType", "TinyExpressionP4::ExternalBooleanInvocation", "TinyExpressionP4::ExternalNumberInvocation", "TinyExpressionP4::ExternalStringInvocation", "TinyExpressionP4::ExternalObjectInvocation", "TinyExpressionP4::MethodInvocationHeader", "TinyExpressionP4::MethodInvocation", "TinyExpressionP4::ArgumentTernary", "TinyExpressionP4::ArgumentExpression", "TinyExpressionP4::Arguments", "TinyExpressionP4::NumberExpression", "TinyExpressionP4::NumberTerm", "TinyExpressionP4::AddOp", "TinyExpressionP4::MulOp", "TinyExpressionP4::MathFunction", "TinyExpressionP4::SinFunction", "TinyExpressionP4::CosFunction", "TinyExpressionP4::TanFunction", "TinyExpressionP4::SqrtFunction", "TinyExpressionP4::MinFunction", "TinyExpressionP4::MaxFunction", "TinyExpressionP4::RandomFunction", "TinyExpressionP4::AbsFunction", "TinyExpressionP4::RoundFunction", "TinyExpressionP4::CeilFunction", "TinyExpressionP4::FloorFunction", "TinyExpressionP4::PowFunction", "TinyExpressionP4::LogFunction", "TinyExpressionP4::ExpFunction", "TinyExpressionP4::ToNumFunction", "TinyExpressionP4::NumberFactor", "TinyExpressionP4::ToUpperCaseFunction", "TinyExpressionP4::ToLowerCaseFunction", "TinyExpressionP4::TrimFunction", "TinyExpressionP4::LengthFunction", "TinyExpressionP4::LenFunction", "TinyExpressionP4::ToUpperCaseDotMethod", "TinyExpressionP4::ToLowerCaseDotMethod", "TinyExpressionP4::TrimDotMethod", "TinyExpressionP4::LengthDotMethod", "TinyExpressionP4::StartsWithFunction", "TinyExpressionP4::EndsWithFunction", "TinyExpressionP4::ContainsFunction", "TinyExpressionP4::InMethod", "TinyExpressionP4::StartsWithDotMethod", "TinyExpressionP4::EndsWithDotMethod", "TinyExpressionP4::ContainsDotMethod", "TinyExpressionP4::StringPredicateReceiver", "TinyExpressionP4::IsPresentFunction", "TinyExpressionP4::InTimeRangeFunction", "TinyExpressionP4::InDayTimeRangeFunction", "TinyExpressionP4::DayOfWeek", "TinyExpressionP4::SliceBaseReceiver", "TinyExpressionP4::SliceStartIndex", "TinyExpressionP4::SliceEndIndex", "TinyExpressionP4::SliceStepIndex", "TinyExpressionP4::SliceBaseExpression", "TinyExpressionP4::SliceNestedExpression", "TinyExpressionP4::SliceExpression", "TinyExpressionP4::StringExpression", "TinyExpressionP4::ParenthesizedStringExpression", "TinyExpressionP4::StringTerm", "TinyExpressionP4::StringCastVariable", "TinyExpressionP4::StringTypedVariable", "TinyExpressionP4::BooleanExpression", "TinyExpressionP4::BooleanAndExpression", "TinyExpressionP4::BooleanXorExpression", "TinyExpressionP4::NotExpression", "TinyExpressionP4::BooleanComparable", "TinyExpressionP4::BooleanEqualityExpression", "TinyExpressionP4::BooleanFactor", "TinyExpressionP4::StringComparisonExpression", "TinyExpressionP4::EqualityOp", "TinyExpressionP4::ComparisonExpression", "TinyExpressionP4::CompareOp", "TinyExpressionP4::ObjectExpression", "TinyExpressionP4::IfExpression", "TinyExpressionP4::BranchExpression", "TinyExpressionP4::TernaryExpression", "TinyExpressionP4::NumberMatchExpression", "TinyExpressionP4::NumberCase", "TinyExpressionP4::NumberDefaultCase", "TinyExpressionP4::NumberCaseValue", "TinyExpressionP4::StringMatchExpression", "TinyExpressionP4::StringCase", "TinyExpressionP4::StringDefaultCase", "TinyExpressionP4::StringCaseValue", "TinyExpressionP4::BooleanMatchExpression", "TinyExpressionP4::BooleanCase", "TinyExpressionP4::BooleanDefaultCase", "TinyExpressionP4::BooleanCaseValue", "TinyExpressionP4::VariableRef", "TinyExpressionP4::TypeKeyword", "TinyExpressionP4::Expression", "TinyExpressionP4::NumberRootExpression", "TinyExpressionP4::BooleanRootExpression", "TinyExpressionP4::StringRootExpression", "TinyExpressionP4::ObjectRootExpression", "TinyExpressionP4::MethodRootExpression", "TinyExpressionP4::ParenthesizedRootExpression"];
const EXPRESSIONS:&[&str]=&["expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1175:1742:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1175:1200:body/0/repeat", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1177:1186:body/0/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1201:1231:body/1/repeat", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1203:1220:body/1/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1232:1269:body/2/repeat", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1234:1253:body/2/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1274:1288:body/3/repeat", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1276:1286:body/3/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1293:1742:body/4/group", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1295:1740:body/4/0/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1295:1362:body/4/0/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1295:1315:body/4/0/0/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1328:1358:body/4/0/0/1/repeat", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1330:1347:body/4/0/0/1/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1359:1362:body/4/0/0/2/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1369:1437:body/4/0/1/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1369:1390:body/4/0/1/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1403:1433:body/4/0/1/1/repeat", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1405:1422:body/4/0/1/1/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1434:1437:body/4/0/1/2/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1444:1511:body/4/0/2/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1444:1464:body/4/0/2/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1477:1507:body/4/0/2/1/repeat", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1479:1496:body/4/0/2/1/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1508:1511:body/4/0/2/2/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1518:1585:body/4/0/3/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1518:1538:body/4/0/3/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1551:1581:body/4/0/3/1/repeat", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1553:1570:body/4/0/3/1/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1582:1585:body/4/0/3/2/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1592:1659:body/4/0/4/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1592:1612:body/4/0/4/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1625:1655:body/4/0/4/1/repeat", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1627:1644:body/4/0/4/1/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1656:1659:body/4/0/4/2/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1666:1740:body/4/0/5/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1666:1693:body/4/0/5/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1706:1736:body/4/0/5/1/repeat", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1708:1725:body/4/0/5/1/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1737:1740:body/4/0/5/2/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2175:2226:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2175:2226:body/0/group", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2177:2224:body/0/0/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2177:2192:body/0/0/0/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2195:2224:body/0/0/1/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2195:2205:body/0/0/1/0/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2206:2215:body/0/0/1/1/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2216:2224:body/0/0/1/2/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2682:2765:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2682:2690:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2691:2700:body/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2712:2738:body/2/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2714:2728:body/2/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2714:2717:body/2/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2718:2728:body/2/0/1/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2739:2743:body/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2744:2754:body/4/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2762:2765:body/5/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2836:2877:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2836:2846:body/0/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2853:2877:body/1/repeat", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2855:2869:body/1/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2855:2858:body/1/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2859:2869:body/1/0/1/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3028:3150:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3028:3053:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3060:3085:body/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3092:3118:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3125:3150:body/3/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3323:3499:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3323:3345:body/0/group", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3325:3343:body/0/0/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3325:3335:body/0/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3338:3343:body/0/0/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3350:3353:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3354:3364:body/2/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3378:3396:body/3/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3380:3394:body/3/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3401:3465:body/4/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3403:3456:body/4/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3403:3408:body/4/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3409:3439:body/4/0/1/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3411:3423:body/4/0/1/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3440:3456:body/4/0/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3470:3491:body/5/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3472:3483:body/5/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3496:3499:body/6/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3672:3848:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3672:3694:body/0/group", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3674:3692:body/0/0/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3674:3684:body/0/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3687:3692:body/0/0/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3699:3702:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3703:3713:body/2/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3727:3745:body/3/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3729:3743:body/3/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3750:3814:body/4/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3752:3805:body/4/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3752:3757:body/4/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3758:3788:body/4/0/1/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3760:3772:body/4/0/1/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3789:3805:body/4/0/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3819:3840:body/5/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3821:3832:body/5/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3845:3848:body/6/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4023:4201:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4023:4045:body/0/group", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4025:4043:body/0/0/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4025:4035:body/0/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4038:4043:body/0/0/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4050:4053:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4054:4064:body/2/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4078:4097:body/3/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4080:4095:body/3/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4102:4167:body/4/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4104:4158:body/4/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4104:4109:body/4/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4110:4140:body/4/0/1/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4112:4124:body/4/0/1/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4141:4158:body/4/0/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4172:4193:body/5/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4174:4185:body/5/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4198:4201:body/6/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4374:4550:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4374:4396:body/0/group", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4376:4394:body/0/0/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4376:4386:body/0/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4389:4394:body/0/0/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4401:4404:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4405:4415:body/2/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4429:4447:body/3/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4431:4445:body/3/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4452:4516:body/4/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4454:4507:body/4/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4454:4459:body/4/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4460:4490:body/4/0/1/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4462:4474:body/4/0/1/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4491:4507:body/4/0/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4521:4542:body/5/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4523:4534:body/5/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4547:4550:body/6/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4569:4673:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4569:4577:body/0/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4571:4575:body/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4578:4673:body/1/group", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4580:4671:body/1/0/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4580:4588:body/1/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4591:4599:body/1/0/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4602:4610:body/1/0/2/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4613:4621:body/1/0/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4628:4637:body/1/0/4/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4640:4649:body/1/0/5/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4652:4660:body/1/0/6/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4663:4671:body/1/0/7/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4698:4750:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4698:4706:body/0/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4700:4704:body/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4707:4750:body/1/group", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4709:4748:body/1/0/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4709:4717:body/1/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4720:4728:body/1/0/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4731:4738:body/1/0/2/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4741:4748:body/1/0/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4774:4806:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4774:4782:body/0/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4776:4780:body/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4783:4806:body/1/group", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4785:4804:body/1/0/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4785:4793:body/1/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4796:4804:body/1/0/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4831:4865:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4831:4839:body/0/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4833:4837:body/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4840:4865:body/1/group", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4842:4863:body/1/0/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4842:4851:body/1/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4854:4863:body/1/0/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4889:4921:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4889:4897:body/0/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4891:4895:body/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4898:4921:body/1/group", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4900:4919:body/1/0/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4900:4908:body/1/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4911:4919:body/1/0/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4973:4992:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4973:4977:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4978:4983:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4984:4992:body/2/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5014:5038:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5014:5027:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5028:5031:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5032:5038:body/2/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5059:5106:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5059:5062:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5063:5073:body/1/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5074:5077:body/2/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5078:5102:body/3/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5080:5100:body/3/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5103:5106:body/4/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5136:5183:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5136:5155:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5156:5183:body/1/repeat", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5158:5181:body/1/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5158:5161:body/1/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5162:5181:body/1/0/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5212:5237:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5212:5222:body/0/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5223:5226:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5227:5237:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5271:5385:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5271:5294:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5301:5324:body/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5331:5355:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5362:5385:body/3/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5566:5711:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5566:5582:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5583:5593:body/1/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5610:5613:body/2/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5620:5652:body/3/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5622:5638:body/3/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5657:5660:body/4/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5665:5668:body/5/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5675:5691:body/6/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5708:5711:body/7/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5892:6037:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5892:5908:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5909:5919:body/1/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5936:5939:body/2/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5946:5978:body/3/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5948:5964:body/3/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5983:5986:body/4/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5991:5994:body/5/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6001:6017:body/6/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6034:6037:body/7/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6220:6367:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6220:6237:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6238:6248:body/1/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6265:6268:body/2/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6275:6307:body/3/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6277:6293:body/3/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6312:6315:body/4/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6320:6323:body/5/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6330:6347:body/6/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6364:6367:body/7/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6548:6693:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6548:6564:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6565:6575:body/1/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6592:6595:body/2/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6602:6634:body/3/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6604:6620:body/3/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6639:6642:body/4/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6647:6650:body/5/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6657:6673:body/6/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6690:6693:body/7/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6770:6825:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6770:6785:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6794:6825:body/1/repeat", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6796:6815:body/1/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6796:6799:body/1/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6800:6815:body/1/0/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6939:6990:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6939:6942:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6943:6953:body/1/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6965:6990:body/2/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6967:6982:body/2/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6967:6971:body/2/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6972:6982:body/2/0/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7017:7035:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7017:7025:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7028:7035:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7061:7069:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7061:7069:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7096:7105:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7096:7105:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7131:7139:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7131:7139:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7159:7233:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7159:7175:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7178:7194:body/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7197:7214:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7217:7233:body/3/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7559:7725:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7559:7569:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7570:7594:body/1/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7572:7592:body/1/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7572:7583:body/1/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7584:7592:body/1/0/1/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7586:7590:body/1/0/1/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7595:7612:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7617:7624:body/3/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7619:7622:body/3/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7629:7693:body/4/group", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7631:7685:body/4/0/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7631:7666:body/4/0/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7631:7640:body/4/0/0/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7652:7655:body/4/0/0/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7656:7666:body/4/0/0/2/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7675:7685:body/4/0/1/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7698:7701:body/5/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7702:7721:body/6/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7704:7713:body/6/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7722:7725:body/7/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7837:8016:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7837:7847:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7852:7915:body/1/group", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7854:7913:body/1/0/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7854:7903:body/1/0/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7854:7878:body/1/0/0/0/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7856:7876:body/1/0/0/0/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7856:7867:body/1/0/0/0/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7868:7876:body/1/0/0/0/0/1/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7870:7874:body/1/0/0/0/0/1/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7879:7895:body/1/0/0/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7896:7903:body/1/0/0/2/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7898:7901:body/1/0/0/2/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7906:7913:body/1/0/1/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7908:7911:body/1/0/1/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7920:7984:body/2/group", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7922:7976:body/2/0/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7922:7957:body/2/0/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7922:7931:body/2/0/0/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7943:7946:body/2/0/0/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7947:7957:body/2/0/0/2/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7966:7976:body/2/0/1/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7989:7992:body/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7993:8012:body/4/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7995:8004:body/4/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8013:8016:body/5/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8128:8293:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8128:8138:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8139:8163:body/1/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8141:8161:body/1/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8141:8152:body/1/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8153:8161:body/1/0/1/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8155:8159:body/1/0/1/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8164:8180:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8185:8192:body/3/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8187:8190:body/3/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8197:8261:body/4/group", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8199:8253:body/4/0/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8199:8234:body/4/0/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8199:8208:body/4/0/0/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8220:8223:body/4/0/0/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8224:8234:body/4/0/0/2/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8243:8253:body/4/0/1/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8266:8269:body/5/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8270:8289:body/6/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8272:8281:body/6/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8290:8293:body/7/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8405:8570:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8405:8415:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8416:8440:body/1/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8418:8438:body/1/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8418:8429:body/1/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8430:8438:body/1/0/1/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8432:8436:body/1/0/1/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8441:8457:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8462:8469:body/3/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8464:8467:body/3/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8474:8538:body/4/group", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8476:8530:body/4/0/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8476:8511:body/4/0/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8476:8485:body/4/0/0/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8497:8500:body/4/0/0/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8501:8511:body/4/0/0/2/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8520:8530:body/4/0/1/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8543:8546:body/5/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8547:8566:body/6/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8549:8558:body/6/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8567:8570:body/7/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8775:8809:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8775:8796:body/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8775:8781:body/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8782:8796:body/0/1/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8784:8794:body/0/1/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8799:8809:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8912:8979:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8912:8934:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8935:8945:body/1/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8952:8955:body/2/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8956:8975:body/3/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8958:8967:body/3/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8976:8979:body/4/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9146:9226:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9146:9163:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9175:9178:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9179:9195:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9206:9209:body/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9210:9226:body/4/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9322:9435:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9322:9337:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9351:9371:body/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9385:9411:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9425:9435:body/3/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9505:9566:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9505:9523:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9532:9566:body/1/repeat", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9534:9556:body/1/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9534:9537:body/1/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9538:9556:body/1/0/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9761:9809:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9761:9771:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9778:9809:body/1/repeat", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9780:9800:body/1/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9780:9785:body/1/0/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9790:9800:body/1/0/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9916:9968:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9916:9928:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9935:9968:body/1/repeat", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9937:9959:body/1/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9937:9942:body/1/0/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9947:9959:body/1/0/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9984:9993:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9984:9987:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9990:9993:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10008:10017:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10008:10011:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10014:10017:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10125:10379:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10125:10136:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10143:10154:body/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10161:10172:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10179:10191:body/3/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10198:10209:body/4/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10216:10227:body/5/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10234:10248:body/6/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10255:10266:body/7/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10273:10286:body/8/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10293:10305:body/9/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10312:10325:body/10/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10332:10343:body/11/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10350:10361:body/12/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10368:10379:body/13/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10435:10472:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10435:10440:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10441:10444:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10445:10463:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10469:10472:body/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10528:10565:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10528:10533:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10534:10537:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10538:10556:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10562:10565:body/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10621:10658:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10621:10626:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10627:10630:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10631:10649:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10655:10658:body/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10716:10754:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10716:10722:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10723:10726:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10727:10745:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10751:10754:body/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10818:10890:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10818:10823:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10824:10827:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10828:10846:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10854:10886:body/3/repeat", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10856:10878:body/3/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10856:10859:body/3/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10860:10878:body/3/0/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10887:10890:body/4/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10954:11026:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10954:10959:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10960:10963:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10964:10982:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10990:11022:body/3/repeat", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10992:11014:body/3/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10992:10995:body/3/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10996:11014:body/3/0/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11023:11026:body/4/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11074:11090:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11074:11082:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11083:11086:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11087:11090:body/2/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11146:11183:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11146:11151:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11152:11155:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11156:11174:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11180:11183:body/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11243:11282:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11243:11250:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11251:11254:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11255:11273:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11279:11282:body/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11340:11378:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11340:11346:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11347:11350:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11351:11369:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11375:11378:body/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11438:11477:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11438:11445:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11446:11449:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11450:11468:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11474:11477:body/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11544:11615:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11544:11549:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11550:11553:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11554:11572:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11579:11582:body/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11583:11601:body/4/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11612:11615:body/5/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11671:11708:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11671:11676:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11677:11680:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11681:11699:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11705:11708:body/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11764:11801:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11764:11769:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11770:11773:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11774:11792:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11798:11801:body/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11955:12031:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11955:11962:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11963:11966:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11967:11983:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11991:11994:body/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11995:12013:body/4/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12028:12031:body/5/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12060:12340:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12060:12077:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12084:12105:body/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12112:12124:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12131:12143:body/3/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12150:12163:body/4/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12170:12185:body/5/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12192:12203:body/6/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12210:12224:body/7/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12231:12255:body/8/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12262:12268:body/9/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12275:12286:body/10/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12293:12309:body/11/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12316:12340:body/12/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12316:12319:body/12/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12320:12336:body/12/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12337:12340:body/12/2/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12493:12538:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12493:12506:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12507:12510:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12511:12527:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12535:12538:body/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12612:12657:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12612:12625:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12626:12629:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12630:12646:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12654:12657:body/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12717:12755:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12717:12723:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12724:12727:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12728:12744:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12752:12755:body/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12819:12859:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12819:12827:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12828:12831:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12832:12848:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12856:12859:body/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12920:12957:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12920:12925:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12926:12929:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12930:12946:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12954:12957:body/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13282:13323:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13282:13293:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13301:13315:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13316:13319:body/2/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13320:13323:body/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13401:13442:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13401:13412:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13420:13434:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13435:13438:body/2/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13439:13442:body/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13506:13540:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13506:13517:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13525:13532:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13533:13536:body/2/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13537:13540:body/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13608:13644:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13608:13619:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13627:13636:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13637:13640:body/2/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13641:13644:body/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13864:13978:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13864:13876:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13877:13880:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13881:13897:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13905:13908:body/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13909:13925:body/4/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13940:13974:body/5/repeat", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13942:13962:body/5/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13942:13945:body/5/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13946:13962:body/5/0/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13975:13978:body/6/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14056:14168:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14056:14066:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14067:14070:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14071:14087:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14095:14098:body/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14099:14115:body/4/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14130:14164:body/5/repeat", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14132:14152:body/5/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14132:14135:body/5/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14136:14152:body/5/0/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14165:14168:body/6/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14246:14358:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14246:14256:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14257:14260:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14261:14277:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14285:14288:body/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14289:14305:body/4/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14320:14354:body/5/repeat", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14322:14342:body/5/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14322:14325:body/5/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14326:14342:body/5/0/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14355:14358:body/6/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14424:14527:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14424:14440:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14448:14453:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14454:14457:body/2/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14458:14474:body/3/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14487:14523:body/4/repeat", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14489:14509:body/4/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14489:14492:body/4/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14493:14509:body/4/0/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14524:14527:body/5/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14692:14810:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14692:14715:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14723:14736:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14737:14740:body/2/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14741:14757:body/3/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14772:14806:body/4/repeat", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14774:14794:body/4/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14774:14777:body/4/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14778:14794:body/4/0/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14807:14810:body/5/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14892:15008:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14892:14915:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14923:14934:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14935:14938:body/2/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14939:14955:body/3/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14970:15004:body/4/repeat", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14972:14992:body/4/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14972:14975:body/4/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14976:14992:body/4/0/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15005:15008:body/5/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15090:15206:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15090:15113:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15121:15132:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15133:15136:body/2/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15137:15153:body/3/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15168:15202:body/4/repeat", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15170:15190:body/4/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15170:15173:body/4/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15174:15190:body/4/0/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15203:15206:body/5/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15240:15310:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15240:15259:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15262:15281:body/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15284:15296:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15299:15310:body/3/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15459:15497:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15459:15470:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15471:15474:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15475:15486:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15494:15497:body/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15661:15740:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15661:15674:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15675:15678:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15679:15695:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15707:15710:body/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15711:15727:body/4/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15737:15740:body/5/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15851:15979:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15851:15867:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15868:15871:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15872:15881:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15892:15895:body/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15896:15912:body/4/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15924:15927:body/5/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15928:15937:body/6/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15946:15949:body/7/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15950:15966:body/8/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15976:15979:body/9/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15999:16081:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15999:16007:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16010:16019:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16022:16033:body/2/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16036:16046:body/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16049:16057:body/4/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16060:16070:body/5/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16073:16081:body/6/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16249:16518:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16249:16273:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16280:16299:body/1/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16280:16283:body/1/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16284:16295:body/1/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16296:16299:body/1/2/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16306:16335:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16342:16361:body/3/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16364:16383:body/4/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16386:16398:body/5/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16405:16425:body/6/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16428:16448:body/7/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16451:16464:body/8/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16471:16477:body/9/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16484:16495:body/10/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16502:16518:body/11/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17034:17050:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17034:17050:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17075:17091:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17075:17091:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17116:17132:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17116:17132:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17224:17829:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17224:17327:body/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17224:17241:body/0/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17249:17252:body/0/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17253:17268:body/0/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17276:17279:body/0/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17280:17293:body/0/4/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17299:17302:body/0/5/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17303:17317:body/0/6/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17324:17327:body/0/7/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17334:17412:body/1/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17334:17351:body/1/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17359:17362:body/1/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17363:17378:body/1/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17386:17389:body/1/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17390:17403:body/1/4/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17409:17412:body/1/5/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17419:17503:body/2/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17419:17436:body/2/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17444:17447:body/2/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17448:17463:body/2/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17471:17474:body/2/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17475:17478:body/2/4/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17479:17493:body/2/5/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17500:17503:body/2/6/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17510:17569:body/3/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17510:17527:body/3/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17535:17538:body/3/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17539:17554:body/3/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17562:17565:body/3/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17566:17569:body/3/4/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17576:17656:body/4/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17576:17593:body/4/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17601:17604:body/4/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17605:17608:body/4/2/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17609:17622:body/4/3/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17628:17631:body/4/4/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17632:17646:body/4/5/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17653:17656:body/4/6/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17663:17718:body/5/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17663:17680:body/5/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17688:17691:body/5/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17692:17695:body/5/2/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17696:17709:body/5/3/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17715:17718:body/5/4/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17725:17786:body/6/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17725:17742:body/6/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17750:17753:body/6/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17754:17757:body/6/2/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17758:17761:body/6/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17762:17776:body/6/4/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17783:17786:body/6/5/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17793:17829:body/7/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17793:17810:body/7/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17818:17821:body/7/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17822:17825:body/7/2/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17826:17829:body/7/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17923:18544:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17923:18028:body/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17923:17942:body/0/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17950:17953:body/0/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17954:17969:body/0/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17977:17980:body/0/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17981:17994:body/0/4/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18000:18003:body/0/5/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18004:18018:body/0/6/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18025:18028:body/0/7/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18035:18115:body/1/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18035:18054:body/1/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18062:18065:body/1/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18066:18081:body/1/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18089:18092:body/1/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18093:18106:body/1/4/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18112:18115:body/1/5/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18122:18208:body/2/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18122:18141:body/2/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18149:18152:body/2/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18153:18168:body/2/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18176:18179:body/2/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18180:18183:body/2/4/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18184:18198:body/2/5/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18205:18208:body/2/6/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18215:18276:body/3/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18215:18234:body/3/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18242:18245:body/3/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18246:18261:body/3/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18269:18272:body/3/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18273:18276:body/3/4/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18283:18365:body/4/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18283:18302:body/4/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18310:18313:body/4/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18314:18317:body/4/2/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18318:18331:body/4/3/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18337:18340:body/4/4/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18341:18355:body/4/5/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18362:18365:body/4/6/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18372:18429:body/5/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18372:18391:body/5/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18399:18402:body/5/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18403:18406:body/5/2/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18407:18420:body/5/3/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18426:18429:body/5/4/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18436:18499:body/6/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18436:18455:body/6/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18463:18466:body/6/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18467:18470:body/6/2/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18471:18474:body/6/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18475:18489:body/6/4/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18496:18499:body/6/5/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18506:18544:body/7/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18506:18525:body/7/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18533:18536:body/7/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18537:18540:body/7/2/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18541:18544:body/7/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18570:18613:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18570:18591:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18594:18613:body/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18814:18860:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18814:18824:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18831:18860:body/1/repeat", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18833:18851:body/1/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18833:18836:body/1/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18841:18851:body/1/0/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18900:18924:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18900:18903:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18904:18920:body/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18921:18924:body/2/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19131:19494:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19131:19152:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19159:19171:body/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19178:19196:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19203:19222:body/3/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19229:19244:body/4/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19251:19280:body/5/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19287:19311:body/6/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19318:19337:body/7/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19340:19359:body/8/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19362:19374:body/9/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19381:19401:body/10/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19404:19424:body/11/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19427:19440:body/12/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19447:19453:body/13/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19460:19471:body/14/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19478:19494:body/15/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19576:19622:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19576:19579:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19580:19603:body/1/group", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19582:19601:body/1/0/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19582:19590:body/1/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19593:19601:body/1/0/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19604:19607:body/2/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19608:19611:body/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19612:19622:body/4/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19712:19761:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19712:19715:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19716:19726:body/1/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19733:19737:body/2/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19738:19761:body/3/group", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19740:19759:body/3/0/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19740:19748:body/3/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19751:19759:body/3/0/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19992:20058:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19992:20012:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20019:20058:body/1/repeat", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20021:20049:body/1/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20021:20024:body/1/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20029:20049:body/1/0/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20195:20261:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20195:20215:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20222:20261:body/1/repeat", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20224:20252:body/1/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20224:20227:body/1/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20232:20252:body/1/0/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20398:20450:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20398:20411:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20418:20450:body/1/repeat", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20420:20441:body/1/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20420:20423:body/1/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20428:20441:body/1/0/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20588:20626:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20588:20593:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20594:20597:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20598:20615:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20623:20626:body/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20660:21092:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20660:20673:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20680:20692:body/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20699:20721:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20728:20753:body/3/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20760:20768:body/4/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20775:20794:body/5/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20801:20818:body/6/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20825:20842:body/7/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20849:20867:body/8/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20874:20890:body/9/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20897:20913:body/10/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20920:20937:body/11/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20944:20963:body/12/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20970:20992:body/13/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20999:21005:body/14/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21012:21019:body/15/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21026:21037:body/16/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21044:21060:body/17/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21067:21092:body/18/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21067:21070:body/18/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21071:21088:body/18/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21089:21092:body/18/2/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21186:21242:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21186:21203:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21210:21220:body/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21225:21242:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21325:21455:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21325:21350:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21364:21384:body/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21398:21424:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21438:21455:body/3/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21719:21773:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21719:21735:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21742:21752:body/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21757:21773:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21800:21811:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21800:21804:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21807:21811:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21895:21948:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21895:21911:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21918:21927:body/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21932:21948:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21974:22011:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21974:21978:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21981:21985:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21988:21992:body/2/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21995:21999:body/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22002:22005:body/4/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22008:22011:body/5/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22165:22335:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22165:22181:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22195:22211:body/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22225:22242:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22256:22280:body/3/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22294:22305:body/4/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22319:22335:body/5/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22510:22640:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22510:22514:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22515:22518:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22519:22536:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22548:22551:body/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22556:22559:body/4/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22560:22576:body/5/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22587:22590:body/6/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22595:22601:body/7/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22606:22609:body/8/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22610:22626:body/9/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22637:22640:body/10/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22722:22972:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22722:22742:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22756:22782:body/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22796:22821:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22835:22851:body/3/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22865:22882:body/4/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22896:22912:body/5/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22926:22942:body/6/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22956:22972:body/7/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23147:23245:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23147:23150:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23151:23168:body/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23180:23183:body/2/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23184:23200:body/3/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23211:23214:body/4/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23215:23231:body/5/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23242:23245:body/6/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23435:23553:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23435:23442:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23443:23446:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23453:23463:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23475:23504:body/3/repeat", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23477:23491:body/3/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23477:23480:body/3/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23481:23491:body/3/0/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23511:23514:body/4/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23515:23532:body/5/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23550:23553:body/6/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23628:23677:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23628:23645:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23657:23661:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23662:23677:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23761:23791:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23761:23770:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23771:23775:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23776:23791:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23871:23887:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23871:23887:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24002:24120:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24002:24009:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24010:24013:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24020:24030:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24042:24071:body/3/repeat", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24044:24058:body/3/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24044:24047:body/3/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24048:24058:body/3/0/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24078:24081:body/4/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24082:24099:body/5/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24117:24120:body/6/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24195:24244:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24195:24212:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24224:24228:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24229:24244:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24328:24358:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24328:24337:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24338:24342:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24343:24358:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24438:24454:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24438:24454:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24571:24692:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24571:24578:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24579:24582:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24589:24600:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24612:24642:body/3/repeat", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24614:24629:body/3/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24614:24617:body/3/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24618:24629:body/3/0/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24649:24652:body/4/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24653:24671:body/5/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24689:24692:body/6/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24769:24819:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24769:24786:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24798:24802:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24803:24819:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24905:24936:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24905:24914:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24915:24919:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24920:24936:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25018:25035:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25018:25035:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25248:25299:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25248:25251:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25252:25262:body/1/tokenRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25269:25299:body/2/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25271:25291:body/2/0/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25271:25279:body/2/0/0/optional", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25273:25277:body/2/0/0/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25280:25291:body/2/0/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25321:25432:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25321:25329:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25332:25340:body/1/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25343:25350:body/2/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25353:25360:body/3/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25367:25375:body/4/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25378:25386:body/5/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25389:25398:body/6/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25401:25410:body/7/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25413:25421:body/8/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25424:25432:body/9/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25749:25925:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25749:25765:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25779:25796:body/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25810:25826:body/2/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25840:25856:body/3/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25870:25886:body/4/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25900:25925:body/5/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25900:25903:body/5/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25904:25914:body/5/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25922:25925:body/5/2/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:26172:26188:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:26172:26188:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:26270:26287:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:26270:26287:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:26368:26384:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:26368:26384:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:26465:26481:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:26465:26481:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:26562:26578:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:26562:26578:body/0/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:26666:26691:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:26666:26669:body/0/literal", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:26670:26680:body/1/ruleRef", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:26688:26691:body/2/literal"];
const BODIES:&[&str]=&["expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1175:1742:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2175:2226:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2682:2765:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2836:2877:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3028:3150:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3323:3499:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3672:3848:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4023:4201:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4374:4550:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4569:4673:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4698:4750:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4774:4806:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4831:4865:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4889:4921:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4973:4992:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5014:5038:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5059:5106:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5136:5183:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5212:5237:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5271:5385:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5566:5711:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5892:6037:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6220:6367:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6548:6693:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6770:6825:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6939:6990:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7017:7035:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7061:7069:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7096:7105:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7131:7139:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7159:7233:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7559:7725:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7837:8016:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8128:8293:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8405:8570:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8775:8809:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8912:8979:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9146:9226:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9322:9435:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9505:9566:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9761:9809:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9916:9968:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9984:9993:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10008:10017:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10125:10379:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10435:10472:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10528:10565:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10621:10658:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10716:10754:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10818:10890:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10954:11026:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11074:11090:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11146:11183:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11243:11282:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11340:11378:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11438:11477:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11544:11615:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11671:11708:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11764:11801:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11955:12031:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12060:12340:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12493:12538:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12612:12657:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12717:12755:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12819:12859:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12920:12957:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13282:13323:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13401:13442:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13506:13540:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13608:13644:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13864:13978:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14056:14168:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14246:14358:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14424:14527:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14692:14810:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14892:15008:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15090:15206:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15240:15310:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15459:15497:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15661:15740:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15851:15979:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15999:16081:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:16249:16518:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17034:17050:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17075:17091:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17116:17132:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17224:17829:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17923:18544:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18570:18613:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18814:18860:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18900:18924:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19131:19494:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19576:19622:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19712:19761:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19992:20058:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20195:20261:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20398:20450:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20588:20626:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20660:21092:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21186:21242:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21325:21455:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21719:21773:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21800:21811:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21895:21948:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21974:22011:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22165:22335:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22510:22640:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22722:22972:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23147:23245:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23435:23553:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23628:23677:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23761:23791:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23871:23887:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24002:24120:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24195:24244:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24328:24358:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24438:24454:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24571:24692:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24769:24819:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24905:24936:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25018:25035:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25248:25299:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25321:25432:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25749:25925:body/choice", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:26172:26188:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:26270:26287:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:26368:26384:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:26465:26481:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:26562:26578:body/seq", "expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:26666:26691:body/seq"];
const RULE_NODE:&[bool]=&[true, true, true, true, false, true, true, true, true, false, false, false, false, false, true, false, false, false, false, false, true, true, true, true, true, true, false, false, false, false, false, true, true, true, true, false, true, true, true, true, true, true, false, false, false, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, false, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, false, true, true, true, false, false, false, false, false, true, true, false, true, false, false, true, true, true, true, true, true, false, true, true, true, false, true, false, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, false, true, true, true, true, true, true, true];
const RULE_SLOTS:&[u32]=&[9, 1, 3, 3, 0, 4, 4, 4, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 3, 3, 3, 2, 2, 0, 0, 0, 0, 0, 3, 3, 3, 3, 0, 2, 3, 1, 2, 5, 5, 0, 0, 0, 1, 1, 1, 1, 3, 3, 0, 1, 1, 1, 1, 2, 1, 1, 2, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 3, 3, 3, 3, 3, 3, 3, 0, 1, 2, 4, 0, 0, 0, 0, 0, 4, 4, 0, 5, 0, 0, 1, 1, 5, 5, 5, 1, 0, 3, 1, 3, 0, 3, 0, 1, 3, 1, 3, 4, 2, 1, 1, 4, 2, 1, 1, 4, 2, 1, 1, 2, 0, 1, 1, 1, 1, 1, 1, 1];
const SITES:&[(&str,&str)]=&[("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1177:1186:body/0/0/ruleRef/capture/0", "codeBlocks"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1203:1220:body/1/0/ruleRef/capture/0", "imports"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1234:1253:body/2/0/ruleRef/capture/0", "declarations"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1295:1315:body/4/0/0/0/ruleRef/capture/0", "expression"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1330:1347:body/4/0/0/1/0/ruleRef/capture/0", "methods"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1369:1390:body/4/0/1/0/ruleRef/capture/0", "expression"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1405:1422:body/4/0/1/1/0/ruleRef/capture/0", "methods"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1444:1464:body/4/0/2/0/ruleRef/capture/0", "expression"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1479:1496:body/4/0/2/1/0/ruleRef/capture/0", "methods"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1518:1538:body/4/0/3/0/ruleRef/capture/0", "expression"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1553:1570:body/4/0/3/1/0/ruleRef/capture/0", "methods"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1592:1612:body/4/0/4/0/ruleRef/capture/0", "expression"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1627:1644:body/4/0/4/1/0/ruleRef/capture/0", "methods"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1666:1693:body/4/0/5/0/ruleRef/capture/0", "expression"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:1708:1725:body/4/0/5/1/0/ruleRef/capture/0", "methods"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2175:2226:body/0/group/capture/0", "source"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2691:2700:body/1/ruleRef/capture/0", "className"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2718:2728:body/2/0/1/tokenRef/capture/0", "method"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2744:2754:body/4/tokenRef/capture/0", "alias"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2836:2846:body/0/tokenRef/capture/0", "head"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:2859:2869:body/1/0/1/tokenRef/capture/0", "tail"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3354:3364:body/2/tokenRef/capture/0", "varName"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3411:3423:body/4/0/1/0/ruleRef/capture/0", "onlyIfAbsent"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3440:3456:body/4/0/2/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3472:3483:body/5/0/ruleRef/capture/0", "desc"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3703:3713:body/2/tokenRef/capture/0", "varName"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3760:3772:body/4/0/1/0/ruleRef/capture/0", "onlyIfAbsent"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3789:3805:body/4/0/2/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:3821:3832:body/5/0/ruleRef/capture/0", "desc"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4054:4064:body/2/tokenRef/capture/0", "varName"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4112:4124:body/4/0/1/0/ruleRef/capture/0", "onlyIfAbsent"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4141:4158:body/4/0/2/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4174:4185:body/5/0/ruleRef/capture/0", "desc"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4405:4415:body/2/tokenRef/capture/0", "varName"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4462:4474:body/4/0/1/0/ruleRef/capture/0", "onlyIfAbsent"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4491:4507:body/4/0/2/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:4523:4534:body/5/0/ruleRef/capture/0", "desc"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5583:5593:body/1/tokenRef/capture/0", "methodName"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5622:5638:body/3/0/ruleRef/capture/0", "parameters"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5675:5691:body/6/ruleRef/capture/0", "expression"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5909:5919:body/1/tokenRef/capture/0", "methodName"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:5948:5964:body/3/0/ruleRef/capture/0", "parameters"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6001:6017:body/6/ruleRef/capture/0", "expression"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6238:6248:body/1/tokenRef/capture/0", "methodName"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6277:6293:body/3/0/ruleRef/capture/0", "parameters"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6330:6347:body/6/ruleRef/capture/0", "expression"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6565:6575:body/1/tokenRef/capture/0", "methodName"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6604:6620:body/3/0/ruleRef/capture/0", "parameters"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6657:6673:body/6/ruleRef/capture/0", "expression"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6770:6785:body/0/ruleRef/capture/0", "values"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6800:6815:body/1/0/1/ruleRef/capture/0", "values"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6943:6953:body/1/tokenRef/capture/0", "paramName"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:6972:6982:body/2/0/1/ruleRef/capture/0", "type"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7631:7640:body/4/0/0/0/ruleRef/capture/0", "className"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7656:7666:body/4/0/0/2/tokenRef/capture/0", "name"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7675:7685:body/4/0/1/tokenRef/capture/0", "name"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7704:7713:body/6/0/ruleRef/capture/0", "args"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7922:7931:body/2/0/0/0/ruleRef/capture/0", "className"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7947:7957:body/2/0/0/2/tokenRef/capture/0", "name"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7966:7976:body/2/0/1/tokenRef/capture/0", "name"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:7995:8004:body/4/0/ruleRef/capture/0", "args"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8199:8208:body/4/0/0/0/ruleRef/capture/0", "className"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8224:8234:body/4/0/0/2/tokenRef/capture/0", "name"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8243:8253:body/4/0/1/tokenRef/capture/0", "name"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8272:8281:body/6/0/ruleRef/capture/0", "args"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8476:8485:body/4/0/0/0/ruleRef/capture/0", "className"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8501:8511:body/4/0/0/2/tokenRef/capture/0", "name"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8520:8530:body/4/0/1/tokenRef/capture/0", "name"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8549:8558:body/6/0/ruleRef/capture/0", "args"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8935:8945:body/1/tokenRef/capture/0", "name"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:8958:8967:body/3/0/ruleRef/capture/0", "args"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9146:9163:body/0/ruleRef/capture/0", "condition"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9179:9195:body/2/ruleRef/capture/0", "thenExpr"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9210:9226:body/4/ruleRef/capture/0", "elseExpr"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9322:9337:body/0/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9351:9371:body/1/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9385:9411:body/2/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9425:9435:body/3/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9505:9523:body/0/ruleRef/capture/0", "values"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9538:9556:body/1/0/1/ruleRef/capture/0", "values"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9761:9771:body/0/ruleRef/capture/0", "left"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9780:9785:body/1/0/0/ruleRef/capture/0", "op"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9790:9800:body/1/0/1/ruleRef/capture/0", "right"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9916:9928:body/0/ruleRef/capture/0", "left"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9937:9942:body/1/0/0/ruleRef/capture/0", "op"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:9947:9959:body/1/0/1/ruleRef/capture/0", "right"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10445:10463:body/2/ruleRef/capture/0", "arg"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10538:10556:body/2/ruleRef/capture/0", "arg"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10631:10649:body/2/ruleRef/capture/0", "arg"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10727:10745:body/2/ruleRef/capture/0", "arg"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10828:10846:body/2/ruleRef/capture/0", "first"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10860:10878:body/3/0/1/ruleRef/capture/0", "rest"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10964:10982:body/2/ruleRef/capture/0", "first"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:10996:11014:body/3/0/1/ruleRef/capture/0", "rest"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11156:11174:body/2/ruleRef/capture/0", "arg"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11255:11273:body/2/ruleRef/capture/0", "arg"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11351:11369:body/2/ruleRef/capture/0", "arg"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11450:11468:body/2/ruleRef/capture/0", "arg"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11554:11572:body/2/ruleRef/capture/0", "base"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11583:11601:body/4/ruleRef/capture/0", "exponent"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11681:11699:body/2/ruleRef/capture/0", "arg"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11774:11792:body/2/ruleRef/capture/0", "arg"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11967:11983:body/2/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:11995:12013:body/4/ruleRef/capture/0", "defaultValue"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12511:12527:body/2/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12630:12646:body/2/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12728:12744:body/2/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12832:12848:body/2/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:12930:12946:body/2/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13282:13293:body/0/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13401:13412:body/0/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13506:13517:body/0/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13608:13619:body/0/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13881:13897:body/2/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13909:13925:body/4/ruleRef/capture/0", "patterns"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:13946:13962:body/5/0/1/ruleRef/capture/0", "patterns"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14071:14087:body/2/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14099:14115:body/4/ruleRef/capture/0", "patterns"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14136:14152:body/5/0/1/ruleRef/capture/0", "patterns"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14261:14277:body/2/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14289:14305:body/4/ruleRef/capture/0", "patterns"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14326:14342:body/5/0/1/ruleRef/capture/0", "patterns"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14424:14440:body/0/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14458:14474:body/3/ruleRef/capture/0", "candidates"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14493:14509:body/4/0/1/ruleRef/capture/0", "candidates"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14692:14715:body/0/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14741:14757:body/3/ruleRef/capture/0", "patterns"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14778:14794:body/4/0/1/ruleRef/capture/0", "patterns"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14892:14915:body/0/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14939:14955:body/3/ruleRef/capture/0", "patterns"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:14976:14992:body/4/0/1/ruleRef/capture/0", "patterns"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15090:15113:body/0/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15137:15153:body/3/ruleRef/capture/0", "patterns"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15174:15190:body/4/0/1/ruleRef/capture/0", "patterns"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15475:15486:body/2/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15679:15695:body/2/ruleRef/capture/0", "startHour"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15711:15727:body/4/ruleRef/capture/0", "endHour"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15872:15881:body/2/ruleRef/capture/0", "startDay"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15896:15912:body/4/ruleRef/capture/0", "startHour"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15928:15937:body/6/ruleRef/capture/0", "endDay"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:15950:15966:body/8/ruleRef/capture/0", "endHour"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17224:17241:body/0/0/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17253:17268:body/0/2/ruleRef/capture/0", "start"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17280:17293:body/0/4/ruleRef/capture/0", "end"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17303:17317:body/0/6/ruleRef/capture/0", "step"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17334:17351:body/1/0/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17363:17378:body/1/2/ruleRef/capture/0", "start"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17390:17403:body/1/4/ruleRef/capture/0", "end"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17419:17436:body/2/0/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17448:17463:body/2/2/ruleRef/capture/0", "start"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17479:17493:body/2/5/ruleRef/capture/0", "step"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17510:17527:body/3/0/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17539:17554:body/3/2/ruleRef/capture/0", "start"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17576:17593:body/4/0/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17609:17622:body/4/3/ruleRef/capture/0", "end"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17632:17646:body/4/5/ruleRef/capture/0", "step"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17663:17680:body/5/0/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17696:17709:body/5/3/ruleRef/capture/0", "end"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17725:17742:body/6/0/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17762:17776:body/6/4/ruleRef/capture/0", "step"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17793:17810:body/7/0/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17923:17942:body/0/0/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17954:17969:body/0/2/ruleRef/capture/0", "start"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:17981:17994:body/0/4/ruleRef/capture/0", "end"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18004:18018:body/0/6/ruleRef/capture/0", "step"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18035:18054:body/1/0/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18066:18081:body/1/2/ruleRef/capture/0", "start"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18093:18106:body/1/4/ruleRef/capture/0", "end"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18122:18141:body/2/0/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18153:18168:body/2/2/ruleRef/capture/0", "start"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18184:18198:body/2/5/ruleRef/capture/0", "step"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18215:18234:body/3/0/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18246:18261:body/3/2/ruleRef/capture/0", "start"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18283:18302:body/4/0/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18318:18331:body/4/3/ruleRef/capture/0", "end"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18341:18355:body/4/5/ruleRef/capture/0", "step"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18372:18391:body/5/0/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18407:18420:body/5/3/ruleRef/capture/0", "end"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18436:18455:body/6/0/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18475:18489:body/6/4/ruleRef/capture/0", "step"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18506:18525:body/7/0/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18814:18824:body/0/ruleRef/capture/0", "left"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18833:18836:body/1/0/0/literal/capture/0", "op"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:18841:18851:body/1/0/1/ruleRef/capture/0", "right"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19612:19622:body/4/tokenRef/capture/0", "name"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19716:19726:body/1/tokenRef/capture/0", "name"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:19992:20012:body/0/ruleRef/capture/0", "left"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20021:20024:body/1/0/0/literal/capture/0", "op"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20029:20049:body/1/0/1/ruleRef/capture/0", "right"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20195:20215:body/0/ruleRef/capture/0", "left"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20224:20227:body/1/0/0/literal/capture/0", "op"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20232:20252:body/1/0/1/ruleRef/capture/0", "right"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20398:20411:body/0/ruleRef/capture/0", "left"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20420:20423:body/1/0/0/literal/capture/0", "op"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20428:20441:body/1/0/1/ruleRef/capture/0", "right"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:20598:20615:body/2/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21186:21203:body/0/ruleRef/capture/0", "left"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21210:21220:body/1/ruleRef/capture/0", "op"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21225:21242:body/2/ruleRef/capture/0", "right"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21325:21350:body/0/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21364:21384:body/1/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21398:21424:body/2/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21438:21455:body/3/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21719:21735:body/0/ruleRef/capture/0", "left"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21742:21752:body/1/ruleRef/capture/0", "op"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21757:21773:body/2/ruleRef/capture/0", "right"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21895:21911:body/0/ruleRef/capture/0", "left"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21918:21927:body/1/ruleRef/capture/0", "op"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:21932:21948:body/2/ruleRef/capture/0", "right"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22165:22181:body/0/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22195:22211:body/1/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22225:22242:body/2/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22256:22280:body/3/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22294:22305:body/4/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22319:22335:body/5/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22519:22536:body/2/ruleRef/capture/0", "condition"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22560:22576:body/5/ruleRef/capture/0", "thenExpr"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22610:22626:body/9/ruleRef/capture/0", "elseExpr"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22722:22742:body/0/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22756:22782:body/1/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22796:22821:body/2/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22835:22851:body/3/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22865:22882:body/4/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22896:22912:body/5/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22926:22942:body/6/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:22956:22972:body/7/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23151:23168:body/1/ruleRef/capture/0", "condition"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23184:23200:body/3/ruleRef/capture/0", "thenExpr"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23215:23231:body/5/ruleRef/capture/0", "elseExpr"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23453:23463:body/2/ruleRef/capture/0", "firstCase"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23481:23491:body/3/0/1/ruleRef/capture/0", "moreCases"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23515:23532:body/5/ruleRef/capture/0", "defaultCase"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23628:23645:body/0/ruleRef/capture/0", "condition"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23662:23677:body/2/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23776:23791:body/2/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:23871:23887:body/0/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24020:24030:body/2/ruleRef/capture/0", "firstCase"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24048:24058:body/3/0/1/ruleRef/capture/0", "moreCases"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24082:24099:body/5/ruleRef/capture/0", "defaultCase"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24195:24212:body/0/ruleRef/capture/0", "condition"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24229:24244:body/2/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24343:24358:body/2/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24438:24454:body/0/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24589:24600:body/2/ruleRef/capture/0", "firstCase"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24618:24629:body/3/0/1/ruleRef/capture/0", "moreCases"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24653:24671:body/5/ruleRef/capture/0", "defaultCase"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24769:24786:body/0/ruleRef/capture/0", "condition"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24803:24819:body/2/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:24920:24936:body/2/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25018:25035:body/0/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25252:25262:body/1/tokenRef/capture/0", "name"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25280:25291:body/2/0/1/ruleRef/capture/0", "type"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25749:25765:body/0/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25779:25796:body/1/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25810:25826:body/2/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25840:25856:body/3/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25870:25886:body/4/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:25904:25914:body/5/1/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:26172:26188:body/0/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:26270:26287:body/0/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:26368:26384:body/0/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:26465:26481:body/0/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:26562:26578:body/0/ruleRef/capture/0", "value"), ("expr:tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf:26670:26680:body/1/ruleRef/capture/0", "value")];
impl<const DIAG: bool> Session<'_, DIAG> {
fn build_values(&mut self,root:EventId)->Result<Vec<u32>,String> {let mut out=Vec::new();self.build_values_into(root,&mut out)?;Ok(out)}
#[inline(never)] fn build_values_into(&mut self,root:EventId,out:&mut Vec<u32>)->Result<(),String> {let frame=0u8;let address=std::ptr::addr_of!(frame) as usize;let anchor=*self.mapping_anchor.get_or_insert(address);if self.mapping_depth>=self.options.limits.mapping_depth || anchor.abs_diff(address)>256*1024 {return Err("maximum mapping depth exceeded".into());}self.mapping_depth+=1;let result=self.build_values_inner(root,out);self.mapping_depth-=1;result}
fn build_values_inner(&mut self,mut root:EventId,out:&mut Vec<u32>)->Result<(),String> {
// 大半の呼出しは Join を含まない 1 本の枝（P4 実測: complex-x64 で 10,883 回すべてが 1 event）。
// その場合は pool から stack を借りずに降りる。
loop {match self.ev(root) {
Event::Capture{child,..}=>root=child,
Event::Values{child,span,wrap,text}=>{let start=out.len();if !text {self.value_region+=1;}let built=self.build_values_into(child,out);if !text {self.value_region-=1;}built?;if text || wrap && ((out.len()==start+1 && self.tree.kind(out[start])==tree::KIND_TEXT) || (out.len()==start && span[0]!=span[1] && !self.has_recovery(child) && !self.has_value_group(child))) {let text=self.semantic_text(child,span);out.truncate(start);out.push(text);}return Ok(());},
Event::Rule{rule,span,child,caps}=>return self.build_rule(rule,caps,span,child,out),
Event::Join(..)=>break,
_=>return Ok(()),}}
let mut stack=self.take_stack();stack.push((root,false));while let Some((id,_))=stack.pop() {match self.ev(id) {Event::Join(a,b)=>{stack.push((b,false));stack.push((a,false));},Event::Capture{child,..}=>stack.push((child,false)),Event::Values{child,span,wrap,text}=>{let start=out.len();if !text {self.value_region+=1;}let built=self.build_values_into(child,out);if !text {self.value_region-=1;}built?;if text || wrap && ((out.len()==start+1 && self.tree.kind(out[start])==tree::KIND_TEXT) || (out.len()==start && span[0]!=span[1] && !self.has_recovery(child) && !self.has_value_group(child))) {let text=self.semantic_text(child,span);out.truncate(start);out.push(text);}},Event::Rule{rule,span,child,caps}=>self.build_rule(rule,caps,span,child,out)?,_=>{}}}self.give_stack(stack);Ok(())}
fn leaf(&mut self,ty:usize,span:Span,text:u32)->Result<u32,String> {let span=self.span(span);self.tree.set_extent(text,span);match ty {
24=>{let op=self.tree.list(&[text]);let right=self.tree.list(&[]);Ok(self.tree.record(tree::K_g_TinyExpressionP4AST_2e_BinaryExpr,40,span,&[tree::NONE,op[0],op[1],right[0],right[1]]))},
_=>{let _=(span,text);Err(format!("unknown leaf type {ty}"))}}}
fn build_rule(&mut self,rule:usize,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);match rule {
0=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_0(caps,span,child,out);self.value_region=outer;built},
1=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_1(caps,span,child,out);self.value_region=outer;built},
2=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_2(caps,span,child,out);self.value_region=outer;built},
3=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_3(caps,span,child,out);self.value_region=outer;built},
4=>self.map_rule_4(caps,span,child,out),
5=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_5(caps,span,child,out);self.value_region=outer;built},
6=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_6(caps,span,child,out);self.value_region=outer;built},
7=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_7(caps,span,child,out);self.value_region=outer;built},
8=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_8(caps,span,child,out);self.value_region=outer;built},
9=>self.map_rule_9(caps,span,child,out),
10=>self.map_rule_10(caps,span,child,out),
11=>self.map_rule_11(caps,span,child,out),
12=>self.map_rule_12(caps,span,child,out),
13=>self.map_rule_13(caps,span,child,out),
14=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_14(caps,span,child,out);self.value_region=outer;built},
15=>self.map_rule_15(caps,span,child,out),
16=>self.map_rule_16(caps,span,child,out),
17=>self.map_rule_17(caps,span,child,out),
18=>self.map_rule_18(caps,span,child,out),
19=>self.map_rule_19(caps,span,child,out),
20=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_20(caps,span,child,out);self.value_region=outer;built},
21=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_21(caps,span,child,out);self.value_region=outer;built},
22=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_22(caps,span,child,out);self.value_region=outer;built},
23=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_23(caps,span,child,out);self.value_region=outer;built},
24=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_24(caps,span,child,out);self.value_region=outer;built},
25=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_25(caps,span,child,out);self.value_region=outer;built},
26=>self.map_rule_26(caps,span,child,out),
27=>self.map_rule_27(caps,span,child,out),
28=>self.map_rule_28(caps,span,child,out),
29=>self.map_rule_29(caps,span,child,out),
30=>self.map_rule_30(caps,span,child,out),
31=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_31(caps,span,child,out);self.value_region=outer;built},
32=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_32(caps,span,child,out);self.value_region=outer;built},
33=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_33(caps,span,child,out);self.value_region=outer;built},
34=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_34(caps,span,child,out);self.value_region=outer;built},
35=>self.map_rule_35(caps,span,child,out),
36=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_36(caps,span,child,out);self.value_region=outer;built},
37=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_37(caps,span,child,out);self.value_region=outer;built},
38=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_38(caps,span,child,out);self.value_region=outer;built},
39=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_39(caps,span,child,out);self.value_region=outer;built},
40=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_40(caps,span,child,out);self.value_region=outer;built},
41=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_41(caps,span,child,out);self.value_region=outer;built},
42=>self.map_rule_42(caps,span,child,out),
43=>self.map_rule_43(caps,span,child,out),
44=>self.map_rule_44(caps,span,child,out),
45=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_45(caps,span,child,out);self.value_region=outer;built},
46=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_46(caps,span,child,out);self.value_region=outer;built},
47=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_47(caps,span,child,out);self.value_region=outer;built},
48=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_48(caps,span,child,out);self.value_region=outer;built},
49=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_49(caps,span,child,out);self.value_region=outer;built},
50=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_50(caps,span,child,out);self.value_region=outer;built},
51=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_51(caps,span,child,out);self.value_region=outer;built},
52=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_52(caps,span,child,out);self.value_region=outer;built},
53=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_53(caps,span,child,out);self.value_region=outer;built},
54=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_54(caps,span,child,out);self.value_region=outer;built},
55=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_55(caps,span,child,out);self.value_region=outer;built},
56=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_56(caps,span,child,out);self.value_region=outer;built},
57=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_57(caps,span,child,out);self.value_region=outer;built},
58=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_58(caps,span,child,out);self.value_region=outer;built},
59=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_59(caps,span,child,out);self.value_region=outer;built},
60=>self.map_rule_60(caps,span,child,out),
61=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_61(caps,span,child,out);self.value_region=outer;built},
62=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_62(caps,span,child,out);self.value_region=outer;built},
63=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_63(caps,span,child,out);self.value_region=outer;built},
64=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_64(caps,span,child,out);self.value_region=outer;built},
65=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_65(caps,span,child,out);self.value_region=outer;built},
66=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_66(caps,span,child,out);self.value_region=outer;built},
67=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_67(caps,span,child,out);self.value_region=outer;built},
68=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_68(caps,span,child,out);self.value_region=outer;built},
69=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_69(caps,span,child,out);self.value_region=outer;built},
70=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_70(caps,span,child,out);self.value_region=outer;built},
71=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_71(caps,span,child,out);self.value_region=outer;built},
72=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_72(caps,span,child,out);self.value_region=outer;built},
73=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_73(caps,span,child,out);self.value_region=outer;built},
74=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_74(caps,span,child,out);self.value_region=outer;built},
75=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_75(caps,span,child,out);self.value_region=outer;built},
76=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_76(caps,span,child,out);self.value_region=outer;built},
77=>self.map_rule_77(caps,span,child,out),
78=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_78(caps,span,child,out);self.value_region=outer;built},
79=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_79(caps,span,child,out);self.value_region=outer;built},
80=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_80(caps,span,child,out);self.value_region=outer;built},
81=>self.map_rule_81(caps,span,child,out),
82=>self.map_rule_82(caps,span,child,out),
83=>self.map_rule_83(caps,span,child,out),
84=>self.map_rule_84(caps,span,child,out),
85=>self.map_rule_85(caps,span,child,out),
86=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_86(caps,span,child,out);self.value_region=outer;built},
87=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_87(caps,span,child,out);self.value_region=outer;built},
88=>self.map_rule_88(caps,span,child,out),
89=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_89(caps,span,child,out);self.value_region=outer;built},
90=>self.map_rule_90(caps,span,child,out),
91=>self.map_rule_91(caps,span,child,out),
92=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_92(caps,span,child,out);self.value_region=outer;built},
93=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_93(caps,span,child,out);self.value_region=outer;built},
94=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_94(caps,span,child,out);self.value_region=outer;built},
95=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_95(caps,span,child,out);self.value_region=outer;built},
96=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_96(caps,span,child,out);self.value_region=outer;built},
97=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_97(caps,span,child,out);self.value_region=outer;built},
98=>self.map_rule_98(caps,span,child,out),
99=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_99(caps,span,child,out);self.value_region=outer;built},
100=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_100(caps,span,child,out);self.value_region=outer;built},
101=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_101(caps,span,child,out);self.value_region=outer;built},
102=>self.map_rule_102(caps,span,child,out),
103=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_103(caps,span,child,out);self.value_region=outer;built},
104=>self.map_rule_104(caps,span,child,out),
105=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_105(caps,span,child,out);self.value_region=outer;built},
106=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_106(caps,span,child,out);self.value_region=outer;built},
107=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_107(caps,span,child,out);self.value_region=outer;built},
108=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_108(caps,span,child,out);self.value_region=outer;built},
109=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_109(caps,span,child,out);self.value_region=outer;built},
110=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_110(caps,span,child,out);self.value_region=outer;built},
111=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_111(caps,span,child,out);self.value_region=outer;built},
112=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_112(caps,span,child,out);self.value_region=outer;built},
113=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_113(caps,span,child,out);self.value_region=outer;built},
114=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_114(caps,span,child,out);self.value_region=outer;built},
115=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_115(caps,span,child,out);self.value_region=outer;built},
116=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_116(caps,span,child,out);self.value_region=outer;built},
117=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_117(caps,span,child,out);self.value_region=outer;built},
118=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_118(caps,span,child,out);self.value_region=outer;built},
119=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_119(caps,span,child,out);self.value_region=outer;built},
120=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_120(caps,span,child,out);self.value_region=outer;built},
121=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_121(caps,span,child,out);self.value_region=outer;built},
122=>self.map_rule_122(caps,span,child,out),
123=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_123(caps,span,child,out);self.value_region=outer;built},
124=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_124(caps,span,child,out);self.value_region=outer;built},
125=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_125(caps,span,child,out);self.value_region=outer;built},
126=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_126(caps,span,child,out);self.value_region=outer;built},
127=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_127(caps,span,child,out);self.value_region=outer;built},
128=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_128(caps,span,child,out);self.value_region=outer;built},
129=>{let outer=std::mem::take(&mut self.value_region);let built=self.map_rule_129(caps,span,child,out);self.value_region=outer;built},
_=>Err(format!("unknown rule {rule}"))}}
fn map_rule_0(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut f0=self.take_values();self.node_values(&caps,&[1],&[],None,&mut f0)?;
if !f0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_ImportDeclarationExpr}) {return Err("imports: mapped value type mismatch".into());}
let f0:Vec<u32>=f0;
let mut f1=self.take_values();self.node_values(&caps,&[2],&[],None,&mut f1)?;
if !f1.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr}) {return Err("declarations: mapped value type mismatch".into());}
let f1:Vec<u32>=f1;
let mut v2=self.take_values();self.node_values(&caps,&[3, 5, 7, 9, 11, 13],&[],None,&mut v2)?;
if !v2.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_ExpressionExpr}) {return Err("expression: mapped value type mismatch".into());}
if v2.is_empty() && self.missing_field(&caps,&[3, 5, 7, 9, 11, 13]) {return Ok(());}
if v2.len()!=1 {return Err("expression requires one node".into());}let f2=v2.pop().unwrap();self.give_values(v2);
let f2:u32=f2;
let mut f3=self.take_values();self.node_values(&caps,&[4, 6, 8, 10, 12, 14],&[],None,&mut f3)?;
if !f3.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr}) {return Err("methods: mapped value type mismatch".into());}
let f3:Vec<u32>=f3;
let mut f4=self.take_values();self.node_values(&caps,&[0],&[],None,&mut f4)?;
if !f4.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_CodeBlockExpr}) {return Err("codeBlocks: mapped value type mismatch".into());}
let f4:Vec<u32>=f4;
self.give_captures(caps);let span=self.span(span);let l0=self.tree.list(&f0);self.give_values(f0);let l1=self.tree.list(&f1);self.give_values(f1);let l3=self.tree.list(&f3);self.give_values(f3);let l4=self.tree.list(&f4);self.give_values(f4);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_FormulaExpr,0,span,&[l0[0],l0[1],l1[0],l1[1],f2,l3[0],l3[1],l4[0],l4[1]]);out.push(node);Ok(())}}
fn map_rule_1(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut t0=self.take_values();self.text_values(&caps,&[15],&mut t0);if t0.is_empty() && self.missing_field(&caps,&[15]) {return Ok(());}
if t0.len()!=1 {return Err("source requires one value".into());}let f0=t0.pop().unwrap();self.give_values(t0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_CodeBlockExpr,1,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_2(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[16],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_QualifiedNameExpr}) {return Err("className: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[16]) {return Ok(());}
if v0.len()!=1 {return Err("className requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
let mut t1=self.take_values();self.text_values(&caps,&[17],&mut t1);if t1.len()>1 {return Err("method requires at most one value".into());}let f1=t1.pop();self.give_values(t1);
let f1:Option<u32>=f1;
let mut t2=self.take_values();self.text_values(&caps,&[18],&mut t2);if t2.is_empty() && self.missing_field(&caps,&[18]) {return Ok(());}
if t2.len()!=1 {return Err("alias requires one value".into());}let f2=t2.pop().unwrap();self.give_values(t2);
let f2:u32=f2;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_ImportDeclarationExpr,2,span,&[f0,f1.unwrap_or(tree::NONE),f2]);out.push(node);Ok(())}}
fn map_rule_3(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut t0=self.take_values();self.text_values(&caps,&[19],&mut t0);if t0.is_empty() && self.missing_field(&caps,&[19]) {return Ok(());}
if t0.len()!=1 {return Err("head requires one value".into());}let f0=t0.pop().unwrap();self.give_values(t0);
let f0:u32=f0;
let mut f1=self.take_values();self.text_values(&caps,&[20],&mut f1);
let f1:Vec<u32>=f1;
self.give_captures(caps);let span=self.span(span);let l1=self.tree.list(&f1);self.give_values(f1);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_QualifiedNameExpr,3,span,&[f0,l1[0],l1[1]]);out.push(node);Ok(())}}
fn map_rule_4(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_5(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut t0=self.take_values();self.text_values(&caps,&[21],&mut t0);if t0.is_empty() && self.missing_field(&caps,&[21]) {return Ok(());}
if t0.len()!=1 {return Err("varName requires one value".into());}let f0=t0.pop().unwrap();self.give_values(t0);
let f0:u32=f0;
let mut v1=self.take_values();self.node_values(&caps,&[22],&[],None,&mut v1)?;
if !v1.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr}) {return Err("onlyIfAbsent: mapped value type mismatch".into());}
if v1.len()>1 {return Err("onlyIfAbsent requires at most one node".into());}let f1=v1.pop();self.give_values(v1);
let f1:Option<u32>=f1;
let mut v2=self.take_values();self.node_values(&caps,&[23],&[],None,&mut v2)?;
if !v2.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BinaryExpr}) {return Err("value: mapped value type mismatch".into());}
if v2.len()>1 {return Err("value requires at most one node".into());}let f2=v2.pop();self.give_values(v2);
let f2:Option<u32>=f2;
let mut t3=self.take_values();self.text_values(&caps,&[24],&mut t3);if t3.len()>1 {return Err("desc requires at most one value".into());}let f3=t3.pop();self.give_values(t3);
let f3:Option<u32>=f3;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr,5,span,&[f0,f1.unwrap_or(tree::NONE),f2.unwrap_or(tree::NONE),f3.unwrap_or(tree::NONE)]);out.push(node);Ok(())}}
fn map_rule_6(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut t0=self.take_values();self.text_values(&caps,&[25],&mut t0);if t0.is_empty() && self.missing_field(&caps,&[25]) {return Ok(());}
if t0.len()!=1 {return Err("varName requires one value".into());}let f0=t0.pop().unwrap();self.give_values(t0);
let f0:u32=f0;
let mut v1=self.take_values();self.node_values(&caps,&[26],&[],None,&mut v1)?;
if !v1.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr}) {return Err("onlyIfAbsent: mapped value type mismatch".into());}
if v1.len()>1 {return Err("onlyIfAbsent requires at most one node".into());}let f1=v1.pop();self.give_values(v1);
let f1:Option<u32>=f1;
let mut v2=self.take_values();self.node_values(&caps,&[27],&[],None,&mut v2)?;
if !v2.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr}) {return Err("value: mapped value type mismatch".into());}
if v2.len()>1 {return Err("value requires at most one node".into());}let f2=v2.pop();self.give_values(v2);
let f2:Option<u32>=f2;
let mut t3=self.take_values();self.text_values(&caps,&[28],&mut t3);if t3.len()>1 {return Err("desc requires at most one value".into());}let f3=t3.pop();self.give_values(t3);
let f3:Option<u32>=f3;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr,6,span,&[f0,f1.unwrap_or(tree::NONE),f2.unwrap_or(tree::NONE),f3.unwrap_or(tree::NONE)]);out.push(node);Ok(())}}
fn map_rule_7(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut t0=self.take_values();self.text_values(&caps,&[29],&mut t0);if t0.is_empty() && self.missing_field(&caps,&[29]) {return Ok(());}
if t0.len()!=1 {return Err("varName requires one value".into());}let f0=t0.pop().unwrap();self.give_values(t0);
let f0:u32=f0;
let mut v1=self.take_values();self.node_values(&caps,&[30],&[],None,&mut v1)?;
if !v1.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr}) {return Err("onlyIfAbsent: mapped value type mismatch".into());}
if v1.len()>1 {return Err("onlyIfAbsent requires at most one node".into());}let f1=v1.pop();self.give_values(v1);
let f1:Option<u32>=f1;
let mut v2=self.take_values();self.node_values(&caps,&[31],&[],None,&mut v2)?;
if !v2.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BooleanOrExpr}) {return Err("value: mapped value type mismatch".into());}
if v2.len()>1 {return Err("value requires at most one node".into());}let f2=v2.pop();self.give_values(v2);
let f2:Option<u32>=f2;
let mut t3=self.take_values();self.text_values(&caps,&[32],&mut t3);if t3.len()>1 {return Err("desc requires at most one value".into());}let f3=t3.pop();self.give_values(t3);
let f3:Option<u32>=f3;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr,7,span,&[f0,f1.unwrap_or(tree::NONE),f2.unwrap_or(tree::NONE),f3.unwrap_or(tree::NONE)]);out.push(node);Ok(())}}
fn map_rule_8(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut t0=self.take_values();self.text_values(&caps,&[33],&mut t0);if t0.is_empty() && self.missing_field(&caps,&[33]) {return Ok(());}
if t0.len()!=1 {return Err("varName requires one value".into());}let f0=t0.pop().unwrap();self.give_values(t0);
let f0:u32=f0;
let mut v1=self.take_values();self.node_values(&caps,&[34],&[],None,&mut v1)?;
if !v1.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr}) {return Err("onlyIfAbsent: mapped value type mismatch".into());}
if v1.len()>1 {return Err("onlyIfAbsent requires at most one node".into());}let f1=v1.pop();self.give_values(v1);
let f1:Option<u32>=f1;
let mut v2=self.take_values();self.node_values(&caps,&[35],&[],None,&mut v2)?;
if !v2.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_ObjectExpr}) {return Err("value: mapped value type mismatch".into());}
if v2.len()>1 {return Err("value requires at most one node".into());}let f2=v2.pop();self.give_values(v2);
let f2:Option<u32>=f2;
let mut t3=self.take_values();self.text_values(&caps,&[36],&mut t3);if t3.len()>1 {return Err("desc requires at most one value".into());}let f3=t3.pop();self.give_values(t3);
let f3:Option<u32>=f3;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr,8,span,&[f0,f1.unwrap_or(tree::NONE),f2.unwrap_or(tree::NONE),f3.unwrap_or(tree::NONE)]);out.push(node);Ok(())}}
fn map_rule_9(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{if self.value_region>0 {return Ok(());}let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_10(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{if self.value_region>0 {return Ok(());}let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_11(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{if self.value_region>0 {return Ok(());}let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_12(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{if self.value_region>0 {return Ok(());}let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_13(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{if self.value_region>0 {return Ok(());}let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_14(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr,14,span,&[]);out.push(node);Ok(())}}
fn map_rule_15(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{if self.value_region>0 {return Ok(());}let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_16(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_17(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_18(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_19(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_20(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut t0=self.take_values();self.text_values(&caps,&[37],&mut t0);if t0.is_empty() && self.missing_field(&caps,&[37]) {return Ok(());}
if t0.len()!=1 {return Err("methodName requires one value".into());}let f0=t0.pop().unwrap();self.give_values(t0);
let f0:u32=f0;
let mut v1=self.take_values();self.node_values(&caps,&[38],&[],None,&mut v1)?;
if !v1.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_MethodParametersExpr}) {return Err("parameters: mapped value type mismatch".into());}
if v1.len()>1 {return Err("parameters requires at most one node".into());}let f1=v1.pop();self.give_values(v1);
let f1:Option<u32>=f1;
let mut v2=self.take_values();self.node_values(&caps,&[39],&[],None,&mut v2)?;
if !v2.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BinaryExpr}) {return Err("expression: mapped value type mismatch".into());}
if v2.is_empty() && self.missing_field(&caps,&[39]) {return Ok(());}
if v2.len()!=1 {return Err("expression requires one node".into());}let f2=v2.pop().unwrap();self.give_values(v2);
let f2:u32=f2;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr,20,span,&[f0,f1.unwrap_or(tree::NONE),f2]);out.push(node);Ok(())}}
fn map_rule_21(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut t0=self.take_values();self.text_values(&caps,&[40],&mut t0);if t0.is_empty() && self.missing_field(&caps,&[40]) {return Ok(());}
if t0.len()!=1 {return Err("methodName requires one value".into());}let f0=t0.pop().unwrap();self.give_values(t0);
let f0:u32=f0;
let mut v1=self.take_values();self.node_values(&caps,&[41],&[],None,&mut v1)?;
if !v1.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_MethodParametersExpr}) {return Err("parameters: mapped value type mismatch".into());}
if v1.len()>1 {return Err("parameters requires at most one node".into());}let f1=v1.pop();self.give_values(v1);
let f1:Option<u32>=f1;
let mut v2=self.take_values();self.node_values(&caps,&[42],&[],None,&mut v2)?;
if !v2.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr}) {return Err("expression: mapped value type mismatch".into());}
if v2.is_empty() && self.missing_field(&caps,&[42]) {return Ok(());}
if v2.len()!=1 {return Err("expression requires one node".into());}let f2=v2.pop().unwrap();self.give_values(v2);
let f2:u32=f2;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr,21,span,&[f0,f1.unwrap_or(tree::NONE),f2]);out.push(node);Ok(())}}
fn map_rule_22(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut t0=self.take_values();self.text_values(&caps,&[43],&mut t0);if t0.is_empty() && self.missing_field(&caps,&[43]) {return Ok(());}
if t0.len()!=1 {return Err("methodName requires one value".into());}let f0=t0.pop().unwrap();self.give_values(t0);
let f0:u32=f0;
let mut v1=self.take_values();self.node_values(&caps,&[44],&[],None,&mut v1)?;
if !v1.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_MethodParametersExpr}) {return Err("parameters: mapped value type mismatch".into());}
if v1.len()>1 {return Err("parameters requires at most one node".into());}let f1=v1.pop();self.give_values(v1);
let f1:Option<u32>=f1;
let mut v2=self.take_values();self.node_values(&caps,&[45],&[],None,&mut v2)?;
if !v2.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BooleanOrExpr}) {return Err("expression: mapped value type mismatch".into());}
if v2.is_empty() && self.missing_field(&caps,&[45]) {return Ok(());}
if v2.len()!=1 {return Err("expression requires one node".into());}let f2=v2.pop().unwrap();self.give_values(v2);
let f2:u32=f2;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr,22,span,&[f0,f1.unwrap_or(tree::NONE),f2]);out.push(node);Ok(())}}
fn map_rule_23(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut t0=self.take_values();self.text_values(&caps,&[46],&mut t0);if t0.is_empty() && self.missing_field(&caps,&[46]) {return Ok(());}
if t0.len()!=1 {return Err("methodName requires one value".into());}let f0=t0.pop().unwrap();self.give_values(t0);
let f0:u32=f0;
let mut v1=self.take_values();self.node_values(&caps,&[47],&[],None,&mut v1)?;
if !v1.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_MethodParametersExpr}) {return Err("parameters: mapped value type mismatch".into());}
if v1.len()>1 {return Err("parameters requires at most one node".into());}let f1=v1.pop();self.give_values(v1);
let f1:Option<u32>=f1;
let mut v2=self.take_values();self.node_values(&caps,&[48],&[],None,&mut v2)?;
if !v2.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_ObjectExpr}) {return Err("expression: mapped value type mismatch".into());}
if v2.is_empty() && self.missing_field(&caps,&[48]) {return Ok(());}
if v2.len()!=1 {return Err("expression requires one node".into());}let f2=v2.pop().unwrap();self.give_values(v2);
let f2:u32=f2;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr,23,span,&[f0,f1.unwrap_or(tree::NONE),f2]);out.push(node);Ok(())}}
fn map_rule_24(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut f0=self.take_values();self.node_values(&caps,&[49, 50],&[],None,&mut f0)?;
if !f0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_MethodParameterExpr}) {return Err("values: mapped value type mismatch".into());}
let f0:Vec<u32>=f0;
self.give_captures(caps);let span=self.span(span);let l0=self.tree.list(&f0);self.give_values(f0);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_MethodParametersExpr,24,span,&[l0[0],l0[1]]);out.push(node);Ok(())}}
fn map_rule_25(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut t0=self.take_values();self.text_values(&caps,&[51],&mut t0);if t0.is_empty() && self.missing_field(&caps,&[51]) {return Ok(());}
if t0.len()!=1 {return Err("paramName requires one value".into());}let f0=t0.pop().unwrap();self.give_values(t0);
let f0:u32=f0;
let mut t1=self.take_values();self.text_values(&caps,&[52],&mut t1);if t1.len()>1 {return Err("type requires at most one value".into());}let f1=t1.pop();self.give_values(t1);
let f1:Option<u32>=f1;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_MethodParameterExpr,25,span,&[f0,f1.unwrap_or(tree::NONE)]);out.push(node);Ok(())}}
fn map_rule_26(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{if self.value_region>0 {return Ok(());}let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_27(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{if self.value_region>0 {return Ok(());}let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_28(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{if self.value_region>0 {return Ok(());}let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_29(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{if self.value_region>0 {return Ok(());}let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_30(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{if self.value_region>0 {return Ok(());}let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_31(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[53],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_QualifiedNameExpr}) {return Err("className: mapped value type mismatch".into());}
if v0.len()>1 {return Err("className requires at most one node".into());}let f0=v0.pop();self.give_values(v0);
let f0:Option<u32>=f0;
let mut t1=self.take_values();self.text_values(&caps,&[54, 55],&mut t1);if t1.is_empty() && self.missing_field(&caps,&[54, 55]) {return Ok(());}
if t1.len()!=1 {return Err("name requires one value".into());}let f1=t1.pop().unwrap();self.give_values(t1);
let f1:u32=f1;
let mut v2=self.take_values();self.node_values(&caps,&[56],&[],None,&mut v2)?;
if !v2.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_ArgumentsExpr}) {return Err("args: mapped value type mismatch".into());}
if v2.len()>1 {return Err("args requires at most one node".into());}let f2=v2.pop();self.give_values(v2);
let f2:Option<u32>=f2;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr,31,span,&[f0.unwrap_or(tree::NONE),f1,f2.unwrap_or(tree::NONE)]);out.push(node);Ok(())}}
fn map_rule_32(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[57],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_QualifiedNameExpr}) {return Err("className: mapped value type mismatch".into());}
if v0.len()>1 {return Err("className requires at most one node".into());}let f0=v0.pop();self.give_values(v0);
let f0:Option<u32>=f0;
let mut t1=self.take_values();self.text_values(&caps,&[58, 59],&mut t1);if t1.is_empty() && self.missing_field(&caps,&[58, 59]) {return Ok(());}
if t1.len()!=1 {return Err("name requires one value".into());}let f1=t1.pop().unwrap();self.give_values(t1);
let f1:u32=f1;
let mut v2=self.take_values();self.node_values(&caps,&[60],&[],None,&mut v2)?;
if !v2.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_ArgumentsExpr}) {return Err("args: mapped value type mismatch".into());}
if v2.len()>1 {return Err("args requires at most one node".into());}let f2=v2.pop();self.give_values(v2);
let f2:Option<u32>=f2;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr,32,span,&[f0.unwrap_or(tree::NONE),f1,f2.unwrap_or(tree::NONE)]);out.push(node);Ok(())}}
fn map_rule_33(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[61],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_QualifiedNameExpr}) {return Err("className: mapped value type mismatch".into());}
if v0.len()>1 {return Err("className requires at most one node".into());}let f0=v0.pop();self.give_values(v0);
let f0:Option<u32>=f0;
let mut t1=self.take_values();self.text_values(&caps,&[62, 63],&mut t1);if t1.is_empty() && self.missing_field(&caps,&[62, 63]) {return Ok(());}
if t1.len()!=1 {return Err("name requires one value".into());}let f1=t1.pop().unwrap();self.give_values(t1);
let f1:u32=f1;
let mut v2=self.take_values();self.node_values(&caps,&[64],&[],None,&mut v2)?;
if !v2.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_ArgumentsExpr}) {return Err("args: mapped value type mismatch".into());}
if v2.len()>1 {return Err("args requires at most one node".into());}let f2=v2.pop();self.give_values(v2);
let f2:Option<u32>=f2;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr,33,span,&[f0.unwrap_or(tree::NONE),f1,f2.unwrap_or(tree::NONE)]);out.push(node);Ok(())}}
fn map_rule_34(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[65],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_QualifiedNameExpr}) {return Err("className: mapped value type mismatch".into());}
if v0.len()>1 {return Err("className requires at most one node".into());}let f0=v0.pop();self.give_values(v0);
let f0:Option<u32>=f0;
let mut t1=self.take_values();self.text_values(&caps,&[66, 67],&mut t1);if t1.is_empty() && self.missing_field(&caps,&[66, 67]) {return Ok(());}
if t1.len()!=1 {return Err("name requires one value".into());}let f1=t1.pop().unwrap();self.give_values(t1);
let f1:u32=f1;
let mut v2=self.take_values();self.node_values(&caps,&[68],&[],None,&mut v2)?;
if !v2.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_ArgumentsExpr}) {return Err("args: mapped value type mismatch".into());}
if v2.len()>1 {return Err("args requires at most one node".into());}let f2=v2.pop();self.give_values(v2);
let f2:Option<u32>=f2;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr,34,span,&[f0.unwrap_or(tree::NONE),f1,f2.unwrap_or(tree::NONE)]);out.push(node);Ok(())}}
fn map_rule_35(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{if self.value_region>0 {return Ok(());}let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_36(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut t0=self.take_values();self.text_values(&caps,&[69],&mut t0);if t0.is_empty() && self.missing_field(&caps,&[69]) {return Ok(());}
if t0.len()!=1 {return Err("name requires one value".into());}let f0=t0.pop().unwrap();self.give_values(t0);
let f0:u32=f0;
let mut v1=self.take_values();self.node_values(&caps,&[70],&[],None,&mut v1)?;
if !v1.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_ArgumentsExpr}) {return Err("args: mapped value type mismatch".into());}
if v1.len()>1 {return Err("args requires at most one node".into());}let f1=v1.pop();self.give_values(v1);
let f1:Option<u32>=f1;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_MethodInvocationExpr,36,span,&[f0,f1.unwrap_or(tree::NONE)]);out.push(node);Ok(())}}
fn map_rule_37(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[71],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BooleanOrExpr}) {return Err("condition: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[71]) {return Ok(());}
if v0.len()!=1 {return Err("condition requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
let mut v1=self.take_values();self.node_values(&caps,&[72],&[],None,&mut v1)?;
if !v1.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BranchExpressionExpr}) {return Err("thenExpr: mapped value type mismatch".into());}
if v1.is_empty() && self.missing_field(&caps,&[72]) {return Ok(());}
if v1.len()!=1 {return Err("thenExpr requires one node".into());}let f1=v1.pop().unwrap();self.give_values(v1);
let f1:u32=f1;
let mut v2=self.take_values();self.node_values(&caps,&[73],&[],None,&mut v2)?;
if !v2.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BranchExpressionExpr}) {return Err("elseExpr: mapped value type mismatch".into());}
if v2.is_empty() && self.missing_field(&caps,&[73]) {return Ok(());}
if v2.len()!=1 {return Err("elseExpr requires one node".into());}let f2=v2.pop().unwrap();self.give_values(v2);
let f2:u32=f2;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_TernaryExpr,37,span,&[f0,f1,f2]);out.push(node);Ok(())}}
fn map_rule_38(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[74, 75, 76, 77],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_ComparisonExpr || k==tree::K_g_TinyExpressionP4AST_2e_ExpressionExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringComparisonExpr || k==tree::K_g_TinyExpressionP4AST_2e_TernaryExpr}) {return Err("value: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[74, 75, 76, 77]) {return Ok(());}
if v0.len()!=1 {return Err("value requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_ArgumentExpressionExpr,38,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_39(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut f0=self.take_values();self.node_values(&caps,&[78, 79],&[],None,&mut f0)?;
if !f0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_ArgumentExpressionExpr}) {return Err("values: mapped value type mismatch".into());}
let f0:Vec<u32>=f0;
self.give_captures(caps);let span=self.span(span);let l0=self.tree.list(&f0);self.give_values(f0);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_ArgumentsExpr,39,span,&[l0[0],l0[1]]);out.push(node);Ok(())}}
fn map_rule_40(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[80],&[],Some(24),&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::KIND_NULL || k==tree::K_g_TinyExpressionP4AST_2e_AbsExpr || k==tree::K_g_TinyExpressionP4AST_2e_ArgumentExpressionExpr || k==tree::K_g_TinyExpressionP4AST_2e_ArgumentsExpr || k==tree::K_g_TinyExpressionP4AST_2e_BinaryExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanAndExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanCaseValueExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanEqualityExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanFactorExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanMatchExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanOrExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanXorExpr || k==tree::K_g_TinyExpressionP4AST_2e_BranchExpressionExpr || k==tree::K_g_TinyExpressionP4AST_2e_CeilExpr || k==tree::K_g_TinyExpressionP4AST_2e_CodeBlockExpr || k==tree::K_g_TinyExpressionP4AST_2e_ComparisonExpr || k==tree::K_g_TinyExpressionP4AST_2e_ContainsDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_ContainsExpr || k==tree::K_g_TinyExpressionP4AST_2e_CosExpr || k==tree::K_g_TinyExpressionP4AST_2e_EndsWithDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_EndsWithExpr || k==tree::K_g_TinyExpressionP4AST_2e_ExpExpr || k==tree::K_g_TinyExpressionP4AST_2e_ExpressionExpr || k==tree::K_g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_FloorExpr || k==tree::K_g_TinyExpressionP4AST_2e_FormulaExpr || k==tree::K_g_TinyExpressionP4AST_2e_IfExpr || k==tree::K_g_TinyExpressionP4AST_2e_ImportDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_InDayTimeRangeExpr || k==tree::K_g_TinyExpressionP4AST_2e_InExpr || k==tree::K_g_TinyExpressionP4AST_2e_InTimeRangeExpr || k==tree::K_g_TinyExpressionP4AST_2e_IsPresentExpr || k==tree::K_g_TinyExpressionP4AST_2e_LengthDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_LengthExpr || k==tree::K_g_TinyExpressionP4AST_2e_LogExpr || k==tree::K_g_TinyExpressionP4AST_2e_MaxExpr || k==tree::K_g_TinyExpressionP4AST_2e_MethodInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_MethodParameterExpr || k==tree::K_g_TinyExpressionP4AST_2e_MethodParametersExpr || k==tree::K_g_TinyExpressionP4AST_2e_MinExpr || k==tree::K_g_TinyExpressionP4AST_2e_NotExpr || k==tree::K_g_TinyExpressionP4AST_2e_NumberCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_NumberCaseValueExpr || k==tree::K_g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_NumberMatchExpr || k==tree::K_g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_ObjectExpr || k==tree::K_g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr || k==tree::K_g_TinyExpressionP4AST_2e_PowExpr || k==tree::K_g_TinyExpressionP4AST_2e_QualifiedNameExpr || k==tree::K_g_TinyExpressionP4AST_2e_RandomExpr || k==tree::K_g_TinyExpressionP4AST_2e_RoundExpr || k==tree::K_g_TinyExpressionP4AST_2e_SinExpr || k==tree::K_g_TinyExpressionP4AST_2e_SliceExpr || k==tree::K_g_TinyExpressionP4AST_2e_SqrtExpr || k==tree::K_g_TinyExpressionP4AST_2e_StartsWithDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_StartsWithExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringCaseValueExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringCastVariableRefExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringComparisonExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringDefaultCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringMatchExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_TanExpr || k==tree::K_g_TinyExpressionP4AST_2e_TernaryExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToLowerCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToNumExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToUpperCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_TrimDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_TrimExpr || k==tree::K_g_TinyExpressionP4AST_2e_VariableRefExpr}) {return Err("left: mapped value type mismatch".into());}
if v0.len()>1 {return Err("left requires at most one node".into());}let f0=v0.pop();self.give_values(v0);
let f0:Option<u32>=f0;
let mut f1=self.take_values();self.text_values(&caps,&[81],&mut f1);
let f1:Vec<u32>=f1;
let mut f2=self.take_values();self.node_values(&caps,&[82],&[],Some(24),&mut f2)?;
if !f2.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_AbsExpr || k==tree::K_g_TinyExpressionP4AST_2e_ArgumentExpressionExpr || k==tree::K_g_TinyExpressionP4AST_2e_ArgumentsExpr || k==tree::K_g_TinyExpressionP4AST_2e_BinaryExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanAndExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanCaseValueExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanEqualityExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanFactorExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanMatchExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanOrExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanXorExpr || k==tree::K_g_TinyExpressionP4AST_2e_BranchExpressionExpr || k==tree::K_g_TinyExpressionP4AST_2e_CeilExpr || k==tree::K_g_TinyExpressionP4AST_2e_CodeBlockExpr || k==tree::K_g_TinyExpressionP4AST_2e_ComparisonExpr || k==tree::K_g_TinyExpressionP4AST_2e_ContainsDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_ContainsExpr || k==tree::K_g_TinyExpressionP4AST_2e_CosExpr || k==tree::K_g_TinyExpressionP4AST_2e_EndsWithDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_EndsWithExpr || k==tree::K_g_TinyExpressionP4AST_2e_ExpExpr || k==tree::K_g_TinyExpressionP4AST_2e_ExpressionExpr || k==tree::K_g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_FloorExpr || k==tree::K_g_TinyExpressionP4AST_2e_FormulaExpr || k==tree::K_g_TinyExpressionP4AST_2e_IfExpr || k==tree::K_g_TinyExpressionP4AST_2e_ImportDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_InDayTimeRangeExpr || k==tree::K_g_TinyExpressionP4AST_2e_InExpr || k==tree::K_g_TinyExpressionP4AST_2e_InTimeRangeExpr || k==tree::K_g_TinyExpressionP4AST_2e_IsPresentExpr || k==tree::K_g_TinyExpressionP4AST_2e_LengthDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_LengthExpr || k==tree::K_g_TinyExpressionP4AST_2e_LogExpr || k==tree::K_g_TinyExpressionP4AST_2e_MaxExpr || k==tree::K_g_TinyExpressionP4AST_2e_MethodInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_MethodParameterExpr || k==tree::K_g_TinyExpressionP4AST_2e_MethodParametersExpr || k==tree::K_g_TinyExpressionP4AST_2e_MinExpr || k==tree::K_g_TinyExpressionP4AST_2e_NotExpr || k==tree::K_g_TinyExpressionP4AST_2e_NumberCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_NumberCaseValueExpr || k==tree::K_g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_NumberMatchExpr || k==tree::K_g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_ObjectExpr || k==tree::K_g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr || k==tree::K_g_TinyExpressionP4AST_2e_PowExpr || k==tree::K_g_TinyExpressionP4AST_2e_QualifiedNameExpr || k==tree::K_g_TinyExpressionP4AST_2e_RandomExpr || k==tree::K_g_TinyExpressionP4AST_2e_RoundExpr || k==tree::K_g_TinyExpressionP4AST_2e_SinExpr || k==tree::K_g_TinyExpressionP4AST_2e_SliceExpr || k==tree::K_g_TinyExpressionP4AST_2e_SqrtExpr || k==tree::K_g_TinyExpressionP4AST_2e_StartsWithDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_StartsWithExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringCaseValueExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringCastVariableRefExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringComparisonExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringDefaultCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringMatchExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_TanExpr || k==tree::K_g_TinyExpressionP4AST_2e_TernaryExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToLowerCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToNumExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToUpperCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_TrimDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_TrimExpr || k==tree::K_g_TinyExpressionP4AST_2e_VariableRefExpr}) {return Err("right: mapped value type mismatch".into());}
let f2:Vec<u32>=f2;
self.give_captures(caps);let span=self.span(span);let l1=self.tree.list(&f1);self.give_values(f1);let l2=self.tree.list(&f2);self.give_values(f2);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_BinaryExpr,40,span,&[f0.unwrap_or(tree::NONE),l1[0],l1[1],l2[0],l2[1]]);out.push(node);Ok(())}}
fn map_rule_41(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[83],&[],Some(24),&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::KIND_NULL || k==tree::K_g_TinyExpressionP4AST_2e_AbsExpr || k==tree::K_g_TinyExpressionP4AST_2e_ArgumentExpressionExpr || k==tree::K_g_TinyExpressionP4AST_2e_ArgumentsExpr || k==tree::K_g_TinyExpressionP4AST_2e_BinaryExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanAndExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanCaseValueExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanEqualityExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanFactorExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanMatchExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanOrExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanXorExpr || k==tree::K_g_TinyExpressionP4AST_2e_BranchExpressionExpr || k==tree::K_g_TinyExpressionP4AST_2e_CeilExpr || k==tree::K_g_TinyExpressionP4AST_2e_CodeBlockExpr || k==tree::K_g_TinyExpressionP4AST_2e_ComparisonExpr || k==tree::K_g_TinyExpressionP4AST_2e_ContainsDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_ContainsExpr || k==tree::K_g_TinyExpressionP4AST_2e_CosExpr || k==tree::K_g_TinyExpressionP4AST_2e_EndsWithDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_EndsWithExpr || k==tree::K_g_TinyExpressionP4AST_2e_ExpExpr || k==tree::K_g_TinyExpressionP4AST_2e_ExpressionExpr || k==tree::K_g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_FloorExpr || k==tree::K_g_TinyExpressionP4AST_2e_FormulaExpr || k==tree::K_g_TinyExpressionP4AST_2e_IfExpr || k==tree::K_g_TinyExpressionP4AST_2e_ImportDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_InDayTimeRangeExpr || k==tree::K_g_TinyExpressionP4AST_2e_InExpr || k==tree::K_g_TinyExpressionP4AST_2e_InTimeRangeExpr || k==tree::K_g_TinyExpressionP4AST_2e_IsPresentExpr || k==tree::K_g_TinyExpressionP4AST_2e_LengthDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_LengthExpr || k==tree::K_g_TinyExpressionP4AST_2e_LogExpr || k==tree::K_g_TinyExpressionP4AST_2e_MaxExpr || k==tree::K_g_TinyExpressionP4AST_2e_MethodInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_MethodParameterExpr || k==tree::K_g_TinyExpressionP4AST_2e_MethodParametersExpr || k==tree::K_g_TinyExpressionP4AST_2e_MinExpr || k==tree::K_g_TinyExpressionP4AST_2e_NotExpr || k==tree::K_g_TinyExpressionP4AST_2e_NumberCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_NumberCaseValueExpr || k==tree::K_g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_NumberMatchExpr || k==tree::K_g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_ObjectExpr || k==tree::K_g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr || k==tree::K_g_TinyExpressionP4AST_2e_PowExpr || k==tree::K_g_TinyExpressionP4AST_2e_QualifiedNameExpr || k==tree::K_g_TinyExpressionP4AST_2e_RandomExpr || k==tree::K_g_TinyExpressionP4AST_2e_RoundExpr || k==tree::K_g_TinyExpressionP4AST_2e_SinExpr || k==tree::K_g_TinyExpressionP4AST_2e_SliceExpr || k==tree::K_g_TinyExpressionP4AST_2e_SqrtExpr || k==tree::K_g_TinyExpressionP4AST_2e_StartsWithDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_StartsWithExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringCaseValueExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringCastVariableRefExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringComparisonExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringDefaultCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringMatchExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_TanExpr || k==tree::K_g_TinyExpressionP4AST_2e_TernaryExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToLowerCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToNumExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToUpperCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_TrimDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_TrimExpr || k==tree::K_g_TinyExpressionP4AST_2e_VariableRefExpr}) {return Err("left: mapped value type mismatch".into());}
if v0.len()>1 {return Err("left requires at most one node".into());}let f0=v0.pop();self.give_values(v0);
let f0:Option<u32>=f0;
let mut f1=self.take_values();self.text_values(&caps,&[84],&mut f1);
let f1:Vec<u32>=f1;
let mut f2=self.take_values();self.node_values(&caps,&[85],&[],Some(24),&mut f2)?;
if !f2.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_AbsExpr || k==tree::K_g_TinyExpressionP4AST_2e_ArgumentExpressionExpr || k==tree::K_g_TinyExpressionP4AST_2e_ArgumentsExpr || k==tree::K_g_TinyExpressionP4AST_2e_BinaryExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanAndExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanCaseValueExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanEqualityExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanFactorExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanMatchExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanMethodDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanOrExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanVariableDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanXorExpr || k==tree::K_g_TinyExpressionP4AST_2e_BranchExpressionExpr || k==tree::K_g_TinyExpressionP4AST_2e_CeilExpr || k==tree::K_g_TinyExpressionP4AST_2e_CodeBlockExpr || k==tree::K_g_TinyExpressionP4AST_2e_ComparisonExpr || k==tree::K_g_TinyExpressionP4AST_2e_ContainsDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_ContainsExpr || k==tree::K_g_TinyExpressionP4AST_2e_CosExpr || k==tree::K_g_TinyExpressionP4AST_2e_EndsWithDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_EndsWithExpr || k==tree::K_g_TinyExpressionP4AST_2e_ExpExpr || k==tree::K_g_TinyExpressionP4AST_2e_ExpressionExpr || k==tree::K_g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_ExternalNumberInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_FloorExpr || k==tree::K_g_TinyExpressionP4AST_2e_FormulaExpr || k==tree::K_g_TinyExpressionP4AST_2e_IfExpr || k==tree::K_g_TinyExpressionP4AST_2e_ImportDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_InDayTimeRangeExpr || k==tree::K_g_TinyExpressionP4AST_2e_InExpr || k==tree::K_g_TinyExpressionP4AST_2e_InTimeRangeExpr || k==tree::K_g_TinyExpressionP4AST_2e_IsPresentExpr || k==tree::K_g_TinyExpressionP4AST_2e_LengthDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_LengthExpr || k==tree::K_g_TinyExpressionP4AST_2e_LogExpr || k==tree::K_g_TinyExpressionP4AST_2e_MaxExpr || k==tree::K_g_TinyExpressionP4AST_2e_MethodInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_MethodParameterExpr || k==tree::K_g_TinyExpressionP4AST_2e_MethodParametersExpr || k==tree::K_g_TinyExpressionP4AST_2e_MinExpr || k==tree::K_g_TinyExpressionP4AST_2e_NotExpr || k==tree::K_g_TinyExpressionP4AST_2e_NumberCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_NumberCaseValueExpr || k==tree::K_g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_NumberMatchExpr || k==tree::K_g_TinyExpressionP4AST_2e_NumberMethodDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_NumberVariableDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_ObjectExpr || k==tree::K_g_TinyExpressionP4AST_2e_ObjectMethodDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_ObjectVariableDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_OnlyIfAbsentExpr || k==tree::K_g_TinyExpressionP4AST_2e_PowExpr || k==tree::K_g_TinyExpressionP4AST_2e_QualifiedNameExpr || k==tree::K_g_TinyExpressionP4AST_2e_RandomExpr || k==tree::K_g_TinyExpressionP4AST_2e_RoundExpr || k==tree::K_g_TinyExpressionP4AST_2e_SinExpr || k==tree::K_g_TinyExpressionP4AST_2e_SliceExpr || k==tree::K_g_TinyExpressionP4AST_2e_SqrtExpr || k==tree::K_g_TinyExpressionP4AST_2e_StartsWithDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_StartsWithExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringCaseValueExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringCastVariableRefExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringComparisonExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringDefaultCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringMatchExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringMethodDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringVariableDeclarationExpr || k==tree::K_g_TinyExpressionP4AST_2e_TanExpr || k==tree::K_g_TinyExpressionP4AST_2e_TernaryExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToLowerCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToNumExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToUpperCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_TrimDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_TrimExpr || k==tree::K_g_TinyExpressionP4AST_2e_VariableRefExpr}) {return Err("right: mapped value type mismatch".into());}
let f2:Vec<u32>=f2;
self.give_captures(caps);let span=self.span(span);let l1=self.tree.list(&f1);self.give_values(f1);let l2=self.tree.list(&f2);self.give_values(f2);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_BinaryExpr,41,span,&[f0.unwrap_or(tree::NONE),l1[0],l1[1],l2[0],l2[1]]);out.push(node);Ok(())}}
fn map_rule_42(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{if self.value_region>0 {return Ok(());}let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_43(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{if self.value_region>0 {return Ok(());}let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_44(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_45(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[86],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_ArgumentExpressionExpr}) {return Err("arg: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[86]) {return Ok(());}
if v0.len()!=1 {return Err("arg requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_SinExpr,45,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_46(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[87],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_ArgumentExpressionExpr}) {return Err("arg: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[87]) {return Ok(());}
if v0.len()!=1 {return Err("arg requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_CosExpr,46,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_47(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[88],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_ArgumentExpressionExpr}) {return Err("arg: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[88]) {return Ok(());}
if v0.len()!=1 {return Err("arg requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_TanExpr,47,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_48(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[89],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_ArgumentExpressionExpr}) {return Err("arg: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[89]) {return Ok(());}
if v0.len()!=1 {return Err("arg requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_SqrtExpr,48,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_49(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[90],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_ArgumentExpressionExpr}) {return Err("first: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[90]) {return Ok(());}
if v0.len()!=1 {return Err("first requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
let mut f1=self.take_values();self.node_values(&caps,&[91],&[],None,&mut f1)?;
if !f1.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_ArgumentExpressionExpr}) {return Err("rest: mapped value type mismatch".into());}
let f1:Vec<u32>=f1;
self.give_captures(caps);let span=self.span(span);let l1=self.tree.list(&f1);self.give_values(f1);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_MinExpr,49,span,&[f0,l1[0],l1[1]]);out.push(node);Ok(())}}
fn map_rule_50(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[92],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_ArgumentExpressionExpr}) {return Err("first: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[92]) {return Ok(());}
if v0.len()!=1 {return Err("first requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
let mut f1=self.take_values();self.node_values(&caps,&[93],&[],None,&mut f1)?;
if !f1.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_ArgumentExpressionExpr}) {return Err("rest: mapped value type mismatch".into());}
let f1:Vec<u32>=f1;
self.give_captures(caps);let span=self.span(span);let l1=self.tree.list(&f1);self.give_values(f1);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_MaxExpr,50,span,&[f0,l1[0],l1[1]]);out.push(node);Ok(())}}
fn map_rule_51(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_RandomExpr,51,span,&[]);out.push(node);Ok(())}}
fn map_rule_52(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[94],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_ArgumentExpressionExpr}) {return Err("arg: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[94]) {return Ok(());}
if v0.len()!=1 {return Err("arg requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_AbsExpr,52,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_53(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[95],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_ArgumentExpressionExpr}) {return Err("arg: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[95]) {return Ok(());}
if v0.len()!=1 {return Err("arg requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_RoundExpr,53,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_54(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[96],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_ArgumentExpressionExpr}) {return Err("arg: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[96]) {return Ok(());}
if v0.len()!=1 {return Err("arg requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_CeilExpr,54,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_55(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[97],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_ArgumentExpressionExpr}) {return Err("arg: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[97]) {return Ok(());}
if v0.len()!=1 {return Err("arg requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_FloorExpr,55,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_56(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[98],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_ArgumentExpressionExpr}) {return Err("base: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[98]) {return Ok(());}
if v0.len()!=1 {return Err("base requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
let mut v1=self.take_values();self.node_values(&caps,&[99],&[],None,&mut v1)?;
if !v1.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_ArgumentExpressionExpr}) {return Err("exponent: mapped value type mismatch".into());}
if v1.is_empty() && self.missing_field(&caps,&[99]) {return Ok(());}
if v1.len()!=1 {return Err("exponent requires one node".into());}let f1=v1.pop().unwrap();self.give_values(v1);
let f1:u32=f1;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_PowExpr,56,span,&[f0,f1]);out.push(node);Ok(())}}
fn map_rule_57(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[100],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_ArgumentExpressionExpr}) {return Err("arg: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[100]) {return Ok(());}
if v0.len()!=1 {return Err("arg requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_LogExpr,57,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_58(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[101],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_ArgumentExpressionExpr}) {return Err("arg: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[101]) {return Ok(());}
if v0.len()!=1 {return Err("arg requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_ExpExpr,58,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_59(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[102],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr}) {return Err("value: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[102]) {return Ok(());}
if v0.len()!=1 {return Err("value requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
let mut v1=self.take_values();self.node_values(&caps,&[103],&[],None,&mut v1)?;
if !v1.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_ArgumentExpressionExpr}) {return Err("defaultValue: mapped value type mismatch".into());}
if v1.is_empty() && self.missing_field(&caps,&[103]) {return Ok(());}
if v1.len()!=1 {return Err("defaultValue requires one node".into());}let f1=v1.pop().unwrap();self.give_values(v1);
let f1:u32=f1;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_ToNumExpr,59,span,&[f0,f1]);out.push(node);Ok(())}}
fn map_rule_60(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_61(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[104],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr}) {return Err("value: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[104]) {return Ok(());}
if v0.len()!=1 {return Err("value requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_ToUpperCaseExpr,61,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_62(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[105],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr}) {return Err("value: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[105]) {return Ok(());}
if v0.len()!=1 {return Err("value requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_ToLowerCaseExpr,62,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_63(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[106],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr}) {return Err("value: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[106]) {return Ok(());}
if v0.len()!=1 {return Err("value requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_TrimExpr,63,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_64(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[107],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr}) {return Err("value: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[107]) {return Ok(());}
if v0.len()!=1 {return Err("value requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_LengthExpr,64,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_65(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[108],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr}) {return Err("value: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[108]) {return Ok(());}
if v0.len()!=1 {return Err("value requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_LengthExpr,65,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_66(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[109],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_VariableRefExpr}) {return Err("value: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[109]) {return Ok(());}
if v0.len()!=1 {return Err("value requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr,66,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_67(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[110],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_VariableRefExpr}) {return Err("value: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[110]) {return Ok(());}
if v0.len()!=1 {return Err("value requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr,67,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_68(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[111],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_VariableRefExpr}) {return Err("value: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[111]) {return Ok(());}
if v0.len()!=1 {return Err("value requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_TrimDotExpr,68,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_69(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[112],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_VariableRefExpr}) {return Err("value: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[112]) {return Ok(());}
if v0.len()!=1 {return Err("value requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_LengthDotExpr,69,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_70(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[113],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr}) {return Err("value: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[113]) {return Ok(());}
if v0.len()!=1 {return Err("value requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
let mut f1=self.take_values();self.node_values(&caps,&[114, 115],&[],None,&mut f1)?;
if !f1.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr}) {return Err("patterns: mapped value type mismatch".into());}
let f1:Vec<u32>=f1;
self.give_captures(caps);let span=self.span(span);let l1=self.tree.list(&f1);self.give_values(f1);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_StartsWithExpr,70,span,&[f0,l1[0],l1[1]]);out.push(node);Ok(())}}
fn map_rule_71(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[116],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr}) {return Err("value: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[116]) {return Ok(());}
if v0.len()!=1 {return Err("value requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
let mut f1=self.take_values();self.node_values(&caps,&[117, 118],&[],None,&mut f1)?;
if !f1.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr}) {return Err("patterns: mapped value type mismatch".into());}
let f1:Vec<u32>=f1;
self.give_captures(caps);let span=self.span(span);let l1=self.tree.list(&f1);self.give_values(f1);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_EndsWithExpr,71,span,&[f0,l1[0],l1[1]]);out.push(node);Ok(())}}
fn map_rule_72(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[119],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr}) {return Err("value: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[119]) {return Ok(());}
if v0.len()!=1 {return Err("value requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
let mut f1=self.take_values();self.node_values(&caps,&[120, 121],&[],None,&mut f1)?;
if !f1.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr}) {return Err("patterns: mapped value type mismatch".into());}
let f1:Vec<u32>=f1;
self.give_captures(caps);let span=self.span(span);let l1=self.tree.list(&f1);self.give_values(f1);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_ContainsExpr,72,span,&[f0,l1[0],l1[1]]);out.push(node);Ok(())}}
fn map_rule_73(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[122],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr}) {return Err("value: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[122]) {return Ok(());}
if v0.len()!=1 {return Err("value requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
let mut f1=self.take_values();self.node_values(&caps,&[123, 124],&[],None,&mut f1)?;
if !f1.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr}) {return Err("candidates: mapped value type mismatch".into());}
let f1:Vec<u32>=f1;
self.give_captures(caps);let span=self.span(span);let l1=self.tree.list(&f1);self.give_values(f1);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_InExpr,73,span,&[f0,l1[0],l1[1]]);out.push(node);Ok(())}}
fn map_rule_74(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[125],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_ToLowerCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToUpperCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_TrimExpr || k==tree::K_g_TinyExpressionP4AST_2e_VariableRefExpr}) {return Err("value: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[125]) {return Ok(());}
if v0.len()!=1 {return Err("value requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
let mut f1=self.take_values();self.node_values(&caps,&[126, 127],&[],None,&mut f1)?;
if !f1.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr}) {return Err("patterns: mapped value type mismatch".into());}
let f1:Vec<u32>=f1;
self.give_captures(caps);let span=self.span(span);let l1=self.tree.list(&f1);self.give_values(f1);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_StartsWithDotExpr,74,span,&[f0,l1[0],l1[1]]);out.push(node);Ok(())}}
fn map_rule_75(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[128],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_ToLowerCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToUpperCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_TrimExpr || k==tree::K_g_TinyExpressionP4AST_2e_VariableRefExpr}) {return Err("value: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[128]) {return Ok(());}
if v0.len()!=1 {return Err("value requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
let mut f1=self.take_values();self.node_values(&caps,&[129, 130],&[],None,&mut f1)?;
if !f1.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr}) {return Err("patterns: mapped value type mismatch".into());}
let f1:Vec<u32>=f1;
self.give_captures(caps);let span=self.span(span);let l1=self.tree.list(&f1);self.give_values(f1);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_EndsWithDotExpr,75,span,&[f0,l1[0],l1[1]]);out.push(node);Ok(())}}
fn map_rule_76(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[131],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_ToLowerCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToUpperCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_TrimExpr || k==tree::K_g_TinyExpressionP4AST_2e_VariableRefExpr}) {return Err("value: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[131]) {return Ok(());}
if v0.len()!=1 {return Err("value requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
let mut f1=self.take_values();self.node_values(&caps,&[132, 133],&[],None,&mut f1)?;
if !f1.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr}) {return Err("patterns: mapped value type mismatch".into());}
let f1:Vec<u32>=f1;
self.give_captures(caps);let span=self.span(span);let l1=self.tree.list(&f1);self.give_values(f1);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_ContainsDotExpr,76,span,&[f0,l1[0],l1[1]]);out.push(node);Ok(())}}
fn map_rule_77(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_78(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[134],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_VariableRefExpr}) {return Err("value: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[134]) {return Ok(());}
if v0.len()!=1 {return Err("value requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_IsPresentExpr,78,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_79(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[135],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BinaryExpr}) {return Err("startHour: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[135]) {return Ok(());}
if v0.len()!=1 {return Err("startHour requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
let mut v1=self.take_values();self.node_values(&caps,&[136],&[],None,&mut v1)?;
if !v1.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BinaryExpr}) {return Err("endHour: mapped value type mismatch".into());}
if v1.is_empty() && self.missing_field(&caps,&[136]) {return Ok(());}
if v1.len()!=1 {return Err("endHour requires one node".into());}let f1=v1.pop().unwrap();self.give_values(v1);
let f1:u32=f1;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_InTimeRangeExpr,79,span,&[f0,f1]);out.push(node);Ok(())}}
fn map_rule_80(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut t0=self.take_values();self.text_values(&caps,&[137],&mut t0);if t0.is_empty() && self.missing_field(&caps,&[137]) {return Ok(());}
if t0.len()!=1 {return Err("startDay requires one value".into());}let f0=t0.pop().unwrap();self.give_values(t0);
let f0:u32=f0;
let mut v1=self.take_values();self.node_values(&caps,&[138],&[],None,&mut v1)?;
if !v1.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BinaryExpr}) {return Err("startHour: mapped value type mismatch".into());}
if v1.is_empty() && self.missing_field(&caps,&[138]) {return Ok(());}
if v1.len()!=1 {return Err("startHour requires one node".into());}let f1=v1.pop().unwrap();self.give_values(v1);
let f1:u32=f1;
let mut t2=self.take_values();self.text_values(&caps,&[139],&mut t2);if t2.is_empty() && self.missing_field(&caps,&[139]) {return Ok(());}
if t2.len()!=1 {return Err("endDay requires one value".into());}let f2=t2.pop().unwrap();self.give_values(t2);
let f2:u32=f2;
let mut v3=self.take_values();self.node_values(&caps,&[140],&[],None,&mut v3)?;
if !v3.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BinaryExpr}) {return Err("endHour: mapped value type mismatch".into());}
if v3.is_empty() && self.missing_field(&caps,&[140]) {return Ok(());}
if v3.len()!=1 {return Err("endHour requires one node".into());}let f3=v3.pop().unwrap();self.give_values(v3);
let f3:u32=f3;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_InDayTimeRangeExpr,80,span,&[f0,f1,f2,f3]);out.push(node);Ok(())}}
fn map_rule_81(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{if self.value_region>0 {return Ok(());}let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_82(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_83(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_84(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_85(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_86(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[141, 145, 148, 151, 153, 156, 158, 160],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::KIND_TEXT || k==tree::K_g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_MethodInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_SliceExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToLowerCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToUpperCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_TrimDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_TrimExpr || k==tree::K_g_TinyExpressionP4AST_2e_VariableRefExpr}) {return Err("value: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[141, 145, 148, 151, 153, 156, 158, 160]) {return Ok(());}
if v0.len()!=1 {return Err("value requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
let mut v1=self.take_values();self.node_values(&caps,&[142, 146, 149, 152],&[],None,&mut v1)?;
if !v1.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BinaryExpr}) {return Err("start: mapped value type mismatch".into());}
if v1.len()>1 {return Err("start requires at most one node".into());}let f1=v1.pop();self.give_values(v1);
let f1:Option<u32>=f1;
let mut v2=self.take_values();self.node_values(&caps,&[143, 147, 154, 157],&[],None,&mut v2)?;
if !v2.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BinaryExpr}) {return Err("end: mapped value type mismatch".into());}
if v2.len()>1 {return Err("end requires at most one node".into());}let f2=v2.pop();self.give_values(v2);
let f2:Option<u32>=f2;
let mut v3=self.take_values();self.node_values(&caps,&[144, 150, 155, 159],&[],None,&mut v3)?;
if !v3.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BinaryExpr}) {return Err("step: mapped value type mismatch".into());}
if v3.len()>1 {return Err("step requires at most one node".into());}let f3=v3.pop();self.give_values(v3);
let f3:Option<u32>=f3;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_SliceExpr,86,span,&[f0,f1.unwrap_or(tree::NONE),f2.unwrap_or(tree::NONE),f3.unwrap_or(tree::NONE)]);out.push(node);Ok(())}}
fn map_rule_87(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[161, 165, 168, 171, 173, 176, 178, 180],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::KIND_TEXT || k==tree::K_g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_MethodInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_SliceExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToLowerCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToUpperCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_TrimDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_TrimExpr || k==tree::K_g_TinyExpressionP4AST_2e_VariableRefExpr}) {return Err("value: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[161, 165, 168, 171, 173, 176, 178, 180]) {return Ok(());}
if v0.len()!=1 {return Err("value requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
let mut v1=self.take_values();self.node_values(&caps,&[162, 166, 169, 172],&[],None,&mut v1)?;
if !v1.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BinaryExpr}) {return Err("start: mapped value type mismatch".into());}
if v1.len()>1 {return Err("start requires at most one node".into());}let f1=v1.pop();self.give_values(v1);
let f1:Option<u32>=f1;
let mut v2=self.take_values();self.node_values(&caps,&[163, 167, 174, 177],&[],None,&mut v2)?;
if !v2.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BinaryExpr}) {return Err("end: mapped value type mismatch".into());}
if v2.len()>1 {return Err("end requires at most one node".into());}let f2=v2.pop();self.give_values(v2);
let f2:Option<u32>=f2;
let mut v3=self.take_values();self.node_values(&caps,&[164, 170, 175, 179],&[],None,&mut v3)?;
if !v3.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BinaryExpr}) {return Err("step: mapped value type mismatch".into());}
if v3.len()>1 {return Err("step requires at most one node".into());}let f3=v3.pop();self.give_values(v3);
let f3:Option<u32>=f3;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_SliceExpr,87,span,&[f0,f1.unwrap_or(tree::NONE),f2.unwrap_or(tree::NONE),f3.unwrap_or(tree::NONE)]);out.push(node);Ok(())}}
fn map_rule_88(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_89(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[181],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::KIND_TEXT || k==tree::K_g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_IfExpr || k==tree::K_g_TinyExpressionP4AST_2e_MethodInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_SliceExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringCastVariableRefExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringMatchExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToLowerCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToUpperCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_TrimDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_TrimExpr || k==tree::K_g_TinyExpressionP4AST_2e_VariableRefExpr}) {return Err("left: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[181]) {return Ok(());}
if v0.len()!=1 {return Err("left requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
let mut f1=self.take_values();self.text_values(&caps,&[182],&mut f1);
let f1:Vec<u32>=f1;
let mut f2=self.take_values();self.node_values(&caps,&[183],&[],None,&mut f2)?;
if !f2.iter().all(|value|{let k=self.tree.kind(*value);k==tree::KIND_TEXT || k==tree::K_g_TinyExpressionP4AST_2e_ExternalStringInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_IfExpr || k==tree::K_g_TinyExpressionP4AST_2e_MethodInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_SliceExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringCastVariableRefExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringMatchExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToLowerCaseDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToLowerCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToUpperCaseDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_ToUpperCaseExpr || k==tree::K_g_TinyExpressionP4AST_2e_TrimDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_TrimExpr || k==tree::K_g_TinyExpressionP4AST_2e_VariableRefExpr}) {return Err("right: mapped value type mismatch".into());}
let f2:Vec<u32>=f2;
self.give_captures(caps);let span=self.span(span);let l1=self.tree.list(&f1);self.give_values(f1);let l2=self.tree.list(&f2);self.give_values(f2);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr,89,span,&[f0,l1[0],l1[1],l2[0],l2[1]]);out.push(node);Ok(())}}
fn map_rule_90(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_91(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_92(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut t0=self.take_values();self.text_values(&caps,&[184],&mut t0);if t0.is_empty() && self.missing_field(&caps,&[184]) {return Ok(());}
if t0.len()!=1 {return Err("name requires one value".into());}let f0=t0.pop().unwrap();self.give_values(t0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_StringCastVariableRefExpr,92,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_93(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut t0=self.take_values();self.text_values(&caps,&[185],&mut t0);if t0.is_empty() && self.missing_field(&caps,&[185]) {return Ok(());}
if t0.len()!=1 {return Err("name requires one value".into());}let f0=t0.pop().unwrap();self.give_values(t0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_StringTypedVariableRefExpr,93,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_94(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[186],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BooleanAndExpr}) {return Err("left: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[186]) {return Ok(());}
if v0.len()!=1 {return Err("left requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
let mut f1=self.take_values();self.text_values(&caps,&[187],&mut f1);
let f1:Vec<u32>=f1;
let mut f2=self.take_values();self.node_values(&caps,&[188],&[],None,&mut f2)?;
if !f2.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BooleanAndExpr}) {return Err("right: mapped value type mismatch".into());}
let f2:Vec<u32>=f2;
self.give_captures(caps);let span=self.span(span);let l1=self.tree.list(&f1);self.give_values(f1);let l2=self.tree.list(&f2);self.give_values(f2);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_BooleanOrExpr,94,span,&[f0,l1[0],l1[1],l2[0],l2[1]]);out.push(node);Ok(())}}
fn map_rule_95(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[189],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BooleanXorExpr}) {return Err("left: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[189]) {return Ok(());}
if v0.len()!=1 {return Err("left requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
let mut f1=self.take_values();self.text_values(&caps,&[190],&mut f1);
let f1:Vec<u32>=f1;
let mut f2=self.take_values();self.node_values(&caps,&[191],&[],None,&mut f2)?;
if !f2.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BooleanXorExpr}) {return Err("right: mapped value type mismatch".into());}
let f2:Vec<u32>=f2;
self.give_captures(caps);let span=self.span(span);let l1=self.tree.list(&f1);self.give_values(f1);let l2=self.tree.list(&f2);self.give_values(f2);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_BooleanAndExpr,95,span,&[f0,l1[0],l1[1],l2[0],l2[1]]);out.push(node);Ok(())}}
fn map_rule_96(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[192],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BooleanFactorExpr}) {return Err("left: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[192]) {return Ok(());}
if v0.len()!=1 {return Err("left requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
let mut f1=self.take_values();self.text_values(&caps,&[193],&mut f1);
let f1:Vec<u32>=f1;
let mut f2=self.take_values();self.node_values(&caps,&[194],&[],None,&mut f2)?;
if !f2.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BooleanFactorExpr}) {return Err("right: mapped value type mismatch".into());}
let f2:Vec<u32>=f2;
self.give_captures(caps);let span=self.span(span);let l1=self.tree.list(&f1);self.give_values(f1);let l2=self.tree.list(&f2);self.give_values(f2);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_BooleanXorExpr,96,span,&[f0,l1[0],l1[1],l2[0],l2[1]]);out.push(node);Ok(())}}
fn map_rule_97(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[195],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BooleanOrExpr}) {return Err("value: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[195]) {return Ok(());}
if v0.len()!=1 {return Err("value requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_NotExpr,97,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_98(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_99(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[196],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::KIND_TEXT || k==tree::K_g_TinyExpressionP4AST_2e_BooleanMatchExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanOrExpr || k==tree::K_g_TinyExpressionP4AST_2e_ContainsDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_ContainsExpr || k==tree::K_g_TinyExpressionP4AST_2e_EndsWithDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_EndsWithExpr || k==tree::K_g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_IfExpr || k==tree::K_g_TinyExpressionP4AST_2e_InDayTimeRangeExpr || k==tree::K_g_TinyExpressionP4AST_2e_InExpr || k==tree::K_g_TinyExpressionP4AST_2e_InTimeRangeExpr || k==tree::K_g_TinyExpressionP4AST_2e_IsPresentExpr || k==tree::K_g_TinyExpressionP4AST_2e_MethodInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_NotExpr || k==tree::K_g_TinyExpressionP4AST_2e_StartsWithDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_StartsWithExpr || k==tree::K_g_TinyExpressionP4AST_2e_VariableRefExpr}) {return Err("left: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[196]) {return Ok(());}
if v0.len()!=1 {return Err("left requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
let mut t1=self.take_values();self.text_values(&caps,&[197],&mut t1);if t1.is_empty() && self.missing_field(&caps,&[197]) {return Ok(());}
if t1.len()!=1 {return Err("op requires one value".into());}let f1=t1.pop().unwrap();self.give_values(t1);
let f1:u32=f1;
let mut v2=self.take_values();self.node_values(&caps,&[198],&[],None,&mut v2)?;
if !v2.iter().all(|value|{let k=self.tree.kind(*value);k==tree::KIND_TEXT || k==tree::K_g_TinyExpressionP4AST_2e_BooleanMatchExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanOrExpr || k==tree::K_g_TinyExpressionP4AST_2e_ContainsDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_ContainsExpr || k==tree::K_g_TinyExpressionP4AST_2e_EndsWithDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_EndsWithExpr || k==tree::K_g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_IfExpr || k==tree::K_g_TinyExpressionP4AST_2e_InDayTimeRangeExpr || k==tree::K_g_TinyExpressionP4AST_2e_InExpr || k==tree::K_g_TinyExpressionP4AST_2e_InTimeRangeExpr || k==tree::K_g_TinyExpressionP4AST_2e_IsPresentExpr || k==tree::K_g_TinyExpressionP4AST_2e_MethodInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_NotExpr || k==tree::K_g_TinyExpressionP4AST_2e_StartsWithDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_StartsWithExpr || k==tree::K_g_TinyExpressionP4AST_2e_VariableRefExpr}) {return Err("right: mapped value type mismatch".into());}
if v2.is_empty() && self.missing_field(&caps,&[198]) {return Ok(());}
if v2.len()!=1 {return Err("right requires one node".into());}let f2=v2.pop().unwrap();self.give_values(v2);
let f2:u32=f2;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_BooleanEqualityExpr,99,span,&[f0,f1,f2]);out.push(node);Ok(())}}
fn map_rule_100(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[199, 200, 201, 202],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::KIND_TEXT || k==tree::K_g_TinyExpressionP4AST_2e_BooleanEqualityExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanMatchExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanOrExpr || k==tree::K_g_TinyExpressionP4AST_2e_ComparisonExpr || k==tree::K_g_TinyExpressionP4AST_2e_ContainsDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_ContainsExpr || k==tree::K_g_TinyExpressionP4AST_2e_EndsWithDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_EndsWithExpr || k==tree::K_g_TinyExpressionP4AST_2e_ExternalBooleanInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_IfExpr || k==tree::K_g_TinyExpressionP4AST_2e_InDayTimeRangeExpr || k==tree::K_g_TinyExpressionP4AST_2e_InExpr || k==tree::K_g_TinyExpressionP4AST_2e_InTimeRangeExpr || k==tree::K_g_TinyExpressionP4AST_2e_IsPresentExpr || k==tree::K_g_TinyExpressionP4AST_2e_MethodInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_NotExpr || k==tree::K_g_TinyExpressionP4AST_2e_StartsWithDotExpr || k==tree::K_g_TinyExpressionP4AST_2e_StartsWithExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringComparisonExpr || k==tree::K_g_TinyExpressionP4AST_2e_VariableRefExpr}) {return Err("value: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[199, 200, 201, 202]) {return Ok(());}
if v0.len()!=1 {return Err("value requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_BooleanFactorExpr,100,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_101(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[203],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr}) {return Err("left: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[203]) {return Ok(());}
if v0.len()!=1 {return Err("left requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
let mut t1=self.take_values();self.text_values(&caps,&[204],&mut t1);if t1.is_empty() && self.missing_field(&caps,&[204]) {return Ok(());}
if t1.len()!=1 {return Err("op requires one value".into());}let f1=t1.pop().unwrap();self.give_values(t1);
let f1:u32=f1;
let mut v2=self.take_values();self.node_values(&caps,&[205],&[],None,&mut v2)?;
if !v2.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr}) {return Err("right: mapped value type mismatch".into());}
if v2.is_empty() && self.missing_field(&caps,&[205]) {return Ok(());}
if v2.len()!=1 {return Err("right requires one node".into());}let f2=v2.pop().unwrap();self.give_values(v2);
let f2:u32=f2;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_StringComparisonExpr,101,span,&[f0,f1,f2]);out.push(node);Ok(())}}
fn map_rule_102(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{if self.value_region>0 {return Ok(());}let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_103(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[206],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BinaryExpr}) {return Err("left: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[206]) {return Ok(());}
if v0.len()!=1 {return Err("left requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
let mut t1=self.take_values();self.text_values(&caps,&[207],&mut t1);if t1.is_empty() && self.missing_field(&caps,&[207]) {return Ok(());}
if t1.len()!=1 {return Err("op requires one value".into());}let f1=t1.pop().unwrap();self.give_values(t1);
let f1:u32=f1;
let mut v2=self.take_values();self.node_values(&caps,&[208],&[],None,&mut v2)?;
if !v2.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BinaryExpr}) {return Err("right: mapped value type mismatch".into());}
if v2.is_empty() && self.missing_field(&caps,&[208]) {return Ok(());}
if v2.len()!=1 {return Err("right requires one node".into());}let f2=v2.pop().unwrap();self.give_values(v2);
let f2:u32=f2;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_ComparisonExpr,103,span,&[f0,f1,f2]);out.push(node);Ok(())}}
fn map_rule_104(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{if self.value_region>0 {return Ok(());}let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_105(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[209, 210, 211, 212, 213, 214],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BinaryExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanOrExpr || k==tree::K_g_TinyExpressionP4AST_2e_ExternalObjectInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_MethodInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr || k==tree::K_g_TinyExpressionP4AST_2e_VariableRefExpr}) {return Err("value: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[209, 210, 211, 212, 213, 214]) {return Ok(());}
if v0.len()!=1 {return Err("value requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_ObjectExpr,105,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_106(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[215],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BooleanOrExpr}) {return Err("condition: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[215]) {return Ok(());}
if v0.len()!=1 {return Err("condition requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
let mut v1=self.take_values();self.node_values(&caps,&[216],&[],None,&mut v1)?;
if !v1.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BranchExpressionExpr}) {return Err("thenExpr: mapped value type mismatch".into());}
if v1.is_empty() && self.missing_field(&caps,&[216]) {return Ok(());}
if v1.len()!=1 {return Err("thenExpr requires one node".into());}let f1=v1.pop().unwrap();self.give_values(v1);
let f1:u32=f1;
let mut v2=self.take_values();self.node_values(&caps,&[217],&[],None,&mut v2)?;
if !v2.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BranchExpressionExpr}) {return Err("elseExpr: mapped value type mismatch".into());}
if v2.is_empty() && self.missing_field(&caps,&[217]) {return Ok(());}
if v2.len()!=1 {return Err("elseExpr requires one node".into());}let f2=v2.pop().unwrap();self.give_values(v2);
let f2:u32=f2;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_IfExpr,106,span,&[f0,f1,f2]);out.push(node);Ok(())}}
fn map_rule_107(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[218, 219, 220, 221, 222, 223, 224, 225],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BinaryExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanEqualityExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanOrExpr || k==tree::K_g_TinyExpressionP4AST_2e_ComparisonExpr || k==tree::K_g_TinyExpressionP4AST_2e_MethodInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_ObjectExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringComparisonExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr}) {return Err("value: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[218, 219, 220, 221, 222, 223, 224, 225]) {return Ok(());}
if v0.len()!=1 {return Err("value requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_BranchExpressionExpr,107,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_108(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[226],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BooleanOrExpr}) {return Err("condition: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[226]) {return Ok(());}
if v0.len()!=1 {return Err("condition requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
let mut v1=self.take_values();self.node_values(&caps,&[227],&[],None,&mut v1)?;
if !v1.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BranchExpressionExpr}) {return Err("thenExpr: mapped value type mismatch".into());}
if v1.is_empty() && self.missing_field(&caps,&[227]) {return Ok(());}
if v1.len()!=1 {return Err("thenExpr requires one node".into());}let f1=v1.pop().unwrap();self.give_values(v1);
let f1:u32=f1;
let mut v2=self.take_values();self.node_values(&caps,&[228],&[],None,&mut v2)?;
if !v2.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BranchExpressionExpr}) {return Err("elseExpr: mapped value type mismatch".into());}
if v2.is_empty() && self.missing_field(&caps,&[228]) {return Ok(());}
if v2.len()!=1 {return Err("elseExpr requires one node".into());}let f2=v2.pop().unwrap();self.give_values(v2);
let f2:u32=f2;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_TernaryExpr,108,span,&[f0,f1,f2]);out.push(node);Ok(())}}
fn map_rule_109(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[229],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_NumberCaseExpr}) {return Err("firstCase: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[229]) {return Ok(());}
if v0.len()!=1 {return Err("firstCase requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
let mut f1=self.take_values();self.node_values(&caps,&[230],&[],None,&mut f1)?;
if !f1.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_NumberCaseExpr}) {return Err("moreCases: mapped value type mismatch".into());}
let f1:Vec<u32>=f1;
let mut v2=self.take_values();self.node_values(&caps,&[231],&[],None,&mut v2)?;
if !v2.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr}) {return Err("defaultCase: mapped value type mismatch".into());}
if v2.is_empty() && self.missing_field(&caps,&[231]) {return Ok(());}
if v2.len()!=1 {return Err("defaultCase requires one node".into());}let f2=v2.pop().unwrap();self.give_values(v2);
let f2:u32=f2;
self.give_captures(caps);let span=self.span(span);let l1=self.tree.list(&f1);self.give_values(f1);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_NumberMatchExpr,109,span,&[f0,l1[0],l1[1],f2]);out.push(node);Ok(())}}
fn map_rule_110(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[232],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BooleanOrExpr}) {return Err("condition: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[232]) {return Ok(());}
if v0.len()!=1 {return Err("condition requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
let mut v1=self.take_values();self.node_values(&caps,&[233],&[],None,&mut v1)?;
if !v1.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_NumberCaseValueExpr}) {return Err("value: mapped value type mismatch".into());}
if v1.is_empty() && self.missing_field(&caps,&[233]) {return Ok(());}
if v1.len()!=1 {return Err("value requires one node".into());}let f1=v1.pop().unwrap();self.give_values(v1);
let f1:u32=f1;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_NumberCaseExpr,110,span,&[f0,f1]);out.push(node);Ok(())}}
fn map_rule_111(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[234],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_NumberCaseValueExpr}) {return Err("value: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[234]) {return Ok(());}
if v0.len()!=1 {return Err("value requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_NumberDefaultCaseExpr,111,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_112(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[235],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BinaryExpr}) {return Err("value: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[235]) {return Ok(());}
if v0.len()!=1 {return Err("value requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_NumberCaseValueExpr,112,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_113(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[236],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_StringCaseExpr}) {return Err("firstCase: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[236]) {return Ok(());}
if v0.len()!=1 {return Err("firstCase requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
let mut f1=self.take_values();self.node_values(&caps,&[237],&[],None,&mut f1)?;
if !f1.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_StringCaseExpr}) {return Err("moreCases: mapped value type mismatch".into());}
let f1:Vec<u32>=f1;
let mut v2=self.take_values();self.node_values(&caps,&[238],&[],None,&mut v2)?;
if !v2.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_StringDefaultCaseExpr}) {return Err("defaultCase: mapped value type mismatch".into());}
if v2.is_empty() && self.missing_field(&caps,&[238]) {return Ok(());}
if v2.len()!=1 {return Err("defaultCase requires one node".into());}let f2=v2.pop().unwrap();self.give_values(v2);
let f2:u32=f2;
self.give_captures(caps);let span=self.span(span);let l1=self.tree.list(&f1);self.give_values(f1);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_StringMatchExpr,113,span,&[f0,l1[0],l1[1],f2]);out.push(node);Ok(())}}
fn map_rule_114(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[239],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BooleanOrExpr}) {return Err("condition: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[239]) {return Ok(());}
if v0.len()!=1 {return Err("condition requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
let mut v1=self.take_values();self.node_values(&caps,&[240],&[],None,&mut v1)?;
if !v1.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_StringCaseValueExpr}) {return Err("value: mapped value type mismatch".into());}
if v1.is_empty() && self.missing_field(&caps,&[240]) {return Ok(());}
if v1.len()!=1 {return Err("value requires one node".into());}let f1=v1.pop().unwrap();self.give_values(v1);
let f1:u32=f1;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_StringCaseExpr,114,span,&[f0,f1]);out.push(node);Ok(())}}
fn map_rule_115(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[241],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_StringCaseValueExpr}) {return Err("value: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[241]) {return Ok(());}
if v0.len()!=1 {return Err("value requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_StringDefaultCaseExpr,115,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_116(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[242],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr}) {return Err("value: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[242]) {return Ok(());}
if v0.len()!=1 {return Err("value requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_StringCaseValueExpr,116,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_117(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[243],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BooleanCaseExpr}) {return Err("firstCase: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[243]) {return Ok(());}
if v0.len()!=1 {return Err("firstCase requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
let mut f1=self.take_values();self.node_values(&caps,&[244],&[],None,&mut f1)?;
if !f1.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BooleanCaseExpr}) {return Err("moreCases: mapped value type mismatch".into());}
let f1:Vec<u32>=f1;
let mut v2=self.take_values();self.node_values(&caps,&[245],&[],None,&mut v2)?;
if !v2.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr}) {return Err("defaultCase: mapped value type mismatch".into());}
if v2.is_empty() && self.missing_field(&caps,&[245]) {return Ok(());}
if v2.len()!=1 {return Err("defaultCase requires one node".into());}let f2=v2.pop().unwrap();self.give_values(v2);
let f2:u32=f2;
self.give_captures(caps);let span=self.span(span);let l1=self.tree.list(&f1);self.give_values(f1);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_BooleanMatchExpr,117,span,&[f0,l1[0],l1[1],f2]);out.push(node);Ok(())}}
fn map_rule_118(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[246],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BooleanOrExpr}) {return Err("condition: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[246]) {return Ok(());}
if v0.len()!=1 {return Err("condition requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
let mut v1=self.take_values();self.node_values(&caps,&[247],&[],None,&mut v1)?;
if !v1.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BooleanCaseValueExpr}) {return Err("value: mapped value type mismatch".into());}
if v1.is_empty() && self.missing_field(&caps,&[247]) {return Ok(());}
if v1.len()!=1 {return Err("value requires one node".into());}let f1=v1.pop().unwrap();self.give_values(v1);
let f1:u32=f1;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_BooleanCaseExpr,118,span,&[f0,f1]);out.push(node);Ok(())}}
fn map_rule_119(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[248],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BooleanCaseValueExpr}) {return Err("value: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[248]) {return Ok(());}
if v0.len()!=1 {return Err("value requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_BooleanDefaultCaseExpr,119,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_120(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[249],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BooleanOrExpr}) {return Err("value: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[249]) {return Ok(());}
if v0.len()!=1 {return Err("value requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_BooleanCaseValueExpr,120,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_121(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut t0=self.take_values();self.text_values(&caps,&[250],&mut t0);if t0.is_empty() && self.missing_field(&caps,&[250]) {return Ok(());}
if t0.len()!=1 {return Err("name requires one value".into());}let f0=t0.pop().unwrap();self.give_values(t0);
let f0:u32=f0;
let mut t1=self.take_values();self.text_values(&caps,&[251],&mut t1);if t1.len()>1 {return Err("type requires at most one value".into());}let f1=t1.pop();self.give_values(t1);
let f1:Option<u32>=f1;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_VariableRefExpr,121,span,&[f0,f1.unwrap_or(tree::NONE)]);out.push(node);Ok(())}}
fn map_rule_122(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{if self.value_region>0 {return Ok(());}let start=out.len();self.build_values_into(child,out)?;if out.len()==start && !self.has_recovery(child) && !self.has_value_group(child) {let text=self.semantic_text(child,span);out.push(text);}Ok(())}}
fn map_rule_123(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[252, 253, 254, 255, 256, 257],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BinaryExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanOrExpr || k==tree::K_g_TinyExpressionP4AST_2e_ExpressionExpr || k==tree::K_g_TinyExpressionP4AST_2e_MethodInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_ObjectExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr}) {return Err("value: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[252, 253, 254, 255, 256, 257]) {return Ok(());}
if v0.len()!=1 {return Err("value requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_ExpressionExpr,123,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_124(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[258],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BinaryExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanOrExpr || k==tree::K_g_TinyExpressionP4AST_2e_ExpressionExpr || k==tree::K_g_TinyExpressionP4AST_2e_MethodInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_ObjectExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr}) {return Err("value: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[258]) {return Ok(());}
if v0.len()!=1 {return Err("value requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_ExpressionExpr,124,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_125(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[259],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BinaryExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanOrExpr || k==tree::K_g_TinyExpressionP4AST_2e_ExpressionExpr || k==tree::K_g_TinyExpressionP4AST_2e_MethodInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_ObjectExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr}) {return Err("value: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[259]) {return Ok(());}
if v0.len()!=1 {return Err("value requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_ExpressionExpr,125,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_126(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[260],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BinaryExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanOrExpr || k==tree::K_g_TinyExpressionP4AST_2e_ExpressionExpr || k==tree::K_g_TinyExpressionP4AST_2e_MethodInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_ObjectExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr}) {return Err("value: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[260]) {return Ok(());}
if v0.len()!=1 {return Err("value requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_ExpressionExpr,126,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_127(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[261],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BinaryExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanOrExpr || k==tree::K_g_TinyExpressionP4AST_2e_ExpressionExpr || k==tree::K_g_TinyExpressionP4AST_2e_MethodInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_ObjectExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr}) {return Err("value: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[261]) {return Ok(());}
if v0.len()!=1 {return Err("value requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_ExpressionExpr,127,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_128(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[262],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BinaryExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanOrExpr || k==tree::K_g_TinyExpressionP4AST_2e_ExpressionExpr || k==tree::K_g_TinyExpressionP4AST_2e_MethodInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_ObjectExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr}) {return Err("value: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[262]) {return Ok(());}
if v0.len()!=1 {return Err("value requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_ExpressionExpr,128,span,&[f0]);out.push(node);Ok(())}}
fn map_rule_129(&mut self,caps:(u32,u32),span:Span,child:EventId,out:&mut Vec<u32>)->Result<(),String> {let _=(caps,span,child,&out);{let mut slots=self.take_captures();self.rule_captures(caps,&mut slots);let caps=slots;
let mut v0=self.take_values();self.node_values(&caps,&[263],&[],None,&mut v0)?;
if !v0.iter().all(|value|{let k=self.tree.kind(*value);k==tree::K_g_TinyExpressionP4AST_2e_BinaryExpr || k==tree::K_g_TinyExpressionP4AST_2e_BooleanOrExpr || k==tree::K_g_TinyExpressionP4AST_2e_ExpressionExpr || k==tree::K_g_TinyExpressionP4AST_2e_MethodInvocationExpr || k==tree::K_g_TinyExpressionP4AST_2e_ObjectExpr || k==tree::K_g_TinyExpressionP4AST_2e_StringConcatExpr}) {return Err("value: mapped value type mismatch".into());}
if v0.is_empty() && self.missing_field(&caps,&[263]) {return Ok(());}
if v0.len()!=1 {return Err("value requires one node".into());}let f0=v0.pop().unwrap();self.give_values(v0);
let f0:u32=f0;
self.give_captures(caps);let span=self.span(span);let node=self.tree.record(tree::K_g_TinyExpressionP4AST_2e_ExpressionExpr,129,span,&[f0]);out.push(node);Ok(())}}
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
