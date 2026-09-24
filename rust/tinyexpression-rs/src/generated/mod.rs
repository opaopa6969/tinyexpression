//! The parser and the typed AST.
//!
//! `ubnfc` is the vendored output of ubnfc's Rust backend for this repository's P4 grammar
//! (`rust/scripts/regenerate-ubnfc.sh`, pinned by `rust/ubnfc-pin.txt`). It is dependency
//! free and `#![forbid(unsafe_code)]`. `ast` is this crate's published typed AST and
//! `compat` converts one into the other.

// Generated code is verified byte-for-byte against the generator, not linted: older clippy
// releases (the CI toolchain is 1.85) flag the generator's constant-folded guards such as
// `(out.ok && true) || (!out.ok && true)`.
#[rustfmt::skip]
#[allow(clippy::all)]
#[path = "ubnfc/mod.rs"]
pub mod ubnfc;

#[rustfmt::skip]
pub mod ast;
#[rustfmt::skip]
pub mod compat;
#[rustfmt::skip]
pub mod evaluator;
pub mod scanners;
