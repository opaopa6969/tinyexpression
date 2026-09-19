//! Native parser frontend generated from the authoritative TinyExpression P4 grammar.
//!
//! Evaluation is intentionally not part of this crate yet. Parsing and mapping are strict:
//! no Java parser, handwritten parser, or evaluator fallback is attempted.

#[rustfmt::skip]
pub mod generated;

use std::error::Error;
use std::fmt::{self, Display, Formatter};

pub use generated::ast::Ast;
use unlaxer_runtime::ParseDiagnostic;

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
    let tree = generated::parser::parse_tree_detailed(source).map_err(FrontendError::Parse)?;
    generated::mapper::map(&tree).map_err(FrontendError::Mapping)
}
