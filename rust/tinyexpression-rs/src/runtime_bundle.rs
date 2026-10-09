//! Exact source snapshot for the native AOT builder. Not enabled by the parser,
//! FFI or wasm builds. Compiling this snapshot without the feature avoids recursion.
pub const FILES: &[(&str, &str)] = include!(concat!(env!("OUT_DIR"), "/runtime_bundle.rs"));
