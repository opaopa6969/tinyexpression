use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::{Command, Output, Stdio};
use std::sync::mpsc;
use std::time::Duration;

use tinyexpression_rs::generated::ubnfc::metadata::CATALOGS;
use tinyexpression_rs::generated::ubnfc::ParseOptions;
use tinyexpression_rs::{
    parse, parse_formula_root, parse_formula_root_with_options, Ast, FrontendError,
};

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
fn library_retries_bare_boolean_comparisons_without_changing_numeric_root_selection() {
    for source in ["1<2", "1==1", "1+2*3>=7", "1<2&2<3", "1>2|3>2"] {
        let ast = parse(source).unwrap_or_else(|error| panic!("{source}: {error}"));
        assert_eq!(ast.span().end, source.chars().count(), "{source}");
        assert!(ast.canonical_json().contains("ComparisonExpr"), "{source}");
    }

    let arithmetic = parse("$a+$b").expect("numeric variable expression");
    let Ast::r#FormulaExpr { expression, .. } = arithmetic else {
        panic!("expected FormulaExpr");
    };
    let Ast::r#ExpressionExpr { value, .. } = *expression else {
        panic!("expected ExpressionExpr");
    };
    assert!(matches!(*value, Ast::r#BinaryExpr { .. }));
}

#[test]
fn library_matches_java_root_expression_fixture() {
    for line in include_str!("fixtures/root-expression.tsv")
        .lines()
        .filter(|line| !line.is_empty() && !line.starts_with('#'))
    {
        let fields: Vec<_> = line.split('\t').collect();
        assert_eq!(fields.len(), 5, "invalid fixture row: {line}");
        let (id, source, expected_root, expected_node) =
            (fields[0], fields[1], fields[3], fields[4]);
        let ast = parse(source).unwrap_or_else(|error| panic!("{id}: {source}: {error}"));
        assert_eq!(ast.span().start, 0, "{id}");
        assert_eq!(ast.span().end, source.chars().count(), "{id}");
        let Ast::r#FormulaExpr { expression, .. } = &ast else {
            panic!("{id}: expected FormulaExpr");
        };
        let Ast::r#ExpressionExpr { value, .. } = expression.as_ref() else {
            panic!("{id}: expected ExpressionExpr");
        };
        assert!(
            value
                .canonical_json()
                .starts_with(&format!(r#"{{"type":"{expected_root}""#)),
            "{id}: expected direct root {expected_root}, got {}",
            value.canonical_json()
        );
        assert!(
            ast.canonical_json().contains(expected_node),
            "{id}: expected {expected_node} in {}",
            ast.canonical_json()
        );
    }
}

#[test]
fn boolean_match_maps_on_an_explicit_two_mib_thread_stack() {
    const SOURCE: &str = "match{true->true,false->$missing,default->$missing}";
    let ast = std::thread::Builder::new()
        .name("tinyexpression-small-stack".to_owned())
        .stack_size(2 * 1024 * 1024)
        .spawn(|| parse(SOURCE))
        .expect("spawn 2 MiB parser thread")
        .join()
        .expect("parser thread must not overflow")
        .expect("boolean match must parse and map");
    assert_eq!(ast.span().start, 0);
    assert_eq!(ast.span().end, SOURCE.chars().count());
    assert!(ast.canonical_json().contains("BooleanMatchExpr"));
}

#[test]
fn document_family_reselection_keeps_absolute_expression_spans() {
    let source = "var $s as string;/*😀*/$s as string string part(){'x'}";
    let ast = parse(source).expect("typed document");
    let Ast::r#FormulaExpr { expression, .. } = ast else {
        panic!("expected FormulaExpr");
    };
    let Ast::r#ExpressionExpr { span, value } = *expression else {
        panic!("expected ExpressionExpr");
    };
    let expression_text: String = source.chars().collect::<Vec<_>>()[span.start..span.end]
        .iter()
        .collect();
    assert_eq!(expression_text.trim(), "$s as string");
    let Ast::r#StringConcatExpr { ref r#left, .. } = *value else {
        panic!("expected StringConcatExpr");
    };
    assert_eq!(value.span(), span);
    assert_eq!(r#left.span(), span);
}

#[test]
fn library_rejects_mixed_match_hint_families_explicitly() {
    for line in include_str!("fixtures/typed-hint-errors.tsv")
        .lines()
        .filter(|line| !line.is_empty() && !line.starts_with('#'))
    {
        let fields: Vec<_> = line.split('\t').collect();
        assert_eq!(fields.len(), 2, "invalid fixture row: {line}");
        let (id, source) = (fields[0], fields[1]);
        let error = match parse(source) {
            Ok(ast) => panic!("{id}: expected error, got {ast:?}"),
            Err(error) => error,
        };
        let FrontendError::TypeMismatch { message, span } = error else {
            panic!("{id}: expected explicit type mismatch");
        };
        assert!(message.contains("match type mismatch"), "{id}: {message}");
        assert_eq!((span.start, span.end), (0, source.chars().count()), "{id}");

        let output = run_with_stdin(&["parse"], source);
        assert_eq!(output.status.code(), Some(4), "{id}: {}", stderr(&output));
        assert!(stdout(&output).contains(r#""stage":"type""#), "{id}");

        let output = run_with_stdin(&["eval"], source);
        assert_eq!(output.status.code(), Some(4), "{id}: {}", stderr(&output));
        let response = stdout(&output);
        assert!(response.contains(r#""stage":"type""#), "{id}: {response}");
        assert!(
            response.contains(r#""kind":"type_mismatch""#),
            "{id}: {response}"
        );
    }
}

#[test]
fn strict_match_nodes_use_owned_unicode_safe_spans() {
    for line in include_str!("fixtures/strict-match-errors.tsv")
        .lines()
        .filter(|line| !line.is_empty() && !line.starts_with('#'))
    {
        let fields: Vec<_> = line.split('\t').collect();
        assert_eq!(fields.len(), 3, "invalid fixture row: {line}");
        let (id, source, expected_snippet) = (fields[0], fields[1], fields[2]);
        let ast = parse(source).unwrap_or_else(|error| panic!("{id}: {error}"));
        let start = source[..source.find(expected_snippet).unwrap()]
            .chars()
            .count();
        let end = start + expected_snippet.chars().count();
        let canonical = ast.canonical_json();
        assert!(
            canonical.contains(&format!(
                r#""type":"MethodInvocationExpr","span":[{start},{end}]"#
            )),
            "{id}: {canonical}"
        );
        let chars: Vec<_> = source.chars().collect();
        let snippet: String = chars[start..end].iter().collect();
        assert_eq!(snippet, expected_snippet, "{id}");
    }
}

#[test]
fn fallback_preserves_formula_and_expression_span_contract() {
    for (source, expression_start) in [("/*lead*/1<2/*tail*/", 8), ("/*😀*/1<2/*終*/", 5)] {
        let ast = parse(source).expect("commented bare comparison");
        let Ast::r#FormulaExpr {
            span, expression, ..
        } = ast
        else {
            panic!("expected FormulaExpr");
        };
        assert_eq!((span.start, span.end), (0, source.chars().count()));
        let Ast::r#ExpressionExpr { span, value } = *expression else {
            panic!("expected ExpressionExpr");
        };
        assert_eq!(
            (span.start, span.end),
            (expression_start, source.chars().count())
        );
        assert_eq!(value.span(), span);
    }
}

#[test]
fn failed_boolean_retry_keeps_the_primary_formula_diagnostic() {
    let source = "1<";
    let unmemoized = ParseOptions {
        memo: false,
        ..ParseOptions::default()
    };
    let FrontendError::Parse(primary) =
        parse_formula_root_with_options(source, unmemoized).expect_err("primary parse must fail")
    else {
        panic!("expected parse diagnostic");
    };
    let FrontendError::Parse(memoized) =
        parse_formula_root(source).expect_err("memoized primary Formula parse must fail")
    else {
        panic!("expected parse diagnostic");
    };
    let FrontendError::Parse(actual) = parse(source).expect_err("invalid comparison must fail")
    else {
        panic!("expected parse diagnostic");
    };
    assert_eq!(memoized, primary);
    assert_eq!(actual, primary);
}

#[test]
fn invalid_nested_ternary_returns_a_diagnostic_within_two_seconds() {
    const SOURCE: &str = "(true ? (false ? 1 : 2 : 3)";
    let (sender, receiver) = mpsc::sync_channel(1);
    let parser = std::thread::spawn(move || sender.send(parse(SOURCE)));
    let result = receiver
        .recv_timeout(Duration::from_secs(2))
        .expect("memoized invalid parse exceeded two seconds");
    parser
        .join()
        .expect("invalid nested ternary parser thread panicked")
        .expect("result receiver remains alive");
    let FrontendError::Parse(diagnostic) = result.expect_err("nested ternary must be rejected")
    else {
        panic!("expected parse diagnostic");
    };
    assert!(!diagnostic.farthest.expected.is_empty());
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
