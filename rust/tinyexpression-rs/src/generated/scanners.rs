// Pinned STRING / CODE_START / CODE_END scanners plus the project-local LONG_CODE_BLOCK.
//
// `ubnfc/scanners.rs` is vendored byte-identically from ubnfc's `scanners/rust/scanners.rs`
// (`rust/scripts/regenerate-ubnfc.sh` copies it; `rust/ubnfc-pin.txt` records its SHA-256).
// It addresses the generated crate as `ubnfc_generated`, so that name is bound below and the
// file is included rather than declared as a module -- which also lets its own `//!` header
// stay the first thing in this module, as an inner doc comment must be.

include!("ubnfc/scanners.rs");

mod ubnfc_generated {
    pub use crate::generated::ubnfc::*;
}

/// The extern tokens of the P4 grammar, paired with the Java parser class each one ports.
pub const EXTERN_CLASSES: &[(&str, &str)] = &[
    (
        "TinyExpressionP4::STRING",
        "org.unlaxer.tinyexpression.parser.StringLiteralParser",
    ),
    (
        "TinyExpressionP4::CODE_START",
        "org.unlaxer.tinyexpression.parser.javalang.CodeStartParser",
    ),
    (
        "TinyExpressionP4::CODE_END",
        "org.unlaxer.tinyexpression.parser.javalang.CodeEndParser",
    ),
];

/// A fresh registry per parse; the scanners only inspect input and cursor state.
pub fn registry() -> P4Registry {
    P4Registry(Registry::new(EXTERN_CLASSES).expect("every pinned P4 extern class is ported"))
}

/// Project-local bindings layered over byte-identical pinned ubnfc scanners.
pub struct P4Registry(Registry);
impl TokenScanner for P4Registry {
    fn scan(
        &mut self,
        id: &str,
        input: &str,
        state: State,
        match_only: bool,
        scope: &ScopeStore,
    ) -> ScanResult {
        if id != "TinyExpressionP4::LONG_CODE_BLOCK" {
            return self.0.scan(id, input, state, match_only, scope);
        }
        let start = if match_only {
            state.matched
        } else {
            state.consumed
        };
        let layout = (!state.invert)
            .then(|| crate::code_blocks::fence::scan(input, start))
            .flatten();
        match layout {
            Some(layout) => ScanResult {
                ok: true,
                consumed_end: if match_only {
                    state.consumed
                } else {
                    layout.end
                },
                matched_end: layout.end,
                value_span: [start, layout.fence_end],
                diagnostics: vec![],
                effects: vec![],
            },
            None => ScanResult {
                ok: false,
                consumed_end: state.consumed,
                matched_end: state.matched,
                value_span: [start; 2],
                diagnostics: vec![ScanDiagnostic {
                    offset: start,
                    expected: "long code block",
                }],
                effects: vec![],
            },
        }
    }
}
