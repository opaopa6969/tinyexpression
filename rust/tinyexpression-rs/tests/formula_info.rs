//! FormulaInfo loader parity with the hand-written Java loader (issue #180).
//!
//! `tests/formula-info/golden/java.jsonl` holds what the Java loader makes of every FormulaInfo
//! fixture in the repository (`src/test/resources/formulaInfo.fi`,
//! `formulaInfo-test/*/formulaInfo.txt` and the accept/reject cases in `formulaInfo-ubnf/`):
//! the syntax (`FormulaInfoBlocksParser` over the whole input, every entry's key and value),
//! the loaded fields (`FormulaInfoList.parse`) and one P4_AST_EVALUATOR evaluation per
//! formula. This test requires the Rust loader (`tinyexpression_rs::formula_info`, a parser
//! generated from `grammar/formula-info.ubnf`) to agree on all three.
//!
//! The one deliberate difference: `FormulaInfoList.parse` does not check that the whole
//! document was consumed, so a document with an unparsable line loads the blocks before it
//! (or nothing) without an error. The Rust loader rejects such a document, as
//! `FormulaInfoSourceDocument.parse` does. This test pins that the Java syntax layer rejects
//! exactly those documents too.
//!
//! Regenerate the golden with `tests/formula-info/regenerate-formula-info-golden.sh` (needs a
//! JDK and Maven); `cargo test` itself never needs a JVM.

use std::fs;
use std::path::{Path, PathBuf};

use tinyexpression_rs::formula_info::{
    self, parse_document, ExecutionBackend, FormulaInfo, LoadError, LoaderOptions,
};
use tinyexpression_rs::runtime::{
    java, Context, ContextClock, EvalError, ExternalCall, ExternalError, ExternalHost, Host,
    Variables, XorShiftRandom,
};
use tinyexpression_rs::Value;

// ------------------------------------------------------------------ minimal JSON reader

#[derive(Debug, Clone, PartialEq)]
enum Json {
    Null,
    Bool(bool),
    Num(f64),
    Str(String),
    Arr(Vec<Json>),
    Obj(Vec<(String, Json)>),
}

impl Json {
    fn get(&self, key: &str) -> &Json {
        match self {
            Json::Obj(entries) => entries
                .iter()
                .find(|(k, _)| k == key)
                .map(|(_, v)| v)
                .unwrap_or(&Json::Null),
            _ => &Json::Null,
        }
    }
    fn has(&self, key: &str) -> bool {
        matches!(self, Json::Obj(entries) if entries.iter().any(|(k, _)| k == key))
    }
    fn str(&self) -> Option<&str> {
        match self {
            Json::Str(s) => Some(s),
            _ => None,
        }
    }
    fn bool(&self) -> bool {
        matches!(self, Json::Bool(true))
    }
    fn arr(&self) -> &[Json] {
        match self {
            Json::Arr(items) => items,
            _ => &[],
        }
    }
}

fn parse_json(text: &str) -> Json {
    let chars: Vec<char> = text.chars().collect();
    let mut index = 0;
    let value = json_value(&chars, &mut index);
    skip_ws(&chars, &mut index);
    assert_eq!(index, chars.len(), "trailing JSON input");
    value
}

fn skip_ws(chars: &[char], index: &mut usize) {
    while *index < chars.len() && chars[*index].is_ascii_whitespace() {
        *index += 1;
    }
}

fn json_value(chars: &[char], index: &mut usize) -> Json {
    skip_ws(chars, index);
    match chars[*index] {
        '{' => {
            *index += 1;
            let mut entries = Vec::new();
            loop {
                skip_ws(chars, index);
                if chars[*index] == '}' {
                    *index += 1;
                    return Json::Obj(entries);
                }
                let Json::Str(key) = json_value(chars, index) else {
                    panic!("object key");
                };
                skip_ws(chars, index);
                assert_eq!(chars[*index], ':');
                *index += 1;
                entries.push((key, json_value(chars, index)));
                skip_ws(chars, index);
                if chars[*index] == ',' {
                    *index += 1;
                }
            }
        }
        '[' => {
            *index += 1;
            let mut items = Vec::new();
            loop {
                skip_ws(chars, index);
                if chars[*index] == ']' {
                    *index += 1;
                    return Json::Arr(items);
                }
                items.push(json_value(chars, index));
                skip_ws(chars, index);
                if chars[*index] == ',' {
                    *index += 1;
                }
            }
        }
        '"' => {
            *index += 1;
            let mut out = String::new();
            let mut pending_high: Option<u32> = None;
            loop {
                let c = chars[*index];
                *index += 1;
                match c {
                    '"' => return Json::Str(out),
                    '\\' => {
                        let escape = chars[*index];
                        *index += 1;
                        let ch = match escape {
                            'n' => '\n',
                            'r' => '\r',
                            't' => '\t',
                            'b' => '\u{8}',
                            'f' => '\u{c}',
                            'u' => {
                                let hex: String = chars[*index..*index + 4].iter().collect();
                                *index += 4;
                                let unit = u32::from_str_radix(&hex, 16).unwrap();
                                if (0xD800..0xDC00).contains(&unit) {
                                    pending_high = Some(unit);
                                    continue;
                                }
                                if let Some(high) = pending_high.take() {
                                    let cp = 0x10000 + ((high - 0xD800) << 10) + (unit - 0xDC00);
                                    char::from_u32(cp).unwrap()
                                } else {
                                    char::from_u32(unit).unwrap_or('\u{FFFD}')
                                }
                            }
                            other => other,
                        };
                        out.push(ch);
                    }
                    other => out.push(other),
                }
            }
        }
        't' => {
            *index += 4;
            Json::Bool(true)
        }
        'f' => {
            *index += 5;
            Json::Bool(false)
        }
        'n' => {
            *index += 4;
            Json::Null
        }
        _ => {
            let start = *index;
            while *index < chars.len()
                && matches!(chars[*index], '-' | '+' | '.' | 'e' | 'E' | '0'..='9')
            {
                *index += 1;
            }
            let text: String = chars[start..*index].iter().collect();
            Json::Num(text.parse().unwrap())
        }
    }
}

// ------------------------------------------------------------------ fixtures

fn repo_root() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join("../..")
}

fn golden() -> Vec<Json> {
    let path = Path::new(env!("CARGO_MANIFEST_DIR")).join("tests/formula-info/golden/java.jsonl");
    fs::read_to_string(path)
        .expect("golden")
        .lines()
        .filter(|line| !line.trim().is_empty())
        .map(parse_json)
        .collect()
}

fn fixture_text(row: &Json) -> String {
    let fixture = row.get("fixture").str().unwrap();
    // Exact bytes: several fixtures use CR / CRLF line breaks.
    String::from_utf8(fs::read(repo_root().join(fixture)).expect("fixture")).expect("UTF-8")
}

/// The Java driver's loader configuration (FormulaInfoParityDriver.additionalFields).
fn java_driver_options() -> LoaderOptions {
    LoaderOptions {
        default_backend: ExecutionBackend::P4AstEvaluator,
        ..LoaderOptions::java_tests()
    }
}

// ------------------------------------------------------------------ externals

/// The FormulaInfo java code blocks `CheckDigits` / `sample.v1.CheckAlphabets`
/// (tests/java-diff/externals), which the Java driver registers in the context.
struct CodeBlocks;

impl ExternalHost for CodeBlocks {
    fn class_exists(&self, class_name: &str) -> bool {
        matches!(class_name, "CheckDigits" | "sample.v1.CheckAlphabets")
    }

    fn invoke(
        &mut self,
        call: &ExternalCall<'_>,
        _: &dyn Variables,
    ) -> Result<Value, ExternalError> {
        let target = match (call.method_name, call.args) {
            ("check", [Value::Null]) => return Err(ExternalError::Failed("null receiver".into())),
            ("check", [value]) => tinyexpression_rs::runtime::java_string(value),
            _ => return Err(ExternalError::MethodNotFound),
        };
        Ok(Value::Boolean(
            !target.is_empty()
                && match call.class_name {
                    "CheckDigits" => target.bytes().all(|b| b.is_ascii_digit()),
                    _ => target.bytes().all(|b| b.is_ascii_alphabetic()),
                },
        ))
    }
}

/// The Java driver's `{kind, text}` for an evaluation outcome.
fn outcome(result: &Result<Value, EvalError>) -> (String, String) {
    match result {
        Ok(Value::Number(v)) => ("float".into(), java::float_to_string(*v)),
        Ok(Value::Double(v)) => ("double".into(), java::double_to_string(*v)),
        Ok(Value::Int(v)) => ("int".into(), v.to_string()),
        Ok(Value::Long(v)) => ("long".into(), v.to_string()),
        Ok(Value::Short(v)) => ("short".into(), v.to_string()),
        Ok(Value::Byte(v)) => ("byte".into(), v.to_string()),
        Ok(Value::Boolean(v)) => ("boolean".into(), v.to_string()),
        Ok(Value::String(v)) => ("string".into(), v.clone()),
        Ok(Value::Null) => ("null".into(), String::new()),
        Ok(Value::Object(object)) => ("object".into(), object.class_name().to_owned()),
        Err(error) => ("error".into(), error.kind.java_name().to_owned()),
    }
}

// ------------------------------------------------------------------ comparisons

fn opt(value: &Json) -> Option<String> {
    value.str().map(str::to_owned)
}

fn compare_fields(fixture: &str, index: usize, java: &Json, rust: &FormulaInfo) -> Vec<String> {
    let mut diffs = Vec::new();
    let mut check = |field: &str, java: Option<String>, rust: Option<String>| {
        if java != rust {
            diffs.push(format!(
                "{fixture} #{index} {field}: java={java:?} rust={rust:?}"
            ));
        }
    };
    check("name", opt(java.get("name")), rust.name.clone());
    check(
        "calculatorName",
        opt(java.get("calculatorName")),
        rust.calculator_name.clone(),
    );
    check(
        "description",
        opt(java.get("description")),
        rust.description.clone(),
    );
    check(
        "tags",
        Some(format!(
            "{:?}",
            java.get("tags")
                .arr()
                .iter()
                .map(|t| t.str().unwrap())
                .collect::<Vec<_>>()
        )),
        Some(format!("{:?}", rust.tags)),
    );
    check(
        "periodStartInclusive",
        opt(java.get("periodStartInclusive")),
        rust.period_start_inclusive.clone(),
    );
    check(
        "periodEndExclusive",
        opt(java.get("periodEndExclusive")),
        rust.period_end_exclusive.clone(),
    );
    check(
        "multiTenancyId",
        opt(java.get("multiTenancyId")),
        rust.multi_tenancy_id.clone(),
    );
    check(
        "dependsOn",
        opt(java.get("dependsOn")),
        rust.depends_on.clone(),
    );
    check(
        "resultType",
        opt(java.get("resultType")),
        Some(rust.result_type.clone()),
    );
    check(
        "numberType",
        opt(java.get("numberType")),
        rust.number_type.clone(),
    );
    check(
        "executionBackend",
        opt(java.get("executionBackend")),
        Some(rust.execution_backend.name().to_owned()),
    );
    check(
        "formulaText",
        opt(java.get("formulaText")),
        Some(rust.formula_text.clone()),
    );
    check("hash", opt(java.get("hash")), Some(rust.hash.clone()));
    check(
        "className",
        opt(java.get("className")),
        Some(rust.class_name.clone()),
    );
    check(
        "classNameWithHash",
        opt(java.get("classNameWithHash")),
        Some(rust.class_name_with_hash.clone()),
    );
    let java_extra: Vec<(String, String)> = match java.get("extraValueByKey") {
        Json::Obj(entries) => entries
            .iter()
            .map(|(k, v)| (k.clone(), v.str().unwrap().to_owned()))
            .collect(),
        _ => Vec::new(),
    };
    check(
        "extraValueByKey",
        Some(format!("{java_extra:?}")),
        Some(format!("{:?}", rust.extra)),
    );
    diffs
}

#[test]
fn every_fixture_is_in_the_golden() {
    let listed: Vec<String> = golden()
        .iter()
        .map(|row| row.get("fixture").str().unwrap().to_owned())
        .collect();
    let dir = repo_root().join("src/test/resources/formulaInfo-ubnf");
    let mut on_disk: Vec<String> = fs::read_dir(&dir)
        .unwrap()
        .map(|entry| entry.unwrap().file_name().into_string().unwrap())
        .filter(|name| name.ends_with(".fi"))
        .map(|name| format!("src/test/resources/formulaInfo-ubnf/{name}"))
        .collect();
    on_disk.sort();
    for fixture in &on_disk {
        assert!(
            listed.contains(fixture),
            "{fixture} is not in the golden; run regenerate-formula-info-golden.sh"
        );
    }
    assert!(listed.iter().any(|f| f.ends_with("formulaInfo.fi")));
    assert_eq!(
        listed
            .iter()
            .filter(|f| f.ends_with("/formulaInfo.txt"))
            .count(),
        2
    );
}

/// The generated parser accepts exactly what the Java syntax layer accepts, and slices every
/// entry into the same key, raw value and normalised value.
#[test]
fn syntax_matches_java_blocks_parser() {
    let mut failures = Vec::new();
    for row in golden() {
        let fixture = row.get("fixture").str().unwrap().to_owned();
        let source = fixture_text(&row);
        let syntax = row.get("syntax");
        let java_accepts = syntax.get("accepted").bool();
        let document = parse_document(&source);
        if document.is_ok() != java_accepts {
            failures.push(format!(
                "{fixture}: java accepts={java_accepts}, rust={document:?}"
            ));
            continue;
        }
        let Ok(document) = document else { continue };
        // Java also reports the zero-width block at the end of input; it has no entries.
        let java_blocks: Vec<&Json> = syntax
            .get("blocks")
            .arr()
            .iter()
            .filter(|b| !b.get("entries").arr().is_empty())
            .collect();
        let rust_blocks: Vec<_> = document
            .blocks
            .iter()
            .filter(|b| !b.entries.is_empty())
            .collect();
        if java_blocks.len() != rust_blocks.len() {
            failures.push(format!(
                "{fixture}: {} blocks with entries in java, {} in rust",
                java_blocks.len(),
                rust_blocks.len()
            ));
            continue;
        }
        for (java_block, rust_block) in java_blocks.iter().zip(&rust_blocks) {
            let java_entries = java_block.get("entries").arr();
            if java_entries.len() != rust_block.entries.len() {
                failures.push(format!("{fixture}: entry count differs"));
                continue;
            }
            for (java_entry, entry) in java_entries.iter().zip(&rust_block.entries) {
                let raw = java_entry.get("rawValue").str().unwrap_or("");
                if raw != entry.raw_value {
                    failures.push(format!(
                        "{fixture}: raw value of {}: java={raw:?} rust={:?}",
                        entry.key, entry.raw_value
                    ));
                }
                if java_entry.has("extractError") {
                    if entry.value().is_some() {
                        failures.push(format!("{fixture}: {} should not extract", entry.key));
                    }
                    continue;
                }
                if java_entry.get("key").str() != Some(entry.key.as_str()) {
                    failures.push(format!("{fixture}: key {:?}", entry.key));
                }
                if java_entry.get("value").str().map(str::to_owned) != entry.value() {
                    failures.push(format!(
                        "{fixture}: value of {}: java={:?} rust={:?}",
                        entry.key,
                        java_entry.get("value"),
                        entry.value()
                    ));
                }
            }
        }
    }
    assert!(failures.is_empty(), "{}", failures.join("\n"));
}

/// Loaded fields, load errors and one evaluation per formula match the Java loader.
#[test]
fn loader_matches_java_loader() {
    let mut failures = Vec::new();
    let mut table = Vec::new();
    for row in golden() {
        let fixture = row.get("fixture").str().unwrap().to_owned();
        let name = fixture.rsplit('/').next().unwrap().to_owned();
        let source = fixture_text(&row);
        let java = row.get("loader");
        let rust = formula_info::load(&source, &java_driver_options());
        let java_syntax_accepts = row.get("syntax").get("accepted").bool();

        // File-name convention of the formulaInfo-ubnf fixtures.
        let expected_kind = if name.starts_with("accept-") {
            Some("load")
        } else if name.starts_with("reject-syntax-") {
            Some("syntax")
        } else if name.starts_with("reject-load-") {
            Some("error")
        } else {
            None
        };
        let actual_kind = match &rust {
            Ok(_) => "load",
            Err(LoadError::Syntax(_)) => "syntax",
            Err(_) => "error",
        };
        if let Some(expected) = expected_kind {
            if expected != actual_kind {
                failures.push(format!(
                    "{fixture}: expected {expected}, rust gave {rust:?}"
                ));
            }
        }

        if !java_syntax_accepts {
            // The deliberate difference: Java loads the prefix (or nothing) silently.
            match &rust {
                Err(LoadError::Syntax(_)) => table.push(format!(
                    "{name}: rust=syntax error, java loader=ok with {} formula(s) (unparsed rest dropped)",
                    java.get("infos").arr().len()
                )),
                other => failures.push(format!("{fixture}: expected a syntax error, got {other:?}")),
            }
            continue;
        }

        match (java.get("ok").bool(), &rust) {
            (true, Ok(formulas)) => {
                let infos = java.get("infos").arr();
                if infos.len() != formulas.len() {
                    failures.push(format!(
                        "{fixture}: {} formulas in java, {} in rust",
                        infos.len(),
                        formulas.len()
                    ));
                    continue;
                }
                let mut external = CodeBlocks;
                let mut random = XorShiftRandom::default();
                let mut host = Host {
                    external: &mut external,
                    clock: &ContextClock,
                    random: &mut random,
                };
                let results = formula_info::evaluate_all(formulas, &Context::new(), &mut host);
                for (index, ((java_info, formula), result)) in
                    infos.iter().zip(formulas).zip(&results).enumerate()
                {
                    failures.extend(compare_fields(&fixture, index, java_info, &formula.info));
                    let evaluation = java_info.get("evaluation");
                    let (kind, text) = outcome(result);
                    let java_kind = evaluation.get("kind").str().unwrap_or("");
                    let java_text = evaluation.get("text").str().unwrap_or("");
                    if (kind.as_str(), text.as_str()) != (java_kind, java_text) {
                        failures.push(format!(
                            "{fixture} #{index} evaluation: java={java_kind}:{java_text} rust={kind}:{text}"
                        ));
                    }
                }
                table.push(format!("{name}: both load {} formula(s)", formulas.len()));
            }
            (false, Err(error)) => {
                let java_error = java.get("error").str().unwrap_or("");
                if error.java_exception() != java_error {
                    failures.push(format!(
                        "{fixture}: java threw {java_error}, rust {} ({error})",
                        error.java_exception()
                    ));
                }
                table.push(format!(
                    "{name}: both reject ({java_error} / {})",
                    error.kind()
                ));
            }
            (java_ok, rust) => failures.push(format!(
                "{fixture}: java ok={java_ok} ({:?}), rust={rust:?}",
                java.get("message")
            )),
        }
    }
    eprintln!("FormulaInfo loader parity:\n  {}", table.join("\n  "));
    assert!(failures.is_empty(), "{}", failures.join("\n"));
}

#[test]
fn formula_span_points_at_the_formula_value() {
    let source = "calculatorName:x\n# c\nformula:\n  1 + 2\n---END_OF_PART---\n";
    let formulas = formula_info::load(source, &LoaderOptions::default()).unwrap();
    let span = formulas[0].info.formula_span;
    let slice: String = source
        .chars()
        .skip(span.start)
        .take(span.end - span.start)
        .collect();
    assert_eq!(slice, "\n  1 + 2\n");
    assert_eq!(formulas[0].info.formula_text, "  1 + 2");
    assert_eq!(
        formulas[0].info.execution_backend,
        ExecutionBackend::JavaCode
    );
}

// ------------------------------------------------------------------ CLI

fn cli(arguments: &[&str]) -> (i32, String) {
    let output = std::process::Command::new(env!("CARGO_BIN_EXE_tinyexpression"))
        .args(arguments)
        .output()
        .expect("run the CLI");
    (
        output.status.code().unwrap_or(-1),
        String::from_utf8(output.stdout).unwrap(),
    )
}

fn fixture_path(name: &str) -> String {
    repo_root()
        .join("src/test/resources/formulaInfo-ubnf")
        .join(name)
        .to_string_lossy()
        .into_owned()
}

#[test]
fn cli_run_loads_and_evaluates_every_formula() {
    let (code, stdout) = cli(&["run", &fixture_path("accept-18-depends-on.fi")]);
    assert_eq!(code, 0, "{stdout}");
    let json = parse_json(&stdout);
    assert!(json.get("ok").bool());
    let formulas = json.get("formulas").arr();
    assert_eq!(formulas.len(), 2);
    assert_eq!(
        formulas[0].get("info").get("calculatorName").str(),
        Some("base")
    );
    assert_eq!(
        formulas[0].get("info").get("executionBackend").str(),
        Some("JAVA_CODE")
    );
    assert_eq!(
        formulas[0].get("value").get("f32Bits").str(),
        Some("0x40400000")
    );
    assert_eq!(formulas[1].get("info").get("dependsOn").str(), Some("base"));
    assert_eq!(
        formulas[1].get("value").get("f32Bits").str(),
        Some("0x41000000")
    );
}

#[test]
fn cli_load_reports_fields_without_evaluating() {
    let (code, stdout) = cli(&[
        "load",
        "--default-backend",
        "p4-ast-evaluator",
        &fixture_path("accept-01-minimal.fi"),
    ]);
    assert_eq!(code, 0, "{stdout}");
    let json = parse_json(&stdout);
    let info = json.get("formulas").arr()[0].get("info");
    assert_eq!(info.get("executionBackend").str(), Some("P4_AST_EVALUATOR"));
    assert_eq!(info.get("formulaText").str(), Some("1+1"));
    assert!(!json.get("formulas").arr()[0].has("value"));
}

#[test]
fn cli_exit_codes_for_rejected_documents() {
    let (code, stdout) = cli(&[
        "load",
        &fixture_path("reject-syntax-07-garbage-after-block.fi"),
    ]);
    assert_eq!(code, 3, "{stdout}");
    assert_eq!(
        parse_json(&stdout).get("error").get("kind").str(),
        Some("syntax")
    );
    let (code, stdout) = cli(&["load", &fixture_path("reject-load-02-unknown-backend.fi")]);
    assert_eq!(code, 7, "{stdout}");
    let error = parse_json(&stdout);
    assert_eq!(
        error.get("error").get("javaException").str(),
        Some("IllegalArgumentException")
    );
    // An external without a registered host object fails at evaluation (exit 5), not at load.
    let (code, stdout) = cli(&[
        "run",
        &repo_root()
            .join("src/test/resources/formulaInfo-test/69/formulaInfo.txt")
            .to_string_lossy(),
    ]);
    assert_eq!(code, 5, "{stdout}");
    assert_eq!(parse_json(&stdout).get("formulas").arr().len(), 10);
    let (code, _) = cli(&["load", "--default-backend", "nope", "x.fi"]);
    assert_eq!(code, 2);
}
