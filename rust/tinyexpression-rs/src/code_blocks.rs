//! Source-preserving code blocks and side-effect-free AOT preflight.
//! No compiler, process, filesystem or host code is invoked by this module.
use crate::{generated::ast::Ast, FrontendError, Span};
pub(crate) mod fence;

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct CodeBlock {
    pub scheme: String,
    /// An external binding label, never a path or unchecked Rust source identifier.
    pub identifier: String,
    pub body: String,
    pub span: Span,
    pub body_span: Span,
    pub name_span: Span,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Target {
    Java,
    Rust,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct BlockDiagnostic {
    pub code: &'static str,
    pub span: Span,
}

/// Parse the complete formula and project its retained AST blocks.
/// Use [`from_ast`] to reuse an existing parse without reparsing or collecting token traces.
pub fn parse(source: &str) -> Result<Vec<CodeBlock>, FrontendError> {
    from_ast(&crate::parse(source)?)
}

/// Scheme, label, exact body and absolute code-point spans, from the same owned AST.
pub fn from_ast(ast: &Ast) -> Result<Vec<CodeBlock>, FrontendError> {
    match ast {
        Ast::FormulaExpr { codeBlocks, .. } => codeBlocks
            .iter()
            .map(|node| match node {
                Ast::CodeBlockExpr { source, span } => from_source(source, *span),
                _ => Err(FrontendError::Mapping(
                    "expected CodeBlockExpr in codeBlocks".into(),
                )),
            })
            .collect(),
        Ast::CodeBlockExpr { source, span } => Ok(vec![from_source(source, *span)?]),
        _ => Ok(Vec::new()),
    }
}

/// Project a retained fenced capture. Its outer trailing line ending may be trimmed by
/// the mapper; the node span retains the consumed ending. Body text is never normalized.
pub(crate) fn from_source(source: &str, span: Span) -> Result<CodeBlock, FrontendError> {
    let chars: Vec<char> = source.chars().collect();
    let invalid = || FrontendError::Mapping("invalid retained code block source/span".into());
    let header_end = chars
        .iter()
        .position(|c| matches!(c, '\r' | '\n'))
        .ok_or_else(invalid)?;
    let body_start = after_line(&chars, header_end);
    let width = chars.iter().take_while(|&&c| c == '`').count();
    let close = chars.len().checked_sub(width).ok_or_else(invalid)?;
    if width < 3
        || !source.ends_with(&"`".repeat(width))
        || (width > 3
            && fence::scan(source, 0).is_none_or(|layout| {
                layout.end != source.len()
                    || source[..layout.body_start].chars().count() != body_start
                    || source[..layout.body_end].chars().count() != close
            }))
        || close < body_start
        || !matches!(chars.get(close.wrapping_sub(1)), Some('\r' | '\n'))
        || span.end < span.start
        || chars.len() > span.end - span.start
    {
        return Err(invalid());
    }
    let header: String = chars[width..header_end].iter().collect();
    let (scheme, identifier) = header.split_once(':').ok_or_else(invalid)?;
    if scheme.is_empty() || identifier.is_empty() {
        return Err(invalid());
    }
    let name_start = span.start + width + scheme.chars().count() + 1;
    Ok(CodeBlock {
        scheme: scheme.into(),
        identifier: identifier.into(),
        body: chars[body_start..close].iter().collect(),
        span,
        body_span: Span {
            start: span.start + body_start,
            end: span.start + close,
        },
        name_span: Span {
            start: name_start,
            end: name_start + identifier.chars().count(),
        },
    })
}

fn after_line(chars: &[char], mut at: usize) -> usize {
    if chars.get(at) == Some(&'\r') {
        at += 1;
    }
    if chars.get(at) == Some(&'\n') {
        at += 1;
    }
    at
}

/// Checks a build request without compiling anything. One diagnostic per block in
/// source order: unknown scheme, duplicate binding, target mismatch, denied permission.
/// Permission is supplied by the trusted host, never inferred from source text.
pub fn preflight(blocks: &[CodeBlock], target: Target, allow_code: bool) -> Vec<BlockDiagnostic> {
    let mut names = std::collections::HashSet::new();
    let mut diagnostics = vec![];
    for block in blocks {
        let language = if block.scheme.eq_ignore_ascii_case("java") {
            Some(Target::Java)
        } else if block.scheme.eq_ignore_ascii_case("rust") {
            Some(Target::Rust)
        } else {
            None
        };
        let duplicate = !names.insert(&block.identifier);
        let code = if language.is_none() {
            Some("CB001")
        } else if duplicate {
            Some("CB002")
        } else if language != Some(target) {
            Some("CB003")
        } else if !allow_code {
            Some("CB004")
        } else {
            None
        };
        if let Some(code) = code {
            diagnostics.push(BlockDiagnostic {
                code,
                span: block.name_span,
            });
        }
    }
    diagnostics
}

/// A normal evaluator cannot run a Rust source block. Preflight permission does
/// not change this: a future, explicit AOT build must supply the compiled bindings.
pub fn uncompiled_rust(blocks: &[CodeBlock]) -> Option<BlockDiagnostic> {
    blocks
        .iter()
        .find(|b| b.scheme.eq_ignore_ascii_case("rust"))
        .map(|b| BlockDiagnostic {
            code: "CB005",
            span: b.name_span,
        })
}

impl std::fmt::Display for BlockDiagnostic {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let message = match self.code {
            "CB001" => "unknown code block scheme",
            "CB002" => "duplicate code block binding",
            "CB003" => "code block does not match the build target",
            "CB004" => "code block build requires explicit host permission",
            _ => "Rust code blocks require an explicit AOT build",
        };
        write!(
            f,
            "{} at code points {}..{}: {}",
            self.code, self.span.start, self.span.end, message
        )
    }
}
