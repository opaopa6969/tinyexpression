//! Evaluation requests with a caller-supplied `CalculationContext` (issue #201).
//!
//! The context-free `eval` and the empty-context `run` cannot evaluate a formula that reads
//! variables. A request is a small JSON document that carries the formula (or a FormulaInfo
//! document), the calculator settings, the variables of the context and stub implementations
//! for `external` invocations:
//!
//! ```json
//! {"formula": "$price * 1.1", "resultType": "float", "numberType": "float",
//!  "angle": "degree", "seed": 7,
//!  "variables": [{"name": "price", "type": "float", "value": "100"},
//!                {"name": "member", "type": "boolean", "value": true},
//!                {"name": "o", "map": "object", "type": "string", "value": "boxed"}],
//!  "externals": [{"class": "sample.Fee", "method": "calculate", "arity": 3,
//!                 "registered": true, "result": {"type": "float", "value": "12.5"}}]}
//! ```
//!
//! - `variables[].type` is the value kind (`float`/`number`, `double`, `int`, `long`, `short`,
//!   `byte`, `boolean`, `string`); `map` is the `CalculationContext` map it goes into (`number`,
//!   `string`, `boolean`, `object`) and defaults to the map of the kind. `value` may be a JSON
//!   string, number or boolean; numbers are read with Java's `Float.parseFloat` rules.
//! - `externals[]` stands in for Java's reflection: a class is loadable when any entry names
//!   it (otherwise `Class.forName` fails as in Java); a method is found by name and, when
//!   `arity` is given, argument count; `registered: false` models a class with no instance in
//!   the context; `result` is the constant the stub returns (`{"type":"null"}` for `null`).
//!   The Java differential rows are evaluated through exactly this mapping by the playground's
//!   parity smoke (`playground/scripts/parity-smoke.mjs`).
//!
//! The JSON reader below is deliberately small (the crate has no dependencies): it accepts
//! standard JSON and reports the first syntax error.

use crate::runtime::{java, Angle, Context, ExternalCall, ExternalError, ExternalHost, Variables};
use crate::runtime::{NumberType, ResultType};
use crate::Value;

// ------------------------------------------------------------------ JSON reader

/// A parsed JSON value.
#[derive(Clone, Debug, PartialEq)]
pub(crate) enum Json {
    Null,
    Bool(bool),
    /// The number's source text (kept verbatim so Java parsing rules apply to it).
    Num(String),
    Str(String),
    Arr(Vec<Json>),
    Obj(Vec<(String, Json)>),
}

impl Json {
    pub(crate) fn get(&self, key: &str) -> Option<&Json> {
        match self {
            Json::Obj(entries) => entries.iter().find(|(k, _)| k == key).map(|(_, v)| v),
            _ => None,
        }
    }

    pub(crate) fn str_field(&self, key: &str) -> Option<&str> {
        match self.get(key) {
            Some(Json::Str(s)) => Some(s),
            _ => None,
        }
    }

    /// A scalar as text: strings verbatim, numbers as written, booleans as `true`/`false`.
    fn scalar_text(&self) -> Option<String> {
        match self {
            Json::Str(s) | Json::Num(s) => Some(s.clone()),
            Json::Bool(b) => Some(b.to_string()),
            _ => None,
        }
    }
}

struct Reader {
    chars: Vec<char>,
    position: usize,
}

pub(crate) fn parse_json(text: &str) -> Result<Json, String> {
    let mut reader = Reader {
        chars: text.chars().collect(),
        position: 0,
    };
    let value = reader.value(0)?;
    reader.whitespace();
    if reader.position != reader.chars.len() {
        return Err(format!(
            "unexpected trailing input at character {}",
            reader.position
        ));
    }
    Ok(value)
}

impl Reader {
    fn peek(&self) -> Option<char> {
        self.chars.get(self.position).copied()
    }

    fn next(&mut self) -> Option<char> {
        let c = self.peek();
        if c.is_some() {
            self.position += 1;
        }
        c
    }

    fn whitespace(&mut self) {
        while matches!(self.peek(), Some(' ' | '\t' | '\n' | '\r')) {
            self.position += 1;
        }
    }

    fn expect(&mut self, expected: char) -> Result<(), String> {
        match self.next() {
            Some(c) if c == expected => Ok(()),
            other => Err(format!(
                "expected '{expected}' at character {}, found {}",
                self.position.saturating_sub(1),
                other.map_or("end of input".to_owned(), |c| format!("'{c}'"))
            )),
        }
    }

    fn literal(&mut self, word: &str, value: Json) -> Result<Json, String> {
        for expected in word.chars() {
            self.expect(expected)?;
        }
        Ok(value)
    }

    fn value(&mut self, depth: usize) -> Result<Json, String> {
        if depth > 64 {
            return Err("request nests deeper than 64 levels".to_owned());
        }
        self.whitespace();
        match self.peek() {
            Some('{') => {
                self.position += 1;
                let mut entries = Vec::new();
                self.whitespace();
                if self.peek() == Some('}') {
                    self.position += 1;
                    return Ok(Json::Obj(entries));
                }
                loop {
                    self.whitespace();
                    let key = self.string()?;
                    self.whitespace();
                    self.expect(':')?;
                    let value = self.value(depth + 1)?;
                    entries.push((key, value));
                    self.whitespace();
                    match self.next() {
                        Some(',') => continue,
                        Some('}') => return Ok(Json::Obj(entries)),
                        _ => {
                            return Err(format!(
                                "expected ',' or '}}' at character {}",
                                self.position.saturating_sub(1)
                            ))
                        }
                    }
                }
            }
            Some('[') => {
                self.position += 1;
                let mut items = Vec::new();
                self.whitespace();
                if self.peek() == Some(']') {
                    self.position += 1;
                    return Ok(Json::Arr(items));
                }
                loop {
                    items.push(self.value(depth + 1)?);
                    self.whitespace();
                    match self.next() {
                        Some(',') => continue,
                        Some(']') => return Ok(Json::Arr(items)),
                        _ => {
                            return Err(format!(
                                "expected ',' or ']' at character {}",
                                self.position.saturating_sub(1)
                            ))
                        }
                    }
                }
            }
            Some('"') => self.string().map(Json::Str),
            Some('t') => self.literal("true", Json::Bool(true)),
            Some('f') => self.literal("false", Json::Bool(false)),
            Some('n') => self.literal("null", Json::Null),
            Some(c) if c == '-' || c.is_ascii_digit() => {
                let start = self.position;
                while matches!(self.peek(), Some(c) if c.is_ascii_digit() || matches!(c, '-' | '+' | '.' | 'e' | 'E'))
                {
                    self.position += 1;
                }
                Ok(Json::Num(self.chars[start..self.position].iter().collect()))
            }
            Some(c) => Err(format!("unexpected '{c}' at character {}", self.position)),
            None => Err("unexpected end of input".to_owned()),
        }
    }

    fn string(&mut self) -> Result<String, String> {
        self.expect('"')?;
        let mut units: Vec<u16> = Vec::new();
        loop {
            let c = self.next().ok_or("unterminated string")?;
            match c {
                '"' => break,
                '\\' => {
                    let escaped = self.next().ok_or("unterminated escape")?;
                    match escaped {
                        'n' => units.push(u16::from(b'\n')),
                        't' => units.push(u16::from(b'\t')),
                        'r' => units.push(u16::from(b'\r')),
                        'b' => units.push(8),
                        'f' => units.push(12),
                        '/' | '\\' | '"' => units.push(escaped as u16),
                        'u' => {
                            let mut hex = String::new();
                            for _ in 0..4 {
                                hex.push(self.next().ok_or("short \\u escape")?);
                            }
                            units.push(
                                u16::from_str_radix(&hex, 16)
                                    .map_err(|_| format!("bad \\u escape {hex:?}"))?,
                            );
                        }
                        other => return Err(format!("bad escape '\\{other}'")),
                    }
                }
                other => {
                    let mut buffer = [0u16; 2];
                    units.extend_from_slice(other.encode_utf16(&mut buffer));
                }
            }
        }
        Ok(String::from_utf16_lossy(&units))
    }
}

// ------------------------------------------------------------------ request

/// Calculator settings and context read from a request.
pub(crate) struct Request {
    pub result_type: ResultType,
    pub number_type: NumberType,
    pub context: Context,
    pub externals: StubExternals,
    pub seed: u64,
}

pub(crate) fn read_request(json: &Json) -> Result<Request, String> {
    if !matches!(json, Json::Obj(_)) {
        return Err("the request must be a JSON object".to_owned());
    }
    let result_type = match json.str_field("resultType") {
        None => ResultType::Float,
        Some(name) => ResultType::parse(name).ok_or(format!("unknown resultType {name:?}"))?,
    };
    let number_type = match json.str_field("numberType") {
        None => NumberType::Float,
        Some(name) => NumberType::parse(name).ok_or(format!("unknown numberType {name:?}"))?,
    };
    let angle = match json.str_field("angle") {
        None | Some("degree") => Angle::Degree,
        Some("radian") => Angle::Radian,
        Some(other) => return Err(format!("unknown angle {other:?}")),
    };
    let mut context = Context::with_angle(angle);
    if let Some(variables) = json.get("variables") {
        let Json::Arr(variables) = variables else {
            return Err("variables must be an array".to_owned());
        };
        for variable in variables {
            set_variable(&mut context, variable)?;
        }
    }
    let externals = match json.get("externals") {
        None | Some(Json::Null) => StubExternals::default(),
        Some(Json::Arr(items)) => StubExternals {
            stubs: items.iter().map(read_stub).collect::<Result<_, _>>()?,
        },
        Some(_) => return Err("externals must be an array".to_owned()),
    };
    let seed = match json.get("seed") {
        None | Some(Json::Null) => 1,
        Some(Json::Num(text)) => text
            .parse::<u64>()
            .map_err(|_| format!("seed must be a non-negative integer, found {text}"))?,
        Some(_) => return Err("seed must be a number".to_owned()),
    };
    Ok(Request {
        result_type,
        number_type,
        context,
        externals,
        seed,
    })
}

/// Reads `{"type": ..., "value": ...}` into a runtime value.
fn typed_value(kind: &str, value: Option<&Json>, what: &str) -> Result<Value, String> {
    if kind == "null" {
        return Ok(Value::Null);
    }
    let raw = value
        .and_then(Json::scalar_text)
        .ok_or(format!("{what}: value must be a string, number or boolean"))?;
    let bad = || format!("{what}: {raw:?} is not a valid {kind}");
    Ok(match kind {
        "float" | "number" => Value::Number(java::parse_float(&raw).ok_or_else(bad)?),
        "double" => Value::Double(java::parse_double(&raw).ok_or_else(bad)?),
        "int" => Value::Int(raw.trim().parse().map_err(|_| bad())?),
        "long" => Value::Long(raw.trim().parse().map_err(|_| bad())?),
        "short" => Value::Short(raw.trim().parse().map_err(|_| bad())?),
        "byte" => Value::Byte(raw.trim().parse().map_err(|_| bad())?),
        "boolean" => match raw.as_str() {
            "true" => Value::Boolean(true),
            "false" => Value::Boolean(false),
            _ => return Err(bad()),
        },
        "string" => Value::String(raw),
        other => return Err(format!("{what}: unknown type {other:?}")),
    })
}

fn set_variable(context: &mut Context, variable: &Json) -> Result<(), String> {
    let name = variable
        .str_field("name")
        .filter(|n| !n.is_empty())
        .ok_or("variables[]: name is required")?;
    let name = name.strip_prefix('$').unwrap_or(name);
    let what = format!("variable {name}");
    let kind = variable.str_field("type").unwrap_or("float");
    let value = typed_value(kind, variable.get("value"), &what)?;
    let default_map = match &value {
        Value::Boolean(_) => "boolean",
        Value::String(_) => "string",
        Value::Null => "object",
        _ => "number",
    };
    match (variable.str_field("map").unwrap_or(default_map), value) {
        ("number", value @ Value::Number(_))
        | ("number", value @ Value::Double(_))
        | ("number", value @ Value::Int(_))
        | ("number", value @ Value::Long(_))
        | ("number", value @ Value::Short(_))
        | ("number", value @ Value::Byte(_)) => context.set_number(name, value),
        ("string", Value::String(s)) => context.set_string(name, s),
        ("boolean", Value::Boolean(b)) => context.set_boolean(name, b),
        ("object", value) => context.set_object(name, value),
        (map, _) => {
            return Err(format!(
                "{what}: a {kind} value cannot go into the {map} map"
            ))
        }
    }
    Ok(())
}

/// One stubbed external method.
#[derive(Clone, Debug)]
pub(crate) struct Stub {
    class: String,
    method: Option<String>,
    arity: Option<usize>,
    registered: bool,
    result: Value,
}

fn read_stub(json: &Json) -> Result<Stub, String> {
    let class = json
        .str_field("class")
        .filter(|c| !c.is_empty())
        .ok_or("externals[]: class is required")?
        .to_owned();
    let what = format!("external {class}");
    let arity = match json.get("arity") {
        None | Some(Json::Null) => None,
        Some(Json::Num(text)) => Some(
            text.parse::<usize>()
                .map_err(|_| format!("{what}: arity must be a non-negative integer"))?,
        ),
        Some(_) => return Err(format!("{what}: arity must be a number")),
    };
    let registered = !matches!(json.get("registered"), Some(Json::Bool(false)));
    let result = match json.get("result") {
        None | Some(Json::Null) => Value::Null,
        Some(result) => typed_value(
            result.str_field("type").unwrap_or("float"),
            result.get("value"),
            &what,
        )?,
    };
    Ok(Stub {
        class,
        method: json.str_field("method").map(str::to_owned),
        arity,
        registered,
        result,
    })
}

/// The external host of a request: constant-returning stubs in place of Java classes.
#[derive(Clone, Debug, Default)]
pub(crate) struct StubExternals {
    stubs: Vec<Stub>,
}

impl ExternalHost for StubExternals {
    fn class_exists(&self, class_name: &str) -> bool {
        self.stubs.iter().any(|stub| stub.class == class_name)
    }

    fn invoke(
        &mut self,
        call: &ExternalCall<'_>,
        _: &dyn Variables,
    ) -> Result<Value, ExternalError> {
        let stub = self.stubs.iter().find(|stub| {
            stub.class == call.class_name
                && stub.method.as_deref() == Some(call.method_name)
                && stub.arity.is_none_or(|arity| arity == call.args.len())
        });
        match stub {
            None => Err(ExternalError::MethodNotFound),
            Some(stub) if !stub.registered => Err(ExternalError::NotRegistered),
            Some(stub) => Ok(stub.result.clone()),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reads_standard_json() {
        let json =
            parse_json(r#" {"a": [1, -2.5e3, "xé\n", true, false, null], "b": {}} "#).unwrap();
        assert_eq!(
            json.get("a"),
            Some(&Json::Arr(vec![
                Json::Num("1".into()),
                Json::Num("-2.5e3".into()),
                Json::Str("x\u{e9}\n".into()),
                Json::Bool(true),
                Json::Bool(false),
                Json::Null,
            ]))
        );
        assert!(parse_json("{\"a\":1,}").is_err());
        assert!(parse_json("[1] 2").is_err());
        assert!(parse_json("\"open").is_err());
    }

    #[test]
    fn variables_go_to_the_map_of_their_kind() {
        let json = parse_json(
            r#"{"variables":[{"name":"$n","type":"float","value":1.5},
                {"name":"s","type":"string","value":"x"},
                {"name":"b","type":"boolean","value":true},
                {"name":"o","map":"object","type":"int","value":"3"}]}"#,
        )
        .unwrap();
        let request = read_request(&json).unwrap();
        assert_eq!(request.context.float_value("n"), Some(1.5));
        assert_eq!(request.context.string("s"), Some("x"));
        assert_eq!(request.context.boolean("b"), Some(true));
        assert_eq!(request.context.object("o"), Some(&Value::Int(3)));
        let bad = parse_json(
            r#"{"variables":[{"name":"b","map":"string","type":"boolean","value":true}]}"#,
        )
        .unwrap();
        assert!(read_request(&bad).is_err());
    }
}
