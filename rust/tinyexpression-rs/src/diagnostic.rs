//! Source positions, JSON string escaping and the published parse diagnostic.
//!
//! These types used to come from `unlaxer-runtime`. The vendored ubnfc parser has no
//! dependency on that crate (issue #178), so the published shapes live here unchanged:
//! the code-point `Span`, the `{"kind","offset","expected","farthestOffset",
//! "farthestExpected"}` diagnostic JSON and the `Display` texts are byte-for-byte what
//! callers saw before the swap.

use std::error::Error;
use std::fmt::{self, Display, Formatter};

/// Half-open Unicode scalar (code-point) offsets, not UTF-8 bytes or UTF-16 units.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Span {
    pub start: usize,
    pub end: usize,
}

/// The farthest position the parser reached, with the terminals it could have accepted there.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ParseError {
    pub offset: usize,
    pub expected: Vec<String>,
}

impl Display for ParseError {
    fn fmt(&self, formatter: &mut Formatter<'_>) -> fmt::Result {
        write!(
            formatter,
            "at code point {}: expected {}",
            self.offset,
            self.expected.join(", ")
        )
    }
}

impl Error for ParseError {}

/// Full-input validation with a stable category and backend-native farthest-failure hints.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ParseDiagnostic {
    pub kind: &'static str,
    pub offset: usize,
    pub expected: Vec<String>,
    pub farthest: ParseError,
}

impl ParseDiagnostic {
    pub fn canonical_json(&self) -> String {
        let expected = join_strings(&self.expected);
        let farthest = join_strings(&self.farthest.expected);
        format!(
            "{{\"kind\":{},\"offset\":{},\"expected\":[{}],\"farthestOffset\":{},\"farthestExpected\":[{}]}}",
            json_string(self.kind),
            self.offset,
            expected,
            self.farthest.offset,
            farthest
        )
    }
}

fn join_strings(values: &[String]) -> String {
    values
        .iter()
        .map(|value| json_string(value))
        .collect::<Vec<_>>()
        .join(",")
}

impl Display for ParseDiagnostic {
    fn fmt(&self, formatter: &mut Formatter<'_>) -> fmt::Result {
        write!(
            formatter,
            "{} at code point {}: expected {}",
            self.kind,
            self.offset,
            self.expected.join(", ")
        )
    }
}

impl Error for ParseDiagnostic {}

/// Escapes one string as a JSON scalar, matching the previous `unlaxer_runtime::json_string`.
pub fn json_string(value: &str) -> String {
    let mut result = String::from("\"");
    for character in value.chars() {
        match character {
            '"' => result.push_str("\\\""),
            '\\' => result.push_str("\\\\"),
            '\n' => result.push_str("\\n"),
            '\r' => result.push_str("\\r"),
            '\t' => result.push_str("\\t"),
            control if control < ' ' => {
                result.push_str(&format!("\\u{:04x}", control as u32));
            }
            other => result.push(other),
        }
    }
    result.push('"');
    result
}
