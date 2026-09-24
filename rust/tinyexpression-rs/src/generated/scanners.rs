// The three extern-token scanners the P4 grammar declares (STRING, CODE_START, CODE_END).
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

/// A fresh scanner registry. The scanners are pure, so building one per parse costs nothing.
pub fn registry() -> Registry {
    Registry::new(EXTERN_CLASSES).expect("every P4 extern class is ported")
}
