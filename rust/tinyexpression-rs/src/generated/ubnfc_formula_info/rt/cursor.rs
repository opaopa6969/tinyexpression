// D-013: begin / consume(0) / commit と frame flag を区別する。
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct State {
    pub consumed: usize,
    pub matched: usize,
    pub invert: bool,
    pub reset: bool,
}
impl Default for State {
    fn default() -> Self {
        Self {
            consumed: 0,
            matched: 0,
            invert: false,
            reset: true,
        }
    }
}
impl State {
    pub fn begin(self) -> Self {
        Self {
            matched: if self.reset {
                self.consumed
            } else {
                self.matched
            },
            ..self
        }
    }
    pub fn position<const MATCH: bool>(self) -> usize {
        if MATCH {
            self.matched
        } else {
            self.consumed
        }
    }
    pub fn advance<const MATCH: bool>(&mut self, length: usize) {
        if MATCH {
            self.matched += length;
        } else {
            self.consumed += length;
            self.matched = self.consumed;
        }
    }
    /// 式本体の入口で行う frame flag の変更と transaction begin（生成コードの失敗再生が使う）。
    #[allow(dead_code)]
    pub fn entered(mut self, reset_false: bool, begin: bool) -> Self {
        if reset_false {
            self.reset = false;
        }
        if begin {
            self.begin()
        } else {
            self
        }
    }
    pub fn commit(self, child: Self) -> Self {
        Self {
            consumed: child.consumed,
            matched: child.matched,
            ..self
        }
    }
}
