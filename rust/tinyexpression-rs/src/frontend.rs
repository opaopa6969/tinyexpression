//! Drives the vendored ubnfc parser and projects its result onto this crate's shapes.

use crate::diagnostic::{ParseDiagnostic, ParseError};
use crate::generated::ast::Ast;
use crate::generated::ubnfc::{
    parse_entry_with_scanner, Diagnostic, DiagnosticKind, ParseOptions, ParseResult,
};
use crate::generated::{compat, scanners};
use crate::{FrontendError, Span};

/// The grammar name the vendored parser answers to.
pub(crate) const GRAMMAR: &str = "TinyExpressionP4";

pub(crate) fn options() -> ParseOptions {
    ParseOptions::default()
}

/// Parses `source` from `entry` (`None` is the grammar root) and returns the ubnfc result.
pub(crate) fn run(entry: Option<&str>, source: &str, options: ParseOptions) -> ParseResult {
    let mut scanner = scanners::registry();
    parse_entry_with_scanner(GRAMMAR, entry, source, options, &mut scanner)
        .expect("the vendored parser knows every entry rule this crate asks for")
}

/// Parses `source` from `entry` and maps the result onto the published typed AST.
pub(crate) fn parse_entry(entry: Option<&str>, source: &str) -> Result<Ast, FrontendError> {
    parse_entry_clipped(entry, source, None)
}

/// Same as [`parse_entry`], with explicit parser options.
pub(crate) fn parse_entry_with_options(
    entry: Option<&str>,
    source: &str,
    options: ParseOptions,
) -> Result<Ast, FrontendError> {
    project(run(entry, source, options), source, None)
}

/// Same as [`parse_entry`], clamping every span into `clip`.
pub(crate) fn parse_entry_clipped(
    entry: Option<&str>,
    source: &str,
    clip: Option<Span>,
) -> Result<Ast, FrontendError> {
    project(run(entry, source, options()), source, clip)
}

fn project(result: ParseResult, source: &str, clip: Option<Span>) -> Result<Ast, FrontendError> {
    if !result.ok {
        return Err(FrontendError::Parse(diagnostic(&result, source)));
    }
    let ast = result
        .ast
        .ok_or_else(|| FrontendError::Mapping("the parser produced no AST".to_owned()))?;
    compat::convert_clipped(&ast, clip).map_err(FrontendError::Mapping)
}

/// Projects the ubnfc diagnostics of a failed parse onto the published diagnostic shape.
pub(crate) fn diagnostic(result: &ParseResult, source: &str) -> ParseDiagnostic {
    let primary = result
        .diagnostics
        .iter()
        .find(|entry| {
            matches!(
                entry.kind,
                DiagnosticKind::Syntax | DiagnosticKind::TrailingInput
            )
        })
        .or_else(|| result.diagnostics.first())
        .or_else(|| result.hints.first());
    match primary {
        Some(entry) => ParseDiagnostic {
            kind: kind(entry),
            offset: entry.offset_cp,
            expected: owned(&entry.expected),
            farthest: ParseError {
                offset: entry.farthest_cp,
                expected: owned(&entry.farthest_expected),
            },
        },
        // Every rejection the generated parser reports carries at least one diagnostic; this
        // keeps the published shape total rather than panicking if that ever stops holding.
        None => ParseDiagnostic {
            kind: if result.consumed_cp < source.chars().count() {
                "trailing_input"
            } else {
                "syntax"
            },
            offset: result.consumed_cp,
            expected: Vec::new(),
            farthest: ParseError {
                offset: result.matched_cp.max(result.consumed_cp),
                expected: Vec::new(),
            },
        },
    }
}

fn kind(entry: &Diagnostic) -> &'static str {
    match entry.kind {
        DiagnosticKind::TrailingInput => "trailing_input",
        DiagnosticKind::Syntax => "syntax",
        DiagnosticKind::Mapping => "mapping",
        DiagnosticKind::Resource => "resource",
        DiagnosticKind::Recovery => "recovery",
    }
}

fn owned(values: &[&'static str]) -> Vec<String> {
    values.iter().map(|value| (*value).to_owned()).collect()
}
