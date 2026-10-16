//! Production facade acceptance and strict sub-second regression guard (Tiny #257).
//! This is ubnfc production evidence, not unlaxer Classic SAFE calibration.
use std::fmt::Write;
use std::fs;
use std::time::Instant;
use tinyexpression_rs::generated::ast::AstValue;
use tinyexpression_rs::{parse, Ast, FrontendError, Span};

const INPUTS: &str = include_str!("fixtures/fraud-alert.tsv");
const LENGTHS: [usize; 5] = [404, 209, 68, 707, 459];

fn conditional_view<'a>(source: &str, raw: &'a Ast) -> &'a Ast {
    let full = Span {
        start: 0,
        end: source.chars().count(),
    };
    let mut ast = raw;
    loop {
        assert_eq!(
            ast.span(),
            full,
            "transparent wrapper must own the complete input"
        );
        ast = match ast {
            Ast::IfExpr { .. } => return ast,
            Ast::FormulaExpr { imports, declarations, expression, methods, codeBlocks, .. }
                if imports.is_empty() && declarations.is_empty() && methods.is_empty() && codeBlocks.is_empty() => expression,
            Ast::ExpressionExpr { value, .. } => value,
            Ast::BinaryExpr { left: AstValue::Node(node), op, right, .. }
                if op.is_empty() && right.is_empty() => node,
            _ => panic!("production result is not a complete conditional or its documented transparent wrappers"),
        };
    }
}

#[test]
fn fraud_formulas_are_accepted_and_mapped_within_one_second() {
    let output = std::env::var_os("FRAUD_OUTPUT")
        .map(std::path::PathBuf::from)
        .unwrap_or_else(|| {
            std::path::Path::new(env!("CARGO_MANIFEST_DIR"))
                .join("../../target/fraud-acceptance/rust.jsonl")
        });
    fs::create_dir_all(output.parent().unwrap()).unwrap();
    // Truncate first: stale green output must not survive a failed run.
    fs::write(&output, "").unwrap();
    let inputs: Vec<_> = INPUTS
        .lines()
        .filter(|line| !line.is_empty() && !line.starts_with('#'))
        .collect();
    assert_eq!(inputs.len(), 5);
    let selected_id = std::env::var("FRAUD_ID").ok();
    if let Some(id) = &selected_id {
        assert!(
            inputs
                .iter()
                .any(|line| line.starts_with(&format!("{id}\t"))),
            "unknown fraud id"
        );
    }
    let mut records = String::new();
    for (index, row) in inputs.into_iter().enumerate() {
        let (id, source) = row.split_once('\t').expect("id/formula");
        if selected_id
            .as_deref()
            .is_some_and(|selected| selected != id)
        {
            continue;
        }
        assert_eq!(source.chars().count(), LENGTHS[index]);
        let mut previous = None;
        for phase in ["cold", "warmup", "warm"] {
            // Serialization, validation and I/O do not enter parse+map timing.
            let start = Instant::now();
            let ast = parse(source).expect("fraud production parse+map must succeed");
            let elapsed = start.elapsed().as_nanos();
            let selected = conditional_view(source, &ast);
            let canonical = selected.canonical_json();
            if let Some(previous) = &previous {
                assert_eq!(previous, &canonical, "phase changed AST/spans");
            }
            previous = Some(canonical.clone());
            let first_observation_in_process = records.is_empty();
            let pid = std::process::id();
            let input_hex = source
                .as_bytes()
                .iter()
                .fold(String::new(), |mut out, byte| {
                    write!(out, "{byte:02x}").unwrap();
                    out
                });
            records.push_str(&format!(
                "{{\"id\":\"{id}\",\"phase\":\"{phase}\",\"backend\":\"rust-ubnfc\",\"pid\":{pid},\"first_observation_in_process\":{first_observation_in_process},\"entry\":\"tinyexpression_rs::parse\",\"memo\":\"ubnfc-default-packrat\",\"input_utf8_hex\":\"{input_hex}\",\"observations\":{{\"diagnostics\":false,\"occurrences\":false}},\"elapsed_ns\":{elapsed},\"cp\":{},\"accepted\":true,\"diagnostic\":null,\"ast\":{canonical},\"raw_ast\":{}}}\n", LENGTHS[index], ast.canonical_json()));
            fs::write(&output, &records).unwrap();
            eprintln!("fraud production {id} {phase} {elapsed} ns");
            assert!(
                elapsed < 1_000_000_000,
                "{id} {phase}: expected < 1 second, got {elapsed} ns"
            );
        }
    }
}

#[test]
fn rejected_and_partial_inputs_cannot_count_as_fast_success() {
    let source = "if(true){1}else{0}";
    conditional_view(source, &parse(source).unwrap());
    assert!(matches!(
        parse(&format!("{source}@")),
        Err(FrontendError::Parse(_))
    ));
    assert!(matches!(parse("@"), Err(FrontendError::Parse(_))));
}
