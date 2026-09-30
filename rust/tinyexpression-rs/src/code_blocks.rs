//! Source-preserving code blocks and side-effect-free AOT preflight.
//! No compiler, process, filesystem or host code is invoked by this module.
use crate::{frontend, FrontendError, Span};

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

/// Parse the complete formula, then project only committed code-block occurrences.
/// Fences inside comments, strings or failed alternatives are not blocks.
pub fn parse(source: &str) -> Result<Vec<CodeBlock>, FrontendError> {
    let mut result = frontend::run(
        None,
        source,
        crate::generated::ubnfc::ParseOptions {
            build_ast: false,
            lexical: true,
            ..Default::default()
        },
    );
    if !result.ok {
        // Same result-family retry as the public facade, including fence text
        // in comments/strings of a bare comparison. These roots have no blocks.
        for entry in ["BooleanExpression", "StringExpression", "ObjectExpression"] {
            let alternate = frontend::run(
                Some(entry),
                source,
                crate::generated::ubnfc::ParseOptions {
                    build_ast: false,
                    lexical: true,
                    ..Default::default()
                },
            );
            if alternate.ok {
                result = alternate;
                break;
            }
        }
    }
    if !result.ok {
        return Err(FrontendError::Parse(frontend::diagnostic(&result, source)));
    }
    let chars: Vec<char> = source.chars().collect();
    let mut blocks = vec![];
    for rule in result
        .lexical
        .iter()
        .filter(|r| r.rule_id == "TinyExpressionP4::CodeBlock")
    {
        let token = |suffix| {
            result.tokens.iter().find(|t| {
                t.parent_occurrence_id == Some(rule.occurrence_id) && t.expr_id.ends_with(suffix)
            })
        };
        let open = token("body/0/tokenRef")
            .ok_or_else(|| FrontendError::Mapping("missing opening fence token".into()))?
            .span;
        let close = token("body/2/tokenRef")
            .ok_or_else(|| FrontendError::Mapping("missing closing fence token".into()))?
            .span;
        // Scanner value spans exclude line terminators in Rust, while Java trace
        // spans can include them. Derive these boundaries from the original source.
        let header_end = (open[0]..chars.len())
            .find(|&i| matches!(chars[i], '\r' | '\n'))
            .unwrap_or(chars.len());
        let body_start = after_line(&chars, header_end);
        let block_end = after_line(&chars, close[0] + 3);
        let header: String = chars[open[0]..header_end].iter().collect();
        let (scheme, identifier) = header
            .strip_prefix("```")
            .and_then(|h| h.split_once(':'))
            .ok_or_else(|| FrontendError::Mapping("invalid code block header projection".into()))?;
        let name_start = open[0] + 3 + scheme.chars().count() + 1;
        blocks.push(CodeBlock {
            scheme: scheme.into(),
            identifier: identifier.into(),
            body: chars[body_start..close[0]].iter().collect(),
            span: Span {
                start: open[0],
                end: block_end,
            },
            body_span: Span {
                start: body_start,
                end: close[0],
            },
            name_span: Span {
                start: name_start,
                end: name_start + identifier.chars().count(),
            },
        });
    }
    blocks.sort_by_key(|block| block.span.start);
    Ok(blocks)
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
