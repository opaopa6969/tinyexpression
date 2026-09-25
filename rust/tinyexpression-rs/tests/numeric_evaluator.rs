use std::io::Write;
use std::process::{Command, Output, Stdio};

use tinyexpression_rs::{evaluate, EvaluationError, Value};

fn cases() -> impl Iterator<Item = (&'static str, &'static str, u32)> {
    include_str!("fixtures/numeric-f32.tsv")
        .lines()
        .filter(|line| !line.is_empty() && !line.starts_with('#'))
        .map(|line| {
            let mut fields = line.splitn(3, '\t');
            let id = fields.next().expect("fixture id");
            let formula = fields.next().expect("fixture formula");
            let bits = u32::from_str_radix(fields.next().expect("fixture f32 bits"), 16)
                .expect("hex f32 bits");
            (id, formula, bits)
        })
}

fn run_with_stdin(arguments: &[&str], input: &str) -> Output {
    let mut child = Command::new(env!("CARGO_BIN_EXE_tinyexpression"))
        .args(arguments)
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .expect("start tinyexpression-rs");
    child
        .stdin
        .take()
        .expect("piped stdin")
        .write_all(input.as_bytes())
        .expect("write evaluator input");
    child.wait_with_output().expect("wait for evaluator")
}

#[test]
fn library_matches_independent_f32_bit_fixture() {
    for (id, formula, expected_bits) in cases() {
        let value = evaluate(formula).unwrap_or_else(|error| panic!("{id}: {error}"));
        assert_eq!(value.f32_bits(), expected_bits, "{id}: {formula}");
    }
}

#[test]
fn eval_cli_matches_independent_f32_bit_fixture() {
    for (id, formula, expected_bits) in cases() {
        let output = run_with_stdin(&["eval"], formula);
        assert_eq!(output.status.code(), Some(0), "{id}: {formula}");
        let stdout = String::from_utf8(output.stdout).expect("CLI output is UTF-8");
        let expected = format!(r#""f32Bits":"0x{expected_bits:08x}""#);
        assert!(stdout.contains(&expected), "{id}: {stdout}");
        assert!(output.stderr.is_empty(), "{id}");
    }
}

#[test]
fn public_value_exposes_number_and_stable_bits() {
    let value = evaluate("1+2").expect("evaluate arithmetic");
    assert_eq!(value, Value::Number(3.0));
    assert_eq!(value.as_f32(), 3.0);
    assert_eq!(
        value.canonical_json(),
        r#"{"kind":"number","value":"3","f32Bits":"0x40400000"}"#
    );
}

#[test]
fn scalar_value_variants_have_stable_accessors_and_json() {
    let boolean = evaluate("/*lead*/ true").expect("evaluate boolean");
    assert_eq!(boolean.boolean(), Some(true));
    assert_eq!(boolean.number(), None);
    assert_eq!(boolean.number_bits(), None);
    assert_eq!(
        boolean.canonical_json(),
        r#"{"kind":"boolean","value":true}"#
    );

    let string = evaluate("'hello'").expect("evaluate string");
    assert_eq!(string.string(), Some("hello"));
    assert_eq!(
        string.canonical_json(),
        r#"{"kind":"string","value":"hello"}"#
    );
}

#[test]
fn declarations_fail_as_context_dependent_without_evaluating_the_expression() {
    let error = evaluate("var $x as float set if not exists 1;\n$x+1")
        .expect_err("context-dependent declaration must fail");
    assert!(matches!(
        error,
        EvaluationError::ContextRequired {
            feature: "declarations",
            ..
        }
    ));
}

#[test]
fn eval_cli_returns_value_and_f32_bits_from_stdin() {
    let output = run_with_stdin(&["eval", "-"], "1+2*3");
    assert_eq!(output.status.code(), Some(0));
    assert_eq!(
        String::from_utf8(output.stdout).unwrap(),
        "{\"ok\":true,\"value\":{\"kind\":\"number\",\"value\":\"7\",\"f32Bits\":\"0x40e00000\"}}\n"
    );
    assert!(output.stderr.is_empty());
}

#[test]
fn eval_cli_reads_a_formula_file() {
    let fixture =
        std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join("tests/fixtures/valid-basic.tiny");
    let output = Command::new(env!("CARGO_BIN_EXE_tinyexpression"))
        .args(["eval", fixture.to_str().unwrap()])
        .output()
        .expect("run evaluator with file");

    assert_eq!(output.status.code(), Some(0));
    assert!(String::from_utf8(output.stdout)
        .unwrap()
        .contains(r#""f32Bits":"0x40400000""#));
    assert!(output.stderr.is_empty());
}

#[test]
fn eval_cli_reports_context_dependent_node_and_exit_five() {
    let output = run_with_stdin(&["eval"], "$missing");
    assert_eq!(output.status.code(), Some(5));
    let stdout = String::from_utf8(output.stdout).unwrap();
    assert!(stdout.starts_with(
        r#"{"ok":false,"stage":"evaluation","error":{"kind":"unsupported_node","span":[0,8],"node":"VariableRefExpr","message":"#
    ));
    assert!(output.stderr.is_empty());
}

#[test]
fn eval_cli_keeps_parse_failures_distinct_from_evaluation_failures() {
    let output = run_with_stdin(&["eval"], "1+");
    assert_eq!(output.status.code(), Some(3));
    assert!(String::from_utf8(output.stdout)
        .unwrap()
        .starts_with(r#"{"ok":false,"stage":"parse","diagnostic":"#));
}
