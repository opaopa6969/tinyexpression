use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::{Command, Output, Stdio};

use tinyexpression_rs::generated::parser::CATALOGS;
use tinyexpression_rs::{parse, Ast, FrontendError};

fn fixture(name: &str) -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR"))
        .join("tests")
        .join("fixtures")
        .join(name)
}

fn run_with_stdin(arguments: &[&str], input: &str) -> Output {
    let mut child = Command::new(env!("CARGO_BIN_EXE_tinyexpression-rs"))
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
        .expect("write parser input");
    child.wait_with_output().expect("wait for parser")
}

fn stdout(output: &Output) -> String {
    String::from_utf8(output.stdout.clone()).expect("stdout is UTF-8")
}

fn stderr(output: &Output) -> String {
    String::from_utf8(output.stderr.clone()).expect("stderr is UTF-8")
}

#[test]
fn library_parses_valid_formula_into_formula_root() {
    let source = include_str!("fixtures/valid-basic.tiny");
    let ast = parse(source).expect("valid formula");

    match &ast {
        Ast::r#FormulaExpr {
            imports,
            declarations,
            expression: _,
            methods,
            ..
        } => {
            assert!(imports.is_empty());
            assert!(declarations.is_empty());
            assert!(methods.is_empty());
        }
        other => panic!("expected FormulaExpr root, got {other:?}"),
    }
    assert_eq!(ast.span().start, 0);
    assert_eq!(ast.span().end, source.chars().count());
    assert!(ast.canonical_json().starts_with(r#"{"type":"FormulaExpr""#));
}

#[test]
fn library_returns_structured_parse_error() {
    let error =
        parse(include_str!("fixtures/invalid-syntax.tiny")).expect_err("invalid formula must fail");

    let FrontendError::Parse(diagnostic) = error else {
        panic!("syntax failure must not be reported as a mapping failure");
    };
    assert!(matches!(diagnostic.kind, "syntax" | "trailing_input"));
    assert!(!diagnostic.expected.is_empty());
    assert!(!diagnostic.farthest.expected.is_empty());
    assert!(diagnostic.canonical_json().starts_with(r#"{"kind":"#));
}

#[test]
fn unicode_spans_are_code_point_offsets() {
    let source = include_str!("fixtures/valid-unicode.tiny");
    let ast = parse(source).expect("Unicode string formula");

    assert_eq!(ast.span().end, source.chars().count());
    assert_ne!(
        ast.span().end,
        source.len(),
        "fixture must distinguish code points from bytes"
    );
    assert!(ast.canonical_json().contains("こんにちは😀"));
}

#[test]
fn library_parses_multiline_document() {
    let source = include_str!("fixtures/valid-multiline.tiny");
    let ast = parse(source).expect("multiline document");

    let Ast::r#FormulaExpr { declarations, .. } = ast else {
        panic!("expected FormulaExpr root");
    };
    assert_eq!(declarations.len(), 1);
}

#[test]
fn cli_reads_stdin_when_parse_has_no_file_argument() {
    let output = run_with_stdin(&["parse"], include_str!("fixtures/valid-basic.tiny"));

    assert_eq!(output.status.code(), Some(0), "stderr: {}", stderr(&output));
    let response = stdout(&output);
    assert!(response.starts_with(r#"{"ok":true,"ast":{"type":"FormulaExpr""#));
    assert!(response.ends_with("}\n"));
    assert!(stderr(&output).is_empty());
}

#[test]
fn cli_accepts_explicit_stdin_marker() {
    let output = run_with_stdin(&["parse", "-"], include_str!("fixtures/valid-unicode.tiny"));

    assert_eq!(output.status.code(), Some(0), "stderr: {}", stderr(&output));
    assert!(stdout(&output).contains("こんにちは😀"));
}

#[test]
fn cli_reads_multiline_formula_from_file() {
    let output = Command::new(env!("CARGO_BIN_EXE_tinyexpression-rs"))
        .args(["parse", fixture("valid-multiline.tiny").to_str().unwrap()])
        .output()
        .expect("run parser with a file");

    assert_eq!(output.status.code(), Some(0), "stderr: {}", stderr(&output));
    assert!(stdout(&output).starts_with(r#"{"ok":true,"ast":{"type":"FormulaExpr""#));
}

#[test]
fn cli_reports_parse_failure_as_json_and_exit_three() {
    let output = Command::new(env!("CARGO_BIN_EXE_tinyexpression-rs"))
        .args(["parse", fixture("invalid-syntax.tiny").to_str().unwrap()])
        .output()
        .expect("run parser with invalid input");

    assert_eq!(output.status.code(), Some(3));
    let response = stdout(&output);
    assert!(response.starts_with(r#"{"ok":false,"stage":"parse","diagnostic":{"kind":"#));
    assert!(response.contains(r#""farthestOffset":"#));
    assert!(stderr(&output).is_empty());
}

#[test]
fn cli_reports_usage_errors_on_stderr_and_exit_two() {
    let output = Command::new(env!("CARGO_BIN_EXE_tinyexpression-rs"))
        .output()
        .expect("run parser without arguments");

    assert_eq!(output.status.code(), Some(2));
    assert!(stdout(&output).is_empty());
    assert_eq!(
        stderr(&output),
        "usage: tinyexpression-rs <parse|eval> [FILE|-]\n"
    );
}

#[test]
fn cli_reports_file_io_errors_as_json_and_exit_six() {
    let missing = fixture("does-not-exist.tiny");
    let output = Command::new(env!("CARGO_BIN_EXE_tinyexpression-rs"))
        .args(["parse", missing.to_str().unwrap()])
        .output()
        .expect("run parser with missing file");

    assert_eq!(output.status.code(), Some(6));
    assert!(stdout(&output).starts_with(r#"{"ok":false,"stage":"io","message":"#));
    assert!(stderr(&output).is_empty());
}

#[test]
fn generated_catalog_metadata_exposes_variable_context() {
    assert_eq!(CATALOGS.len(), 1);
    assert_eq!(CATALOGS[0].rule, "VariableRef");
    assert_eq!(CATALOGS[0].context, "variable");
    assert_eq!(CATALOGS[0].captures, &["name", "type"]);
}
