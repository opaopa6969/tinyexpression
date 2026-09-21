use std::{fs, hint::black_box, time::Instant, hash::{Hash, Hasher}, collections::hash_map::DefaultHasher};
use tinyexpression_rs::generated::parser;
use unlaxer_runtime::{Memoization, ParseOptions};
fn fingerprint(value: impl std::fmt::Debug) -> u64 {
    let mut hash = DefaultHasher::new();
    format!("{value:?}").hash(&mut hash);
    hash.finish()
}
fn main() {
    let options = ParseOptions::with_memoization(Memoization::SafeFailures);
    for (n, name) in [(1,"complex"),(4,"complex-x4"),(16,"complex-x16"),(64,"complex-x64")] {
        let path = format!("{}/../../benchmarks/fixtures/{name}.tiny", env!("CARGO_MANIFEST_DIR"));
        let source = fs::read_to_string(path).unwrap();
        let tree = parser::parse_tree_detailed_with_options(&source, options).unwrap();
        println!("fixture n={n} bytes={} nodes={} fingerprint={:016x}",source.len(),tree.nodes.len(),fingerprint(&tree));
        for input in [&source[..source.len()/2], &format!("{source}@")] {
            println!("diagnostic n={n} fingerprint={:016x}",fingerprint(parser::parse_tree_detailed_with_options(input,options)));
        }
        unlaxer_runtime::scale245::reset();
        let measured = parser::parse_tree_detailed_with_options(&source,options).unwrap();
        black_box(&measured);
        for (name,value) in unlaxer_runtime::scale245::snapshot() { println!("counter n={n} {name}={value}"); }
    }
}
