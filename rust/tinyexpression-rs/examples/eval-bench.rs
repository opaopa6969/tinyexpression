//! Dependency-free timing of the contextual runtime, stage by stage.
//!
//! ```sh
//! cargo run --release --example eval-bench -- [--runs N] [--iterations N] INPUT...
//! ```
//!
//! An INPUT is a `.tiny` file (one formula) or a `.tsv` file of `id<TAB>formula` rows.
//! Every formula is evaluated as result type `float` against an empty context with the default
//! host, the same setting as ubnfc's `StageTimingRunner` for the Java path.
//!
//! Prints one TSV row per formula:
//! `name  chars  parse_med  parse_min  compile_med  compile_min  tree_med  tree_min
//!  closure_med  closure_min  outcome` — parse/compile in ms (one sample = one call),
//! tree/closure in µs per evaluation (one sample = the mean over `--iterations` evaluations).
//! `parse` is `Program::new` (parse + evaluation-root selection).

use std::env;
use std::fs;
use std::path::Path;
use std::time::Instant;

use tinyexpression_rs::runtime::{
    calculator_result, Context, ContextClock, Host, NoExternals, Options, Program, ResultType,
    XorShiftRandom,
};

fn stats(mut samples: Vec<f64>) -> (f64, f64) {
    samples.sort_by(|a, b| a.partial_cmp(b).expect("no NaN timings"));
    (samples[samples.len() / 2], samples[0])
}

fn time<F: FnMut()>(runs: usize, mut action: F) -> (f64, f64) {
    for _ in 0..3 {
        action();
    }
    let samples = (0..runs)
        .map(|_| {
            let start = Instant::now();
            action();
            start.elapsed().as_secs_f64() * 1000.0
        })
        .collect();
    stats(samples)
}

fn main() {
    let mut runs = 9usize;
    let mut iterations = 2_000usize;
    let mut inputs = Vec::new();
    let mut arguments = env::args().skip(1);
    while let Some(argument) = arguments.next() {
        match argument.as_str() {
            "--runs" => {
                runs = arguments
                    .next()
                    .and_then(|v| v.parse().ok())
                    .expect("--runs N")
            }
            "--iterations" => {
                iterations = arguments
                    .next()
                    .and_then(|v| v.parse().ok())
                    .expect("--iterations N")
            }
            _ => inputs.push(argument),
        }
    }
    assert!(
        !inputs.is_empty(),
        "usage: eval-bench [--runs N] [--iterations N] INPUT..."
    );

    let mut formulas = Vec::new();
    for path in &inputs {
        let text = fs::read_to_string(path).unwrap_or_else(|e| panic!("{path}: {e}"));
        if path.ends_with(".tsv") {
            for line in text
                .lines()
                .filter(|l| !l.is_empty() && !l.starts_with('#'))
            {
                let (id, formula) = line.split_once('\t').expect("id<TAB>formula");
                formulas.push((id.to_owned(), formula.to_owned()));
            }
        } else {
            let name = Path::new(path)
                .file_name()
                .and_then(|n| n.to_str())
                .unwrap_or(path)
                .to_owned();
            formulas.push((name, text));
        }
    }

    println!("input\tchars\tparse_med_ms\tparse_min_ms\tcompile_med_ms\tcompile_min_ms\ttree_med_us\ttree_min_us\tclosure_med_us\tclosure_min_us\toutcome");
    let options = Options::new(ResultType::Float);
    for (name, formula) in formulas {
        let chars = formula.chars().count();
        let program = match Program::new(&formula, options) {
            Ok(program) => program,
            Err(error) => {
                println!(
                    "{name}\t{chars}\t-\t-\t-\t-\t-\t-\t-\t-\trejected: {}",
                    error.kind.java_name()
                );
                continue;
            }
        };
        let (parse_med, parse_min) = time(runs, || {
            Program::new(&formula, options).expect("parsed once already");
        });
        let (compile_med, compile_min) = time(runs, || {
            std::hint::black_box(program.compile());
        });
        let compiled = program.compile();
        let mut external = NoExternals;
        let mut random = XorShiftRandom::new(1);
        let mut outcome = String::new();
        let mut eval_timing = |closure: bool| {
            time(runs, || {
                for _ in 0..iterations {
                    let mut context = Context::new();
                    let mut host = Host {
                        external: &mut external,
                        clock: &ContextClock,
                        random: &mut random,
                    };
                    let result = if closure {
                        compiled.eval(&mut context, &mut host)
                    } else {
                        program.eval_tree(&mut context, &mut host)
                    };
                    std::hint::black_box(&result);
                    if outcome.is_empty() {
                        outcome = match calculator_result(result) {
                            Ok(value) => value.canonical_json(),
                            Err(error) => error.kind.java_name().to_owned(),
                        };
                    }
                }
            })
        };
        let per_iteration = |(med, min): (f64, f64)| {
            (
                med * 1000.0 / iterations as f64,
                min * 1000.0 / iterations as f64,
            )
        };
        let (tree_med, tree_min) = per_iteration(eval_timing(false));
        let (closure_med, closure_min) = per_iteration(eval_timing(true));
        println!(
            "{name}\t{chars}\t{parse_med:.3}\t{parse_min:.3}\t{compile_med:.3}\t{compile_min:.3}\t{tree_med:.3}\t{tree_min:.3}\t{closure_med:.3}\t{closure_min:.3}\t{outcome}"
        );
    }
}
