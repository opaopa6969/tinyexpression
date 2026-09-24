// 局所診断を共有する arena。memo replay の rule path は親に接続して復元する。
// handle は位置を持ち、join の比較で arena を参照しない（大きな arena への
// ランダム参照を避ける）。id 0 は「診断なし」。
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct Diag {
    pub id: u32,
    pub pos: u32,
}
impl Diag {
    pub const NONE: Diag = Diag { id: 0, pos: 0 };
}
// node は 24 byte。位置は handle が持つので Failure は label だけを持つ。
#[derive(Clone, Copy)]
enum Node {
    Empty,
    Failure(&'static str),
    Join(Diag, Diag),
    Rule(u32, Diag),
}
/// summary の node id 用 hasher（u32 の乗算 hash。SipHash より安い）。
#[derive(Default)]
pub struct IdHasher(u64);
impl std::hash::Hasher for IdHasher {
    fn finish(&self) -> u64 {
        let h = self.0.wrapping_mul(0x9E37_79B9_7F4A_7C15);
        h ^ (h >> 32)
    }
    fn write(&mut self, bytes: &[u8]) {
        for &b in bytes {
            self.0 = (self.0 << 8) | u64::from(b);
        }
    }
    fn write_u32(&mut self, n: u32) {
        self.0 = u64::from(n);
    }
}
pub struct Diagnostics {
    nodes: Vec<Node>,
    limit: usize,
    // 記録しない parse（fast mode）では limit を 0 にして push を最初の検査で返す。
    // `enabled` はそのときに予算超過（`exhausted`）と誤判定しないためだけに要る。
    enabled: bool,
    pub exhausted: bool,
}
impl Diagnostics {
    pub fn new() -> Self {
        Self::with_limit(2_000_000)
    }
    pub fn with_limit(limit: usize) -> Self {
        Self::with_limit_and_capacity(limit, 0)
    }
    /// capacity は入力長から見積もる（P4 実測 約 18 node / byte）。予算より大きくは確保しない。
    pub fn with_limit_and_capacity(limit: usize, capacity: usize) -> Self {
        let limit = limit.min(u32::MAX as usize);
        let mut nodes = Vec::with_capacity(capacity.min(limit).clamp(1, 1 << 20));
        nodes.push(Node::Empty);
        Self {
            nodes,
            limit,
            enabled: true,
            exhausted: false,
        }
    }
    /// 記録の有無を切り替える（`false` は limit 0 と同じ扱いで、予算超過にはしない）。
    #[allow(dead_code)]
    pub fn enable(&mut self, enabled: bool) {
        self.enabled = enabled;
    }
    /// 内容を捨てて再利用する（確保は保持）。
    #[allow(dead_code)]
    pub fn reset(&mut self, limit: usize) {
        self.nodes.clear();
        self.nodes.push(Node::Empty);
        self.limit = limit.min(u32::MAX as usize);
        self.enabled = true;
        self.exhausted = false;
    }
    #[allow(dead_code)]
    pub fn capacity_bytes(&self) -> usize {
        self.nodes.capacity() * std::mem::size_of::<Node>()
    }
    fn push(&mut self, pos: usize, node: Node) -> Diag {
        if self.nodes.len() >= self.limit {
            // fast mode（limit 0）は予算超過ではない。
            self.exhausted |= self.enabled;
            return Diag::NONE;
        }
        let Ok(pos) = u32::try_from(pos) else {
            self.exhausted = true;
            return Diag::NONE;
        };
        let id = self.nodes.len() as u32;
        self.nodes.push(node);
        Diag { id, pos }
    }
    #[inline]
    pub fn fail(&mut self, pos: usize, label: &'static str) -> Diag {
        if !self.enabled {
            return Diag::NONE;
        }
        self.push(pos, Node::Failure(label))
    }
    #[inline]
    pub fn join(&mut self, a: Diag, b: Diag) -> Diag {
        if a.id == 0 {
            return b;
        }
        if b.id == 0 || a == b {
            return a;
        }
        if a.pos > b.pos {
            a
        } else if b.pos > a.pos {
            b
        } else {
            self.push(a.pos as usize, Node::Join(a, b))
        }
    }
    #[inline]
    pub fn rule(&mut self, rule: usize, child: Diag) -> Diag {
        if child.id == 0 {
            child
        } else {
            self.push(child.pos as usize, Node::Rule(rule as u32, child))
        }
    }
    pub fn summary(&self, root: Diag) -> Option<(usize, Vec<&'static str>, Vec<usize>)> {
        if root.id == 0 {
            return None;
        }
        let mut expected: Vec<&'static str> = Vec::new();
        // label の重複は内容で除く（線形探索。位置ごとの label は少数）。
        // Local suffix depth is independent of the caller. Reconnect the chosen
        // suffix from the root, so a shared node reached by a longer path wins.
        let mut depths = std::collections::HashMap::<
            u32,
            usize,
            std::hash::BuildHasherDefault<IdHasher>,
        >::default();
        let mut stack = vec![(root, false)];
        while let Some((id, leave)) = stack.pop() {
            if leave {
                depths.insert(
                    id.id,
                    match self.nodes[id.id as usize] {
                        Node::Join(a, b) => depths[&a.id].max(depths[&b.id]),
                        Node::Rule(_, child) => depths[&child.id] + 1,
                        _ => 0usize,
                    },
                );
                continue;
            }
            if depths.contains_key(&id.id) {
                continue;
            }
            match self.nodes[id.id as usize] {
                Node::Empty => {
                    depths.insert(id.id, 0);
                }
                Node::Failure(label) => {
                    if !expected.contains(&label) {
                        expected.push(label);
                    }
                    depths.insert(id.id, 0);
                }
                Node::Join(a, b) => {
                    stack.push((id, true));
                    stack.push((b, false));
                    stack.push((a, false));
                }
                Node::Rule(_, child) => {
                    stack.push((id, true));
                    stack.push((child, false));
                }
            }
        }
        let mut deepest = Vec::new();
        let mut id = root;
        loop {
            match self.nodes[id.id as usize] {
                Node::Join(a, b) => id = if depths[&a.id] >= depths[&b.id] { a } else { b },
                Node::Rule(rule, child) => {
                    deepest.push(rule as usize);
                    id = child;
                }
                _ => break,
            }
        }
        Some((root.pos as usize, expected, deepest))
    }
}

impl Default for Diagnostics {
    fn default() -> Self {
        Self::new()
    }
}

// D-014: 公開 farthest 候補の局所 frame。Rust の fail_at 主位置とは独立。
// 観測できるのは「最遠位置とその位置の label 集合」だけなので、frame ごとに
// (far, base, reached) を持ち、label は 1 本の log に追記する。frame の生存 label は
// log[base..] で、全て far 位置のもの。leave で親へ O(1)（同位置は連結済み、
// より遠い場合は移動、近い場合は切り捨て）に伝播する。失敗 1 件は O(1)。
// floor は Not の外で記録した最遠位置。Not の外の frame は破棄されないので、
// floor は最終的な最遠位置の下界であり、それより手前の label は（memo に保存しても
// 再生時に捨てられるため）記録しない。
// 成功 rule の失敗も残し、Not 成功時だけ frame を破棄する。memo は局所
// snapshot（far, reached, saved label 範囲）を再生するため、保存時の親 frame を混ぜない。
#[derive(Clone, Copy, Debug, Default)]
pub struct DisplaySnapshot {
    pub far: u32,
    pub reached: u32,
    pub labels: [u32; 2],
}
// log の要素は記録の番号（`registry` の index、u32）。候補の失敗 label 列（emitter が置く静的
// 配列）は 1 記録とし、重複除去も記録単位（先頭 pointer）で行う（label ごとの hash を避ける）。
// 個々の label に展開するのは summary だけ。log / saved を 4 byte にするのは、入れ子の memo
// snapshot が同じ range を何度も saved へ複製するため（x64 で 1 parse 約 90 万記録）。
// registry は process の生存期間中有効な静的 pointer だけを持つので parse をまたいで保持する。
#[derive(Clone, Copy)]
enum Entry {
    One(&'static str),
    Many(&'static [&'static str]),
}
impl Entry {
    #[inline]
    fn key(self) -> usize {
        match self {
            Entry::One(label) => label.as_ptr() as usize,
            Entry::Many(labels) => labels.as_ptr() as usize,
        }
    }
    fn labels(&self) -> &[&'static str] {
        match self {
            Entry::One(label) => std::slice::from_ref(label),
            Entry::Many(labels) => labels,
        }
    }
}
/// pointer 用 hasher（乗算 hash。SipHash より安い）。
#[derive(Default)]
pub struct KeyHasher(u64);
impl std::hash::Hasher for KeyHasher {
    fn finish(&self) -> u64 {
        let h = self.0.wrapping_mul(0x9E37_79B9_7F4A_7C15);
        h ^ (h >> 29)
    }
    fn write(&mut self, bytes: &[u8]) {
        for &b in bytes {
            self.0 = (self.0 << 8) | u64::from(b);
        }
    }
    fn write_usize(&mut self, n: usize) {
        self.0 = n as u64 >> 3;
    }
}
// 記録 → 番号。直接写像の cache（pointer の hash で 1 slot）で大半を引き、外れたときだけ
// HashMap を引く（無ければ登録）。
struct Registry {
    entries: Vec<Entry>,
    lookup: std::collections::HashMap<usize, u32, std::hash::BuildHasherDefault<KeyHasher>>,
    cache: Vec<(usize, u32)>,
}
impl Registry {
    fn new() -> Self {
        Self {
            entries: vec![],
            lookup: Default::default(),
            cache: vec![(usize::MAX, 0); 1024],
        }
    }
    #[inline]
    fn intern(&mut self, entry: Entry) -> u32 {
        let key = entry.key();
        let slot = ((key >> 4).wrapping_mul(0x9E37_79B1) >> 7) & (self.cache.len() - 1);
        let cached = self.cache[slot];
        if cached.0 == key {
            return cached.1;
        }
        let index = *self.lookup.entry(key).or_insert_with(|| {
            let index = u32::try_from(self.entries.len()).expect("display registry");
            self.entries.push(entry);
            index
        });
        self.cache[slot] = (key, index);
        index
    }
}
#[derive(Clone, Copy, Debug, Default)]
struct Frame {
    far: usize,
    base: usize,
    reached: usize,
    // 生存記録集合（log[base..]）の世代印。range が空になる操作は必ず世代を付け替える。
    generation: u32,
}
pub struct DisplayDiagnostics {
    log: Vec<u32>,
    saved: Vec<u32>,
    registry: Registry,
    // 現在（最も内側）の frame は field に持ち、外側の frame だけを stack に積む
    // （記録のたびに末尾を引かない）。
    current: Frame,
    frames: Vec<Frame>,
    seen: Seen,
    // log の変更ごとに進む版。直前の snapshot と同じ range（同じ base、同じ版）なら保存済みの
    // range を共有する（入れ子の memo が同じ内容を何度も saved へ複製しない）。
    version: u64,
    last_saved: (u64, usize, [u32; 2]),
    floor: usize,
    guards: usize,
    work: usize,
    limit: usize,
    // 記録しない parse（fast mode）。frame の出入りも含めて何もしない。
    enabled: bool,
    pub exhausted: bool,
}
// 生存記録の重複除去は記録時に番号で行う（静的 label は同じ定数を指すことが多く、内容の重複は
// summary が落とす）。世代印付きの小さな open addressing 表で、frame の生存集合ごとに世代を
// 持つ。表が混んでいて判定できないときは重複を残す（重複は観測できない。summary は集合として
// 扱い、予算は記録回数で数える）。逆に「無い記録を有る」と誤ることは無い: (記録, 世代) は
// 記録を世代の range へ push したときだけ置き、range が空になる操作（open の位置更新、
// leave / discard の切り捨て）は世代を捨てる。
struct Seen {
    slots: Vec<(u32, u32)>,
    generation: u32,
}
impl Seen {
    fn new() -> Self {
        Self {
            slots: vec![(0, 0); 256],
            generation: 0,
        }
    }
    // 新しい世代（以前の世代の印は全て無効になる）。
    fn next(&mut self) -> u32 {
        self.generation = self.generation.wrapping_add(1);
        if self.generation == 0 {
            self.slots.iter_mut().for_each(|s| *s = (0, 0));
            self.generation = 1;
        }
        self.generation
    }
    // 記録が世代の集合に無ければ加えて true。表が混んでいるときも true（重複を残すだけ）。
    #[inline]
    fn insert(&mut self, generation: u32, entry: u32) -> bool {
        let mask = self.slots.len() - 1;
        let mut index = (entry.wrapping_mul(0x9E37_79B1) >> 8) as usize & mask;
        for _ in 0..8 {
            let slot = self.slots[index];
            if slot.1 != generation {
                self.slots[index] = (entry, generation);
                return true;
            }
            if slot.0 == entry {
                return false;
            }
            index = (index + 1) & mask;
        }
        true
    }
}
impl DisplayDiagnostics {
    pub fn new(limit: usize) -> Self {
        Self {
            log: Vec::with_capacity(64),
            saved: vec![],
            registry: Registry::new(),
            current: Frame::default(),
            frames: vec![],
            seen: Seen::new(),
            version: 0,
            last_saved: (u64::MAX, 0, [0, 0]),
            floor: 0,
            guards: 0,
            work: 0,
            limit,
            enabled: true,
            exhausted: false,
        }
    }
    /// 記録の有無を切り替える。`false` では frame・log・予算のいずれも触らない。
    #[allow(dead_code)]
    pub fn enable(&mut self, enabled: bool) {
        self.enabled = enabled;
    }
    /// 内容を捨てて再利用する（確保は保持）。世代印を進めるので dedupe 表は空と同じ。
    /// registry（静的 pointer → 番号）は有効なまま残す。
    #[allow(dead_code)]
    pub fn reset(&mut self, limit: usize) {
        self.log.clear();
        self.saved.clear();
        self.frames.clear();
        self.current = Frame::default();
        self.seen.next();
        self.version = 0;
        self.last_saved = (u64::MAX, 0, [0, 0]);
        self.floor = 0;
        self.guards = 0;
        self.work = 0;
        self.limit = limit;
        self.enabled = true;
        self.exhausted = false;
    }
    #[allow(dead_code)]
    pub fn capacity_bytes(&self) -> usize {
        (self.log.capacity() + self.saved.capacity()) * std::mem::size_of::<u32>()
            + self.frames.capacity() * std::mem::size_of::<Frame>()
            + self.registry.entries.capacity() * std::mem::size_of::<Entry>()
    }
    #[inline(always)]
    pub fn enter(&mut self, reached: usize) {
        if !self.enabled {
            return;
        }
        self.enter_on(reached);
    }
    #[inline(never)]
    fn enter_on(&mut self, reached: usize) {
        self.frames.push(self.current);
        self.current = Frame {
            far: 0,
            base: self.log.len(),
            reached,
            generation: 0,
        };
    }
    // Not の子 frame。成功時（子失敗）は discard、失敗時（子成功）は leave_guard。
    #[inline]
    pub fn enter_guard(&mut self, reached: usize) {
        if !self.enabled {
            return;
        }
        self.guards += 1;
        self.enter(reached);
    }
    // log[base..] を世代の集合へ取り込み、重複を詰める。
    fn absorb(&mut self, generation: u32, base: usize) {
        self.version += 1;
        let mut keep = base;
        for i in base..self.log.len() {
            let entry = self.log[i];
            if self.seen.insert(generation, entry) {
                self.log[keep] = entry;
                keep += 1;
            }
        }
        self.log.truncate(keep);
    }
    // 子 frame の観測を親へ伝える。
    #[inline(always)]
    pub fn leave(&mut self) {
        if !self.enabled {
            return;
        }
        self.leave_on();
    }
    #[inline(never)]
    fn leave_on(&mut self) {
        let inner = self.current;
        self.current = self.frames.pop().expect("balanced diagnostic frame");
        let parent = &mut self.current;
        parent.reached = parent.reached.max(inner.reached);
        let len = self.log.len();
        if len == inner.base {
            return;
        }
        if inner.base == parent.base {
            parent.far = inner.far;
            parent.generation = inner.generation;
        } else if inner.far > parent.far {
            self.version += 1;
            self.log.copy_within(inner.base..len, parent.base);
            self.log.truncate(parent.base + (len - inner.base));
            parent.far = inner.far;
            parent.generation = inner.generation;
        } else if inner.far < parent.far {
            self.version += 1;
            self.log.truncate(inner.base);
        } else {
            let generation = parent.generation;
            self.absorb(generation, inner.base);
        }
    }
    #[inline]
    pub fn leave_guard(&mut self) {
        if !self.enabled {
            return;
        }
        self.guards -= 1;
        self.leave();
        if self.guards == 0 {
            // Not の外へ出た観測は破棄されない。
            let frame = self.current;
            if self.log.len() > frame.base {
                self.floor = self.floor.max(frame.far);
            }
        }
    }
    // Not 成功: 子 frame の観測（reached を含む）を捨てる。
    #[inline]
    pub fn discard(&mut self) {
        if !self.enabled {
            return;
        }
        self.guards -= 1;
        let inner = self.current;
        self.current = self.frames.pop().expect("balanced diagnostic frame");
        if self.log.len() > inner.base {
            self.version += 1;
        }
        self.log.truncate(inner.base);
    }
    // 現在 frame の局所 snapshot。memo と trivia cache が保存する。
    pub fn snapshot(&mut self) -> DisplaySnapshot {
        if !self.enabled {
            return DisplaySnapshot::default();
        }
        let frame = self.current;
        let labels = if self.log.len() > frame.base && frame.far >= self.floor {
            if self.last_saved.0 == self.version && self.last_saved.1 == frame.base {
                self.last_saved.2
            } else {
                let start = self.saved.len();
                self.saved.extend_from_slice(&self.log[frame.base..]);
                let range = [
                    u32::try_from(start).expect("saved labels"),
                    u32::try_from(self.saved.len()).expect("saved labels"),
                ];
                self.last_saved = (self.version, frame.base, range);
                range
            }
        } else {
            [0, 0]
        };
        DisplaySnapshot {
            far: u32::try_from(frame.far).expect("input byte budget"),
            reached: u32::try_from(frame.reached).expect("input byte budget"),
            labels,
        }
    }
    #[inline(always)]
    pub fn reach(&mut self, reached: usize) {
        if !self.enabled {
            return;
        }
        self.current.reached = self.current.reached.max(reached);
    }
    // 予算は「候補が残った記録の回数」（fail / failures / merge 呼出し単位）。
    #[inline]
    fn budget(&mut self) -> bool {
        self.work += 1;
        if self.work > self.limit {
            self.exhausted = true;
            return false;
        }
        true
    }
    // 現在 frame に pos の記録を置ける状態にし、置けるなら (base, 世代) を返す。
    #[inline]
    fn open(&mut self, pos: usize) -> Option<(usize, u32)> {
        let frame = &mut self.current;
        frame.reached = frame.reached.max(pos);
        if pos < self.floor {
            return None;
        }
        if self.log.len() == frame.base || pos > frame.far {
            if self.log.len() > frame.base {
                self.version += 1;
            }
            self.log.truncate(frame.base);
            frame.far = pos;
            frame.generation = self.seen.next();
        } else if pos < frame.far {
            return None;
        }
        if self.guards == 0 {
            self.floor = pos;
        }
        Some((frame.base, frame.generation))
    }
    // 表の混雑で残った重複を、range が伸びたときに詰める（観測には影響しない）。
    #[inline]
    fn compact_if_long(&mut self, base: usize) {
        if self.log.len() - base >= 128 {
            let generation = self.seen.next();
            self.current.generation = generation;
            self.absorb(generation, base);
        }
    }
    // 記録を現在 frame の生存集合へ加える（重複は捨てる）。
    #[inline]
    fn push(&mut self, base: usize, generation: u32, entry: Entry) {
        let index = self.registry.intern(entry);
        if self.seen.insert(generation, index) {
            self.version += 1;
            self.log.push(index);
            self.compact_if_long(base);
        }
    }
    #[inline(always)]
    pub fn fail(&mut self, pos: usize, label: &'static str) {
        if !self.enabled {
            return;
        }
        self.fail_on(pos, label);
    }
    #[inline(never)]
    fn fail_on(&mut self, pos: usize, label: &'static str) {
        let Some((base, generation)) = self.open(pos) else {
            return;
        };
        if !self.budget() {
            return;
        }
        self.push(base, generation, Entry::One(label));
    }
    // 候補の失敗 label 列を 1 回の記録として置く（label が空なら位置の更新だけ）。
    #[inline(always)]
    pub fn failures(&mut self, pos: usize, labels: &'static [&'static str]) {
        if !self.enabled {
            return;
        }
        // 手前の位置の記録（大半）は frame を開かずに捨てる。
        self.current.reached = self.current.reached.max(pos);
        if pos < self.floor {
            return;
        }
        self.failures_at(pos, labels);
    }
    fn failures_at(&mut self, pos: usize, labels: &'static [&'static str]) {
        let Some((base, generation)) = self.open(pos) else {
            return;
        };
        if labels.is_empty() || !self.budget() {
            return;
        }
        self.push(base, generation, Entry::Many(labels));
    }
    // memo hit などで保存した局所 snapshot を現在 frame へ再生する。
    pub fn merge(&mut self, snapshot: DisplaySnapshot) {
        if !self.enabled {
            return;
        }
        self.reach(snapshot.reached as usize);
        let range = snapshot.labels[0] as usize..snapshot.labels[1] as usize;
        if range.is_empty() {
            return;
        }
        let Some((base, generation)) = self.open(snapshot.far as usize) else {
            return;
        };
        if !self.budget() {
            return;
        }
        for i in range {
            let entry = self.saved[i];
            if self.seen.insert(generation, entry) {
                self.version += 1;
                self.log.push(entry);
            }
        }
        self.compact_if_long(base);
    }
    /// 最も内側 frame の最遠失敗位置（記録が無ければ `None`）。
    /// Java `Diagnostics.localFarthest()` に対応する（回復の同期候補の記録条件）。
    #[allow(dead_code)]
    pub fn local_far(&self) -> Option<usize> {
        if !self.enabled || self.log.len() <= self.current.base {
            return None;
        }
        Some(self.current.far)
    }
    /// 最も内側の frame **だけ**を畳み込む（D-025 の回復診断用）。
    /// 回復は「その規則の解析が失敗した」ことの診断なので、候補は規則の frame に
    /// 属するものに限る（Java `Session.recover` の `diagnostics.leaveRule()` と同じ範囲）。
    /// frame に記録が無ければ `None`（Java の `failure.farthest() < 0`）。
    #[allow(dead_code)]
    pub fn local_summary(&self) -> Option<(usize, Vec<&'static str>)> {
        if !self.enabled {
            return None;
        }
        let frame = self.current;
        if self.log.len() <= frame.base {
            return None;
        }
        let mut labels = Vec::new();
        for &entry in &self.log[frame.base..] {
            for &label in self.registry.entries[entry as usize].labels() {
                if !labels.contains(&label) {
                    labels.push(label);
                }
            }
        }
        labels.sort();
        Some((frame.far, labels))
    }
    // 全 frame を内側から畳み込む（parse 終了後は global frame だけ）。
    pub fn summary(&self) -> (usize, Vec<&'static str>) {
        if !self.enabled {
            return (0, vec![]);
        }
        let mut reached = 0;
        let mut far = 0;
        let mut segments: Vec<(usize, usize)> = vec![];
        let mut end = self.log.len();
        for frame in std::iter::once(&self.current).chain(self.frames.iter().rev()) {
            reached = reached.max(frame.reached);
            if frame.base < end {
                if segments.is_empty() || frame.far > far {
                    far = frame.far;
                    segments.clear();
                    segments.push((frame.base, end));
                } else if frame.far == far {
                    segments.push((frame.base, end));
                }
            }
            end = frame.base;
        }
        if segments.is_empty() {
            return (reached, vec![]);
        }
        let mut labels = Vec::new();
        for (start, end) in segments {
            for &entry in &self.log[start..end] {
                for &label in self.registry.entries[entry as usize].labels() {
                    if !labels.contains(&label) {
                        labels.push(label);
                    }
                }
            }
        }
        labels.sort();
        (far, labels)
    }
}
