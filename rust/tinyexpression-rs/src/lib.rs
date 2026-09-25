//! Native parser frontend for the authoritative TinyExpression P4 grammar.
//!
//! The parser is the vendored output of ubnfc's Rust backend (`generated::ubnfc`), which is
//! dependency free and forbids unsafe code. Parsing, mapping, and the context-free f32
//! evaluator are strict: no Java parser, handwritten parser, or evaluator fallback is
//! attempted.
//!
//! [`runtime`] is the contextual evaluator with the Java `P4_AST_EVALUATOR` semantics
//! (variables, declarations, methods, host-supplied externals, clock and random), available as a
//! tree walker and as a closure-compiled form; `tests/java_differential.rs` gates it against a
//! golden produced by the Java implementation.
//!
//! [`formula_info`] loads FormulaInfo documents (the Java loader's block format) with a parser
//! generated from `grammar/formula-info.ubnf` and builds each formula as a [`runtime::Program`];
//! `tests/formula_info.rs` gates it against the Java loader.

#![forbid(unsafe_code)]

pub mod api;
pub mod generated;

mod diagnostic;
mod evaluator;
pub mod formula_info;
mod frontend;
mod request;
pub mod runtime;

use std::error::Error;
use std::fmt::{self, Display, Formatter};

pub use diagnostic::{json_string, ParseDiagnostic, ParseError, Span};
pub use evaluator::{evaluate, evaluate_ast, EvaluationError, Value};
pub use generated::ast::Ast;
use generated::ast::AstValue;

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum FrontendError {
    Parse(ParseDiagnostic),
    Mapping(String),
    TypeMismatch { message: String, span: Span },
}

impl Display for FrontendError {
    fn fmt(&self, formatter: &mut Formatter<'_>) -> fmt::Result {
        match self {
            Self::Parse(error) => Display::fmt(error, formatter),
            Self::Mapping(error) => write!(formatter, "AST mapping failed: {error}"),
            Self::TypeMismatch { message, .. } => formatter.write_str(message),
        }
    }
}

impl Error for FrontendError {}

/// Parses the complete UTF-8 source from the grammar root only.
///
/// [`parse`] additionally retries the other result-family roots the way the Java facade does;
/// this entry point is the primary `Formula` parse on its own.
pub fn parse_formula_root(source: &str) -> Result<Ast, FrontendError> {
    frontend::parse_entry(None, source)
}

/// [`parse_formula_root`] with explicit parser options (memoization, prediction, limits).
pub fn parse_formula_root_with_options(
    source: &str,
    options: generated::ubnfc::ParseOptions,
) -> Result<Ast, FrontendError> {
    frontend::parse_entry_with_options(None, source, options)
}

/// Parses the complete UTF-8 source and returns an owned, source-preserving typed AST.
pub fn parse(source: &str) -> Result<Ast, FrontendError> {
    match frontend::parse_entry(None, source) {
        Ok(ast) => select_explicit_result_family(source, ast),
        Err(primary @ FrontendError::Parse(_)) => {
            parse_alternate_root(source).unwrap_or(Err(primary))
        }
        Err(other) => Err(other),
    }
}

fn normalize_direct_match_root(ast: Ast, source: &str) -> Ast {
    if is_document(&ast, source) {
        return ast;
    }
    match ast {
        Ast::r#FormulaExpr {
            span,
            r#imports,
            r#declarations,
            r#expression,
            r#methods,
        } => match *r#expression {
            Ast::r#ExpressionExpr {
                span: expression_span,
                r#value,
            } => {
                let direct_match = direct_match_node(&r#value).cloned();
                Ast::r#FormulaExpr {
                    span,
                    r#imports,
                    r#declarations,
                    r#expression: Box::new(Ast::r#ExpressionExpr {
                        span: expression_span,
                        r#value: Box::new(direct_match.unwrap_or(*r#value)),
                    }),
                    r#methods,
                }
            }
            expression => Ast::r#FormulaExpr {
                span,
                r#imports,
                r#declarations,
                r#expression: Box::new(expression),
                r#methods,
            },
        },
        other => other,
    }
}

fn is_document(ast: &Ast, source: &str) -> bool {
    let Ast::r#FormulaExpr {
        r#imports,
        r#declarations,
        r#expression,
        r#methods,
        ..
    } = ast
    else {
        return false;
    };
    if !r#imports.is_empty() || !r#declarations.is_empty() || !r#methods.is_empty() {
        return true;
    }
    let span = r#expression.span();
    let chars: Vec<char> = source.chars().collect();
    if span.start > span.end || span.end > chars.len() {
        return true;
    }
    !is_java_trivia(&chars[..span.start]) || !is_java_trivia(&chars[span.end..])
}

fn is_java_trivia(chars: &[char]) -> bool {
    let mut index = 0;
    while index < chars.len() {
        if chars[index].is_whitespace() {
            index += 1;
        } else if chars[index] == '/' && chars.get(index + 1) == Some(&'/') {
            index += 2;
            while index < chars.len() && chars[index] != '\n' {
                index += 1;
            }
        } else if chars[index] == '/' && chars.get(index + 1) == Some(&'*') {
            index += 2;
            while index + 1 < chars.len() && !(chars[index] == '*' && chars[index + 1] == '/') {
                index += 1;
            }
            if index + 1 >= chars.len() {
                return false;
            }
            index += 2;
        } else {
            return false;
        }
    }
    true
}

fn direct_match_node(ast: &Ast) -> Option<&Ast> {
    match ast {
        Ast::r#NumberMatchExpr { .. }
        | Ast::r#StringMatchExpr { .. }
        | Ast::r#BooleanMatchExpr { .. } => Some(ast),
        Ast::r#BinaryExpr {
            r#left: AstValue::Node(left),
            r#op,
            r#right,
            ..
        }
        | Ast::r#StringConcatExpr {
            r#left: AstValue::Node(left),
            r#op,
            r#right,
            ..
        } if r#op.is_empty() && r#right.is_empty() => direct_match_node(left),
        Ast::r#BooleanOrExpr {
            r#left,
            r#op,
            r#right,
            ..
        } if r#op.is_empty() && r#right.is_empty() => direct_match_node(r#left),
        Ast::r#BooleanAndExpr {
            r#left,
            r#op,
            r#right,
            ..
        } if r#op.is_empty() && r#right.is_empty() => direct_match_node(r#left),
        Ast::r#BooleanXorExpr {
            r#left,
            r#op,
            r#right,
            ..
        } if r#op.is_empty() && r#right.is_empty() => direct_match_node(r#left),
        Ast::r#BooleanFactorExpr {
            r#value: AstValue::Node(value),
            ..
        } => direct_match_node(value),
        _ => None,
    }
}

fn match_root(ast: &Ast) -> Option<&Ast> {
    match ast {
        Ast::r#FormulaExpr { r#expression, .. }
        | Ast::r#ExpressionExpr {
            r#value: r#expression,
            ..
        } => match_root(r#expression),
        _ => direct_match_node(ast),
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum ResultFamily {
    Number,
    String,
    Boolean,
    Object,
}

fn select_explicit_result_family(source: &str, ast: Ast) -> Result<Ast, FrontendError> {
    let mut hints = Vec::new();
    collect_result_hints(&ast, &mut hints);
    let Some(family) = hints.first().copied() else {
        return Ok(ast);
    };
    if hints.iter().any(|candidate| *candidate != family) {
        return Err(type_mismatch(source));
    }
    // Preserve the legacy public shape for unhinted matches; normalization is part of typed
    // family selection only.
    let ast = normalize_direct_match_root(ast, source);
    let current_match_family = match_family(&ast);
    let document = is_document(&ast, source);
    if (!document && current_match_family == Some(family))
        || (current_match_family.is_none() && family == ResultFamily::Number)
    {
        return Ok(ast);
    }
    let match_result = current_match_family.is_some();
    if match_result && family == ResultFamily::Object {
        return Err(type_mismatch(source));
    }
    let rule_name = match (match_result, family) {
        (true, ResultFamily::Number) => "NumberMatchExpression",
        (true, ResultFamily::String) => "StringMatchExpression",
        (true, ResultFamily::Boolean) => "BooleanMatchExpression",
        (false, ResultFamily::String) => "StringExpression",
        (false, ResultFamily::Boolean) => "BooleanExpression",
        (false, ResultFamily::Object) => "ObjectExpression",
        (false, ResultFamily::Number) | (true, ResultFamily::Object) => unreachable!(),
    };
    let document_span = if document {
        document_expression_span(&ast)
    } else {
        None
    };
    let family_source = document_span
        .map(|span| mask_outside(source, span))
        .unwrap_or_else(|| source.to_owned());
    match parse_family_root(&family_source, rule_name, document_span) {
        Err(FrontendError::Parse(_)) if match_result => Err(type_mismatch(source)),
        Err(error) => Err(error),
        Ok(reparsed) => match document_span {
            Some(span) => replace_document_expression(ast, reparsed, span),
            None => Ok(reparsed),
        },
    }
}

fn document_expression_span(ast: &Ast) -> Option<Span> {
    match ast {
        Ast::r#FormulaExpr { r#expression, .. } => Some(r#expression.span()),
        _ => None,
    }
}

fn mask_outside(source: &str, span: Span) -> String {
    source
        .chars()
        .enumerate()
        .map(|(index, value)| {
            if (index >= span.start && index < span.end) || matches!(value, '\n' | '\r' | '\t') {
                value
            } else {
                ' '
            }
        })
        .collect()
}

fn replace_document_expression(
    document: Ast,
    reparsed: Ast,
    expression_span: Span,
) -> Result<Ast, FrontendError> {
    let replacement = match reparsed {
        Ast::r#FormulaExpr { r#expression, .. } => match *r#expression {
            Ast::r#ExpressionExpr { r#value, .. } => *r#value,
            value => value,
        },
        Ast::r#ExpressionExpr { r#value, .. } => *r#value,
        value => value,
    };
    match document {
        Ast::r#FormulaExpr {
            span,
            r#imports,
            r#declarations,
            r#methods,
            ..
        } => Ok(Ast::r#FormulaExpr {
            span,
            r#imports,
            r#declarations,
            r#expression: Box::new(Ast::r#ExpressionExpr {
                span: expression_span,
                r#value: Box::new(replacement),
            }),
            r#methods,
        }),
        _ => Err(FrontendError::Mapping(
            "document expression replacement requires FormulaExpr".to_owned(),
        )),
    }
}

fn parse_family_root(
    source: &str,
    rule_name: &str,
    clip_to: Option<Span>,
) -> Result<Ast, FrontendError> {
    frontend::parse_entry_clipped(Some(rule_name), source, clip_to)
        .map(|value| wrap_expression_root(value, source.chars().count()))
}

fn type_mismatch(source: &str) -> FrontendError {
    FrontendError::TypeMismatch {
        message: "P4 match type mismatch: case and default values must use one result family"
            .to_owned(),
        span: Span {
            start: 0,
            end: source.chars().count(),
        },
    }
}

fn match_family(ast: &Ast) -> Option<ResultFamily> {
    match match_root(ast)? {
        Ast::r#NumberMatchExpr { .. } => Some(ResultFamily::Number),
        Ast::r#StringMatchExpr { .. } => Some(ResultFamily::String),
        Ast::r#BooleanMatchExpr { .. } => Some(ResultFamily::Boolean),
        _ => None,
    }
}

fn collect_result_hints(ast: &Ast, hints: &mut Vec<ResultFamily>) {
    if let Some(root) = match_root(ast) {
        if !core::ptr::eq(root, ast) {
            collect_result_hints(root, hints);
            return;
        }
    }
    match ast {
        Ast::r#FormulaExpr { r#expression, .. }
        | Ast::r#ExpressionExpr {
            r#value: r#expression,
            ..
        } => collect_result_hints(r#expression, hints),
        Ast::r#NumberMatchExpr {
            r#firstCase,
            r#moreCases,
            r#defaultCase,
            ..
        }
        | Ast::r#StringMatchExpr {
            r#firstCase,
            r#moreCases,
            r#defaultCase,
            ..
        }
        | Ast::r#BooleanMatchExpr {
            r#firstCase,
            r#moreCases,
            r#defaultCase,
            ..
        } => {
            collect_case_hint(r#firstCase, hints);
            for case in r#moreCases {
                collect_case_hint(case, hints);
            }
            collect_case_hint(r#defaultCase, hints);
        }
        _ => {
            if let Some(hint) = direct_variable_hint(ast) {
                hints.push(hint);
            }
        }
    }
}

fn collect_case_hint(ast: &Ast, hints: &mut Vec<ResultFamily>) {
    match ast {
        Ast::r#NumberCaseExpr { r#value, .. }
        | Ast::r#NumberDefaultCaseExpr { r#value, .. }
        | Ast::r#NumberCaseValueExpr { r#value, .. }
        | Ast::r#StringCaseExpr { r#value, .. }
        | Ast::r#StringDefaultCaseExpr { r#value, .. }
        | Ast::r#StringCaseValueExpr { r#value, .. }
        | Ast::r#BooleanCaseExpr { r#value, .. }
        | Ast::r#BooleanDefaultCaseExpr { r#value, .. }
        | Ast::r#BooleanCaseValueExpr { r#value, .. } => collect_case_hint(r#value, hints),
        _ => {
            if let Some(hint) = direct_variable_hint(ast) {
                hints.push(hint);
            }
        }
    }
}

fn direct_variable_hint(ast: &Ast) -> Option<ResultFamily> {
    match ast {
        Ast::r#VariableRefExpr {
            r#type: Some(kind), ..
        } => match kind.to_ascii_lowercase().as_str() {
            "number" | "float" => Some(ResultFamily::Number),
            "string" => Some(ResultFamily::String),
            "boolean" => Some(ResultFamily::Boolean),
            "object" => Some(ResultFamily::Object),
            _ => None,
        },
        Ast::r#StringTypedVariableRefExpr { .. } | Ast::r#StringCastVariableRefExpr { .. } => {
            Some(ResultFamily::String)
        }
        Ast::r#BinaryExpr {
            r#left,
            r#op,
            r#right,
            ..
        }
        | Ast::r#StringConcatExpr {
            r#left,
            r#op,
            r#right,
            ..
        } if r#op.is_empty() && r#right.is_empty() => direct_value_hint(r#left),
        Ast::r#BooleanOrExpr {
            r#left,
            r#op,
            r#right,
            ..
        } if r#op.is_empty() && r#right.is_empty() => direct_variable_hint(r#left),
        Ast::r#BooleanAndExpr {
            r#left,
            r#op,
            r#right,
            ..
        } if r#op.is_empty() && r#right.is_empty() => direct_variable_hint(r#left),
        Ast::r#BooleanXorExpr {
            r#left,
            r#op,
            r#right,
            ..
        } if r#op.is_empty() && r#right.is_empty() => direct_variable_hint(r#left),
        Ast::r#BooleanFactorExpr { r#value, .. } => direct_value_hint(r#value),
        Ast::r#ObjectExpr { r#value, .. } => direct_variable_hint(r#value),
        _ => None,
    }
}

fn direct_value_hint(value: &AstValue) -> Option<ResultFamily> {
    match value {
        AstValue::Node(ast) => direct_variable_hint(ast),
        AstValue::Text { .. } => None,
    }
}

/// Mirrors Java's `P4PreferredAstMapper` compatibility retries for PEG root dispatch.
///
/// `Expression` intentionally tries `NumberExpression` before `BooleanExpression` so that
/// arithmetic such as `$a+$b` is consumed as one expression. A bare comparison such as `1<2`
/// therefore commits the numeric prefix `1`, and the enclosing `Formula` subsequently fails at
/// EOF. Retrying only after that failure keeps the successful numeric hot path unchanged while
/// accepting the complete boolean expression.
fn parse_alternate_root(source: &str) -> Option<Result<Ast, FrontendError>> {
    for rule_name in ["BooleanExpression", "StringExpression", "ObjectExpression"] {
        if let Ok(value) = frontend::parse_entry(Some(rule_name), source) {
            return Some(Ok(wrap_expression_root(value, source.chars().count())));
        }
    }
    None
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
