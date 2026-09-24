use std::fs;
use std::path::PathBuf;
use std::time::Duration;

use criterion::{black_box, criterion_group, criterion_main, BenchmarkId, Criterion};
use tinyexpression_rs::generated::ubnfc::{parse_with_scanner, ParseOptions, ParseResult};
use tinyexpression_rs::generated::{compat, scanners};

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

fn parse(source: &str, options: ParseOptions) -> ParseResult {
    let mut scanner = scanners::registry();
    parse_with_scanner(source, options, &mut scanner)
}

fn parser_benchmarks(criterion: &mut Criterion) {
    let recognize = ParseOptions {
        build_ast: false,
        ..ParseOptions::default()
    };
    let with_ast = ParseOptions::default();
    let fixtures = FIXTURES
        .iter()
        .map(|&(name, file_name)| {
            let path = fixture_path(file_name);
            let source = fs::read_to_string(&path)
                .unwrap_or_else(|error| panic!("failed to read {}: {error}", path.display()));
            let result = parse(&source, with_ast);
            assert!(result.ok, "invalid fixture {}", path.display());
            let ast = result.ast.expect("fixture AST");
            compat::convert(&ast).unwrap_or_else(|error| {
                panic!("fixture {} cannot be mapped: {error}", path.display())
            });
            tinyexpression_rs::parse(&source).unwrap_or_else(|error| {
                panic!(
                    "fixture {} cannot be parsed and mapped: {error}",
                    path.display()
                )
            });
            (name, source, ast)
        })
        .collect::<Vec<_>>();

    let mut group = criterion.benchmark_group("tinyexpression");

    for (fixture_name, source, ast) in &fixtures {
        group.bench_with_input(
            BenchmarkId::new("recognize-only", fixture_name),
            source,
            |bencher, source| {
                bencher.iter(|| black_box(parse(black_box(source.as_str()), recognize)));
            },
        );

        group.bench_with_input(
            BenchmarkId::new("parse-with-ast", fixture_name),
            source,
            |bencher, source| {
                bencher.iter(|| black_box(parse(black_box(source.as_str()), with_ast)));
            },
        );

        group.bench_with_input(
            BenchmarkId::new("map-only", fixture_name),
            ast,
            |bencher, ast| {
                bencher.iter(|| {
                    let mapped = compat::convert(black_box(ast))
                        .expect("fixture was validated before measurement");
                    black_box(mapped)
                });
            },
        );

        group.bench_with_input(
            BenchmarkId::new("parse+map", fixture_name),
            source,
            |bencher, source| {
                bencher.iter(|| {
                    let result = parse(black_box(source.as_str()), with_ast);
                    let ast = result
                        .ast
                        .expect("fixture was validated before measurement");
                    let mapped =
                        compat::convert(&ast).expect("fixture was validated before measurement");
                    black_box(mapped)
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
