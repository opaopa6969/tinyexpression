// 外部依存なし。全実装は pure（入力と5状態だけを読み、効果なし）。
use ubnfc_generated::{rt::scope::ScopeStore, ScanDiagnostic, ScanResult, State, TokenScanner};
pub const PURE: bool = true;

#[derive(Clone, Copy)]
enum Kind {
    Double,
    Single,
    String,
    Start,
    End,
    External,
}
fn kind(class: &str) -> Option<Kind> {
    Some(match class {
        "DoubleQuotedParser" | "org.unlaxer.parser.elementary.DoubleQuotedParser" => Kind::Double,
        "SingleQuotedParser" | "org.unlaxer.parser.elementary.SingleQuotedParser" => Kind::Single,
        "StringLiteralParser" | "org.unlaxer.tinyexpression.parser.StringLiteralParser" => {
            Kind::String
        }
        "CodeStartParser" | "org.unlaxer.tinyexpression.parser.javalang.CodeStartParser" => {
            Kind::Start
        }
        "CodeEndParser" | "org.unlaxer.tinyexpression.parser.javalang.CodeEndParser" => Kind::End,
        "oracle.ExternalCursorParser" => Kind::External,
        _ => return None,
    })
}
pub struct Registry {
    bindings: Vec<(&'static str, Kind)>,
}
impl Registry {
    pub fn new(classes: &'static [(&'static str, &'static str)]) -> Result<Self, &'static str> {
        let _ = PURE;
        let bindings = classes
            .iter()
            .map(|&(id, class)| kind(class).map(|k| (id, k)).ok_or("未登録の Extern クラス"))
            .collect::<Result<_, _>>()?;
        Ok(Self { bindings })
    }
}
impl TokenScanner for Registry {
    fn scan(
        &mut self,
        id: &str,
        input: &str,
        state: State,
        match_only: bool,
        _: &ScopeStore,
    ) -> ScanResult {
        let Some((_, kind)) = self.bindings.iter().find(|(key, _)| *key == id) else {
            return ScanResult {
                ok: false,
                consumed_end: state.consumed,
                matched_end: state.matched,
                value_span: [state.consumed; 2],
                diagnostics: vec![ScanDiagnostic {
                    offset: state.consumed,
                    expected: "未登録の Extern ID",
                }],
                effects: vec![],
            };
        };
        Scan {
            input,
            state,
            match_only,
            p: if match_only {
                state.matched
            } else {
                state.consumed
            },
            value_start: 0,
            value_end: 0,
            failure: 0,
            expected: "token",
        }
        .run(*kind)
    }
}
struct Scan<'a> {
    input: &'a str,
    state: State,
    match_only: bool,
    p: usize,
    value_start: usize,
    value_end: usize,
    failure: usize,
    expected: &'static str,
}
impl Scan<'_> {
    fn cp(&self) -> Option<char> {
        self.input[self.p..].chars().next()
    }
    fn fail(&mut self, expected: &'static str) -> bool {
        self.failure = self.p;
        self.expected = expected;
        false
    }
    fn word(&mut self, word: &'static str, label: &'static str) -> bool {
        // unlaxer-common/src/main/java/org/unlaxer/parser/elementary/WordParser.java:67-82: 各 terminal の invert XOR。
        if self.cp().is_none() || self.input[self.p..].starts_with(word) == self.state.invert {
            return self.fail(label);
        }
        for _ in word.chars() {
            if let Some(c) = self.cp() {
                self.p += c.len_utf8();
            }
        }
        true
    }
    fn head(c: char) -> bool {
        c.is_ascii_alphabetic() || c == '_'
    }
    fn identifier(&mut self) -> bool {
        // unlaxer-common/src/main/java/org/unlaxer/parser/clang/IdentifierParser.java:24-31、posix/Alphabet*UnderScoreParser。
        let Some(c) = self.cp().filter(|&c| Self::head(c) != self.state.invert) else {
            return self.fail("identifier");
        };
        self.p += c.len_utf8();
        while let Some(c) = self
            .cp()
            .filter(|&c| (Self::head(c) || c.is_ascii_digit()) != self.state.invert)
        {
            self.p += c.len_utf8();
        }
        true
    }
    fn quoted(&mut self, quote: char) -> bool {
        // unlaxer-common/src/main/java/org/unlaxer/parser/elementary/QuotedParser.java:55-71、EscapeInQuotedParser.java:22-26。
        // 任意1 code point の escape、改行/NUL 可。single だけ引用符を value から除く。
        let start = self.p;
        if self.cp() != Some(quote) {
            return self.fail("quote");
        }
        self.p += 1;
        if self.state.invert {
            return self.fail("closing quote");
        }
        while let Some(c) = self.cp() {
            self.p += c.len_utf8();
            if c == quote {
                self.value_start = start + usize::from(quote == '\'');
                self.value_end = self.p - usize::from(quote == '\'');
                return true;
            }
            if c == '\\' {
                let Some(c) = self.cp() else {
                    return self.fail("escaped character");
                };
                self.p += c.len_utf8();
            }
        }
        self.fail("closing quote")
    }
    fn fence(&mut self, start: bool) -> bool {
        // tinyexpression/src/main/java/org/unlaxer/tinyexpression/parser/javalang/
        // CodeStartParser.java:32-40、CodeEndParser.java:21-26。
        // tinyexpression/src/main/java/org/unlaxer/tinyexpression/parser/javatype/JavaClassNameParser.java:28-39 は identifier ('.' identifier)*。
        // unlaxer-common/src/main/java/org/unlaxer/parser/elementary/StartOfLineParser.java:38-53。
        let sol = self.p == 0 || matches!(self.input.as_bytes()[self.p - 1], b'\r' | b'\n');
        if !sol || self.state.invert {
            return self.fail("start of line");
        }
        if !self.word("```", "'```'") {
            return false;
        }
        if start {
            if !self.identifier() || !self.word(":", "':'") || !self.identifier() {
                return false;
            }
            while self.cp() == Some('.') {
                self.p += 1;
                if !self.identifier() {
                    return false;
                }
            }
        }
        self.value_end = self.p;
        // unlaxer-common/src/main/java/org/unlaxer/parser/elementary/LineTerminatorParser.java:20-26: CRLF / CR / LF / EOF。
        if self.input[self.p..].starts_with("\r\n") {
            self.p += 2;
        } else if matches!(self.cp(), Some('\r' | '\n')) {
            self.p += 1;
        } else if self.cp().is_some() {
            return self.fail("end of line");
        }
        true
    }
    fn run(mut self, kind: Kind) -> ScanResult {
        // ChainInterface.java:17 は reset=false、ChoiceInterface.java:28-29 は begin の reset を参照。
        if matches!(kind, Kind::String) && self.match_only && self.state.reset {
            self.p = self.state.consumed;
        }
        self.value_start = self.p;
        let mut ok = match kind {
            Kind::Double => self.quoted('"'),
            Kind::Single => self.quoted('\''),
            // tinyexpression/src/main/java/org/unlaxer/tinyexpression/parser/StringLiteralParser.java:19-25。
            Kind::String => self.quoted(if self.cp() == Some('"') { '"' } else { '\'' }),
            Kind::Start => self.fence(true),
            Kind::End => self.fence(false),
            // corpus/oracle/tools/ExternalCursorParser.java:8-10: 😀 消費 + x の MatchOnly。
            Kind::External => self.word("😀", "'😀'"),
        };
        let consumed = if self.match_only {
            self.state.consumed
        } else {
            self.p
        };
        if matches!(kind, Kind::External) && ok {
            self.value_end = self.p;
            ok = self.word("x", "'x'");
        }
        if !ok {
            // ExternalCursorParser.java:10 / ParseContext.java:1097,1111-1148:
            // Chain の先頭失敗では MatchOnly の子候補 'x' も同じ位置に残る。
            let mut diagnostics = vec![ScanDiagnostic {
                offset: self.failure,
                expected: self.expected,
            }];
            if matches!(kind, Kind::External) && self.expected == "'😀'" {
                diagnostics.push(ScanDiagnostic {
                    offset: self.failure,
                    expected: "'x'",
                });
            }
            return ScanResult {
                ok,
                consumed_end: self.state.consumed,
                matched_end: self.state.matched,
                value_span: [self.state.consumed; 2],
                diagnostics,
                effects: vec![],
            };
        }
        ScanResult {
            ok,
            consumed_end: consumed,
            matched_end: self.p,
            value_span: [self.value_start, self.value_end],
            diagnostics: vec![],
            effects: vec![],
        }
    }
}

#[cfg(test)]
#[path = "scanners_tests.rs"]
mod tests;
