// ubnfc runtime template: input

#[derive(Clone, Debug)]
struct NonAsciiEnd {
    byte: usize,
    cp: usize,
}

/// UTF-8 入力。外部へ渡す位置は Unicode scalar (code point)、内部位置は byte。
/// 構築は O(bytes)、索引は非 ASCII 文字数 m に対して O(m)。ASCII は確保なし。
#[derive(Clone, Debug)]
pub struct Input<'a> {
    bytes: &'a [u8],
    non_ascii: Vec<NonAsciiEnd>,
    code_points: usize,
}

impl<'a> Input<'a> {
    /// &str に限定し、不正 UTF-8 や孤立 surrogate の扱いを生成コードへ持ち込まない。
    pub fn new(text: &'a str) -> Self {
        let mut input = Self {
            bytes: text.as_bytes(),
            non_ascii: Vec::new(),
            code_points: text.len(),
        };
        if !text.is_ascii() {
            for (cp, (byte, ch)) in text.char_indices().enumerate() {
                if !ch.is_ascii() {
                    input.non_ascii.push(NonAsciiEnd {
                        byte: byte + ch.len_utf8(),
                        cp: cp + 1,
                    });
                }
            }
            let last = input.non_ascii.last().expect("非 ASCII 文字が存在する");
            input.code_points -= last.byte - last.cp;
        }
        input
    }

    pub fn bytes(&self) -> &'a [u8] {
        self.bytes
    }

    fn is_boundary(&self, byte: usize) -> bool {
        byte == self.bytes.len() || self.bytes.get(byte).is_some_and(|b| b & 0xc0 != 0x80)
    }

    /// EOF を含む文字境界のみ有効。ASCII は恒等 O(1)、それ以外は O(log m)。
    pub fn byte_to_cp(&self, byte: usize) -> Option<usize> {
        if !self.is_boundary(byte) {
            return None;
        }
        let count = self.non_ascii.partition_point(|end| end.byte <= byte);
        let extra = count
            .checked_sub(1)
            .map_or(0, |i| self.non_ascii[i].byte - self.non_ascii[i].cp);
        Some(byte - extra)
    }

    /// EOF を含む code point index を byte offset に戻す。
    pub fn cp_to_byte(&self, cp: usize) -> Option<usize> {
        if cp > self.code_points {
            return None;
        }
        let count = self.non_ascii.partition_point(|end| end.cp <= cp);
        let extra = count
            .checked_sub(1)
            .map_or(0, |i| self.non_ascii[i].byte - self.non_ascii[i].cp);
        Some(cp + extra)
    }

    /// byte 位置の文字と UTF-8 byte 長。最大 4 byte だけを調べる O(1) 操作。
    pub fn cp_at(&self, byte: usize) -> Option<(char, usize)> {
        let tail = self.bytes.get(byte..)?;
        let len = match *tail.first()? {
            0x00..=0x7f => 1,
            0xc2..=0xdf => 2,
            0xe0..=0xef => 3,
            0xf0..=0xf4 => 4,
            _ => return None,
        };
        let ch = std::str::from_utf8(tail.get(..len)?).ok()?.chars().next()?;
        Some((ch, len))
    }

    /// D-040: その byte 位置が識別子構成文字（ASCII `[A-Za-z0-9_]`）か。
    /// 非 ASCII は先頭 byte が 0x80 以上なので自然に false になる。
    pub fn identifier_byte_at(&self, byte: usize) -> bool {
        self.bytes
            .get(byte)
            .is_some_and(|b| b.is_ascii_alphanumeric() || *b == b'_')
    }

    /// 単語境界を要求しない接頭辞一致。空 literal は EOF でも一致する。
    pub fn starts_with(&self, byte: usize, lit: &str) -> bool {
        self.is_boundary(byte)
            && self
                .bytes
                .get(byte..)
                .is_some_and(|tail| tail.starts_with(lit.as_bytes()))
    }

    pub fn starts_with_ignore_case(&self, mut byte: usize, lit: &str) -> bool {
        if !self.is_boundary(byte) {
            return false;
        }
        for expected in lit.chars() {
            let Some((actual, len)) = self.cp_at(byte) else {
                return false;
            };
            if !equal_ignore_case(actual, expected) {
                return false;
            }
            byte += len;
        }
        true
    }

    /// 両端は文字境界。範囲外・文字途中・逆向きの範囲は None。
    pub fn cp_len(&self, byte_start: usize, byte_end: usize) -> Option<usize> {
        self.byte_to_cp(byte_end)?
            .checked_sub(self.byte_to_cp(byte_start)?)
    }
}

// Java equalsIgnoreCase の単一 code point 比較を近似する。単純な lowercase 同士の
// 比較では final sigma や dotless i を取りこぼすので、uppercase を比較し、次に
// lowercase(uppercase) を比較する。locale や単語境界は考慮しない。
// Java Character は単一 code point を返すが Rust の case mapping は複数文字に
// 展開し得る。展開は元の文字に戻す（ß を SS、ﬀ を ff と同一視しない）。
// İ の simple lowercase は i に補正する。その他の full/simple mapping の差と
// Rust/JDK の Unicode 版の差は残るため、Java との完全一致は保証しない。
// https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html#equalsIgnoreCase(java.lang.String)
// https://doc.rust-lang.org/std/primitive.char.html#method.to_uppercase
pub fn equal_ignore_case(left: char, right: char) -> bool {
    if left == right {
        return true;
    }
    let upper_left = single_or_original(left, left.to_uppercase());
    let upper_right = single_or_original(right, right.to_uppercase());
    upper_left == upper_right || simple_lower(upper_left) == simple_lower(upper_right)
}

fn single_or_original(original: char, mut mapping: impl Iterator<Item = char>) -> char {
    let first = mapping.next().unwrap_or(original);
    if mapping.next().is_none() {
        first
    } else {
        original
    }
}

fn simple_lower(ch: char) -> char {
    if ch == '\u{0130}' {
        'i'
    } else {
        single_or_original(ch, ch.to_lowercase())
    }
}
