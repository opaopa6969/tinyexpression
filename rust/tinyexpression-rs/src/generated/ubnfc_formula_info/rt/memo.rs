// Vec open addressing。slot は payload index のみ。rehash で結果を複製しない。
use super::cursor::State;
use std::cell::Cell;
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Key {
    pub expression: usize,
    pub state: State,
    pub matched_mode: bool,
    pub version: u32,
}
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
struct PackedKey {
    expression: u32,
    consumed: u32,
    matched: u32,
    version: u32,
    flags: u32,
}
impl PackedKey {
    // 20 byte の固定長 key。乗算とシフトの混合で十分に散り、SipHash より安い。
    #[inline]
    fn hash(self) -> u64 {
        let mut h = (self.expression as u64 | ((self.consumed as u64) << 32))
            .wrapping_mul(0x9E37_79B9_7F4A_7C15);
        h ^= (self.matched as u64 | ((self.version as u64) << 32))
            .wrapping_mul(0xD6E8_FEB8_6659_FD93);
        h ^= (self.flags as u64).wrapping_mul(0xA24B_AED4_963E_E407);
        h ^= h >> 29;
        h = h.wrapping_mul(0xBF58_476D_1CE4_E5B9);
        h ^ (h >> 32)
    }
    fn pack(key: Key) -> Option<Self> {
        Some(Self {
            expression: u32::try_from(key.expression).ok()?,
            consumed: u32::try_from(key.state.consumed).ok()?,
            matched: u32::try_from(key.state.matched).ok()?,
            version: key.version,
            flags: key.matched_mode as u32
                | ((key.state.invert as u32) << 1)
                | ((key.state.reset as u32) << 2),
        })
    }
}
pub struct Memo<T> {
    slots: Vec<u32>,
    entries: Vec<(PackedKey, T)>,
    max_probe: Cell<usize>,
    /// 最初の insert で確保する entry 数の見積り。memo を 1 度も使わない parse
    /// （例: JSON は memo 対象の expression に再訪しない）では表を確保しない。
    planned: usize,
}
impl<T: Copy> Default for Memo<T> {
    fn default() -> Self {
        Self {
            slots: vec![],
            entries: vec![],
            max_probe: Cell::new(0),
            planned: 0,
        }
    }
}
impl<T: Copy> Memo<T> {
    /// `capacity` は見積り。表は最初の insert で確保する（それまで確保しない）。
    pub fn with_capacity(capacity: usize) -> Self {
        Self {
            planned: capacity.min(1_000_000),
            ..Self::default()
        }
    }
    /// 内容を捨てて再利用する（確保は保持）。前回の表が今回の見積りより大きすぎるときは
    /// 縮める（小さい入力で大きな slot 表を毎回ゼロ埋めしない）。
    #[allow(dead_code)]
    pub fn reset(&mut self, capacity: usize) {
        let capacity = capacity.min(1_000_000);
        let wanted = ((capacity + 1) * 2).next_power_of_two().max(64);
        self.planned = capacity;
        self.entries.clear();
        if self.slots.is_empty() {
            // まだ確保していない（最初の insert で見積りどおりに確保する）。
        } else if self.slots.len() > wanted.saturating_mul(8) {
            self.slots = vec![0; wanted];
            self.entries.shrink_to(capacity);
        } else if self.slots.len() < wanted {
            self.slots = vec![0; wanted];
        } else {
            self.slots.fill(0);
        }
        self.max_probe.set(0);
    }
    #[allow(dead_code)]
    pub fn capacity_bytes(&self) -> usize {
        self.slots.capacity() * std::mem::size_of::<u32>()
            + self.entries.capacity() * std::mem::size_of::<(PackedKey, T)>()
    }
    pub fn len(&self) -> usize {
        self.entries.len()
    }
    pub fn is_empty(&self) -> bool {
        self.entries.is_empty()
    }
    pub fn max_probe_len(&self) -> usize {
        self.max_probe.get()
    }
    pub fn get(&self, key: Key) -> Option<T> {
        if self.slots.is_empty() {
            return None;
        }
        let key = PackedKey::pack(key)?;
        let mut index = key.hash() as usize & (self.slots.len() - 1);
        let mut probes = 0;
        loop {
            probes += 1;
            self.max_probe.set(self.max_probe.get().max(probes));
            let slot = self.slots[index];
            if slot == 0 {
                return None;
            }
            let (found, value) = self.entries[slot as usize - 1];
            if found == key {
                return Some(value);
            }
            index = (index + 1) & (self.slots.len() - 1);
        }
    }
    pub fn insert(&mut self, key: Key, value: T) {
        if self.entries.len() >= u32::MAX as usize - 1 {
            return;
        }
        if self.slots.is_empty() {
            self.slots = vec![0; ((self.planned + 1) * 2).next_power_of_two().max(64)];
            self.entries.reserve(self.planned);
        }
        if (self.entries.len() + 1) * 2 >= self.slots.len() {
            self.slots = vec![0; (self.slots.len() * 2).max(64)];
            for (i, (key, _)) in self.entries.iter().enumerate() {
                let mut index = key.hash() as usize & (self.slots.len() - 1);
                while self.slots[index] != 0 {
                    index = (index + 1) & (self.slots.len() - 1);
                }
                self.slots[index] = (i + 1) as u32;
            }
        }
        let Some(key) = PackedKey::pack(key) else {
            return;
        };
        let mut index = key.hash() as usize & (self.slots.len() - 1);
        let mut probes = 1;
        while self.slots[index] != 0 {
            probes += 1;
            self.max_probe.set(self.max_probe.get().max(probes));
            let entry = &mut self.entries[self.slots[index] as usize - 1];
            if entry.0 == key {
                entry.1 = value;
                return;
            }
            index = (index + 1) & (self.slots.len() - 1);
        }
        self.max_probe.set(self.max_probe.get().max(probes));
        self.entries.push((key, value));
        self.slots[index] = self.entries.len() as u32;
    }
}
