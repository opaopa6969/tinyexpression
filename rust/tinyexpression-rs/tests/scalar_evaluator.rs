use std::io::Write;
use std::process::{Command, Stdio};

use tinyexpression_rs::{evaluate, Value};

#[derive(Debug)]
struct Case<'a> {
    id: &'a str,
    kind: &'a str,
    formula: &'a str,
    outcome: &'a str,
    expected: &'a str,
}

fn cases() -> impl Iterator<Item = Case<'static>> {
    include_str!("fixtures/scalar-control.tsv")
        .lines()
        .filter(|line| !line.is_empty() && !line.starts_with('#'))
        .map(|line| {
            let fields: Vec<_> = line.split('\t').collect();
            assert_eq!(fields.len(), 5, "invalid fixture row: {line}");
            Case {
                id: fields[0],
                kind: fields[1],
                formula: fields[2],
                outcome: fields[3],
                expected: fields[4],
            }
        })
}

#[test]
fn library_matches_java_scalar_control_fixture() {
    for case in cases() {
        if case.outcome == "error" {
            let output = eval_cli(case.formula);
            assert_eq!(
                output.status.code(),
                Some(5),
                "{}: {}",
                case.id,
                case.formula
            );
            continue;
        }

        let result = evaluate(case.formula);
        let value = result.unwrap_or_else(|error| panic!("{}: {error}", case.id));
        match (case.kind, value) {
            ("float", Value::Number(actual)) => {
                let expected = u32::from_str_radix(case.expected, 16).expect("fixture f32 bits");
                assert_eq!(actual.to_bits(), expected, "{}: {}", case.id, case.formula);
            }
            ("boolean", Value::Boolean(actual)) => assert_eq!(
                actual,
                case.expected.parse::<bool>().expect("fixture boolean"),
                "{}: {}",
                case.id,
                case.formula
            ),
            ("string", Value::String(actual)) => {
                assert_eq!(actual, case.expected, "{}: {}", case.id, case.formula);
            }
            (expected, actual) => panic!(
                "{}: expected {expected}, got {actual:?} for {}",
                case.id, case.formula
            ),
        }
    }
}

fn eval_cli(formula: &str) -> std::process::Output {
    let mut child = Command::new(env!("CARGO_BIN_EXE_tinyexpression"))
        .arg("eval")
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .expect("start evaluator CLI");
    child
        .stdin
        .take()
        .expect("piped stdin")
        .write_all(formula.as_bytes())
        .expect("write formula");
    child.wait_with_output().expect("wait for evaluator CLI")
}

#[test]
fn cli_serializes_each_scalar_kind() {
    for (formula, expected) in [
        (
            "7",
            r#"{"kind":"number","value":"7","f32Bits":"0x40e00000"}"#,
        ),
        ("true", r#"{"kind":"boolean","value":true}"#),
        ("'hello'", r#"{"kind":"string","value":"hello"}"#),
    ] {
        let output = eval_cli(formula);
        assert_eq!(output.status.code(), Some(0), "{formula}");
        assert!(output.stderr.is_empty(), "{formula}");
        assert!(
            String::from_utf8(output.stdout)
                .expect("UTF-8 output")
                .contains(expected),
            "{formula}"
        );
    }
}

#[test]
fn boolean_operators_are_eager_but_control_flow_is_lazy() {
    for formula in [
        "true|isPresent($missing)",
        "false&isPresent($missing)",
        "true^isPresent($missing)",
    ] {
        let error = evaluate(formula).expect_err("boolean operand must be evaluated");
        assert_eq!(error.kind(), "unsupported_node", "{formula}");
    }

    assert_eq!(
        evaluate("if(true){1}else{$missing}"),
        Ok(Value::Number(1.0))
    );
    assert_eq!(evaluate("(true?2:$missing)"), Ok(Value::Number(2.0)));
    assert_eq!(
        evaluate("match{true->3,false->$missing,default->$missing}"),
        Ok(Value::Number(3.0))
    );
}
