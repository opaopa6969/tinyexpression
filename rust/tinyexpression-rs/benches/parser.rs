use std::fs;
use std::path::PathBuf;
use std::time::Duration;

use criterion::{black_box, criterion_group, criterion_main, BenchmarkId, Criterion};
use tinyexpression_rs::generated::{mapper, parser};
use unlaxer_runtime::{Diagnostics, Memoization, ParseOptions};

const FIXTURES: &[(&str, &str)] = &[
    ("complex", "complex.tiny"),
    ("flat-arithmetic", "flat-arithmetic.tiny"),
    ("large-match", "large-match.tiny"),
    // Scaled copies for checking linearity of parse-only and map-only separately.
    ("complex-x4", "complex-x4.tiny"),
    ("complex-x16", "complex-x16.tiny"),
    ("complex-x64", "complex-x64.tiny"),
];

const FACADE_FIXTURES: &[(&str, &str)] = &[
    ("complex", "complex.tiny"),
    ("comparison-heavy", "comparison-heavy.tiny"),
    // Scaled copies (x4 / x16 / x64 of the base fixtures) for checking linearity in input size.
    ("complex-x4", "complex-x4.tiny"),
    ("complex-x16", "complex-x16.tiny"),
    ("complex-x64", "complex-x64.tiny"),
    ("comparison-heavy-x4", "comparison-heavy-x4.tiny"),
    ("comparison-heavy-x16", "comparison-heavy-x16.tiny"),
    ("comparison-heavy-x64", "comparison-heavy-x64.tiny"),
];

fn fixture_path(file_name: &str) -> PathBuf {
    PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("../../benchmarks/fixtures")
        .join(file_name)
}

fn parser_benchmarks(criterion: &mut Criterion) {
    let off = ParseOptions::with_memoization(Memoization::Off);
    let safe = ParseOptions::with_memoization(Memoization::SafeFailures);
    // Diagnostics are recorded only when the parse fails (unlaxer-parser #257).
    let deferred = safe.with_diagnostics(Diagnostics::DetailedOnFailure);
    let fixtures = FIXTURES
        .iter()
        .map(|&(name, file_name)| {
            let path = fixture_path(file_name);
            let source = fs::read_to_string(&path)
                .unwrap_or_else(|error| panic!("failed to read {}: {error}", path.display()));
            let tree = parser::parse_tree_detailed_with_options(&source, off)
                .unwrap_or_else(|error| panic!("invalid fixture {}: {error}", path.display()));
            mapper::map(&tree).unwrap_or_else(|error| {
                panic!("fixture {} cannot be mapped: {error}", path.display())
            });
            tinyexpression_rs::parse(&source).unwrap_or_else(|error| {
                panic!(
                    "fixture {} cannot be parsed and mapped: {error}",
                    path.display()
                )
            });
            (name, source, tree)
        })
        .collect::<Vec<_>>();

    let mut group = criterion.benchmark_group("tinyexpression");

    for (fixture_name, source, tree) in &fixtures {
        // This exercises the public generated path. The immutable grammar graph is
        // initialized once and shared; ParseContext and all mutable parse state stay local.
        group.bench_with_input(
            BenchmarkId::new("parse-only-off", fixture_name),
            source,
            |bencher, source| {
                bencher.iter(|| {
                    let tree =
                        parser::parse_tree_detailed_with_options(black_box(source.as_str()), off)
                            .expect("fixture was validated before measurement");
                    black_box(tree)
                });
            },
        );

        group.bench_with_input(
            BenchmarkId::new("parse-only-safe", fixture_name),
            source,
            |bencher, source| {
                bencher.iter(|| {
                    let tree =
                        parser::parse_tree_detailed_with_options(black_box(source.as_str()), safe)
                            .expect("fixture was validated before measurement");
                    black_box(tree)
                });
            },
        );

        group.bench_with_input(
            BenchmarkId::new("parse-only-deferred", fixture_name),
            source,
            |bencher, source| {
                bencher.iter(|| {
                    let tree = parser::parse_tree_detailed_with_options(
                        black_box(source.as_str()),
                        deferred,
                    )
                    .expect("fixture was validated before measurement");
                    black_box(tree)
                });
            },
        );

        group.bench_with_input(
            BenchmarkId::new("map-only", fixture_name),
            tree,
            |bencher, tree| {
                bencher.iter(|| {
                    let ast = mapper::map(black_box(tree))
                        .expect("fixture was validated before measurement");
                    black_box(ast)
                });
            },
        );

        group.bench_with_input(
            BenchmarkId::new("parse+map-off", fixture_name),
            source,
            |bencher, source| {
                bencher.iter(|| {
                    let tree =
                        parser::parse_tree_detailed_with_options(black_box(source.as_str()), off)
                            .expect("fixture was validated before measurement");
                    let ast = mapper::map(&tree).expect("fixture was validated before measurement");
                    black_box(ast)
                });
            },
        );

        group.bench_with_input(
            BenchmarkId::new("parse+map-safe", fixture_name),
            source,
            |bencher, source| {
                bencher.iter(|| {
                    let tree =
                        parser::parse_tree_detailed_with_options(black_box(source.as_str()), safe)
                            .expect("fixture was validated before measurement");
                    let ast = mapper::map(&tree).expect("fixture was validated before measurement");
                    black_box(ast)
                });
            },
        );
    }

    for &(fixture_name, file_name) in FACADE_FIXTURES {
        let path = fixture_path(file_name);
        let source = fs::read_to_string(&path)
            .unwrap_or_else(|error| panic!("failed to read {}: {error}", path.display()));
        tinyexpression_rs::parse(&source)
            .unwrap_or_else(|error| panic!("invalid facade fixture {}: {error}", path.display()));
        group.bench_with_input(
            BenchmarkId::new("public-facade", fixture_name),
            &source,
            |bencher, source| {
                bencher.iter(|| {
                    let ast = tinyexpression_rs::parse(black_box(source.as_str()))
                        .expect("fixture was validated before measurement");
                    black_box(ast)
                });
            },
        );
    }

    group.finish();
}

criterion_group! {
    name = benches;
    config = Criterion::default()
        .warm_up_time(Duration::from_secs(5))
        .measurement_time(Duration::from_secs(10))
        .sample_size(100);
    targets = parser_benchmarks
}
criterion_main!(benches);
