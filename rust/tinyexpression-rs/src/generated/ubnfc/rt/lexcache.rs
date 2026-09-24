// ubnfc runtime template: 位置ごとの字句 cache（統合 DFA の結果と trivia の読み飛ばし先）。
//
// 健全性: UBNF の終端は (入力, 位置) の純粋な述語である（例外は状態依存を宣言する Extern
// token だけで、そこは cache しない）。したがって結果を位置ごとに保存しても、どの parse の
// 受理・診断も変わらない。key は位置（と trivia policy）だけで、parser の状態（consumed /
// matched のどちらを選ぶか、mode、invert、reset、scope 版）は key に入らない。呼出し側が
// 「選ばれたカーソル位置」を渡すので、matchOnly の照合が consumed 用の entry を汚すことは
// ない（どちらも同じ位置なら同じ答えになる）。
use std::cell::Cell;

/// 位置ごとの literal 一致集合。1 位置あたり `words` 個の u64（literal id の bitset）。
/// 長さは literal ごとに静的（word の byte 長）なので保持しない。
/// epoch 印で parse ごとの消去を避ける（確保は pool が保持する）。
#[derive(Default)]
pub struct LexCache {
    words: usize,
    bits: Vec<u64>,
    epoch: Vec<u32>,
    generation: u32,
    /// 統合 DFA を走らせた回数（= cache miss）。
    pub runs: u64,
    /// 参照回数（= 終端照合の試行のうち cache を引いたもの）。
    pub probes: u64,
}
impl LexCache {
    /// 入力長に合わせて作り直す。同じ形なら epoch を進めるだけで消去しない。
    pub fn reset(&mut self, words: usize, positions: usize) {
        self.runs = 0;
        self.probes = 0;
        if words == 0 || positions == 0 {
            self.words = 0;
            self.epoch.clear();
            self.bits.clear();
            return;
        }
        if self.words != words || self.epoch.len() < positions {
            self.words = words;
            self.bits = vec![0; words * positions];
            self.epoch = vec![0; positions];
            self.generation = 1;
            return;
        }
        self.generation = self.generation.wrapping_add(1);
        if self.generation == 0 {
            self.epoch.iter_mut().for_each(|e| *e = 0);
            self.generation = 1;
        }
    }
    pub fn capacity_bytes(&self) -> usize {
        self.bits.capacity() * std::mem::size_of::<u64>()
            + self.epoch.capacity() * std::mem::size_of::<u32>()
    }
    /// この位置をまだ埋めていなければ印を付けて true。範囲外は None（呼出し側は走査へ戻る）。
    #[inline]
    pub fn need_fill(&mut self, pos: usize) -> Option<bool> {
        self.probes += 1;
        let epoch = self.epoch.get_mut(pos)?;
        if *epoch == self.generation {
            return Some(false);
        }
        *epoch = self.generation;
        self.runs += 1;
        Some(true)
    }
    /// 埋める先の bitset（0 に戻して返す）。`need_fill` が Some を返した位置にだけ使う。
    #[inline]
    pub fn slot(&mut self, pos: usize) -> &mut [u64] {
        let words = self.words;
        let slot = &mut self.bits[pos * words..pos * words + words];
        slot.fill(0);
        slot
    }
    #[inline]
    pub fn test(&self, pos: usize, id: u32) -> bool {
        let index = pos * self.words + (id as usize) / 64;
        self.bits[index] >> ((id as usize) % 64) & 1 == 1
    }
}

/// trivia policy ごとの `next_non_trivia[p]`。診断を記録しない読み飛ばし（`skip_i`、候補除外の
/// 判定位置にだけ使う）の結果を位置で直接引く。この読み飛ばしは display / 主診断のどちらにも
/// 触れないので、(policy, 位置, mode) だけを key にすれば観測は変わらない。
/// `skip_i` は `&self` を取る（呼出し側が `self.input` を同時に借りる）ので Cell で持つ。
#[derive(Default)]
pub struct SkipCache {
    stride: usize,
    /// (consumed 側の進み, matched 側の終端) を開始位置からの差で持つ。
    /// consumed 側が `u32::MAX` のときは「進まない」印（入力 State をそのまま返す）。
    deltas: Vec<Cell<(u32, u32)>>,
    epoch: Vec<Cell<u32>>,
    generation: Cell<u32>,
    pub hits: Cell<u64>,
    pub misses: Cell<u64>,
}
impl SkipCache {
    pub fn reset(&mut self, policies: usize, positions: usize) {
        self.hits.set(0);
        self.misses.set(0);
        let len = policies.saturating_mul(2).saturating_mul(positions);
        if len == 0 {
            self.stride = 0;
            self.deltas.clear();
            self.epoch.clear();
            return;
        }
        // 小さい入力へ戻ったときに確保を捨てない（stride は見た最大の入力長で据え置く）。
        let stride = positions.max(self.stride);
        let len = policies.saturating_mul(2).saturating_mul(stride);
        if stride != self.stride || self.epoch.len() < len {
            self.stride = stride;
            self.deltas = vec![Cell::new((0, 0)); len];
            self.epoch = vec![Cell::new(0); len];
            self.generation.set(1);
            return;
        }
        let next = self.generation.get().wrapping_add(1);
        if next == 0 {
            self.epoch.iter().for_each(|e| e.set(0));
            self.generation.set(1);
        } else {
            self.generation.set(next);
        }
    }
    pub fn capacity_bytes(&self) -> usize {
        self.deltas.capacity() * std::mem::size_of::<Cell<(u32, u32)>>()
            + self.epoch.capacity() * std::mem::size_of::<Cell<u32>>()
    }
    #[inline]
    fn index(&self, policy: usize, matched_mode: bool, pos: usize) -> Option<usize> {
        if pos >= self.stride {
            return None;
        }
        Some((policy * 2 + usize::from(matched_mode)) * self.stride + pos)
    }
    #[inline]
    pub fn get(&self, policy: usize, matched_mode: bool, pos: usize) -> Option<(u32, u32)> {
        let index = self.index(policy, matched_mode, pos)?;
        if self.epoch[index].get() != self.generation.get() {
            self.misses.set(self.misses.get() + 1);
            return None;
        }
        self.hits.set(self.hits.get() + 1);
        Some(self.deltas[index].get())
    }
    /// `consumed` が `u32::MAX` なら「1 文字も進まない」（State をそのまま返す）。
    #[inline]
    pub fn insert(&self, policy: usize, matched_mode: bool, pos: usize, deltas: (u32, u32)) {
        let Some(index) = self.index(policy, matched_mode, pos) else {
            return;
        };
        self.deltas[index].set(deltas);
        self.epoch[index].set(self.generation.get());
    }
}
