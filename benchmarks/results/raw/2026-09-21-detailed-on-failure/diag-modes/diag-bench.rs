use std::fs;
use std::time::Duration;
use criterion::{black_box, criterion_group, criterion_main, BenchmarkId, Criterion};
use tinyexpression_rs::generated::parser;
use unlaxer_runtime::{Diagnostics, Memoization, ParseOptions};

const FX: &str = "/home/opa/work/unlaxer-workspace/wt-te-next/benchmarks/fixtures";

fn bench(c: &mut Criterion) {
    let detailed = ParseOptions::with_memoization(Memoization::SafeFailures);
    let deferred = detailed.with_diagnostics(Diagnostics::DetailedOnFailure);
    let mut group = c.benchmark_group("diag");
    for name in ["complex", "complex-x4", "complex-x16", "complex-x64"] {
        let source = fs::read_to_string(format!("{FX}/{name}.tiny")).unwrap();
        let half = source[..source.len() / 2].to_string();
        let tail = format!("{source}@");
        for (label, input, ok) in [("ok", &source, true), ("half", &half, false), ("tail", &tail, false)] {
            for (mode, opts) in [("detailed", detailed), ("deferred", deferred)] {
                let r = parser::parse_tree_detailed_with_options(input, opts);
                assert_eq!(r.is_ok(), ok, "{name}/{label}/{mode}");
                group.bench_with_input(BenchmarkId::new(format!("{mode}/{label}"), name), input, |b, input| {
                    b.iter(|| black_box(parser::parse_tree_detailed_with_options(black_box(input.as_str()), opts)))
                });
            }
        }
    }
    group.finish();
}
criterion_group! { name = benches; config = Criterion::default().warm_up_time(Duration::from_secs(3)).measurement_time(Duration::from_secs(6)).sample_size(50); targets = bench }
criterion_main!(benches);
