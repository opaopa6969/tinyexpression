use tinyexpression_rs::generated::{compat, ubnfc};
use tinyexpression_rs::{parse, Ast};

// Explicit non-root entries are an independent structural oracle, never production retries.
#[test]
fn explicit_hint_projection_retains_subtree_structure_and_cp_spans() {
    for line in include_str!("fixtures/root-family-projection.tsv")
        .lines()
        .filter(|line| !line.is_empty() && !line.starts_with('#'))
    {
        let fields: Vec<_> = line.split('\t').collect();
        assert_eq!(fields.len(), 4, "{line}");
        let source = fields[1];
        let result = ubnfc::parse_entry_with_options(
            "TinyExpressionP4",
            Some(fields[2]),
            source,
            ubnfc::ParseOptions::default(),
        )
        .unwrap();
        assert!(
            result.ok,
            "{} explicit entry: {:?}",
            fields[0], result.diagnostics
        );
        let expected = compat::convert(result.ast.as_ref().unwrap()).unwrap();
        let parsed = parse(source).unwrap_or_else(|error| panic!("{}: {error}", fields[0]));
        let Ast::FormulaExpr { expression, .. } = parsed else {
            panic!("{} Formula", fields[0]);
        };
        let Ast::ExpressionExpr { value, .. } = *expression else {
            panic!("{} Expression", fields[0]);
        };
        assert_eq!(*value, expected, "{} projected tree", fields[0]);
        parse("'👨‍👩‍👧‍👦' + 'x'").unwrap();
        assert_eq!(*value, expected, "{} retained tree", fields[0]);
    }
}

#[test]
fn identifier_alphabet_and_illegal_unicode_tail_are_not_broadened() {
    for source in ["$名 as string", "$𐐀 as string", "/*😀*/1<2@", ""] {
        let error = parse(source).unwrap_err();
        if source == "/*😀*/1<2@" {
            let tinyexpression_rs::FrontendError::Parse(diagnostic) = error else {
                panic!("syntax diagnostic");
            };
            assert_eq!(diagnostic.farthest.offset, 8, "code point failure position");
        }
    }
}

#[test]
fn complete_document_branches_keep_declarations_values_and_cp_positions() {
    use tinyexpression_rs::runtime::{
        Context, ContextClock, Host, NoExternals, Options, Program, ResultType, XorShiftRandom,
    };
    use tinyexpression_rs::{Span, Value};
    for line in include_str!("fixtures/root-document-dispatch.tsv")
        .lines()
        .filter(|line| !line.is_empty() && !line.starts_with('#'))
    {
        let fields: Vec<_> = line.split('\t').collect();
        assert_eq!(fields.len(), 9, "{line}");
        let source = fields[1];
        let result_type = if fields[2] == "string" {
            ResultType::String
        } else {
            ResultType::Boolean
        };
        let expected = if fields[2] == "string" {
            Value::String(fields[3].to_owned())
        } else {
            Value::Boolean(fields[3] == "true")
        };
        let program = Program::new(source, Options::new(result_type)).unwrap();
        let Ast::FormulaExpr {
            declarations,
            expression,
            methods,
            span,
            ..
        } = program.root().unwrap()
        else {
            panic!("{} Formula", fields[0]);
        };
        assert_eq!(
            declarations.len(),
            fields[7].parse::<usize>().unwrap(),
            "{} declarations",
            fields[0]
        );
        assert_eq!(
            methods.len(),
            fields[8].parse::<usize>().unwrap(),
            "{} methods",
            fields[0]
        );
        assert_eq!(
            *span,
            Span {
                start: 0,
                end: source.chars().count()
            },
            "{} Formula span",
            fields[0]
        );
        assert_eq!(
            expression.span(),
            Span {
                start: fields[5].parse().unwrap(),
                end: fields[6].parse().unwrap()
            },
            "{} expression span",
            fields[0]
        );
        let Ast::ExpressionExpr { value, .. } = expression.as_ref() else {
            panic!("{} Expression", fields[0]);
        };
        assert!(
            value
                .canonical_json()
                .starts_with(&format!("{{\"type\":\"{}\"", fields[4])),
            "{} value",
            fields[0]
        );
        let mut external = NoExternals;
        let mut random = XorShiftRandom::new(1);
        let mut host = Host {
            external: &mut external,
            clock: &ContextClock,
            random: &mut random,
        };
        parse("'different😀'").unwrap();
        assert_eq!(
            program.eval_tree(&mut Context::new(), &mut host).unwrap(),
            expected,
            "{} walker",
            fields[0]
        );
        assert_eq!(
            program
                .compile()
                .eval(&mut Context::new(), &mut host)
                .unwrap(),
            expected,
            "{} compiled",
            fields[0]
        );
    }
}

#[test]
fn losing_formula_branches_do_not_duplicate_or_leak_scope_metadata() {
    for source in [
        "var $n as number set 1;$n<2",
        "var $n as number set 1;$n<2 @",
        "var $n as number set 1;$n<2 float unused($x as number){$x} @",
    ] {
        let result = ubnfc::parse_entry_with_options(
            "TinyExpressionP4",
            None,
            source,
            ubnfc::ParseOptions {
                occurrences: true,
                ..ubnfc::ParseOptions::default()
            },
        )
        .unwrap();
        assert_eq!(result.ok, !source.ends_with('@'), "{source}");
        assert_eq!(
            result.declarations.len(),
            usize::from(result.ok),
            "{source} declarations"
        );
        assert_eq!(
            result.references.len(),
            usize::from(result.ok),
            "{source} references"
        );
        assert_eq!(result.ast.is_some(), result.ok, "{source} AST");
        assert_eq!(
            result
                .captures
                .iter()
                .filter(|capture| capture.name == "expression")
                .count(),
            usize::from(result.ok),
            "{source} expression captures"
        );
        if !result.ok {
            assert!(result.captures.is_empty(), "{source} speculative captures");
        }
    }
}
