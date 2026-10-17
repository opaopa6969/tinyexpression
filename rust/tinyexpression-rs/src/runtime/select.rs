//! Evaluation-root selection: the Java `P4PreferredAstMapper.parseByAstSimpleNamesDetailed`
//! path that `AstEvaluatorCalculator` uses (candidate order, whole-source coverage, and the
//! explicit result-family projection driven by typed variable hints).

#![allow(non_snake_case)]

use super::ast_meta::{children, node_name, record_children};
use super::java;
use super::{ErrorKind, EvalError, ResultType};
use crate::generated::ast::{Ast, AstValue};
use crate::{frontend, Span};

/// `TinyExpressionParserCapabilities.stripJavaStyleCommentsPreservingLayout`, per code point:
/// comments outside quotes become spaces (line breaks kept), so offsets are unchanged.
pub(crate) fn strip_comments(source: &str) -> Vec<char> {
    let chars: Vec<char> = source.chars().collect();
    let mut out = Vec::with_capacity(chars.len());
    let mut single = false;
    let mut double = false;
    let mut i = 0;
    while i < chars.len() {
        let current = chars[i];
        let next = chars.get(i + 1).copied().unwrap_or('\0');
        let prev = if i > 0 { chars[i - 1] } else { '\0' };
        // Host-language bodies are opaque. The grammar, not this layout pass,
        // validates the header and the legacy-first-triple / extended-exact-line terminator.
        if !single
            && !double
            && (i == 0 || matches!(prev, '\r' | '\n'))
            && chars[i..].starts_with(&['`', '`', '`'])
        {
            let end = if chars[i..].starts_with(&['`'; 4]) {
                let tail: String = chars[i..].iter().collect();
                i + tail[..crate::code_blocks::fence::opaque_end(&tail)]
                    .chars()
                    .count()
            } else {
                chars[i + 3..]
                    .windows(3)
                    .position(|s| s == ['`', '`', '`'])
                    .map_or(chars.len(), |at| i + 3 + at + 3)
            };
            out.extend_from_slice(&chars[i..end]);
            i = end;
            continue;
        }
        if current == '\'' && !double && prev != '\\' {
            single = !single;
            out.push(current);
            i += 1;
            continue;
        }
        if current == '"' && !single && prev != '\\' {
            double = !double;
            out.push(current);
            i += 1;
            continue;
        }
        if !single && !double && current == '/' && next == '/' {
            out.push(' ');
            out.push(' ');
            i += 2;
            while i < chars.len() && chars[i] != '\n' {
                out.push(if chars[i] == '\r' { '\r' } else { ' ' });
                i += 1;
            }
            if i < chars.len() {
                out.push(chars[i]);
            }
            i += 1;
            continue;
        }
        if !single && !double && current == '/' && next == '*' {
            out.push(' ');
            out.push(' ');
            i += 2;
            while i < chars.len() {
                let inner = chars[i];
                let inner_next = chars.get(i + 1).copied().unwrap_or('\0');
                if inner == '*' && inner_next == '/' {
                    out.push(' ');
                    out.push(' ');
                    i += 1;
                    break;
                }
                out.push(if matches!(inner, '\n' | '\r') {
                    inner
                } else {
                    ' '
                });
                i += 1;
            }
            i += 1;
            continue;
        }
        out.push(current);
        i += 1;
    }
    out
}

fn strip_chars(chars: &[char]) -> &[char] {
    let start = chars
        .iter()
        .position(|c| !java::is_whitespace(*c))
        .unwrap_or(chars.len());
    let end = chars
        .iter()
        .rposition(|c| !java::is_whitespace(*c))
        .map_or(start, |i| i + 1);
    &chars[start..end.max(start)]
}

fn covers(node: &Ast, source: &[char]) -> bool {
    let span = node.span();
    let end = span.end.min(source.len());
    let start = span.start.min(end);
    strip_chars(&source[start..end]) == strip_chars(source)
}

/// `candidateAstSimpleNames(resultType, AST_EVALUATOR)`.
fn candidates(result_type: ResultType) -> Vec<&'static str> {
    let mut names: Vec<&'static str> = Vec::new();
    let mut add = |name: &'static str| {
        if !names.contains(&name) {
            names.push(name);
        }
    };
    match result_type {
        ResultType::String => add("StringMatchExpr"),
        ResultType::Boolean => add("BooleanMatchExpr"),
        ResultType::Object => {}
        _ => add("NumberMatchExpr"),
    }
    add("IfExpr");
    add("TernaryExpr");
    add(match result_type {
        ResultType::Boolean => "ExternalBooleanInvocationExpr",
        ResultType::String => "ExternalStringInvocationExpr",
        ResultType::Object => "ExternalObjectInvocationExpr",
        _ => "ExternalNumberInvocationExpr",
    });
    for structured in [
        "SinExpr",
        "CosExpr",
        "TanExpr",
        "SqrtExpr",
        "MinExpr",
        "MaxExpr",
        "RandomExpr",
        "AbsExpr",
        "RoundExpr",
        "CeilExpr",
        "FloorExpr",
        "PowExpr",
        "LogExpr",
        "ExpExpr",
        "ToNumExpr",
        "ToUpperCaseExpr",
        "ToLowerCaseExpr",
        "TrimExpr",
        "LengthExpr",
        "ToUpperCaseDotExpr",
        "ToLowerCaseDotExpr",
        "TrimDotExpr",
        "LengthDotExpr",
        "StartsWithExpr",
        "EndsWithExpr",
        "ContainsExpr",
        "InExpr",
        "StartsWithDotExpr",
        "EndsWithDotExpr",
        "ContainsDotExpr",
        "IsPresentExpr",
        "InTimeRangeExpr",
        "InDayTimeRangeExpr",
        "SliceExpr",
        "MethodInvocationExpr",
    ] {
        add(structured);
    }
    match result_type {
        ResultType::String => add("StringConcatExpr"),
        ResultType::Boolean => add("BooleanOrExpr"),
        ResultType::Object => {
            add("ObjectExpr");
            add("StringConcatExpr");
            add("BooleanOrExpr");
            add("BinaryExpr");
        }
        _ => add("BinaryExpr"),
    }
    add("VariableRefExpr");
    add("FormulaExpr");
    names
}

fn is_typed_family_root(name: &str) -> bool {
    matches!(
        name,
        "StringConcatExpr"
            | "BooleanOrExpr"
            | "ObjectExpr"
            | "NumberMatchExpr"
            | "StringMatchExpr"
            | "BooleanMatchExpr"
    )
}

/// `P4SourceMapping.select(preferred)`: the shallowest node of that type (ties: larger start).
fn find_shallowest<'a>(root: &'a Ast, name: &str) -> Option<&'a Ast> {
    let mut level = vec![root];
    while !level.is_empty() {
        let mut best: Option<&Ast> = None;
        for node in &level {
            if node_name(node) == name
                && best.is_none_or(|b: &Ast| node.span().start > b.span().start)
            {
                best = Some(node);
            }
        }
        if best.is_some() {
            return best;
        }
        level = level.iter().flat_map(|node| children(node)).collect();
    }
    None
}

fn parse_failure(message: String) -> EvalError {
    EvalError::new(ErrorKind::Parse, message)
}

fn map_candidates(
    root: &Ast,
    source: &[char],
    candidates: &[&'static str],
    allow_default: bool,
) -> Result<(Ast, Option<&'static str>), EvalError> {
    for candidate in candidates {
        if let Some(node) = find_shallowest(root, candidate) {
            if covers(node, source) {
                return Ok((node.clone(), Some(candidate)));
            }
        }
    }
    if allow_default {
        return Ok((root.clone(), None));
    }
    Err(parse_failure(format!(
        "No whole-source generated AST mapping found: {}",
        source.iter().collect::<String>()
    )))
}

/// Recognizes Formula once and preserves the historical public subtree for bare comparisons.
fn parse_root(source: &str) -> Result<Ast, EvalError> {
    let ast = frontend::parse_entry(None, source)?;
    let stripped = strip_comments(source);
    if !is_document(&ast, &stripped) {
        if let Ast::FormulaExpr { expression, .. } = &ast {
            if let Ast::ExpressionExpr { value, .. } = expression.as_ref() {
                if legacy_numeric_prefix(value) {
                    return Ok(crate::family_projection::with_root_span(
                        *value.clone(),
                        Span {
                            start: 0,
                            end: stripped.len(),
                        },
                    ));
                }
            }
        }
    }
    Ok(ast)
}

fn legacy_numeric_prefix(ast: &Ast) -> bool {
    match ast {
        Ast::StringConcatExpr { left, op, .. } => !op.is_empty() && numeric_value(left),
        Ast::BooleanOrExpr { left, .. } => {
            let Ast::BooleanAndExpr { left, .. } = left.as_ref() else {
                return false;
            };
            let Ast::BooleanXorExpr { left, .. } = left.as_ref() else {
                return false;
            };
            let Ast::BooleanFactorExpr {
                value: AstValue::Node(value),
                ..
            } = left.as_ref()
            else {
                return false;
            };
            match value.as_ref() {
                Ast::ComparisonExpr { .. } => true,
                Ast::StringComparisonExpr { left, .. } => match left.as_ref() {
                    Ast::StringConcatExpr { left, .. } => numeric_value(left),
                    _ => false,
                },
                Ast::BooleanEqualityExpr { left, .. } => numeric_value(left),
                value => numeric_atom(value),
            }
        }
        _ => false,
    }
}

fn numeric_value(value: &AstValue) -> bool {
    matches!(value, AstValue::Node(node) if numeric_atom(node))
}

fn numeric_atom(ast: &Ast) -> bool {
    match ast {
        Ast::VariableRefExpr { .. }
        | Ast::StringTypedVariableRefExpr { .. }
        | Ast::MethodInvocationExpr { .. }
        | Ast::IfExpr { .. } => true,
        Ast::StringConcatExpr { left, right, .. } => {
            numeric_value(left) && right.iter().all(numeric_value)
        }
        Ast::StringMatchExpr {
            firstCase,
            moreCases,
            defaultCase,
            ..
        }
        | Ast::BooleanMatchExpr {
            firstCase,
            moreCases,
            defaultCase,
            ..
        } => std::iter::once(firstCase.as_ref())
            .chain(moreCases.iter())
            .chain(std::iter::once(defaultCase.as_ref()))
            .all(|case| case_value(case).is_some_and(numeric_atom)),
        Ast::BooleanOrExpr { left, op, .. } if op.is_empty() => {
            let Ast::BooleanAndExpr { left, op, .. } = left.as_ref() else {
                return false;
            };
            if !op.is_empty() {
                return false;
            }
            let Ast::BooleanXorExpr { left, op, .. } = left.as_ref() else {
                return false;
            };
            if !op.is_empty() {
                return false;
            }
            matches!(left.as_ref(), Ast::BooleanFactorExpr { value, .. } if numeric_value(value))
        }
        _ => false,
    }
}

pub(crate) fn select_root(
    source: &str,
    stripped: &[char],
    result_type: ResultType,
) -> Result<Ast, EvalError> {
    let tree = parse_root(source)?;
    let candidates = candidates(result_type);
    let (parsed, parsed_name) = map_candidates(&tree, stripped, &candidates, false)?;
    let parsed_index = parsed_name
        .and_then(|name| candidates.iter().position(|c| *c == name))
        .unwrap_or(0);
    let earlier_typed_family = parsed_index > 0
        && candidates[..parsed_index]
            .iter()
            .any(|c| is_typed_family_root(c));
    let root = if earlier_typed_family {
        let selected = select_explicit_result_family(source, stripped, parsed.clone())?;
        let selected_index = candidates.iter().position(|c| *c == node_name(&selected));
        match selected_index {
            Some(index) if index <= parsed_index => selected,
            _ => parsed,
        }
    } else {
        parsed
    };
    if let Some(violation) = strict_match_violation(&root, stripped) {
        return Err(parse_failure(violation));
    }
    Ok(root)
}

/// `P4StrictMatchTypingValidator.firstViolation`: a match case value that is directly a method
/// invocation, or a variable whose inline type hint names another family, is rejected.
fn strict_match_violation(root: &Ast, source: &[char]) -> Option<String> {
    let mut stack = vec![root];
    while let Some(node) = stack.pop() {
        let (value, label) = match node {
            Ast::NumberCaseValueExpr { value, .. } => (Some(value), "number"),
            Ast::StringCaseValueExpr { value, .. } => (Some(value), "string"),
            Ast::BooleanCaseValueExpr { value, .. } => (Some(value), "boolean"),
            _ => (None, ""),
        };
        if let Some(direct) = value.and_then(|value| direct_case_value(value)) {
            let snippet = || {
                let span = direct.span();
                let end = span.end.min(source.len());
                source[span.start.min(end)..end].iter().collect::<String>()
            };
            match direct {
                Ast::VariableRefExpr {
                    r#type: Some(hint), ..
                } => {
                    let accepted = match label {
                        "number" => {
                            hint.eq_ignore_ascii_case("number")
                                || hint.eq_ignore_ascii_case("float")
                        }
                        other => hint.eq_ignore_ascii_case(other),
                    };
                    if !accepted {
                        return Some(format!(
                            "P4 strict match typing rejected direct {label} case value with mismatched type hint: {}",
                            snippet()
                        ));
                    }
                }
                Ast::MethodInvocationExpr { .. } => {
                    return Some(format!(
                        "P4 strict match typing rejected direct method invocation in {label} match case: {}",
                        snippet()
                    ));
                }
                _ => {}
            }
        }
        // Depth-first in field order, like the Java record reflection.
        let mut next = record_children(node);
        next.reverse();
        stack.extend(next);
    }
    None
}

fn direct_case_value(value: &Ast) -> Option<&Ast> {
    match value {
        Ast::VariableRefExpr { .. } | Ast::MethodInvocationExpr { .. } => Some(value),
        Ast::BinaryExpr {
            left: AstValue::Node(left),
            op,
            right,
            ..
        }
        | Ast::StringConcatExpr {
            left: AstValue::Node(left),
            op,
            right,
            ..
        } if op.is_empty() && right.is_empty() => direct_case_value(left),
        Ast::BooleanOrExpr {
            left, op, right, ..
        }
        | Ast::BooleanAndExpr {
            left, op, right, ..
        }
        | Ast::BooleanXorExpr {
            left, op, right, ..
        } if op.is_empty() && right.is_empty() => direct_case_value(left),
        Ast::BooleanFactorExpr {
            value: AstValue::Node(value),
            ..
        } => direct_case_value(value),
        _ => None,
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum Family {
    Number,
    String,
    Boolean,
    Object,
}

fn mismatch(source: &str) -> EvalError {
    parse_failure(format!(
        "P4 match type mismatch: case and default values must use one result family: {source}"
    ))
}

fn result_root(ast: &Ast) -> &Ast {
    match ast {
        Ast::FormulaExpr { expression, .. } => result_root(expression),
        Ast::ExpressionExpr { value, .. } => result_root(value),
        Ast::BinaryExpr {
            left: AstValue::Node(left),
            op,
            right,
            ..
        }
        | Ast::StringConcatExpr {
            left: AstValue::Node(left),
            op,
            right,
            ..
        } if op.is_empty() && right.is_empty() => result_root(left),
        Ast::BooleanOrExpr {
            left, op, right, ..
        }
        | Ast::BooleanAndExpr {
            left, op, right, ..
        }
        | Ast::BooleanXorExpr {
            left, op, right, ..
        } if op.is_empty() && right.is_empty() => result_root(left),
        Ast::BooleanFactorExpr {
            value: AstValue::Node(value),
            ..
        } => result_root(value),
        other => other,
    }
}

fn match_family(ast: &Ast) -> Option<Family> {
    match result_root(ast) {
        Ast::NumberMatchExpr { .. } => Some(Family::Number),
        Ast::StringMatchExpr { .. } => Some(Family::String),
        Ast::BooleanMatchExpr { .. } => Some(Family::Boolean),
        _ => None,
    }
}

fn direct_family(ast: &Ast) -> Result<Option<Family>, EvalError> {
    Ok(match ast {
        Ast::VariableRefExpr {
            r#type: Some(kind), ..
        } => Some(match kind.to_ascii_lowercase().as_str() {
            "number" | "float" => Family::Number,
            "string" => Family::String,
            "boolean" => Family::Boolean,
            "object" => Family::Object,
            other => {
                return Err(parse_failure(format!(
                    "Unknown explicit result type: {other}"
                )))
            }
        }),
        Ast::StringTypedVariableRefExpr { .. } | Ast::StringCastVariableRefExpr { .. } => {
            Some(Family::String)
        }
        Ast::BinaryExpr { left, op, .. } if op.is_empty() => match left {
            AstValue::Node(node) => direct_family(node)?,
            AstValue::Text { .. } => None,
        },
        Ast::StringConcatExpr {
            left: AstValue::Node(node),
            op,
            ..
        } if op.is_empty() => direct_family(node)?,
        Ast::BooleanOrExpr { left, op, .. }
        | Ast::BooleanAndExpr { left, op, .. }
        | Ast::BooleanXorExpr { left, op, .. }
            if op.is_empty() =>
        {
            direct_family(left)?
        }
        Ast::BooleanFactorExpr {
            value: AstValue::Node(node),
            ..
        } => direct_family(node)?,
        Ast::ObjectExpr { value, .. } => direct_family(value)?,
        _ => None,
    })
}

fn case_value(case: &Ast) -> Option<&Ast> {
    // NumberCaseExpr.value() is a NumberCaseValueExpr whose value() is the typed expression.
    match case {
        Ast::NumberCaseExpr { value, .. }
        | Ast::NumberDefaultCaseExpr { value, .. }
        | Ast::StringCaseExpr { value, .. }
        | Ast::StringDefaultCaseExpr { value, .. }
        | Ast::BooleanCaseExpr { value, .. }
        | Ast::BooleanDefaultCaseExpr { value, .. } => match value.as_ref() {
            Ast::NumberCaseValueExpr { value, .. }
            | Ast::StringCaseValueExpr { value, .. }
            | Ast::BooleanCaseValueExpr { value, .. } => Some(value),
            other => Some(other),
        },
        _ => None,
    }
}

fn explicit_family(ast: &Ast, source: &str) -> Result<Option<Family>, EvalError> {
    let root = result_root(ast);
    let mut families = Vec::new();
    match root {
        Ast::NumberMatchExpr {
            firstCase,
            moreCases,
            defaultCase,
            ..
        }
        | Ast::StringMatchExpr {
            firstCase,
            moreCases,
            defaultCase,
            ..
        }
        | Ast::BooleanMatchExpr {
            firstCase,
            moreCases,
            defaultCase,
            ..
        } => {
            for case in std::iter::once(firstCase.as_ref())
                .chain(moreCases.iter())
                .chain(std::iter::once(defaultCase.as_ref()))
            {
                if let Some(value) = case_value(case) {
                    if let Some(family) = direct_family(value)? {
                        families.push(family);
                    }
                }
            }
        }
        other => {
            if let Some(family) = direct_family(other)? {
                families.push(family);
            }
        }
    }
    let mut selected = None;
    for family in families {
        if selected.is_some_and(|s| s != family) {
            let _ = source;
            return Err(mismatch("captured explicit result hints"));
        }
        selected = Some(family);
    }
    Ok(selected)
}

fn is_document(ast: &Ast, stripped: &[char]) -> bool {
    let Ast::FormulaExpr {
        imports,
        declarations,
        expression,
        methods,
        ..
    } = ast
    else {
        return false;
    };
    if !imports.is_empty() || !declarations.is_empty() || !methods.is_empty() {
        return true;
    }
    let span = expression.span();
    let end = span.end.min(stripped.len());
    let start = span.start.min(end);
    strip_chars(stripped) != strip_chars(&stripped[start..end])
}

fn select_explicit_result_family(
    source: &str,
    stripped: &[char],
    parsed: Ast,
) -> Result<Ast, EvalError> {
    let document = is_document(&parsed, stripped);
    let family = explicit_family(&parsed, source)?;
    let current_match = match_family(&parsed);
    let Some(family) = family else {
        return Ok(parsed);
    };
    if (!document && Some(family) == current_match)
        || (current_match.is_none() && family == Family::Number)
    {
        return Ok(parsed);
    }
    if current_match.is_some() && family == Family::Object {
        return Err(mismatch(source));
    }
    let expression_span = match (&parsed, document) {
        (Ast::FormulaExpr { expression, .. }, true) => Some(expression.span()),
        _ => None,
    };
    let target = if current_match.is_some() {
        result_root(&parsed)
    } else {
        &parsed
    };
    let family_name = match family {
        Family::String => "STRING",
        Family::Boolean => "BOOLEAN",
        Family::Object => "OBJECT",
        Family::Number => "NUMBER",
    };
    let selected =
        crate::family_projection::project(target, family_name, source).map_err(|_| {
            if current_match.is_some() {
                mismatch(source)
            } else {
                parse_failure(format!(
                    "Explicit result type could not be parsed as {}: {source}",
                    family_name.to_lowercase()
                ))
            }
        })?;
    let selected = crate::family_projection::with_root_span(
        selected,
        expression_span.unwrap_or(Span {
            start: 0,
            end: source.chars().count(),
        }),
    );
    match (parsed, expression_span) {
        (
            Ast::FormulaExpr {
                span,
                imports,
                declarations,
                methods,
                codeBlocks,
                ..
            },
            Some(expression_span),
        ) => Ok(Ast::FormulaExpr {
            span,
            imports,
            declarations,
            expression: Box::new(Ast::ExpressionExpr {
                span: expression_span,
                value: Box::new(selected),
            }),
            methods,
            codeBlocks,
        }),
        _ => Ok(selected),
    }
}
