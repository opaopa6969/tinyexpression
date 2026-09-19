//! Native parser frontend generated from the authoritative TinyExpression P4 grammar.
//!
//! Parsing, mapping, and the context-free f32 evaluator are strict: no Java parser,
//! handwritten parser, or evaluator fallback is attempted.

#[rustfmt::skip]
pub mod generated;
mod evaluator;

use std::error::Error;
use std::fmt::{self, Display, Formatter};

pub use evaluator::{evaluate, evaluate_ast, EvaluationError, Value};
pub use generated::ast::Ast;
use unlaxer_runtime::{parse_detailed_shared, ParseDiagnostic, Span};

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum FrontendError {
    Parse(ParseDiagnostic),
    Mapping(String),
}

impl Display for FrontendError {
    fn fmt(&self, formatter: &mut Formatter<'_>) -> fmt::Result {
        match self {
            Self::Parse(error) => Display::fmt(error, formatter),
            Self::Mapping(error) => write!(formatter, "AST mapping failed: {error}"),
        }
    }
}

impl Error for FrontendError {}

/// Parses the complete UTF-8 source and returns an owned, source-preserving typed AST.
pub fn parse(source: &str) -> Result<Ast, FrontendError> {
    match generated::parser::parse_tree_detailed(source) {
        Ok(tree) => generated::mapper::map(&tree).map_err(FrontendError::Mapping),
        Err(primary) => parse_boolean_root(source).unwrap_or(Err(FrontendError::Parse(primary))),
    }
}

/// Mirrors Java's `P4PreferredAstMapper` compatibility retry for PEG root dispatch.
///
/// `Expression` intentionally tries `NumberExpression` before `BooleanExpression` so that
/// arithmetic such as `$a+$b` is consumed as one expression. A bare comparison such as `1<2`
/// therefore commits the numeric prefix `1`, and the enclosing `Formula` subsequently fails at
/// EOF. Retrying only after that failure keeps the successful numeric hot path unchanged while
/// accepting the complete boolean expression.
fn parse_boolean_root(source: &str) -> Option<Result<Ast, FrontendError>> {
    let grammar = generated::parser::grammar();
    let boolean_root = grammar
        .iter()
        .position(|rule| rule.name == "BooleanExpression")?;
    let tree = parse_detailed_shared(grammar, boolean_root, true, source).ok()?;
    Some(
        generated::mapper::map(&tree)
            .map(|value| wrap_expression_root(value, source.chars().count()))
            .map_err(FrontendError::Mapping),
    )
}

fn wrap_expression_root(value: Ast, source_len: usize) -> Ast {
    let value = match value {
        Ast::r#BooleanOrExpr {
            span,
            r#left,
            r#op,
            r#right,
        } => Ast::r#BooleanOrExpr {
            span: Span {
                start: r#left.span().start,
                end: span.end,
            },
            r#left,
            r#op,
            r#right,
        },
        other => other,
    };
    let expression_span = value.span();
    Ast::r#FormulaExpr {
        span: Span {
            start: 0,
            end: source_len,
        },
        r#imports: Vec::new(),
        r#declarations: Vec::new(),
        r#expression: Box::new(Ast::r#ExpressionExpr {
            span: expression_span,
            r#value: Box::new(value),
        }),
        r#methods: Vec::new(),
    }
}
