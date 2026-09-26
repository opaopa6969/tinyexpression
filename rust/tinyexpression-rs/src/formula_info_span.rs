//! Where in a FormulaInfo document a [`LoadError`] is (issue #212): the span, in code points
//! of the document, that the playground's FormulaInfo editor underlines.
//!
//! [`formula_info::load`] reports *what* failed, as the Java loader does; this module finds
//! *where*, without changing the loader. It reads the document again with
//! [`formula_info::parse_document`] and finds the block the error comes from by loading each
//! block on its own, in document order: the blocks are loaded independently up to the
//! `dependsOn` wiring, so the first block that fails alone is the one `load` stopped at.
//! Inside the block the error's key / value picks the entry.
//!
//! Java: `FormulaInfoParseException` carries an offset only for a document the block parser
//! could not consume (its message names it); the other loader exceptions have no position.
//! The span is an addition of the JSON API (`"span"` of a `load` failure), not a difference in
//! what is accepted, so the Java/Rust parity (`tests/formula_info.rs`) is unaffected.

use crate::formula_info::{self, Block, Entry, LoadError, LoaderOptions};
use crate::runtime::java;
use crate::Span;

/// The document span of `error`, which [`formula_info::load`] returned for `source` with
/// `options`. A position inside a line may be a point (`start == end`); a block-level error
/// without a better position is the block's first entry line. `None` when the document does
/// not parse (a [`LoadError::Syntax`] is located by its diagnostic instead, see below).
pub fn load_error_span(source: &str, options: &LoaderOptions, error: &LoadError) -> Option<Span> {
    if let LoadError::Syntax(diagnostic) = error {
        return Some(point(diagnostic.offset));
    }
    let document = formula_info::parse_document(source).ok()?;
    let chars: Vec<char> = source.chars().collect();
    let blocks: Vec<&Block> = document
        .blocks
        .iter()
        .filter(|b| !b.entries.is_empty())
        .collect();
    if let LoadError::UnknownDependsOn {
        calculator_name,
        depends_on,
    } = error
    {
        return depends_on_span(&blocks, calculator_name.as_deref(), depends_on);
    }
    let block = blocks.iter().copied().find(|block| {
        let text: String = chars[block.span.start..block.span.end].iter().collect();
        // Alone, a block with a dependsOn fails the wiring; that is not its own failure.
        !matches!(
            formula_info::load(&text, options),
            Ok(_) | Err(LoadError::UnknownDependsOn { .. })
        )
    })?;
    Some(entry_span(block, error).unwrap_or_else(|| head_of(block)))
}

fn point(offset: usize) -> Span {
    Span {
        start: offset,
        end: offset,
    }
}

/// The key line of the block's first entry (`key:` up to the value).
fn head_of(block: &Block) -> Span {
    let first = &block.entries[0];
    Span {
        start: first.span.start,
        end: first.value_span.start,
    }
}

fn key_span(entry: &Entry) -> Span {
    Span {
        start: entry.span.start,
        end: entry.value_span.start,
    }
}

fn entry_span(block: &Block, error: &LoadError) -> Option<Span> {
    let entries = &block.entries;
    let with_key = |key: &str| {
        let key = key.to_owned();
        entries.iter().filter(move |e| e.key == key)
    };
    let value_of = |e: &Entry| e.value().unwrap_or_default();
    match error {
        LoadError::EmptyValueAtEnd { key } => {
            with_key(key).find(|e| e.raw_value.is_empty()).map(key_span)
        }
        LoadError::UnknownExecutionBackend { value } => entries
            .iter()
            .filter(|e| {
                e.key.eq_ignore_ascii_case("executionBackend")
                    || e.key.eq_ignore_ascii_case("backend")
            })
            .find(|e| value_of(e) == *value)
            .map(value_text_span),
        LoadError::UnknownType { key, value } => with_key(key)
            .find(|e| value_of(e) == *value)
            .map(value_text_span),
        LoadError::OddHexLength { key, length } => with_key(key)
            .find(|e| java::utf16_len(&value_of(e)) == *length)
            .map(value_text_span),
        LoadError::InvalidHexDigit { key, digit } => with_key(key)
            .find(|e| value_of(e).contains(*digit))
            .map(value_text_span),
        // The last entry wins (`info.resultType` / `numberType` are overwritten).
        LoadError::UnsupportedType { key, .. } => with_key(key).next_back().map(value_text_span),
        LoadError::MissingFormula { .. } => with_key("formula").next_back().map(key_span),
        LoadError::Formula { error, .. } => {
            let formula = with_key("formula").next_back()?;
            Some(match &error.diagnostic {
                Some(diagnostic) => point(value_offset(formula, diagnostic.offset)),
                None => value_text_span(formula),
            })
        }
        _ => None,
    }
}

/// The dependsOn name `depends_on` in the dependsOn value of the block `calculator_name`.
fn depends_on_span(
    blocks: &[&Block],
    calculator_name: Option<&str>,
    depends_on: &str,
) -> Option<Span> {
    let name_of = |block: &Block| {
        block
            .entries
            .iter()
            .rfind(|e| e.key == "calculatorName")
            .and_then(Entry::value)
    };
    let block = blocks
        .iter()
        .copied()
        .find(|b| name_of(b).as_deref() == calculator_name)?;
    let entry = block.entries.iter().rfind(|e| e.key == "dependsOn")?;
    let value = entry.value().unwrap_or_default();
    // `java_split(value, ',')`: find the piece that is `depends_on`, in code points.
    let mut start = 0;
    for piece in value.split(',') {
        let length = piece.chars().count();
        if piece == depends_on {
            return Some(Span {
                start: value_offset(entry, start),
                end: value_offset(entry, start + length),
            });
        }
        start += length + 1;
    }
    Some(value_text_span(entry))
}

/// The span of the normalised value ([`Entry::value`]) in the raw value.
fn value_text_span(entry: &Entry) -> Span {
    let length = entry.value().map_or(0, |v| v.chars().count());
    Span {
        start: value_offset(entry, 0),
        end: value_offset(entry, length),
    }
}

/// A code point offset into the normalised value ([`Entry::value`]: `#` lines and blank lines
/// dropped, the kept lines joined by `\n`) → the document offset of the same character.
fn value_offset(entry: &Entry, offset: usize) -> usize {
    let raw: Vec<char> = entry.raw_value.chars().collect();
    // The kept lines: (start in raw, length), in code points.
    let mut kept = Vec::new();
    let mut line_start = 0;
    for (i, c) in raw.iter().chain(std::iter::once(&'\n')).enumerate() {
        if *c != '\n' {
            continue;
        }
        let line: String = raw[line_start..i.min(raw.len())].iter().collect();
        if !line.starts_with('#') && !java::strip(&line).is_empty() {
            kept.push((line_start, i.min(raw.len()) - line_start));
        }
        line_start = i + 1;
    }
    let mut joined = 0;
    for (index, (start, length)) in kept.iter().enumerate() {
        let last = index + 1 == kept.len();
        if offset <= joined + length || last {
            let column = (offset - joined.min(offset)).min(*length);
            return entry.value_span.start + start + column;
        }
        joined += length + 1;
    }
    entry.value_span.start
}
