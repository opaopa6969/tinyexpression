//! Same positive production files as the Java default mapper regression (Tiny #259).
//! Rust retains Formula/Expression wrappers; the Java facade projects bare comparisons.
use std::{fs, path::Path};
use tinyexpression_rs::{parse, Ast, Span};

#[test]
fn large_default_inputs_keep_owned_full_source_results_and_reject_partial_input() {
    let root = Path::new(env!("CARGO_MANIFEST_DIR")).join("../..");
    for line in include_str!("fixtures/default-mapping-inputs.tsv")
        .lines()
        .filter(|line| !line.is_empty() && !line.starts_with('#'))
    {
        let fields: Vec<_> = line.split('\t').collect();
        assert_eq!(fields.len(), 8);
        let source = fs::read_to_string(root.join(fields[1])).unwrap();
        let cp: usize = fields[3].parse().unwrap();
        assert_eq!(source.chars().count(), cp, "{} source identity", fields[0]);
        let ast = parse(&source).unwrap_or_else(|error| panic!("{}: {error}", fields[0]));
        assert_eq!(ast.span(), Span { start: 0, end: cp });
        assert!(ast
            .canonical_json()
            .starts_with(&format!("{{\"type\":\"{}\"", fields[6])));
        let Ast::FormulaExpr { expression, .. } = &ast else {
            panic!("{} expected owned Formula", fields[0]);
        };
        let Ast::ExpressionExpr { value, .. } = expression.as_ref() else {
            panic!("{} expected owned Expression", fields[0]);
        };
        assert!(value
            .canonical_json()
            .starts_with(&format!("{{\"type\":\"{}\"", fields[7])));
        let retained = ast.canonical_json();
        assert!(parse("1+2@").is_err(), "{} partial parse", fields[0]);
        parse("'different😀'").unwrap();
        assert_eq!(
            ast.canonical_json(),
            retained,
            "{} retained AST/spans",
            fields[0]
        );
    }
}
