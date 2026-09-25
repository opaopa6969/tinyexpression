//! Evaluation trace (issue #201, stage 3): `Program::eval_tree_traced`, `api::eval_trace_json`
//! (`te_eval_trace`) and `tinyexpression eval --trace` / `eval-context --trace`.

use std::io::Write;
use std::process::{Command, Stdio};

use tinyexpression_rs::api;
use tinyexpression_rs::runtime::{
    Context, ContextClock, Host, NoExternals, Options, Program, ResultType, TraceRecorder,
    XorShiftRandom,
};
use tinyexpression_rs::Value;

fn traced(formula: &str, options: Options, context: &mut Context) -> (Value, TraceRecorder) {
    let program = Program::new(formula, options).unwrap();
    let mut external = NoExternals;
    let mut random = XorShiftRandom::new(1);
    let mut host = Host {
        external: &mut external,
        clock: &ContextClock,
        random: &mut random,
    };
    let mut recorder = TraceRecorder::default();
    let value = program
        .eval_tree_traced(context, &mut host, &mut recorder)
        .unwrap();
    let untraced = {
        let mut external = NoExternals;
        let mut random = XorShiftRandom::new(1);
        let mut host = Host {
            external: &mut external,
            clock: &ContextClock,
            random: &mut random,
        };
        program.eval_tree(&mut context.clone(), &mut host).unwrap()
    };
    assert_eq!(value, untraced);
    (value, recorder)
}

#[test]
fn records_every_step_with_kind_span_and_value() {
    let mut context = Context::new();
    context.set_float("a", 3.0);
    let formula = "if($a > 1){ $a * 2 + 1 }else{ 0 }";
    let (value, recorder) = traced(formula, Options::new(ResultType::Float), &mut context);
    assert_eq!(value, Value::Number(7.0));
    let nodes = recorder.nodes();
    let root = &nodes[recorder.roots()[0]];
    assert_eq!(root.kind, "IfExpr");
    assert_eq!(
        (root.span.start, root.span.end),
        (0, formula.chars().count())
    );
    assert_eq!(root.outcome, Some(Ok(Value::Number(7.0))));
    // The comparison and the literal leaves of `$a * 2 + 1` are steps of their own.
    let comparison = nodes.iter().find(|n| n.kind == "ComparisonExpr").unwrap();
    assert_eq!(comparison.outcome, Some(Ok(Value::Boolean(true))));
    let leaves: Vec<_> = nodes
        .iter()
        .filter_map(|n| n.leaf.as_deref().map(|leaf| (leaf, n.outcome.clone())))
        .collect();
    assert!(
        leaves.contains(&("2", Some(Ok(Value::Number(2.0))))),
        "{leaves:?}"
    );
    assert!(
        leaves.contains(&("1", Some(Ok(Value::Number(1.0))))),
        "{leaves:?}"
    );
    // The else branch is not evaluated, so it is not in the trace.
    assert!(!nodes.iter().any(|n| n.leaf.as_deref() == Some("0")));
    assert!(!recorder.truncated());
    assert_eq!(recorder.steps(), nodes.len());
}

#[test]
fn bounds_the_recorded_steps() {
    let program = Program::new("1+1+1+1+1+1+1+1", Options::default()).unwrap();
    let mut external = NoExternals;
    let mut random = XorShiftRandom::new(1);
    let mut host = Host {
        external: &mut external,
        clock: &ContextClock,
        random: &mut random,
    };
    let mut recorder = TraceRecorder::new(3);
    let value = program
        .eval_tree_traced(&mut Context::new(), &mut host, &mut recorder)
        .unwrap();
    assert_eq!(value, Value::Number(8.0));
    assert_eq!(recorder.nodes().len(), 3);
    assert!(recorder.truncated());
    assert!(recorder.to_json().contains("\"truncated\":true"));
}

#[test]
fn trace_json_adds_the_trace_to_the_eval_context_response() {
    let request =
        r#"{"formula":"$price * 2","variables":[{"name":"price","type":"float","value":"1.5"}]}"#;
    let plain = api::eval_context_json(request);
    let traced = api::eval_trace_json(request);
    assert_eq!(plain.exit_code, traced.exit_code);
    let prefix = &plain.json[..plain.json.len() - 1];
    assert!(traced.json.starts_with(prefix), "{}", traced.json);
    assert!(traced.json.contains(",\"trace\":{\"steps\":"));
    assert!(traced.json.contains("\"leaf\":\"2\""), "{}", traced.json);
    assert!(traced.json.contains("\"span\":[0,10]"), "{}", traced.json);
}

#[test]
fn a_failing_step_carries_the_error() {
    let request = r#"{"formula":"1 + 10 / $zero","resultType":"int","numberType":"int","variables":[{"name":"zero","type":"int","value":"0"}]}"#;
    let traced = api::eval_trace_json(request);
    assert_eq!(traced.exit_code, api::EXIT_EVALUATION);
    assert!(traced.json.starts_with(
        "{\"ok\":false,\"stage\":\"apply\",\"error\":{\"kind\":\"ArithmeticException\""
    ));
    assert!(
        traced
            .json
            .contains("\"span\":[4,14],\"error\":{\"kind\":\"ArithmeticException\""),
        "{}",
        traced.json
    );
    let parse = api::eval_trace_json(r#"{"formula":"1 +"}"#);
    assert_eq!(parse.exit_code, api::EXIT_PARSE);
    assert!(parse.json.ends_with(",\"trace\":null}"), "{}", parse.json);
}

fn cli(arguments: &[&str], input: &str) -> (i32, String) {
    let mut child = Command::new(env!("CARGO_BIN_EXE_tinyexpression"))
        .args(arguments)
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .spawn()
        .unwrap();
    child
        .stdin
        .take()
        .unwrap()
        .write_all(input.as_bytes())
        .unwrap();
    let output = child.wait_with_output().unwrap();
    (
        output.status.code().unwrap(),
        String::from_utf8(output.stdout).unwrap(),
    )
}

#[test]
fn cli_eval_trace() {
    let (code, json) = cli(&["eval", "--trace", "-"], "(1 + 2) * 3");
    assert_eq!(code, 0);
    assert_eq!(
        json.trim(),
        api::eval_formula_trace_json("(1 + 2) * 3").json
    );
    assert!(json.contains("\"text\":\"9.0\",\"trace\":{"), "{json}");
    let request = r#"{"formula":"$x + 1","variables":[{"name":"x","type":"int","value":"2"}],"numberType":"int","resultType":"int"}"#;
    let (code, json) = cli(&["eval-context", "--trace"], request);
    assert_eq!(code, 0);
    assert_eq!(json.trim(), api::eval_trace_json(request).json);
}
