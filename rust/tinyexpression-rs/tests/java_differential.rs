//! Java-vs-Rust differential gate (issue #179).
//!
//! `tests/java-diff/golden/` holds the Java `P4_AST_EVALUATOR` outcome of every formula the
//! repository evaluates in tests (Java `src/test/**`, Rust tests and fixtures, FormulaInfo
//! fixtures, benchmark fixtures), each under the result types and context profiles
//! `build_corpus.py` generates. This test re-evaluates every row with the Rust runtime and
//! requires the same outcome: type and bits for numbers, value for booleans/strings, and the
//! Java exception class for errors. It also runs the closure-compiled form on every row and
//! requires it to agree exactly with the tree walker.
//!
//! Regenerate the golden with `rust/tinyexpression-rs/tests/java-diff/regenerate-java-golden.sh`
//! (needs a JDK and Maven); `cargo test` itself never needs a JVM.

use std::collections::HashMap;
use std::fs;
use std::path::Path;

use tinyexpression_rs::runtime::{
    calculator_result, java, Compiled, Context, ContextClock, EvalError, ExternalCall,
    ExternalError, ExternalHost, Host, NumberType, Options, Program, ResultType, Variables,
    XorShiftRandom,
};
use tinyexpression_rs::Value;

// ------------------------------------------------------------------ minimal JSON reader

#[derive(Debug, Clone)]
enum Json {
    Null,
    Bool(bool),
    Num(f64),
    Str(String),
    Arr(Vec<Json>),
    Obj(Vec<(String, Json)>),
}

impl Json {
    fn get(&self, key: &str) -> Option<&Json> {
        match self {
            Json::Obj(entries) => entries.iter().find(|(k, _)| k == key).map(|(_, v)| v),
            _ => None,
        }
    }
    fn str(&self, key: &str) -> Option<&str> {
        match self.get(key) {
            Some(Json::Str(s)) => Some(s),
            _ => None,
        }
    }
    fn as_str(&self) -> &str {
        match self {
            Json::Str(s) => s,
            _ => panic!("expected string, found {self:?}"),
        }
    }
}

struct Reader<'a> {
    chars: std::iter::Peekable<std::str::Chars<'a>>,
}

impl Reader<'_> {
    fn parse(text: &str) -> Json {
        let mut reader = Reader {
            chars: text.chars().peekable(),
        };
        reader.value()
    }
    fn ws(&mut self) {
        while self.chars.peek().is_some_and(|c| c.is_ascii_whitespace()) {
            self.chars.next();
        }
    }
    fn value(&mut self) -> Json {
        self.ws();
        match self.chars.peek().copied() {
            Some('{') => {
                self.chars.next();
                let mut entries = Vec::new();
                loop {
                    self.ws();
                    if self.chars.peek() == Some(&'}') {
                        self.chars.next();
                        break;
                    }
                    let Json::Str(key) = self.value() else {
                        panic!("object key")
                    };
                    self.ws();
                    assert_eq!(self.chars.next(), Some(':'));
                    let value = self.value();
                    entries.push((key, value));
                    self.ws();
                    if self.chars.peek() == Some(&',') {
                        self.chars.next();
                    }
                }
                Json::Obj(entries)
            }
            Some('[') => {
                self.chars.next();
                let mut items = Vec::new();
                loop {
                    self.ws();
                    if self.chars.peek() == Some(&']') {
                        self.chars.next();
                        break;
                    }
                    items.push(self.value());
                    self.ws();
                    if self.chars.peek() == Some(&',') {
                        self.chars.next();
                    }
                }
                Json::Arr(items)
            }
            Some('"') => {
                self.chars.next();
                let mut units: Vec<u16> = Vec::new();
                loop {
                    match self.chars.next().expect("unterminated string") {
                        '"' => break,
                        '\\' => match self.chars.next().expect("escape") {
                            'n' => units.push(b'\n'.into()),
                            't' => units.push(b'\t'.into()),
                            'r' => units.push(b'\r'.into()),
                            'b' => units.push(8),
                            'f' => units.push(12),
                            'u' => {
                                let hex: String =
                                    (0..4).filter_map(|_| self.chars.next()).collect();
                                units.push(u16::from_str_radix(&hex, 16).expect("hex escape"));
                            }
                            other => {
                                let mut buf = [0u16; 2];
                                units.extend_from_slice(other.encode_utf16(&mut buf));
                            }
                        },
                        other => {
                            let mut buf = [0u16; 2];
                            units.extend_from_slice(other.encode_utf16(&mut buf));
                        }
                    }
                }
                Json::Str(String::from_utf16_lossy(&units))
            }
            Some('t') => {
                for _ in 0..4 {
                    self.chars.next();
                }
                Json::Bool(true)
            }
            Some('f') => {
                for _ in 0..5 {
                    self.chars.next();
                }
                Json::Bool(false)
            }
            Some('n') => {
                for _ in 0..4 {
                    self.chars.next();
                }
                Json::Null
            }
            _ => {
                let mut text = String::new();
                while self
                    .chars
                    .peek()
                    .is_some_and(|c| c.is_ascii_digit() || matches!(c, '-' | '+' | '.' | 'e' | 'E'))
                {
                    text.push(self.chars.next().unwrap());
                }
                Json::Num(text.parse().expect("number"))
            }
        }
    }
}

// ------------------------------------------------------------------ test host

/// Native stand-ins for the Java classes the corpus calls through `external`
/// (`org.unlaxer.tinyexpression.Fee`, `...parser.TestSideEffector`, and the FormulaInfo java
/// code blocks `CheckDigits` / `sample.v1.CheckAlphabets`). Argument conversion follows
/// `P4TypedAstEvaluator.convertToParamType`.
struct TestHost {
    registered: bool,
}

const LOADABLE: [&str; 4] = [
    "org.unlaxer.tinyexpression.Fee",
    "org.unlaxer.tinyexpression.parser.TestSideEffector",
    "CheckDigits",
    "sample.v1.CheckAlphabets",
];

fn param_float(value: &Value) -> Result<f32, ExternalError> {
    Ok(match value {
        Value::Null => return Err(ExternalError::Failed("null for primitive float".into())),
        Value::Number(v) => *v,
        Value::Double(v) => *v as f32,
        Value::Int(v) => *v as f32,
        Value::Long(v) => *v as f32,
        Value::Short(v) => f32::from(*v),
        Value::Byte(v) => f32::from(*v),
        other => java::parse_float(&tinyexpression_rs::runtime::java_string(other)).unwrap_or(0.0),
    })
}

fn param_bool(value: &Value) -> Result<bool, ExternalError> {
    Ok(match value {
        Value::Null => return Err(ExternalError::Failed("null for primitive boolean".into())),
        Value::Boolean(b) => *b,
        other => tinyexpression_rs::runtime::java_string(other).eq_ignore_ascii_case("true"),
    })
}

fn param_string(value: &Value) -> Result<String, ExternalError> {
    match value {
        Value::Null => Err(ExternalError::Failed("null receiver".into())),
        other => Ok(tinyexpression_rs::runtime::java_string(other)),
    }
}

impl ExternalHost for TestHost {
    fn class_exists(&self, class_name: &str) -> bool {
        LOADABLE.contains(&class_name)
    }

    fn invoke(
        &mut self,
        call: &ExternalCall<'_>,
        _: &dyn Variables,
    ) -> Result<Value, ExternalError> {
        let args = call.args;
        let known_method = match (call.class_name, call.method_name, args.len()) {
            ("org.unlaxer.tinyexpression.Fee", "calculate", 3 | 5) => true,
            ("org.unlaxer.tinyexpression.parser.TestSideEffector", method, n) => matches!(
                (method, n),
                ("setBlackList", 1 | 2)
                    | ("booleanToFloatMethod", 1)
                    | ("salary", 2)
                    | ("beforeSupecifiedDate", 1)
                    | ("getAge", 1)
                    | ("getYear", 1)
            ),
            ("CheckDigits" | "sample.v1.CheckAlphabets", "check", 1) => true,
            _ => false,
        };
        if !known_method {
            return Err(ExternalError::MethodNotFound);
        }
        if !self.registered {
            return Err(ExternalError::NotRegistered);
        }
        Ok(match (call.class_name, call.method_name, args.len()) {
            ("org.unlaxer.tinyexpression.Fee", _, 3) => {
                let (age, fee, tax) = (
                    param_float(&args[0])?,
                    param_float(&args[1])?,
                    param_float(&args[2])?,
                );
                Value::Number(if age < 18.0 { 0.0 } else { fee + fee * tax })
            }
            ("org.unlaxer.tinyexpression.Fee", _, _) => {
                let (age, mut fee, tax) = (
                    param_float(&args[0])?,
                    param_float(&args[1])?,
                    param_float(&args[2])?,
                );
                let free = param_bool(&args[3])?;
                let name = param_string(&args[4])?;
                if free {
                    Value::Number(0.0)
                } else if name.contains('猫') {
                    Value::Number(-1000.0)
                } else {
                    if age < 18.0 {
                        fee *= 0.5;
                    }
                    Value::Number(fee + fee * tax)
                }
            }
            (_, "booleanToFloatMethod", _) => {
                Value::Number(if param_bool(&args[0])? { 69.0 } else { 6969.0 })
            }
            (_, "salary", _) => {
                let average = param_float(&args[0])?;
                let name = param_string(&args[1])?;
                Value::Number(if name.contains("Dr.") {
                    average * 2.0
                } else {
                    average
                })
            }
            (_, "beforeSupecifiedDate", _) => Value::Boolean(true),
            (_, "getAge", _) => Value::Number(0.0),
            (_, "getYear", _) => {
                let date = param_string(&args[0])?;
                let parts: Vec<&str> = date.split('/').collect();
                if parts.iter().all(|p| p.is_empty()) && !date.is_empty() {
                    return Err(ExternalError::Failed("ArrayIndexOutOfBounds".into()));
                }
                Value::String(parts[0].to_owned())
            }
            (_, "setBlackList", 1) => Value::Number(param_float(&args[0])? * 2.0),
            (_, "setBlackList", _) => {
                let original = param_float(&args[0])?;
                Value::Number(if param_bool(&args[1])? {
                    original * 2.0
                } else {
                    original
                })
            }
            ("CheckDigits", _, _) => {
                let target = param_string(&args[0])?;
                Value::Boolean(!target.is_empty() && target.bytes().all(|b| b.is_ascii_digit()))
            }
            (_, "check", _) => {
                let target = param_string(&args[0])?;
                Value::Boolean(
                    !target.is_empty() && target.bytes().all(|b| b.is_ascii_alphabetic()),
                )
            }
            _ => return Err(ExternalError::MethodNotFound),
        })
    }
}

// ------------------------------------------------------------------ rows

fn result_type(name: &str) -> ResultType {
    ResultType::parse(name).unwrap_or_else(|| panic!("result type {name}"))
}

fn number_type(name: &str) -> NumberType {
    NumberType::parse(name).unwrap_or_else(|| panic!("number type {name}"))
}

fn context_of(vars: &Json) -> Context {
    let mut context = Context::new();
    let Json::Arr(vars) = vars else {
        panic!("vars")
    };
    for var in vars {
        let Json::Arr(fields) = var else {
            panic!("var")
        };
        let (map, kind, name, raw) = (
            fields[0].as_str(),
            fields[1].as_str(),
            fields[2].as_str(),
            fields[3].as_str(),
        );
        let value = match kind {
            "float" => Value::Number(java::parse_float(raw).expect("float")),
            "double" => Value::Double(java::parse_double(raw).expect("double")),
            "int" => Value::Int(raw.parse().expect("int")),
            "long" => Value::Long(raw.parse().expect("long")),
            "boolean" => Value::Boolean(raw == "true"),
            _ => Value::String(raw.to_owned()),
        };
        match (map, value) {
            ("number", value) => context.set_number(name, value),
            ("string", Value::String(s)) => context.set_string(name, s),
            ("boolean", Value::Boolean(b)) => context.set_boolean(name, b),
            (_, value) => context.set_object(name, value),
        }
    }
    context
}

/// The Rust outcome in the golden's vocabulary: (kind, text, bits).
fn outcome(result: &Result<Value, EvalError>, create: bool) -> (String, String, Option<String>) {
    match result {
        Err(error) => (
            "error".into(),
            format!(
                "{}@{}",
                error.kind.java_name(),
                if create { "create" } else { "apply" }
            ),
            None,
        ),
        Ok(value) => match value {
            Value::Number(v) => (
                "float".into(),
                String::new(),
                Some(format!("{:08x}", v.to_bits())),
            ),
            Value::Double(v) => (
                "double".into(),
                String::new(),
                Some(format!("{:016x}", v.to_bits())),
            ),
            Value::Int(v) => ("int".into(), v.to_string(), None),
            Value::Long(v) => ("long".into(), v.to_string(), None),
            Value::Short(v) => ("short".into(), v.to_string(), None),
            Value::Byte(v) => ("byte".into(), v.to_string(), None),
            Value::Boolean(v) => ("boolean".into(), v.to_string(), None),
            Value::String(v) => ("string".into(), v.clone(), None),
            Value::Null => ("null".into(), String::new(), None),
            Value::Object(o) => ("object".into(), o.class_name().to_owned(), None),
        },
    }
}

fn expected(java: &Json) -> (String, String, Option<String>) {
    let kind = java.str("kind").unwrap().to_owned();
    match kind.as_str() {
        "error" => (
            kind,
            format!(
                "{}@{}",
                java.str("text").unwrap(),
                java.str("stage").unwrap()
            ),
            None,
        ),
        "float" | "double" => (kind, String::new(), java.str("bits").map(str::to_owned)),
        _ => (kind, java.str("text").unwrap_or_default().to_owned(), None),
    }
}

/// Documented deviations: rows where the Rust outcome is allowed to differ, with the reason.
/// Every entry is listed in the crate README ("Java 差分").
fn deviation(
    formula: &str,
    _row: &Json,
    java: &(String, String, Option<String>),
    rust: &(String, String, Option<String>),
) -> Option<&'static str> {
    if formula.contains("random(") && java.0 == rust.0 {
        return Some("random(): Java uses Math.random(); only the result kind is comparable");
    }
    None
}

struct Loaded {
    formulas: Vec<String>,
    origins: Vec<String>,
    rows: Vec<Json>,
}

fn load() -> Loaded {
    let dir = Path::new(env!("CARGO_MANIFEST_DIR")).join("tests/java-diff/golden");
    let mut formulas = Vec::new();
    let mut origins = Vec::new();
    for line in fs::read_to_string(dir.join("formulas.jsonl"))
        .unwrap()
        .lines()
    {
        let entry = Reader::parse(line);
        formulas.push(entry.str("text").unwrap().to_owned());
        origins.push(entry.str("origin").unwrap().to_owned());
    }
    let rows = fs::read_to_string(dir.join("p4ast.jsonl"))
        .unwrap()
        .lines()
        .map(Reader::parse)
        .collect();
    Loaded {
        formulas,
        origins,
        rows,
    }
}

type Built = Result<(Program, Compiled), EvalError>;

fn formula_index(row: &Json) -> usize {
    match row.get("f") {
        Some(Json::Num(n)) => *n as usize,
        _ => panic!("f"),
    }
}

/// Debug builds use far larger parser frames than release; give the corpus (which includes the
/// deeply nested fraud-alert formulas) the stack a release CLI main thread has.
fn with_stack<F: FnOnce() + Send + 'static>(body: F) {
    std::thread::Builder::new()
        .stack_size(256 << 20)
        .spawn(body)
        .unwrap()
        .join()
        .unwrap_or_else(|panic| std::panic::resume_unwind(panic));
}

#[test]
fn rust_runtime_matches_the_java_golden() {
    with_stack(java_golden);
}

fn java_golden() {
    let loaded = load();
    let mut programs: HashMap<(usize, String, String), Built> = HashMap::new();
    let mut form_mismatches = Vec::new();
    let mut mismatches = Vec::new();
    let mut deviations: HashMap<&'static str, usize> = HashMap::new();
    let mut compared = 0usize;
    for row in &loaded.rows {
        let f = formula_index(row);
        let formula = &loaded.formulas[f];
        let (rt, nt) = (row.str("rt").unwrap(), row.str("nt").unwrap());
        let program = programs
            .entry((f, rt.to_owned(), nt.to_owned()))
            .or_insert_with(|| {
                Program::new(
                    formula,
                    Options::new(result_type(rt)).with_number_type(number_type(nt)),
                )
                .map(|program| {
                    let compiled = program.compile();
                    (program, compiled)
                })
            });
        let rust = match program {
            Err(error) => outcome(&Err(error.clone()), true),
            Ok((program, compiled)) => {
                let registered = matches!(row.get("ext"), Some(Json::Bool(true)));
                let run = |closure: bool| {
                    let mut context = context_of(row.get("vars").unwrap());
                    let mut external = TestHost { registered };
                    let mut random = XorShiftRandom::new(7);
                    let mut host = Host {
                        external: &mut external,
                        clock: &ContextClock,
                        random: &mut random,
                    };
                    let result = if closure {
                        compiled.eval(&mut context, &mut host)
                    } else {
                        program.eval_tree(&mut context, &mut host)
                    };
                    (calculator_result(result.clone()), result)
                };
                let (tree, tree_raw) = run(false);
                let (closure, closure_raw) = run(true);
                // The two Rust forms must agree exactly, including error messages.
                let same = outcome(&tree_raw, false) == outcome(&closure_raw, false)
                    && tree_raw.as_ref().err().map(|e| &e.message)
                        == closure_raw.as_ref().err().map(|e| &e.message);
                if !same {
                    form_mismatches.push(format!(
                        "{} formula={formula:?}\n    tree={tree_raw:?}\n    closure={closure_raw:?}",
                        row.str("id").unwrap()
                    ));
                }
                let _ = closure;
                outcome(&tree, false)
            }
        };
        let java = expected(row.get("java").unwrap());
        compared += 1;
        if rust == java {
            continue;
        }
        if let Some(reason) = deviation(formula, row, &java, &rust) {
            *deviations.entry(reason).or_default() += 1;
            continue;
        }
        mismatches.push(format!(
            "{} [{}] rt={rt} nt={nt} profile={} formula={:?}\n    java={java:?}\n    rust={rust:?}",
            row.str("id").unwrap(),
            loaded.origins[f],
            row.str("profile").unwrap(),
            formula.chars().take(160).collect::<String>(),
        ));
    }
    if let Ok(path) = std::env::var("JAVA_DIFF_DUMP") {
        fs::write(path, mismatches.join("\n")).unwrap();
    }
    eprintln!(
        "compared {compared} rows over {} formulas",
        loaded.formulas.len()
    );
    for (reason, count) in &deviations {
        eprintln!("documented deviation ({count} rows): {reason}");
    }
    assert!(
        form_mismatches.is_empty(),
        "{} rows differ between the tree walker and the closure-compiled form:\n{}",
        form_mismatches.len(),
        form_mismatches
            .iter()
            .take(30)
            .cloned()
            .collect::<Vec<_>>()
            .join("\n")
    );
    eprintln!("tree walker and closure form agree on all {compared} rows");
    if !mismatches.is_empty() {
        let shown: Vec<_> = mismatches.iter().take(60).cloned().collect();
        panic!(
            "{} of {compared} rows differ from the Java golden:\n{}",
            mismatches.len(),
            shown.join("\n")
        );
    }
}
