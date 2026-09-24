//! Dependency-free timing of the public `parse` entry point, for A/B runs against another
//! build of this crate on the same host.
//!
//! ```sh
//! cargo run --release --example parse-bench -- [--rounds N] FIXTURE...
//! ```
//!
//! Prints one TSV row per fixture: `name<TAB>samples<TAB>median_ms<TAB>min_ms`. Every sample
//! is one full `parse` of the whole file, after one warm-up parse.

use std::env;
use std::fs;
use std::path::Path;
use std::time::Instant;

fn main() {
    let mut rounds = 25usize;
    let mut paths = Vec::new();
    let mut arguments = env::args().skip(1);
    while let Some(argument) = arguments.next() {
        if argument == "--rounds" {
            rounds = arguments
                .next()
                .and_then(|value| value.parse().ok())
                .expect("--rounds takes a number");
        } else {
            paths.push(argument);
        }
    }
    assert!(
        !paths.is_empty(),
        "usage: parse-bench [--rounds N] FIXTURE..."
    );

    for path in &paths {
        let source = fs::read_to_string(path).unwrap_or_else(|error| panic!("{path}: {error}"));
        tinyexpression_rs::parse(&source).unwrap_or_else(|error| panic!("{path}: {error}"));
        let mut samples = Vec::with_capacity(rounds);
        for _ in 0..rounds {
            let start = Instant::now();
            let parsed = tinyexpression_rs::parse(&source);
            let elapsed = start.elapsed();
            assert!(parsed.is_ok(), "{path} stopped parsing mid-run");
            samples.push(elapsed.as_secs_f64() * 1000.0);
        }
        samples.sort_by(|left, right| left.partial_cmp(right).expect("no NaN timings"));
        let name = Path::new(path)
            .file_stem()
            .map_or(path.as_str(), |stem| stem.to_str().unwrap_or(path));
        println!(
            "{name}\t{}\t{:.4}\t{:.4}",
            samples.len(),
            samples[samples.len() / 2],
            samples[0]
        );
    }
}
